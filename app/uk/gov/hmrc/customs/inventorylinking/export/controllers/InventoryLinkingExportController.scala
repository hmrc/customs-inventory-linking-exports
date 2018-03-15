/*
 * Copyright 2018 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.customs.inventorylinking.export.controllers

import javax.inject.{Inject, Singleton}

import play.api.http.MimeTypes
import play.api.mvc._
import uk.gov.hmrc.auth.core.AuthProvider.{GovernmentGateway, PrivilegedApplication}
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.Retrievals
import uk.gov.hmrc.customs.api.common.controllers.ErrorResponse
import uk.gov.hmrc.customs.api.common.controllers.ErrorResponse.UnauthorizedCode
import uk.gov.hmrc.customs.inventorylinking.export.connectors.InventoryLinkingAuthConnector
import uk.gov.hmrc.customs.inventorylinking.export.controllers.CustomHeaderNames.X_CONVERSATION_ID_HEADER_NAME
import uk.gov.hmrc.customs.inventorylinking.export.logging.ExportsLogger
import uk.gov.hmrc.customs.inventorylinking.export.model.{BadgeIdentifier, ConversationId, Eori}
import uk.gov.hmrc.customs.inventorylinking.export.services.{CustomsConfigService, ExportsBusinessService, ProcessingResult}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.logging.Authorization
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.control.NonFatal
import scala.xml.NodeSeq

@Singleton
class InventoryLinkingExportController @Inject()(override val authConnector: InventoryLinkingAuthConnector,
                                                 customsConfigService: CustomsConfigService,
                                                 exportsBusinessService: ExportsBusinessService,
                                                 logger: ExportsLogger)
  extends BaseController
    with HeaderValidator with AuthorisedFunctions {

  private lazy val apiScopeKey = customsConfigService.apiDefinitionConfig.apiScope

  private lazy val enrolmentName = customsConfigService.customsEnrolmentConfig.customsEnrolmentName
  private lazy val enrolmentEoriIdentifier = customsConfigService.customsEnrolmentConfig.eoriIdentifierName

  private lazy val ErrorResponseEoriNotFoundInCustomsEnrolment =
    ErrorResponse(UNAUTHORIZED, UnauthorizedCode, "EORI number not found in Customs Enrolment")

  private lazy val ErrorResponseUnauthorisedGeneral =
    ErrorResponse(UNAUTHORIZED, UnauthorizedCode, "Unauthorised request")

  private def xmlOrEmptyBody: BodyParser[AnyContent] = BodyParser(rq => parse.xml(rq).map {
    case Right(xml) => Right(AnyContentAsXml(xml))
    case _ => Right(AnyContentAsEmpty)
  })

  def post(): Action[AnyContent] = validateHeaders().async(bodyParser = xmlOrEmptyBody) {
    implicit request =>
      val maybeBadgeIdentifier: Option[BadgeIdentifier] = extractBadgeIdentifier(request)

      logger.info(s"Inventory linking exports request received")
      logger.debug("Inventory Linking Exports request", payload = request.body.toString)
      request.body.asXml match {
        case Some(xml) => processXmlPayload(xml, maybeBadgeIdentifier)
        case None =>
          val errorMessage = "Request body does not contain well-formed XML."
          logger.error(errorMessage)
          Future.successful(ErrorResponse.errorBadRequest(errorMessage).XmlResult)
      }
  }

  private def extractBadgeIdentifier(request: Request[AnyContent]): Option[BadgeIdentifier] = {
    request.headers.get(XBadgeIdentifier) match {
      case Some(id) => Some(BadgeIdentifier(id))
      case _ => None
    }
  }

  private def processXmlPayload(xml: NodeSeq, maybeBadgeIdentifier: Option[BadgeIdentifier])(implicit hc: HeaderCarrier): Future[Result] = {
    logger.debug("processXmlPayload")
    (authoriseCspSubmission(xml, maybeBadgeIdentifier: Option[BadgeIdentifier]) orElseIfInsufficientEnrolments authoriseNonCspSubmission(xml) orElse unauthorised)
      .map {
        case Right(conversationId) => Accepted.as(MimeTypes.XML).withHeaders(X_CONVERSATION_ID_HEADER_NAME -> conversationId.value)
        case Left(errorResponse) => errorResponse.XmlResult
      }
      .recoverWith {
        case NonFatal(e) =>
          logger.error(s"Inventory linking call failed: ${e.getMessage}", e)
          Future.successful(ErrorResponse.ErrorInternalServerError.XmlResult)
      }
  }

  private def validateHeaders(): ActionBuilder[Request] = {
    validateAccept(acceptHeaderValidation) andThen validateContentType(contentTypeValidation) andThen validateXBadgeIdentifier(badgeIdentifierValidation)
  }

  private def authoriseCspSubmission(xml: NodeSeq, maybeBadgeIdentifier: Option[BadgeIdentifier])(implicit hc: HeaderCarrier): Future[ProcessingResult] = {
    authorised(Enrolment(apiScopeKey) and AuthProviders(PrivilegedApplication)) {

      maybeBadgeIdentifier match {
        case None =>
          logger.error("Header validation failed because X-Badge-Identifier header is missing or invalid")
          Future.successful (Left(ErrorResponseBadgeIdentifierHeaderMissing))
        case Some(_) =>
          logger.info("[authoriseCspSubmission] Processing an authorised CSP submission.")
          exportsBusinessService.authorisedCspSubmission(xml, maybeBadgeIdentifier)
      }
    }
  }

  private def authoriseNonCspSubmission(xml: NodeSeq)(implicit hc: HeaderCarrier): Future[ProcessingResult] = {
    logger.info("Authorising a non-CSP request.")
    authorised(Enrolment(enrolmentName) and AuthProviders(GovernmentGateway)).retrieve(Retrievals.authorisedEnrolments) {
      enrolments =>
        val maybeEori = findEoriInCustomsEnrolment(enrolments, hc.authorization)
        logger.debug(s"EORI from Customs enrolment for non-CSP request: $maybeEori")
        maybeEori match {
          case Some(_) =>
            logger.info("Processing an authorised non-CSP submission.")
            exportsBusinessService.authorisedNonCspSubmission(xml)

          case _ => Future.successful(Left(ErrorResponseEoriNotFoundInCustomsEnrolment))
        }
    }
  }

  private def findEoriInCustomsEnrolment(enrolments: Enrolments, authHeader: Option[Authorization])(implicit hc: HeaderCarrier): Option[Eori] = {
    val maybeCustomsEnrolment = enrolments.getEnrolment(enrolmentName)
    if (maybeCustomsEnrolment.isEmpty) {
      logger.warn(s"Customs enrolment $enrolmentName not retrieved for authorised non-CSP call")
    }
    for {
      customsEnrolment <- maybeCustomsEnrolment
      eoriIdentifier <- customsEnrolment.getIdentifier(enrolmentEoriIdentifier)
    } yield Eori(eoriIdentifier.value)
  }

  private def unauthorised(authException: AuthorisationException)(implicit hc: HeaderCarrier): Future[Left[ErrorResponse, ConversationId]] = {
    logger.error("Unauthorized inventory linking exports call", authException)
    Future.successful(Left(ErrorResponseUnauthorisedGeneral))
  }

  private implicit class AuthOps(val authFuture: Future[ProcessingResult]) {
    def orElseIfInsufficientEnrolments(elseFuture: => Future[ProcessingResult]): Future[ProcessingResult] = {
      authFuture recoverWith {
        case _: InsufficientEnrolments => elseFuture
      }
    }

    def orElse(elseFuture: AuthorisationException => Future[ProcessingResult]): Future[ProcessingResult] =
      authFuture recoverWith {
        case authException: AuthorisationException => elseFuture(authException)
      }
  }
}
