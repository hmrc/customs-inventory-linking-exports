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

package unit.logging

import org.scalatestplus.mockito.MockitoSugar
import play.api.http.HeaderNames._
import play.api.mvc.{AnyContentAsEmpty, Request}
import play.api.test.FakeRequest
import uk.gov.hmrc.customs.inventorylinking.exports.controllers.CustomHeaderNames
import uk.gov.hmrc.customs.inventorylinking.exports.logging.LoggingHelper
import uk.gov.hmrc.customs.inventorylinking.exports.model.actionbuilders.{ConversationIdRequest, ValidatedHeadersRequest}
import uk.gov.hmrc.customs.inventorylinking.exports.model.{ClientId, VersionOne}
import util.CustomsMetricsTestData.EventStart
import util.UnitSpec
import util.TestData.conversationId

class LoggingHelperSpec extends UnitSpec with MockitoSugar {

  private def expectedMessage(message: String) = s"[conversationId=$conversationId]" +
    "[clientId=some-client-id]" +
    s"[requestedApiVersion=1.0] $message"
  private val requestMock = mock[Request[_]]
  private val conversationIdRequest =
    ConversationIdRequest(
      conversationId,
      EventStart,
      FakeRequest().withHeaders(
        CONTENT_TYPE -> "A",
        ACCEPT -> "B",
        CustomHeaderNames.XConversationIdHeaderName -> "C",
        CustomHeaderNames.XClientIdHeaderName -> "D",
        CustomHeaderNames.XBadgeIdentifierHeaderName -> "BADGE",
        CustomHeaderNames.XSubmitterIdentifierHeaderName -> "EORI1234",
        "IGNORE" -> "IGNORE"
      )
    )

  private val conversationIdRequestMixedCaseHeaders: ConversationIdRequest[AnyContentAsEmpty.type] =
    ConversationIdRequest(
      conversationId,
      EventStart,
      FakeRequest().withHeaders(
        CONTENT_TYPE -> "A",
        ACCEPT -> "B",
        "X-ConVerSaTion-ID" -> "C",
        "X-CliEnT-ID" -> "D",
        "X-BaDge-IdeNtiFier" -> "BADGE",
        "X-Submitter-IdeNtiFier" -> "EORI1234",
        "IGNORE" -> "IGNORE"
      )
    )
  private val validatedHeadersRequest: ValidatedHeadersRequest[Any] = ValidatedHeadersRequest(conversationId, EventStart, VersionOne, ClientId("some-client-id"), requestMock)

  "LoggingHelper" should {


    "testFormatInfo" in {
      LoggingHelper.formatInfo("Info message", validatedHeadersRequest) shouldBe expectedMessage("Info message")
    }

    "testFormatError" in {
      LoggingHelper.formatError("Error message", validatedHeadersRequest) shouldBe expectedMessage("Error message")
    }

    "testFormatWarn" in {
      LoggingHelper.formatWarn("Warn message", validatedHeadersRequest) shouldBe expectedMessage("Warn message")
    }

    "testFormatDebug" in {
      LoggingHelper.formatDebug("Debug message", validatedHeadersRequest) shouldBe expectedMessage("Debug message")
    }

    "testFormatDebugFull" in {
      LoggingHelper.formatDebugFull("Debug message.", conversationIdRequest) shouldBe s"[conversationId=$conversationId] Debug message. headers=TreeMap(Accept -> B, X-Client-ID -> D, Content-Type -> A, X-Conversation-ID -> C, X-Badge-Identifier -> BADGE)"
    }

    "testFormatDebugFull with mixed case headernames" in {
      LoggingHelper.formatDebugFull("Debug message.", conversationIdRequestMixedCaseHeaders) shouldBe s"[conversationId=$conversationId] Debug message. headers=TreeMap(Accept -> B, X-CliEnT-ID -> D, Content-Type -> A, X-ConVerSaTion-ID -> C, X-BaDge-IdeNtiFier -> BADGE)"
    }
  }
}
