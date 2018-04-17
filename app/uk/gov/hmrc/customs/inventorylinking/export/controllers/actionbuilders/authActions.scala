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

package uk.gov.hmrc.customs.inventorylinking.export.controllers.actionbuilders

import javax.inject.{Inject, Singleton}

import play.api.http.Status
import play.api.http.Status.UNAUTHORIZED
import play.api.mvc.{ActionFunction, ActionRefiner, RequestHeader, Result}
import uk.gov.hmrc.auth.core.AuthProvider.{GovernmentGateway, PrivilegedApplication}
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.Retrievals
import uk.gov.hmrc.customs.api.common.controllers.ErrorResponse
import uk.gov.hmrc.customs.api.common.controllers.ErrorResponse.{UnauthorizedCode, errorBadRequest}
import uk.gov.hmrc.customs.inventorylinking.export.connectors.InventoryLinkingAuthConnector
import uk.gov.hmrc.customs.inventorylinking.export.logging.ExportsLogger2
import uk.gov.hmrc.customs.inventorylinking.export.model.actionbuilders.ActionBuilderModelHelper._
import uk.gov.hmrc.customs.inventorylinking.export.model.actionbuilders.{AuthorisedRequest, CorrelationIds, ValidatedHeadersRequest}
import uk.gov.hmrc.customs.inventorylinking.export.model.{AuthorisedAs, Eori, HeaderConstants}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.logging.Authorization
import uk.gov.hmrc.play.HeaderCarrierConverter

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.control.NonFatal

trait AuthAction {
  protected type EitherResultOrAuthRequest[A] = Either[Result, AuthorisedRequest[A]]

  protected val errorResponseUnauthorisedGeneral =
    ErrorResponse(Status.UNAUTHORIZED, UnauthorizedCode, "Unauthorised request")

}

/** Composed action builder that attempts to authorise request as a CSP else a NON CSP
  * <li/>INPUT - `ValidatedHeadersRequest` - maybeAuthorised is `None`
  * <li/>OUTPUT - `AuthorisedRequest` - if authorised maybeAuthorised will be `Some(AuthorisedAs.Csp)` or `Some(AuthorisedAs.Csp)`
  * <li/>ERROR - 401 if not authorised as CSP or NON CSP. On any downstream errors it returns 500
  */
@Singleton
class CspAndThenNonCspAuthAction @Inject()(cspAuthAction: CspAuthAction, nonCspAuthAction: NonCspAuthAction) {

  lazy val authAction: ActionFunction[ValidatedHeadersRequest, AuthorisedRequest] = cspAuthAction andThen nonCspAuthAction
}


/** Action builder that attempts to authorise request as a CSP
  * <li/>INPUT - `ValidatedHeadersRequest` - maybeAuthorised is `None`
  * <li/>OUTPUT - `AuthorisedRequest` - if CSP, maybeAuthorised will be `Some(AuthorisedAs.Csp)`, otherwise maybeAuthorised will be `None`
  * <li/>ERROR - 401 if authorised as CSP but badge identifier is missing. On any downstream errors it returns 500
  */
@Singleton
class CspAuthAction @Inject()(
  override val authConnector: InventoryLinkingAuthConnector,
  logger: ExportsLogger2
  ) extends ActionRefiner[ValidatedHeadersRequest, AuthorisedRequest] with AuthorisedFunctions with AuthAction {

  private val errorResponseBadgeIdentifierHeaderMissing = errorBadRequest(s"${HeaderConstants.XBadgeIdentifierHeaderName} header is missing or invalid")

  override def refine[A](vr: ValidatedHeadersRequest[A]): Future[EitherResultOrAuthRequest[A]] = {
    implicit val todoRemove = vr

    logger.debug("in CSP authorisation")
    authoriseAsCsp[A]
  }

  // pure function that tames exceptions throw by HMRC auth api into an Either
  // this enables calling function to not worry about recover blocks
  // returns a Future of Left(Result) on error or a Right(AuthorisedRequest) on success
  private def authoriseAsCsp[A](implicit vr: ValidatedHeadersRequest[A]): Future[EitherResultOrAuthRequest[A]] = {
    implicit def hc(implicit rh: RequestHeader): HeaderCarrier = HeaderCarrierConverter.fromHeadersAndSession(rh.headers)

    authorised(Enrolment("write:customs-inventory-linking-exports") and AuthProviders(PrivilegedApplication)) {
      Future.successful{
        if (vr.maybeBadgeIdentifier.isDefined) {
          logger.debug("found badge identifier for CSP")
          Right(authorisedRequest(vr, Some(AuthorisedAs.Csp)))
        } else {
          logger.debug("badge identifier not present for CSP")
          Left(errorResponseBadgeIdentifierHeaderMissing.XmlResult.withConversationId)
        }
      }
    }.recover{
      case NonFatal(_: AuthorisationException) =>
        logger.debug("Not authorised as CSP")
        Right(authorisedRequest(vr))
      case NonFatal(e) =>
        val msg = "Error authorising CSP"
        logger.debug(msg, e)
        logger.error(msg) //TODO: confirm if we are showing stack trace for errors these days
        throw e
    }
  }

}

