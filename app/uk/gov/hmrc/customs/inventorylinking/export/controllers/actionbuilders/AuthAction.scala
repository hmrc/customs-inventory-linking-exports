/*
 * Copyright 2022 HM Revenue & Customs
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
import play.api.mvc._
import uk.gov.hmrc.customs.api.common.controllers.ErrorResponse
import uk.gov.hmrc.customs.api.common.controllers.ErrorResponse.errorInternalServerError
import uk.gov.hmrc.customs.inventorylinking.export.model.actionbuilders.{ApiSubscriptionFieldsRequest, HasConversationId, HasRequest}
import uk.gov.hmrc.customs.inventorylinking.export.model.{AuthorisedAsCsp, BadgeIdentifier, Csp, Eori}
import uk.gov.hmrc.customs.inventorylinking.export.controllers.HeaderValidator
import uk.gov.hmrc.customs.inventorylinking.export.logging.ExportsLogger
import uk.gov.hmrc.customs.inventorylinking.export.model.actionbuilders.ActionBuilderModelHelper._
import uk.gov.hmrc.customs.inventorylinking.export.model.actionbuilders.AuthorisedRequest
import uk.gov.hmrc.customs.inventorylinking.export.services.CustomsAuthService
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.http.HeaderCarrierConverter

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Left

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
    implicit def hc(implicit rh: RequestHeader): HeaderCarrier = HeaderCarrierConverter.fromRequest(rh)

    authAsCspWithOptionalAuthHeaders.flatMap{
      case Right(maybeAuthorisedAsCspWithIdentifierHeaders) =>
        maybeAuthorisedAsCspWithIdentifierHeaders.fold{
          customsAuthService.authAsNonCsp.map[Either[Result, AuthorisedRequest[A]]]{
            case Left(errorResponse) =>
              Left(errorResponse.XmlResult.withConversationId)
            case Right(nonCspData) =>
              Right(asfr.toNonCspAuthorisedRequest(nonCspData.eori))
          }
        }{ cspData =>
          if (validIdentifier(cspData, asfr.apiSubscriptionFields.fields.authenticatedEori)) {
            Future.successful(Right(asfr.toCspAuthorisedRequest(cspData)))
          } else {
            val msg = "Missing authenticated eori in service lookup. Alternately, use X-Badge-Identifier or X-Submitter-Identifier headers."
            logger.error(s"For CSP request $msg")
            Future.successful(Left(errorInternalServerError(msg).XmlResult.withConversationId))
          }
        }
      case Left(result) =>
        Future.successful(Left(result.XmlResult.withConversationId))
    }
  }

  private def authAsCspWithOptionalAuthHeaders[A](implicit vfr: HasRequest[A] with HasConversationId, hc: HeaderCarrier): Future[Either[ErrorResponse, Option[AuthorisedAsCsp]]] = {
    customsAuthService.authAsCsp.map {
      case Right(isCsp) =>
        if (isCsp) {
            eitherCspAuthData.right.map(authAsCsp => Some(authAsCsp))
        } else {
          Right(None)
        }
      case Left(errorResponse) =>
        Left(errorResponse)
    }
  }

  def eitherCspAuthData[A](implicit vhr: HasRequest[A] with HasConversationId): Either[ErrorResponse, AuthorisedAsCsp] = {
    for {
      maybeBadgeId <- eitherBadgeIdentifier(allowNone = true).right
      maybeEori <- eitherEori.right
    } yield Csp(maybeEori, maybeBadgeId)
  }

  private def eitherEori[A](implicit vhr: HasRequest[A] with HasConversationId): Either[ErrorResponse, Option[Eori]] = {
    headerValidator.eoriMustBeValidIfPresent
  }

  protected def eitherBadgeIdentifier[A](allowNone: Boolean)(implicit vhr: HasRequest[A] with HasConversationId): Either[ErrorResponse, Option[BadgeIdentifier]] = {
    headerValidator.eitherBadgeIdentifier(allowNone = allowNone)
  }

  private def validIdentifier(cspData: AuthorisedAsCsp, authenticatedEori: Option[String]): Boolean = {
    if (!cspData.isEmpty || (authenticatedEori.isDefined && !authenticatedEori.get.trim.isEmpty)) {
      true
    } else {
      false
    }
  }

}
