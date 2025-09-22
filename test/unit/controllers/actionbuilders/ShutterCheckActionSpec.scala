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

package unit.controllers.actionbuilders

import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar
import play.api.http.Status.SERVICE_UNAVAILABLE
import play.api.mvc.Result
import play.api.test.Helpers.ACCEPT
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.customs.inventorylinking.exports.controllers.ErrorResponse
import uk.gov.hmrc.customs.inventorylinking.exports.controllers.ErrorResponse.ErrorAcceptHeaderInvalid
import uk.gov.hmrc.customs.inventorylinking.exports.controllers.actionbuilders.ShutterCheckAction
import uk.gov.hmrc.customs.inventorylinking.exports.logging.ExportsLogger
import uk.gov.hmrc.customs.inventorylinking.exports.model.actionbuilders.ActionBuilderModelHelper.ConversationIdRequestOps
import uk.gov.hmrc.customs.inventorylinking.exports.model.actionbuilders.ConversationIdRequest
import uk.gov.hmrc.customs.inventorylinking.exports.model.{ExportsShutterConfig, VersionOne, VersionTwo}
import uk.gov.hmrc.customs.inventorylinking.exports.services.ExportsConfigService
import util.CustomsMetricsTestData.EventStart
import util.RequestHeaders.{ACCEPT_HEADER_INVALID, ValidHeaders, X_CONVERSATION_ID_NAME}
import util.TestData._
import util.UnitSpec

import scala.concurrent.ExecutionContext

class ShutterCheckActionSpec extends UnitSpec with MockitoSugar {

  trait SetUp {
    protected implicit val ec: ExecutionContext = Helpers.stubControllerComponents().executionContext
    val mockConfigService = mock[ExportsConfigService]
    private val mockLogger = mock[ExportsLogger]
    val errorResponseVersionShuttered: Result = ErrorResponse(SERVICE_UNAVAILABLE, "SERVER_ERROR", "Service unavailable").XmlResult

    val allVersionsShuttered = ExportsShutterConfig(Some(true), Some(true))
    val versionOneShuttered = ExportsShutterConfig(Some(true), Some(false))
    val versionTwoShuttered = ExportsShutterConfig(Some(false), Some(true))
    val allVersionsShutteringUnspecified = ExportsShutterConfig(None, None)

    when(mockConfigService.exportsShutterConfig).thenReturn(allVersionsUnshuttered)

    val action = new ShutterCheckAction(mockLogger, mockConfigService)
  }
  
  "in happy path, validation" should {
    "be successful for a valid request with accept header for V1" in new SetUp {
      await(action.refine(TestConversationIdRequestWithV1Headers)) shouldBe Right(TestConversationIdRequestWithV1Headers.toApiVersionRequest(VersionOne))
    }

    "be successful for a valid request with accept header for V2" in new SetUp {
      await(action.refine(TestConversationIdRequestWithV2Headers)) shouldBe Right(TestConversationIdRequestWithV2Headers.toApiVersionRequest(VersionTwo))
    }
  }

  "in unhappy path, validation" should {
    "fail for a valid request with missing accept header" in new SetUp {
      val requestWithoutAcceptHeader = FakeRequest().withXmlBody(TestXmlPayload).withHeaders((ValidHeaders - ACCEPT).toSeq: _*)
      
      val result = await(action.refine(ConversationIdRequest(conversationId, EventStart, requestWithoutAcceptHeader)))
      result shouldBe Left(ErrorAcceptHeaderInvalid.XmlResult.withHeaders(X_CONVERSATION_ID_NAME -> conversationIdValue))
    }

    "fail for a valid request with invalid accept header" in new SetUp {
      val requestWithInvalidAcceptHeader = FakeRequest().withXmlBody(TestXmlPayload).withHeaders((ValidHeaders + ACCEPT_HEADER_INVALID).toSeq: _*)

      val result = await(action.refine(ConversationIdRequest(conversationId, EventStart, requestWithInvalidAcceptHeader)))
      result shouldBe Left(ErrorAcceptHeaderInvalid.XmlResult.withHeaders(X_CONVERSATION_ID_NAME -> conversationIdValue))
    }
  }
  
  "when shuttered set" should {
    "return 503 error for a valid request with v1 accept header and v1 is shuttered" in new SetUp {
      when(mockConfigService.exportsShutterConfig).thenReturn(versionOneShuttered)
      val result = await(action.refine(TestConversationIdRequestWithV1Headers))

      result shouldBe Left(errorResponseVersionShuttered)
    }

    "return 503 error for a valid request with v2 accept header and v2 is shuttered" in new SetUp {
      when(mockConfigService.exportsShutterConfig).thenReturn(versionTwoShuttered)
      val result = await(action.refine(TestConversationIdRequestWithV2Headers))

      result shouldBe Left(errorResponseVersionShuttered)
    }

    "return 503 error for a valid request with v2 accept header and all versions are shuttered" in new SetUp {
      when(mockConfigService.exportsShutterConfig).thenReturn(allVersionsShuttered)
      val result = await(action.refine(TestConversationIdRequestWithV2Headers))

      result shouldBe Left(errorResponseVersionShuttered)
    }

    "be successful when a valid request with v1 accept header and no shuttering is unspecified" in new SetUp {
      when(mockConfigService.exportsShutterConfig).thenReturn(allVersionsShutteringUnspecified)
      val result = await(action.refine(TestConversationIdRequestWithV1Headers))

      result shouldBe Right(TestConversationIdRequestWithV1Headers.toApiVersionRequest(VersionOne))
    }
  }
  
}
