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

package unit.xml

import org.scalatest.prop.TableDrivenPropertyChecks._
import org.scalatestplus.mockito.MockitoSugar
import play.api.mvc.AnyContentAsXml
import uk.gov.hmrc.customs.inventorylinking.`export`.services.DateTimeService
import uk.gov.hmrc.customs.inventorylinking.export.model.actionbuilders.ValidatedPayloadRequest
import uk.gov.hmrc.customs.inventorylinking.export.xml.PayloadDecorator
import util.UnitSpec
import util.ApiSubscriptionFieldsTestData._
import util.TestData._
import util.XMLTestData._

import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import scala.xml.NodeSeq

class PayloadDecoratorSpec extends UnitSpec with MockitoSugar {

  private val xmlPayload: NodeSeq = <node1></node1>

  private val commonLabel = "requestCommon"

  private val decorator = new PayloadDecorator()

  "PayloadDecorator for CSP" should {
    val vprWithEoriAndBadgeIdentifier: ValidatedPayloadRequest[AnyContentAsXml] = TestCspValidatedPayloadRequestWithEoriAndBadgeIdentifier
    def wrapPayloadWithEoriAndBadgeIdentifier(payload: NodeSeq = xmlPayload): NodeSeq = {
      decorator.decorate(payload, TestSubscriptionFieldsId, correlationId, dateTime)(vprWithEoriAndBadgeIdentifier)
    }

    "wrap passed complete inventoryLinkingMovementRequest" in {
      val result = wrapPayloadWithEoriAndBadgeIdentifier(ValidInventoryLinkingMovementRequestXML)

       scala.xml.Utility.trim(result.head) shouldBe scala.xml.Utility.trim(wrappedValidXML.head)
    }

    forAll(xmlRequests) { (linkingType, xml) =>
      s"wrap passed $linkingType" in {
        val result = wrapPayloadWithEoriAndBadgeIdentifier(xml)

        val header = result \ commonLabel
        val request = result \\ linkingType
        request should have size 1
        request.head.child shouldBe xml.head.child
        header.isEmpty shouldBe false
      }
    }

    "set the timestamp in the wrapper" in {
      val result = wrapPayloadWithEoriAndBadgeIdentifier()

      val rd = result \ commonLabel \ "dateTimeStamp"

      val isoFormatDate: DateTimeFormatter = new DateTimeService().isoFormatNoMillis

      rd.head.text shouldBe dateTime.atOffset(ZoneOffset.UTC).format(isoFormatDate)
    }

    "set the conversationId" in {
      val result = wrapPayloadWithEoriAndBadgeIdentifier()

      val rd = result \ commonLabel \ "conversationID"

      rd.head.text shouldBe conversationIdValue
    }

    "set the correlationId" in {
      val result = wrapPayloadWithEoriAndBadgeIdentifier()

      val rd = result \ commonLabel \ "correlationID"

      rd.head.text shouldBe correlationIdValue
    }

    "set the clientId" in {
      val result = wrapPayloadWithEoriAndBadgeIdentifier()

      val rd = result \ commonLabel \ "clientID"

      rd.head.text shouldBe TestSubscriptionFieldsId.value
    }

    "set the badgeIdentifier when present" in {
      val result = decorator.decorate(xmlPayload, TestSubscriptionFieldsId, correlationId, dateTime)(TestCspValidatedPayloadRequestWithBadgeIdentifier)

      val rd = result \\ "badgeIdentifier"

      rd.head.text shouldBe badgeIdentifier.value
    }

    "omit the badgeIdentifier when absent" in {
      implicit val vpr: ValidatedPayloadRequest[AnyContentAsXml] = TestCspValidatedPayloadRequestWithEori
      val result = decorator.decorate(xmlPayload, TestSubscriptionFieldsId, correlationId, dateTime)

      val rd = result \\ "badgeIdentifier"

      rd.isEmpty shouldBe true
    }

    "set the submitter when present" in {
      val result = wrapPayloadWithEoriAndBadgeIdentifier()

      val rd = result \\ "submitter"

      rd.head.text shouldBe declarantEori.value
    }
    
    "set the submitter to badgeIdentifier when eori is absent" in {
      implicit val vpr: ValidatedPayloadRequest[AnyContentAsXml] = TestCspValidatedPayloadRequestWithBadgeIdentifier
      val result = decorator.decorate(xmlPayload, TestSubscriptionFieldsId, correlationId, dateTime)

      val rd = result \\ "submitter"

      rd.head.text shouldBe badgeIdentifier.value
    }
  }

  "PayloadDecorator for non-CSP" should {
    implicit val vpr: ValidatedPayloadRequest[AnyContentAsXml] = TestNonCspValidatedPayloadRequest
    def wrapPayload(payload: NodeSeq = xmlPayload): NodeSeq = decorator.decorate(payload, TestSubscriptionFieldsId, correlationId, dateTime)

    "wrap passed complete inventoryLinkingMovementRequest" in {
      val result = wrapPayload()

      val requestDetail = result \\ "requestDetail"
      requestDetail.head.child.contains(<node1/>) shouldBe true
    }

    forAll(xmlRequests) { (linkingType, xml) =>
      s"wrap passed $linkingType" in {
        val result = wrapPayload(xml)

        val header = result \ commonLabel
        val request = result \\ linkingType
        request should have size 1
        request.head.child shouldBe xml.head.child
        header.isEmpty shouldBe false
      }
    }

    "set the timestamp in the wrapper" in {
      val result = wrapPayload()
      val isoFormatDate: DateTimeFormatter = new DateTimeService().isoFormatNoMillis
      val rd = result \ commonLabel \ "dateTimeStamp"

      rd.head.text shouldBe dateTime.atOffset(ZoneOffset.UTC).format(isoFormatDate)

     // dateTime.toString(dateTimeFormat)
    }

    "set the conversationId" in {
      val result = wrapPayload()

      val rd = result \ commonLabel \ "conversationID"

      rd.head.text shouldBe conversationIdValue
    }

    "set the correlationId" in {
      val result = wrapPayload()

      val rd = result \ commonLabel \ "correlationID"

      rd.head.text shouldBe correlationIdValue
    }

    "set the clientId" in {
      val result = wrapPayload()

      val rd = result \ commonLabel \ "clientID"

      rd.head.text shouldBe TestSubscriptionFieldsId.value
    }

    "should not set the badgeIdentifier when absent" in {
      val result = wrapPayload()

      val rd = result \\ "badgeIdentifier"

      rd.isEmpty shouldBe true
    }

    "set the submitter identifier when present" in {
      val result =  wrapPayload()

      val rd = result \\ "submitter"

      rd.head.text shouldBe declarantEori.value
    }
  }
}