/** Action builder that attempts to authorise request as a NON CSP
  * <ul>
  * <li/>INPUT - `ValidatedHeadersRequest` - maybeAuthorised may be  be `None` or `Some(AuthorisedAs.Csp)` (processing is skipped)
  * <li/>OUTPUT - `AuthorisedRequest` - if CSP, maybeAuthorised will be `Some(AuthorisedAs.Csp)` or `Some(AuthorisedAs.NonCsp)`
  * <li/>ERROR -
  * <ul>
  * <li/>401 if not authorised as NON CSP
  * <li/>401 if authorised as NON CSP but enrolments does not contain an EORI.
  * <li/>500 on any downstream errors it returns 500
  * </ul>
  * </ul>
  */
@Singleton
class NonCspAuthAction @Inject()(
                               override val authConnector: InventoryLinkingAuthConnector,
                               logger: ExportsLogger2
                             ) extends ActionRefiner[AuthorisedRequest, AuthorisedRequest] with AuthorisedFunctions with AuthAction {

  private lazy val errorResponseEoriNotFoundInCustomsEnrolment =
    ErrorResponse(UNAUTHORIZED, UnauthorizedCode, "EORI number not found in Customs Enrolment")


  override def refine[A](ar: AuthorisedRequest[A]): Future[EitherResultOrAuthRequest[A]] = {
    implicit val todoRemove = ar

    logger.debug("in Non CSP authorisation.")

    if (ar.maybeAuthorised.isEmpty) {
      logger.debug("about to authorise as Non CSP")
      authoriseAsNonCsp[A]
    } else {
      logger.debug("already authorised as CSP")
      Future.successful(Right(ar))
    }
  }

  // pure function that tames exceptions throw by HMRC auth api into an Either
  // this enables calling function to not worry about recover blocks
  // returns a Future of Left(Result) on error or a Right(AuthorisedRequest) on success
  private def authoriseAsNonCsp[A](implicit ar: AuthorisedRequest[A]): Future[EitherResultOrAuthRequest[A]] = {
    implicit def hc(implicit rh: RequestHeader): HeaderCarrier = HeaderCarrierConverter.fromHeadersAndSession(rh.headers)

    authorised(Enrolment("HMRC-CUS-ORG") and AuthProviders(GovernmentGateway)).retrieve(Retrievals.authorisedEnrolments) {
      enrolments =>
        val maybeEori: Option[Eori] = findEoriInCustomsEnrolment(enrolments, hc.authorization)
        logger.debug(s"EORI from Customs enrolment for non-CSP request: $maybeEori")
        maybeEori match {
          case Some(_) =>
            logger.info("Processing an authorised non-CSP submission.")
            Future.successful(Right(ar.asNonCsp))
          case _ =>
            Future.successful(Left(errorResponseEoriNotFoundInCustomsEnrolment.XmlResult.withConversationId))
        }
    }.recover{
      case NonFatal(_: AuthorisationException) =>
        Left(errorResponseUnauthorisedGeneral.XmlResult.withConversationId)
      case NonFatal(e) =>
        val msg = "Error authorising Non CSP"
        logger.debug(msg, e)
        logger.error(msg) //TODO: confirm if we are showing stack trace for errors these days
        throw e
    }
  }

  private def findEoriInCustomsEnrolment[A](enrolments: Enrolments, authHeader: Option[Authorization])(implicit ar: AuthorisedRequest[A], hc: HeaderCarrier): Option[Eori] = {
    val maybeCustomsEnrolment = enrolments.getEnrolment("HMRC-CUS-ORG")
    if (maybeCustomsEnrolment.isEmpty) {
      logger.warn(s"Customs enrolment HMRC-CUS-ORG not retrieved for authorised non-CSP call")
    }
    for {
      customsEnrolment <- maybeCustomsEnrolment
      eoriIdentifier <- customsEnrolment.getIdentifier("EORINumber")
    } yield Eori(eoriIdentifier.value)
  }

}


