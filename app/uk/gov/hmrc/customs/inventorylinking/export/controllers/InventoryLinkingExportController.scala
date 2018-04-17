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
import uk.gov.hmrc.customs.api.common.controllers.ErrorResponse
import uk.gov.hmrc.customs.inventorylinking.export.controllers.actionbuilders.{CorrelationIdsAction, CspAndThenNonCspAuthAction, ValidateAndExtractHeadersAction}
import uk.gov.hmrc.customs.inventorylinking.export.logging.ExportsLogger
import uk.gov.hmrc.customs.inventorylinking.export.model._
import uk.gov.hmrc.customs.inventorylinking.export.model.actionbuilders.ActionBuilderModelHelper._
import uk.gov.hmrc.customs.inventorylinking.export.model.actionbuilders.AuthorisedRequest
import uk.gov.hmrc.customs.inventorylinking.export.services.{CustomsConfigService, ExportsBusinessService}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.control.NonFatal
import scala.xml.NodeSeq

@Singleton
class InventoryLinkingExportController @Inject()(
                                                  authAction: CspAndThenNonCspAuthAction,
                                                  customsConfigService: CustomsConfigService,
                                                  exportsBusinessService: ExportsBusinessService,
                                                  correlationIdsAction: CorrelationIdsAction,
                                                  validateAndExtractHeadersAction: ValidateAndExtractHeadersAction,
                                                  logger: ExportsLogger)
  extends BaseController {

  private def xmlOrEmptyBody: BodyParser[AnyContent] = BodyParser(rq => parse.xml(rq).map {
    case Right(xml) =>
      Right(AnyContentAsXml(xml))
    case _ =>
      Right(AnyContentAsEmpty)
  })

  def post(): Action[AnyContent] = (
    Action andThen
    correlationIdsAction andThen
    validateAndExtractHeadersAction andThen
    authAction.authAction
    ).async(bodyParser = xmlOrEmptyBody) {
    implicit ar: AuthorisedRequest[AnyContent] =>

      // TODO: remove Ids after PayloadValidationAction is wired in
      val ids = Ids(ar.conversationId, ar.correlationId, ar.maybeBadgeIdentifier)

      logger.debug(s"Request received. Payload = ${ar.body.toString} headers = ${ar.headers.headers} ids = $ids")
      logger.info(s"Inventory linking exports request received")

      processRequest(ids)(ar)
  }

  /*
  called processXmlPayload
  which calls authoriseCspSubmission/authoriseNonCspSubmission
  which in turn call exportsBusinessService
  which in turn calls xmlValidationService.validate and sends submission
   */
  private def processRequest(ids: Ids)(implicit ar: AuthorisedRequest[AnyContent]): Future[Result] = {
    ar.body.asXml  match {
      case Some(xml) =>
        processXmlPayload(xml, ids)
      case None =>
        val errorMessage = "Request body does not contain well-formed XML."
        logger.error(errorMessage)
        Future.successful(ErrorResponse.errorBadRequest(errorMessage).XmlResult.withConversationId)
    }
  }

  private def processXmlPayload(xml: NodeSeq, ids: Ids)(implicit ar: AuthorisedRequest[AnyContent], hc: HeaderCarrier): Future[Result] = {
    logger.debug("processXmlPayload")
    (ar.maybeAuthorised match {
      case Some(AuthorisedAs.Csp) =>
        exportsBusinessService.authorisedCspSubmission(xml, ids)
      case Some(AuthorisedAs.NonCsp) =>
        exportsBusinessService.authorisedNonCspSubmission(xml, ids)
      //TODO: deal with no match case after PayloadValidationAction is wired in
    }).map {
      case Right(_) => Accepted.as(MimeTypes.XML).withConversationId
      case Left(errorResponse) => errorResponse.XmlResult.withConversationId
    }
      .recoverWith {
        case NonFatal(e) =>
          logger.error(s"Inventory linking call failed: ${e.getMessage}", e)
          Future.successful(ErrorResponse.ErrorInternalServerError.XmlResult.withConversationId)
      }

  }
}
