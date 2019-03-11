/*
 * Copyright 2019 HM Revenue & Customs
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

package uk.gov.hmrc.customs.inventorylinking.export.controllers.actionbuilders

import javax.inject.{Inject, Singleton}
import play.api.http.Status
import play.api.http.Status.UNAUTHORIZED
import play.api.mvc.{ActionRefiner, RequestHeader, Result}
import uk.gov.hmrc.auth.core.AuthProvider.{GovernmentGateway, PrivilegedApplication}
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.Retrievals
import uk.gov.hmrc.customs.api.common.controllers.ErrorResponse
import uk.gov.hmrc.customs.api.common.controllers.ErrorResponse.{ErrorInternalServerError, UnauthorizedCode, errorBadRequest}
import uk.gov.hmrc.customs.inventorylinking.export.controllers.CustomHeaderNames._
import uk.gov.hmrc.customs.inventorylinking.export.logging.ExportsLogger
import uk.gov.hmrc.customs.inventorylinking.export.model.actionbuilders.ActionBuilderModelHelper._
import uk.gov.hmrc.customs.inventorylinking.export.model.actionbuilders.{ApiSubscriptionFieldsRequest, AuthorisedRequest, ValidatedHeadersRequest}
import uk.gov.hmrc.customs.inventorylinking.export.model.{BadgeIdentifier, BadgeIdentifierEoriPair, Eori}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.logging.Authorization
import uk.gov.hmrc.play.HeaderCarrierConverter

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Left
import scala.util.control.NonFatal

@Singleton
class AuthAction @Inject()(
                            override val authConnector: AuthConnector,
                            logger: ExportsLogger
                          ) extends ActionRefiner[ApiSubscriptionFieldsRequest, AuthorisedRequest] with AuthorisedFunctions {
  protected type EitherResultOrAuthRequest[A] = Either[Result, AuthorisedRequest[A]]

  protected val errorResponseUnauthorisedGeneral =
    ErrorResponse(Status.UNAUTHORIZED, UnauthorizedCode, "Unauthorised request")
  private val errorResponseBadgeIdentifierHeaderMissing = errorBadRequest(s"$XBadgeIdentifierHeaderName header is missing or invalid")
  private val errorResponseSubmitterIdentifierHeaderInvalid = errorBadRequest(s"$XSubmitterIdentifierHeaderName header is invalid")

  private lazy val errorResponseEoriNotFoundInCustomsEnrolment =
    ErrorResponse(UNAUTHORIZED, UnauthorizedCode, "EORI number not found in Customs Enrolment")
  private lazy val xBadgeIdentifierRegex = "^[0-9A-Z]{6,12}$".r

  private lazy val xSubmitterIdentifierRegex = "^[0-9A-Za-z]{1,17}$".r

  override def refine[A](asf: ApiSubscriptionFieldsRequest[A]): Future[Either[Result, AuthorisedRequest[A]]] = {
    implicit val implicitAsf: ApiSubscriptionFieldsRequest[A] = asf

    authoriseAsCsp.flatMap {
      case Right(maybeAuthorisedAsCspWithBadgeIdAndEori) =>
        maybeAuthorisedAsCspWithBadgeIdAndEori.fold {
          authoriseAsNonCsp
        } { pair =>
          Future.successful(Right(asf.toCspAuthorisedRequest(pair)))
        }
      case Left(result) =>
        Future.successful(Left(result))
    }
  }

  // pure function that tames exceptions throw by HMRC auth api into an Either
  // this enables calling function to not worry about recover blocks
  // returns a Future of Left(Result) on error or a Right(AuthorisedRequest) on success
  private def authoriseAsCsp[A](implicit asf: ApiSubscriptionFieldsRequest[A]): Future[Either[Result, Option[BadgeIdentifierEoriPair]]] = {
    implicit def hc(implicit rh: RequestHeader): HeaderCarrier = HeaderCarrierConverter.fromHeadersAndSession(rh.headers)

    authorised(Enrolment("write:customs-inventory-linking-exports") and AuthProviders(PrivilegedApplication)) {
      Future.successful(eitherMaybeBadgeIdentifierEoriPair)
    }.recover[Either[Result, Option[BadgeIdentifierEoriPair]]] {
      case NonFatal(_: AuthorisationException) =>
        logger.debug("Not authorised as CSP")
        Right(None)
      case NonFatal(e) =>
        logger.error("Error authorising CSP", e)
        Left(ErrorInternalServerError.XmlResult.withConversationId)
    }
  }

  private def eitherSubmitterIdentifierWithValidationCSP[A](implicit asf: ApiSubscriptionFieldsRequest[A]) = {
    val maybeSubmitterId: Option[String] = maybeHeaderCaseInsensitive(XSubmitterIdentifierHeaderName)
    maybeSubmitterId.fold[Either[Result, Eori]] {
      asf.apiSubscriptionFields.fields.authenticatedEori.fold[Either[Result, Eori]] {
        logger.error(s"Submitter identifier and Authenticated EORI not present for CSP")
        Left(ErrorInternalServerError.XmlResult.withConversationId)
      } { eoriFromFields =>
        if (eoriFromFields.trim.nonEmpty) {
          Right(Eori(eoriFromFields))
        } else {
          logger.error(s"Authenticated EORI ($eoriFromFields) invalid for CSP")
          Left(ErrorInternalServerError.XmlResult.withConversationId)
        }
      }
    }{eoriFromHeader =>
      if (validEori(eoriFromHeader)) {
        Right(Eori(eoriFromHeader))
      } else {
        logger.error(s"Submitter identifier ($eoriFromHeader) invalid for CSP")
        Left(errorResponseSubmitterIdentifierHeaderInvalid.XmlResult.withConversationId)
      }
    }
  }

  private def eitherBadgeIdentifierWithValidation[A](implicit asf: ApiSubscriptionFieldsRequest[A]) = {
    val maybeBadgeIdString: Option[String] = maybeHeaderCaseInsensitive(XBadgeIdentifierHeaderName)
    maybeBadgeIdString.filter(xBadgeIdentifierRegex.findFirstIn(_).nonEmpty).map(BadgeIdentifier).fold[Either[Result, BadgeIdentifier]] {
      logger.error("badge identifier invalid or not present for CSP")
      Left(errorResponseBadgeIdentifierHeaderMissing.XmlResult.withConversationId)
    } { badgeId: BadgeIdentifier => Right(badgeId) }
  }


  private def validEori(eori: String): Boolean = {
    xSubmitterIdentifierRegex.findFirstIn(eori).nonEmpty
  }

  private def maybeValidEori(maybeValue: Option[String]) = {
    maybeValue.filter(validEori).map(Eori)
  }

  private def eitherMaybeBadgeIdentifierEoriPair[A](implicit asf: ApiSubscriptionFieldsRequest[A]): Either[Result, Some[BadgeIdentifierEoriPair]] = {
    for {
      badgeId <- eitherBadgeIdentifierWithValidation.right
      eori <- eitherSubmitterIdentifierWithValidationCSP.right
    } yield Some(BadgeIdentifierEoriPair(badgeId, eori))

  }

  // pure function that tames exceptions throw by HMRC auth api into an Either
  // this enables calling function to not worry about recover blocks
  // returns a Future of Left(Result) on error or a Right(AuthorisedRequest) on success
  private def authoriseAsNonCsp[A](implicit asf: ApiSubscriptionFieldsRequest[A]): Future[Either[Result, AuthorisedRequest[A]]] = {
    implicit def hc(implicit rh: RequestHeader): HeaderCarrier = HeaderCarrierConverter.fromHeadersAndSession(rh.headers)

    authorised(Enrolment("HMRC-CUS-ORG") and AuthProviders(GovernmentGateway)).retrieve(Retrievals.authorisedEnrolments) {
      enrolments =>
        val maybeEori: Option[Eori] = findEoriInCustomsEnrolment(enrolments, hc.authorization)
        logger.debug(s"EORI from Customs enrolment for non-CSP request: $maybeEori")
        maybeEori.fold[Future[Either[Result, AuthorisedRequest[A]]]] {
          Future.successful(Left(errorResponseEoriNotFoundInCustomsEnrolment.XmlResult.withConversationId))
        } { eori =>

          val maybeSubmitterHeader = maybeHeaderCaseInsensitive(XSubmitterIdentifierHeaderName)

          def logAndRight(anEori: Eori): Future[Either[Result, AuthorisedRequest[A]]] = {
            logger.debug("Authorising as non-CSP")
            Future.successful(Right(asf.toNonCspAuthorisedRequest(anEori)))
          }

          maybeSubmitterHeader match {
            case Some(submitterFromHeader) => maybeValidEori(maybeSubmitterHeader) match {
              case Some(validSubmitterFromHeader) =>
                logger.debug(s"Submitter passed in header was in a valid format: $validSubmitterFromHeader")
                logAndRight(eori)
              case None =>
                logger.error(s"Submitter passed in header was in an invalid format: $submitterFromHeader")
                Future.successful(Left(errorResponseSubmitterIdentifierHeaderInvalid.XmlResult.withConversationId))
            }
            case None =>
              logger.debug("No Submitter passed in header")
              logAndRight(eori)
          }
        }
    }.recover {
      case NonFatal(_: AuthorisationException) =>
        Left(errorResponseUnauthorisedGeneral.XmlResult.withConversationId)
      case NonFatal(e) =>
        logger.error("Error authorising Non CSP", e)
        Left(ErrorInternalServerError.XmlResult.withConversationId)
    }
  }

  private def maybeHeaderCaseInsensitive[A](headerName: String)(implicit asf: ApiSubscriptionFieldsRequest[A]) = {
    asf.request.headers.toSimpleMap.get(headerName)
  }

  private def findEoriInCustomsEnrolment[A](enrolments: Enrolments, authHeader: Option[Authorization])(implicit asf: ApiSubscriptionFieldsRequest[A], hc: HeaderCarrier): Option[Eori] = {
    val maybeCustomsEnrolment = enrolments.getEnrolment("HMRC-CUS-ORG")
    if (maybeCustomsEnrolment.isEmpty) {
      logger.warn(s"Customs enrolment HMRC-CUS-ORG not retrieved for authorised non-CSP call")
    }
    for {
      customsEnrolment <- maybeCustomsEnrolment
      eori <- customsEnrolment.getIdentifier("EORINumber")
    } yield Eori(eori.value)
  }

}
