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

package unit.logging

import org.scalatest.mockito.MockitoSugar
import play.api.mvc.Request
import play.api.test.FakeRequest
import uk.gov.hmrc.customs.inventorylinking.export.logging.LoggingHelper2
import uk.gov.hmrc.customs.inventorylinking.export.model.actionbuilders.{CorrelationIdsRequest, ValidatedHeadersRequest}
import uk.gov.hmrc.customs.inventorylinking.export.model.{ClientId, ConversationId, CorrelationId, VersionOne}
import uk.gov.hmrc.play.test.UnitSpec
import play.api.http.HeaderNames._
import uk.gov.hmrc.customs.inventorylinking.export.controllers.CustomHeaderNames

class LoggingHelper2Spec extends UnitSpec with MockitoSugar {

  private def expectedMessage(message: String) = "[conversationId=conversation-id]" +
    "[clientId=some-client-id]" +
    s"[requestedApiVersion=1.0] $message"
  private val requestMock = mock[Request[_]]
  private val correlationIdsRequest =
    CorrelationIdsRequest(
      ConversationId("conversation-id"),
      CorrelationId("correlation-id"),
      FakeRequest().withHeaders(
        CONTENT_TYPE -> "A",
        ACCEPT -> "B",
        CustomHeaderNames.X_CONVERSATION_ID_HEADER_NAME -> "C",
        CustomHeaderNames.X_CLIENT_ID_HEADER_NAME -> "D",
        "IGNORE" -> "IGNORE"
      )
    )
  private val validatedHeadersRequest = ValidatedHeadersRequest(ConversationId("conversation-id"), CorrelationId("correlation-id"), None, VersionOne, ClientId("some-client-id"), requestMock)

  "LoggingHelper" should {


    "testFormatInfo" in {
      LoggingHelper2.formatInfo("Info message", validatedHeadersRequest) shouldBe expectedMessage("Info message")
    }

    "testFormatError" in {
      LoggingHelper2.formatError("Error message", validatedHeadersRequest) shouldBe expectedMessage("Error message")
    }

    "testFormatWarn" in {
      LoggingHelper2.formatWarn("Warn message", validatedHeadersRequest) shouldBe expectedMessage("Warn message")
    }

    "testFormatDebug" in {
      LoggingHelper2.formatDebug("Debug message", validatedHeadersRequest) shouldBe expectedMessage("Debug message")
    }

    "testFormatDebugFull" in {
      LoggingHelper2.formatDebugFull("Debug message.", correlationIdsRequest) shouldBe s"[conversationId=conversation-id] Debug message. headers=Map(Accept -> B, X-Client-ID -> D, Content-Type -> A, X-Conversation-ID -> C)"
    }
  }
}
