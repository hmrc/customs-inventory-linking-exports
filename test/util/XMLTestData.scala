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

package util

import org.scalatest.prop.TableDrivenPropertyChecks.Table
import util.ApiSubscriptionFieldsTestData._
import util.TestData._

import scala.xml.Elem

object XMLTestData {

  val InvalidXML: Elem =
    <inventoryLinkingMovementRequest foo="bar" xmlns="http://gov.uk/customs/inventoryLinking/v1">
      <messageCode>EAA</messageCode>
      <agentDetails>
        <EORI>{declarantEoriValue}</EORI>
      </agentDetails>
      <ucrBlock>
        <ucr>GB/AAAA-00000</ucr>
        <ucrType>D</ucrType>
      </ucrBlock>
      <goodsLocation>Secret location</goodsLocation>
    </inventoryLinkingMovementRequest>

  val InvalidXMLWith3Errors: Elem =
    <inventoryLinkingMovementRequest foo="bar" xmlns="http://gov.uk/customs/inventoryLinking/v1">
      <messageCode>EAA</messageCode>
      <agentDetails>
        <EORI>{declarantEoriValue}</EORI>
      </agentDetails>
      <ucrBlock>
        <ucr>GB/AAAA-00000</ucr>
        <ucrType>D</ucrType>
      </ucrBlock>
      <goodsLocation random="1234">
        <node>whatever</node>
      </goodsLocation>
    </inventoryLinkingMovementRequest>

  val ValidInventoryLinkingMovementRequestXML: Elem =
    <inventoryLinkingMovementRequest xmlns="http://gov.uk/customs/inventoryLinking/v1">
      <messageCode>EAA</messageCode>
      <agentDetails>
        <EORI>{declarantEoriValue}</EORI>
      </agentDetails>
      <ucrBlock>
        <ucr>GB/AAAA-00000</ucr>
        <ucrType>D</ucrType>
      </ucrBlock>
      <goodsLocation>Secret location</goodsLocation>
    </inventoryLinkingMovementRequest>

  val ValidInventoryLinkingConsolidationRequestXML: Elem =
    <inventoryLinkingConsolidationRequest xmlns="http://gov.uk/customs/inventoryLinking/v1">
      <messageCode>EAC</messageCode>
      <transactionType>Disassociate</transactionType>
      <masterUCR>GB/AAAA-00000</masterUCR>
      <ucrBlock>
        <ucr>GB/BBBB-00000</ucr>
        <ucrType>D</ucrType>
      </ucrBlock>
    </inventoryLinkingConsolidationRequest>

  val ValidInventoryLinkingQueryRequestXML: Elem =
    <inventoryLinkingQueryRequest xmlns="http://gov.uk/customs/inventoryLinking/v1">
    <queryUCR>
      <ucr>GB/AAAA-00000</ucr>
      <ucrType>M</ucrType>
    </queryUCR>
    <agentDetails>
      <EORI>{declarantEoriValue}</EORI>
    </agentDetails>
  </inventoryLinkingQueryRequest>

  val wrappedValidXML: Elem =
      <n1:InventoryLinkingExportsInboundRequest xmlns:n1="http://www.hmrc.gov.uk/cds/inventorylinking/exportmovement"
                                                xmlns:gw="http://gov.uk/customs/inventoryLinking/gatewayHeader/v1"
                                                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:inv="http://gov.uk/customs/inventoryLinking/v1">
        <n1:requestCommon>
          <gw:badgeIdentifier>{validBadgeIdentifierValue}</gw:badgeIdentifier>
          <gw:eori>{declarantEoriValue}</gw:eori>
          <gw:clientID>{TestSubscriptionFieldsId.value}</gw:clientID>
          <gw:conversationID>{conversationIdValue}</gw:conversationID>
          <gw:correlationID>{correlationIdValue}</gw:correlationID>
          <gw:dateTimeStamp>{dateTime.toString(dateTimeFormat)}</gw:dateTimeStamp>
        </n1:requestCommon>
        <n1:requestDetail>
          <inventoryLinkingMovementRequest xmlns="http://gov.uk/customs/inventoryLinking/v1">
            <messageCode>EAA</messageCode>
            <agentDetails>
              <EORI>{declarantEoriValue}</EORI>
            </agentDetails>
            <ucrBlock>
              <ucr>GB/AAAA-00000</ucr>
              <ucrType>D</ucrType>
            </ucrBlock>
            <goodsLocation>Secret location</goodsLocation>
          </inventoryLinkingMovementRequest>
      </n1:requestDetail>
      </n1:InventoryLinkingExportsInboundRequest>

  val xmlRequests = Table(
    ("linkingType", "xml"),
    ("inventoryLinkingMovementRequest", ValidInventoryLinkingMovementRequestXML),
    ("inventoryLinkingConsolidationRequest", ValidInventoryLinkingConsolidationRequestXML),
    ("inventoryLinkingQueryRequest", ValidInventoryLinkingQueryRequestXML)
  )
}
