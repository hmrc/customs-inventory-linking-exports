/*
 * Copyright 2021 HM Revenue & Customs
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

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatestplus.mockito.MockitoSugar
import play.api.mvc.{AnyContentAsXml, Result}
import play.api.test.Helpers
import uk.gov.hmrc.customs.api.common.controllers.ErrorResponse._
import uk.gov.hmrc.customs.inventorylinking.export.controllers.CustomHeaderNames.XConversationIdHeaderName
import uk.gov.hmrc.customs.inventorylinking.export.controllers.HeaderValidator
import uk.gov.hmrc.customs.inventorylinking.export.controllers.actionbuilders.ValidateAndExtractHeadersAction
import uk.gov.hmrc.customs.inventorylinking.export.logging.ExportsLogger
import uk.gov.hmrc.customs.inventorylinking.export.model.VersionOne
import uk.gov.hmrc.customs.inventorylinking.export.model.actionbuilders.ActionBuilderModelHelper.ConversationIdRequestOps
import uk.gov.hmrc.customs.inventorylinking.export.model.actionbuilders.{ApiVersionRequest, ValidatedHeadersRequest}
import util.TestData.{TestConversationIdRequest, TestExtractedHeaders, TestValidatedHeadersRequest, conversationIdValue}
import util.UnitSpec

import scala.concurrent.ExecutionContext

class ValidateAndExtractHeadersActionSpec extends UnitSpec with MockitoSugar with TableDrivenPropertyChecks {

  trait SetUp {
    val mockLogger: ExportsLogger = mock[ExportsLogger]
    val mockHeaderValidator: HeaderValidator = mock[HeaderValidator]
    implicit val ec: ExecutionContext = Helpers.stubControllerComponents().executionContext
    val validateAndExtractHeadersAction = new ValidateAndExtractHeadersAction(mockHeaderValidator)
  }

  "HeaderValidationAction when validation succeeds" should {
    "extract headers from incoming request and copy relevant values on to the ValidatedHeaderRequest" in new SetUp {
      val apiVersionRequest: ApiVersionRequest[AnyContentAsXml] = TestConversationIdRequest.toApiVersionRequest(VersionOne)
      when(mockHeaderValidator.validateHeaders(any[ApiVersionRequest[_]])).thenReturn(Right(TestExtractedHeaders))

      val actualResult: Either[Result, ValidatedHeadersRequest[_]] = await(validateAndExtractHeadersAction.refine(apiVersionRequest))

      actualResult shouldBe Right(TestValidatedHeadersRequest)
    }
  }

  "HeaderValidationAction when validation fails" should {
    "return error with conversation Id in the headers" in new SetUp {
      val apiVersionRequest: ApiVersionRequest[AnyContentAsXml] = TestConversationIdRequest.toApiVersionRequest(VersionOne)
      when(mockHeaderValidator.validateHeaders(any[ApiVersionRequest[_]])).thenReturn(Left(ErrorContentTypeHeaderInvalid))

      val actualResult: Either[Result, ValidatedHeadersRequest[_]] = await(validateAndExtractHeadersAction.refine(apiVersionRequest))

      actualResult shouldBe Left(ErrorContentTypeHeaderInvalid.XmlResult.withHeaders(XConversationIdHeaderName -> conversationIdValue))
    }
  }

}
