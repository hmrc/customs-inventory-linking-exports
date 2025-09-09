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

package uk.gov.hmrc.customs.inventorylinking.exports.xml

import uk.gov.hmrc.customs.inventorylinking.exports.model._
import uk.gov.hmrc.customs.inventorylinking.exports.model.actionbuilders.ValidatedPayloadRequest
import uk.gov.hmrc.customs.inventorylinking.exports.model.{CorrelationId, NonCsp, SubscriptionFieldsId}
import uk.gov.hmrc.customs.inventorylinking.exports.services.DateTimeService

import java.time.format.DateTimeFormatter
import java.time.{LocalDateTime, ZoneOffset}
import javax.inject.Singleton
import scala.xml.NodeSeq

@Singleton
class PayloadDecorator() {

  private def maybeBadgeIdentifierElement(authorisedAs: AuthorisedAs): NodeSeq = {
    def badgeIdElementFrom(badgeIdentifier: BadgeIdentifier): NodeSeq = {
      <gw:badgeIdentifier>{ badgeIdentifier.value }</gw:badgeIdentifier>
    }

    authorisedAs match {
      case CspWithBadgeId(badgeIdentifier) =>
        badgeIdElementFrom(badgeIdentifier)
      case CspWithEoriAndBadgeId(_, badgeIdentifier) =>
        badgeIdElementFrom(badgeIdentifier)
      case _ =>
        NodeSeq.Empty
    }
  }

  private def submitterElement(authorisedAs: AuthorisedAs): NodeSeq = {
    def submitterElementFrom(submitter: String): NodeSeq = {
      <gw:submitter>{ submitter }</gw:submitter>
    }

    authorisedAs match {
      case NonCsp(eori) =>
        submitterElementFrom(eori.value)
      case CspWithEori(eori) =>
        submitterElementFrom(eori.value)
      case CspWithEoriAndBadgeId(eori, _) =>
        submitterElementFrom(eori.value)
      case CspWithBadgeId(badgeIdentifier) =>
        submitterElementFrom(badgeIdentifier.value)
    }
  }

  def decorate[A](xml: NodeSeq, clientId: SubscriptionFieldsId, correlationId: CorrelationId, dateTime: LocalDateTime)(implicit vpr: ValidatedPayloadRequest[A]): NodeSeq = {
    val isoFormatDate: DateTimeFormatter = new DateTimeService().isoFormatNoMillis

    <n1:InventoryLinkingExportsInboundRequest xmlns:inv="http://gov.uk/customs/inventoryLinking/v1"
                                              xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                                              xmlns:gw="http://gov.uk/customs/inventoryLinking/gatewayHeader/v1"
                                              xmlns:n1="http://www.hmrc.gov.uk/cds/inventorylinking/exportmovement"
                                              xsi:schemaLocation="http://www.hmrc.gov.uk/cds/inventorylinking/exportmovement DEC39_Root.xsd">
      <n1:requestCommon>
        { maybeBadgeIdentifierElement(vpr.authorisedAs) }
        { submitterElement(vpr.authorisedAs) }
        <gw:clientID>{clientId.value}</gw:clientID>
        <gw:conversationID>{vpr.conversationId.toString}</gw:conversationID>
        <gw:correlationID>{correlationId.toString}</gw:correlationID>
        <gw:dateTimeStamp>{dateTime.atOffset(ZoneOffset.UTC).format(isoFormatDate)}</gw:dateTimeStamp>
      </n1:requestCommon>
      <n1:requestDetail>
        { xml }
      </n1:requestDetail>
    </n1:InventoryLinkingExportsInboundRequest>
  }
}
