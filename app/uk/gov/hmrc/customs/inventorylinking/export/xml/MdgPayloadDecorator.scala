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

package uk.gov.hmrc.customs.inventorylinking.export.xml

import javax.inject.Singleton

import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat
import uk.gov.hmrc.customs.inventorylinking.export.model.actionbuilders.ValidatedPayloadRequest

import scala.xml.NodeSeq

@Singleton
class MdgPayloadDecorator() {

  def decorate[A](xml: NodeSeq, clientId: String, correlationId: String, dateTime: DateTime)(implicit vpr: ValidatedPayloadRequest[A]): NodeSeq = {

    <n1:InventoryLinkingExportsInboundRequest xmlns:inv="http://gov.uk/customs/inventoryLinking/v1"
                                              xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                                              xmlns:gw="http://gov.uk/customs/inventoryLinking/gatewayHeader/v1"
                                              xmlns:n1="http://www.hmrc.gov.uk/cds/inventorylinking/exportmovement">
      <n1:requestCommon>
        { if(vpr.maybeBadgeIdentifier.isDefined) <gw:badgeIdentifier>{vpr.maybeBadgeIdentifier.get.value}</gw:badgeIdentifier> }
        <gw:clientID>{clientId}</gw:clientID>
        <gw:conversationID>{vpr.conversationId.value}</gw:conversationID>
        <gw:correlationID>{correlationId}</gw:correlationID>
        <gw:dateTimeStamp>{dateTime.toString(ISODateTimeFormat.dateTimeNoMillis)}</gw:dateTimeStamp>
      </n1:requestCommon>
      <n1:requestDetail>
        { xml }
      </n1:requestDetail>
    </n1:InventoryLinkingExportsInboundRequest>
  }
}
