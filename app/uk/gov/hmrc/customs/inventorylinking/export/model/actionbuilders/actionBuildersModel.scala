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

package uk.gov.hmrc.customs.inventorylinking.export.model.actionbuilders

import play.api.mvc.{Request, Result, WrappedRequest}
import uk.gov.hmrc.customs.inventorylinking.export.controllers.CustomHeaderNames.X_CONVERSATION_ID_HEADER_NAME
import uk.gov.hmrc.customs.inventorylinking.export.model.AuthorisedAs.AuthorisedAs
import uk.gov.hmrc.customs.inventorylinking.export.model.{AuthorisedAs, _}

object ActionBuilderModelHelper {
  implicit class AddConversationId(result: Result) {
    def withConversationId(implicit c: CorrelationIds): Result = {
      result.withHeaders(X_CONVERSATION_ID_HEADER_NAME -> c.conversationId.value)
    }
  }

  implicit class AuthRequestAsNonCsp[A](ar: AuthorisedRequest[A]) {

    // we can not use normal case class copy on a wrapped request as it is overridden by RequestHeader
    def asNonCsp: AuthorisedRequest[A] = authorisedRequest(Some(AuthorisedAs.NonCsp))

    // we can not use normal case class copy on a wrapped request as it is overridden by RequestHeader
    def asCsp: AuthorisedRequest[A] = authorisedRequest(Some(AuthorisedAs.Csp))

    def authorisedRequest(maybeAuthorised: Option[AuthorisedAs]): AuthorisedRequest[A] = {
      AuthorisedRequest(
        ar.conversationId,
        ar.correlationId,
        ar.maybeBadgeIdentifier,
        ar.requestedApiVersion,
        ar.clientId,
        maybeAuthorised,
        ar.request
      )
    }
  }

  def authorisedRequest[A](vr: ValidatedHeadersRequest[A], maybeAuthorised: Option[AuthorisedAs] = None): AuthorisedRequest[A] = {
    AuthorisedRequest(
      vr.conversationId,
      vr.correlationId,
      vr.maybeBadgeIdentifier,
      vr.requestedApiVersion,
      vr.clientId,
      maybeAuthorised,
      vr.request
    )
  }

}

// TODO: rename to HasConversationId later on when convenient
trait CorrelationIds {
  val conversationId: ConversationId
  val correlationId: CorrelationId // TODO: remove later on when convenient and cascade changes
}

trait ExtractedHeaders {
  val maybeBadgeIdentifier: Option[BadgeIdentifier]
  val requestedApiVersion: ApiVersion
  val clientId: ClientId
}

trait HasAuthorisedAs {
  val maybeAuthorised: Option[AuthorisedAs]
}

case class ExtractedHeadersImpl(
  maybeBadgeIdentifier: Option[BadgeIdentifier],
  requestedApiVersion: ApiVersion,
  clientId: ClientId
) extends ExtractedHeaders

// Available after CorrelationIdsAction action builder
case class CorrelationIdsRequest[A](
  conversationId: ConversationId,
  correlationId: CorrelationId,
  request: Request[A]
) extends WrappedRequest[A](request) with CorrelationIds

// Available after ValidatedHeadersAction builder
case class ValidatedHeadersRequest[A](
  conversationId: ConversationId,
  correlationId: CorrelationId,
  maybeBadgeIdentifier: Option[BadgeIdentifier],
  requestedApiVersion: ApiVersion,
  clientId: ClientId,
  request: Request[A]
) extends WrappedRequest[A](request) with CorrelationIds with ExtractedHeaders

// Available after ValidatedHeadersAction builder
case class AuthorisedRequest[A](
  conversationId: ConversationId,
  correlationId: CorrelationId,
  maybeBadgeIdentifier: Option[BadgeIdentifier],
  requestedApiVersion: ApiVersion,
  clientId: ClientId,
  maybeAuthorised: Option[AuthorisedAs] = None,
  request: Request[A]
) extends WrappedRequest[A](request) with CorrelationIds with ExtractedHeaders with HasAuthorisedAs