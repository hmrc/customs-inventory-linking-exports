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
import uk.gov.hmrc.customs.inventorylinking.export.xml.MdgPayloadDecorator
import uk.gov.hmrc.play.test.UnitSpec
import util.XMLTestData._
import util.TestData._

import scala.xml.NodeSeq

class MdgPayloadDecoratorSpec extends UnitSpec with MockitoSugar{

  val xmlPayload: NodeSeq = <inventoryLinkingMovementRequest>
    <node1>whatever</node1>
  </inventoryLinkingMovementRequest>

  private val commonLabel = "requestCommon"

  private val decorator = new MdgPayloadDecorator()

  private def wrapPayloadWithBadgeIdentifier(payload: NodeSeq = xmlPayload) = decorator.decorate(payload, conversationIdValue, correlationId, clientId, Some(badgeIdentifier), dateTime)
  private def wrapPayloadWithoutBadgeIdentifier(payload: NodeSeq = xmlPayload) = decorator.decorate(payload, conversationIdValue, correlationId, clientId, None, dateTime)

  "MdgPayloadDecorator" should {

    "wrap passed complete inventoryLinkingMovementRequest in MDG wrapper" in {
      val result = wrapPayloadWithBadgeIdentifier(ValidInventoryLinkingMovementRequestXML)

       xml.Utility.trim(result.head) shouldBe xml.Utility.trim(wrappedValidXML().head)
    }

    forAll(xmlRequests) { (linkingType, xml) =>
      s"wrap passed $linkingType in MDG wrapper" in {
        val result = wrapPayloadWithBadgeIdentifier(xml)

        val header = result \ commonLabel
        val request = result \\ linkingType

        request should have size 1
        request.head.child shouldBe xml.head.child
        header shouldBe 'nonEmpty
      }
    }

    "set the timestamp in the wrapper" in {
      val result = wrapPayloadWithBadgeIdentifier()

      val rd = result \ commonLabel \ "dateTimeStamp"

      rd.head.text shouldBe dateTime.toString(dateTimeFormat)
    }

    "set the conversationId" in {
      val result = wrapPayloadWithBadgeIdentifier()

      val rd = result \ commonLabel \ "conversationID"

      rd.head.text shouldBe conversationIdValue
    }

    "set the correlationId" in {
      val result = wrapPayloadWithBadgeIdentifier()

      val rd = result \ commonLabel \ "correlationID"

      rd.head.text shouldBe correlationId
    }

    "set the clientId" in {
      val result = wrapPayloadWithBadgeIdentifier()

      val rd = result \ commonLabel \ "clientID"

      rd.head.text shouldBe clientId
    }

    "set the badgeIdentifier when present" in {
      val result = wrapPayloadWithBadgeIdentifier()

      val rd = result \\ "badgeIdentifier"

      rd.head.text shouldBe badgeIdentifier.value
    }

    "should not set the badgeIdentifier when absent" in {
      val result = wrapPayloadWithoutBadgeIdentifier()

      val rd = result \\ "badgeIdentifier"

      rd.isEmpty
    }


  }

}
