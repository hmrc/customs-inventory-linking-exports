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

import play.api.mvc.{Request, WrappedRequest}
import uk.gov.hmrc.customs.inventorylinking.export.model._

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