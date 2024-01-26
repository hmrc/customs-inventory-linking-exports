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

package uk.gov.hmrc.customs.inventorylinking.export.model

import play.api.libs.json.{JsString, Reads, Writes}

import java.util.UUID
import scala.util.matching.Regex

case class Eori private(value: String) extends AnyVal {
  override def toString: String = value
}

object Eori {

  private val ValidEoriHeaderRegex: Regex = "(^[A-Za-z]{2}[a-zA-Z0-9]{1,15}$)".r

  def fromString(value: String): Option[Eori] = {
    if (ValidEoriHeaderRegex.pattern.matcher(value).matches) {
      Some(new Eori(value))
    } else {
      None
    }
  }
}

case class ClientId(value: String) extends AnyVal

case class ConversationId(uuid: UUID) extends AnyVal {
  override def toString: String = uuid.toString
}

object ConversationId {
  implicit val writer: Writes[ConversationId] = Writes[ConversationId] { x => JsString(x.uuid.toString) }
  implicit val reader: Reads[ConversationId] = Reads.of[UUID].map(new ConversationId(_))
}

case class CorrelationId(uuid: UUID) extends AnyVal {
  override def toString: String = uuid.toString
}

case class BadgeIdentifier(value: String) extends AnyVal

case class SubscriptionFieldsId(value: String) extends AnyVal

sealed trait ApiVersion {
  val value: String
  val configPrefix: String

  override def toString: String = value
}

object VersionOne extends ApiVersion {
  override val value: String = "1.0"
  override val configPrefix: String = ""
}

object VersionTwo extends ApiVersion {
  override val value: String = "2.0"
  override val configPrefix: String = "v2."
}

sealed trait AuthorisedAs

sealed trait Csp extends AuthorisedAs

case class CspWithEori(eori: Eori) extends Csp

case class CspWithBadgeId(badgeIdentifier: BadgeIdentifier) extends Csp

case class CspWithEoriAndBadgeId(eori: Eori, badgeIdentifier: BadgeIdentifier) extends Csp

case class NonCsp(eori: Eori) extends AuthorisedAs
