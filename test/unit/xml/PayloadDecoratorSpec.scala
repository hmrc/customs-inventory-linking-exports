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

package unit.xml

import org.scalatest.mockito.MockitoSugar
import org.scalatest.prop.TableDrivenPropertyChecks._
import play.api.mvc.AnyContentAsXml
import uk.gov.hmrc.customs.inventorylinking.export.model.actionbuilders.ValidatedPayloadRequest
import uk.gov.hmrc.customs.inventorylinking.export.xml.PayloadDecorator
import uk.gov.hmrc.play.test.UnitSpec
import util.ApiSubscriptionFieldsTestData._
import util.TestData._
import util.XMLTestData._

import scala.xml.NodeSeq

class PayloadDecoratorSpec extends UnitSpec with MockitoSugar{

  private val xmlPayload: NodeSeq = <node1></node1>

  private val commonLabel = "requestCommon"

  private val decorator = new PayloadDecorator()

  "PayloadDecorator for CSP" should {
    implicit val vpr: ValidatedPayloadRequest[AnyContentAsXml] = TestCspValidatedPayloadRequest
    def wrapPayloadWithBadgeIdentifierAndEori(payload: NodeSeq = xmlPayload): NodeSeq = decorator.decorate(payload, TestSubscriptionFieldsId, correlationId, dateTime)

    "wrap passed complete inventoryLinkingMovementRequest" in {
      val result = wrapPayloadWithBadgeIdentifierAndEori(ValidInventoryLinkingMovementRequestXML)

       xml.Utility.trim(result.head) shouldBe xml.Utility.trim(wrappedValidXML.head)
    }

    forAll(xmlRequests) { (linkingType, xml) =>
      s"wrap passed $linkingType" in {
        val result = wrapPayloadWithBadgeIdentifierAndEori(xml)

        val header = result \ commonLabel
        val request = result \\ linkingType
        request should have size 1
        request.head.child shouldBe xml.head.child
        header.isEmpty shouldBe false
      }
    }

    "set the timestamp in the wrapper" in {
      val result = wrapPayloadWithBadgeIdentifierAndEori()

      val rd = result \ commonLabel \ "dateTimeStamp"

      rd.head.text shouldBe dateTime.toString(dateTimeFormat)
    }

    "set the conversationId" in {
      val result = wrapPayloadWithBadgeIdentifierAndEori()

      val rd = result \ commonLabel \ "conversationID"

      rd.head.text shouldBe conversationIdValue
    }

    "set the correlationId" in {
      val result = wrapPayloadWithBadgeIdentifierAndEori()

      val rd = result \ commonLabel \ "correlationID"

      rd.head.text shouldBe correlationIdValue
    }

    "set the clientId" in {
      val result = wrapPayloadWithBadgeIdentifierAndEori()

      val rd = result \ commonLabel \ "clientID"

      rd.head.text shouldBe TestSubscriptionFieldsId.value
    }

    "set the badgeIdentifier when present" in {
      val result = wrapPayloadWithBadgeIdentifierAndEori()

      val rd = result \\ "badgeIdentifier"

      rd.head.text shouldBe badgeIdentifier.value
    }

    "set the Eori identifier when present" in {
      val result = wrapPayloadWithBadgeIdentifierAndEori()

      val rd = result \\ "eori"

      rd.head.text shouldBe declarantEori.value
    }
  }

  "PayloadDecorator for non-CSP" should {
    implicit val vpr: ValidatedPayloadRequest[AnyContentAsXml] = TestNonCspValidatedPayloadRequest
    def wrapPayloadWithoutBadgeIdentifier(payload: NodeSeq = xmlPayload): NodeSeq = decorator.decorate(payload, TestSubscriptionFieldsId, correlationId, dateTime)

    "wrap passed complete inventoryLinkingMovementRequest" in {
      val result = wrapPayloadWithoutBadgeIdentifier()

      val requestDetail = result \\ "requestDetail"
      requestDetail.head.child.contains(<node1/>) shouldBe true
    }

    forAll(xmlRequests) { (linkingType, xml) =>
      s"wrap passed $linkingType" in {
        val result = wrapPayloadWithoutBadgeIdentifier(xml)

        val header = result \ commonLabel
        val request = result \\ linkingType
        request should have size 1
        request.head.child shouldBe xml.head.child
        header.isEmpty shouldBe false
      }
    }

    "set the timestamp in the wrapper" in {
      val result = wrapPayloadWithoutBadgeIdentifier()

      val rd = result \ commonLabel \ "dateTimeStamp"

      rd.head.text shouldBe dateTime.toString(dateTimeFormat)
    }

    "set the conversationId" in {
      val result = wrapPayloadWithoutBadgeIdentifier()

      val rd = result \ commonLabel \ "conversationID"

      rd.head.text shouldBe conversationIdValue
    }

    "set the correlationId" in {
      val result = wrapPayloadWithoutBadgeIdentifier()

      val rd = result \ commonLabel \ "correlationID"

      rd.head.text shouldBe correlationIdValue
    }

    "set the clientId" in {
      val result = wrapPayloadWithoutBadgeIdentifier()

      val rd = result \ commonLabel \ "clientID"

      rd.head.text shouldBe TestSubscriptionFieldsId.value
    }

    "should not set the badgeIdentifier when absent" in {
      val result = wrapPayloadWithoutBadgeIdentifier()

      val rd = result \\ "badgeIdentifier"

      rd.isEmpty shouldBe true
    }
  }

}
