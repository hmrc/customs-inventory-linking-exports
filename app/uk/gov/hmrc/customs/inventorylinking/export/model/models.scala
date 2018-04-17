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

package uk.gov.hmrc.customs.inventorylinking.export.model

case class Eori(value: String) extends AnyVal

case class ConversationId(value: String) extends AnyVal

case class CorrelationId(value: String) extends AnyVal

case class BadgeIdentifier(value: String) extends AnyVal

case class ClientId(value: String)

sealed trait ApiVersion {
  val value: String
}
object VersionOne extends ApiVersion{
  override val value: String = "1.0"
}

object AuthorisedAs extends Enumeration {
  type AuthorisedAs = Value
  val Csp, NonCsp = Value
}

// TODO: remove this class after PayloadValidationAction is wired in
case class Ids(conversationId: ConversationId,
               correlationId: CorrelationId,
               maybeBadgeIdentifier: Option[BadgeIdentifier] = None)
