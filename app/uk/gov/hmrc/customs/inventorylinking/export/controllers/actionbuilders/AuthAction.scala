/*
 * Copyright 2024 HM Revenue & Customs
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

import play.api.mvc._
import uk.gov.hmrc.customs.api.common.controllers.ErrorResponse.errorInternalServerError
import uk.gov.hmrc.customs.inventorylinking.`export`.model.{Csp, CspWithBadgeId, CspWithEori, CspWithEoriAndBadgeId, Eori}
import uk.gov.hmrc.customs.inventorylinking.export.controllers.HeaderValidator
import uk.gov.hmrc.customs.inventorylinking.export.logging.ExportsLogger
import uk.gov.hmrc.customs.inventorylinking.export.model.actionbuilders.ActionBuilderModelHelper._
import uk.gov.hmrc.customs.inventorylinking.export.model.actionbuilders.{ApiSubscriptionFieldsRequest, AuthorisedRequest}
import uk.gov.hmrc.customs.inventorylinking.export.services.CustomsAuthService
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.http.HeaderCarrierConverter

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

/** Action builder that attempts to authorise request as a CSP or else NON CSP
 * <ul>
 * <li/>INPUT - `ValidatedHeadersRequest`
 * <li/>OUTPUT - `AuthorisedRequest` - authorised will be `AuthorisedAs.Csp` or `AuthorisedAs.NonCsp`
 * <li/>ERROR -
 * <ul>
 * <li/>401 if authorised as non-CSP but enrolments does not contain an EORI.
 * <li/>401 if not authorised as CSP or non-CSP
 * <li/>500 on any downstream errors returning 500
 * </ul>
 * </ul>
 */
@Singleton
class AuthAction @Inject()(customsAuthService: CustomsAuthService,
                           headerValidator: HeaderValidator,
                           logger: ExportsLogger)
                          (implicit ec: ExecutionContext)
  extends ActionRefiner[ApiSubscriptionFieldsRequest, AuthorisedRequest] {

  protected[this] def executionContext: ExecutionContext = ec

  override def refine[A](asfr: ApiSubscriptionFieldsRequest[A]): Future[Either[Result, AuthorisedRequest[A]]] = {
    implicit val implicitAsfr: ApiSubscriptionFieldsRequest[A] = asfr

    implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(asfr)

    customsAuthService.authAsCsp.flatMap {
      case Right(true) =>
        Future.successful(maybeCspAuthorisedRequest(asfr))
      case Right(false) =>
        customsAuthService.authAsNonCsp.map {
          case Right(nonCspData) =>
            Right(asfr.toAuthorisedRequest(nonCspData))
          case Left(errorResponse) =>
            Left(errorResponse.XmlResult.withConversationId)
        }
      case Left(result) =>
        Future.successful(Left(result.XmlResult.withConversationId))
    }
  }

  private def maybeCspAuthorisedRequest[A](implicit asfr: ApiSubscriptionFieldsRequest[A])
  : Either[Result, AuthorisedRequest[A]] = {
    def toRight(csp: Csp): Right[Nothing, AuthorisedRequest[A]] = Right(asfr.toAuthorisedRequest(csp))

    val maybeAuthenticatedEori = asfr.apiSubscriptionFields.fields.authenticatedEori.flatMap(Eori.fromString)
    (for {
      maybeEori <- headerValidator.eoriMustBeValidIfPresent
      maybeBadgeId <- headerValidator.eitherBadgeIdentifier
    } yield (maybeEori, maybeBadgeId, maybeAuthenticatedEori)) match {
      case Right((Some(eori), None, _)) =>
        toRight(CspWithEori(eori))
      case Right((None, Some(badgeId), _)) =>
        toRight(CspWithBadgeId(badgeId))
      case Right((Some(eori), Some(badgeId), _)) =>
        toRight(CspWithEoriAndBadgeId(eori, badgeId))
      case Right((None, None, Some(authenticatedEori))) =>
        toRight(CspWithEori(authenticatedEori))
      case Right((None, None, None)) =>
        val msg = "Missing authenticated eori in service lookup. Alternately, use X-Badge-Identifier or X-Submitter-Identifier headers."
        logger.error(s"For CSP request $msg")
        Left(errorInternalServerError(msg).XmlResult.withConversationId)
      case Left(result) =>
        Left(result.XmlResult.withConversationId)
    }
  }
}
