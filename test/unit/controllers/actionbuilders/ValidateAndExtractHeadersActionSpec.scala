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

package unit.controllers.actionbuilders

import org.mockito.ArgumentMatchers.{any, eq => ameq}
import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar
import org.scalatest.prop.TableDrivenPropertyChecks
import play.api.mvc.{Request, Result}
import uk.gov.hmrc.customs.api.common.controllers.ErrorResponse._
import uk.gov.hmrc.customs.inventorylinking.export.controllers.HeaderValidator
import uk.gov.hmrc.customs.inventorylinking.export.controllers.actionbuilders.ValidateAndExtractHeadersAction
import uk.gov.hmrc.customs.inventorylinking.export.logging.ExportsLogger2
import uk.gov.hmrc.customs.inventorylinking.export.model.VersionOne
import uk.gov.hmrc.customs.inventorylinking.export.model.actionbuilders.{CorrelationIdsRequest, ExtractedHeadersImpl, ValidatedHeadersRequest}
import uk.gov.hmrc.play.test.UnitSpec
import util.TestData._
import util.{ApiSubscriptionFieldsTestData, RequestHeaders}

class ValidateAndExtractHeadersActionSpec extends UnitSpec with MockitoSugar with TableDrivenPropertyChecks {

  private val mockRequest = mock[Request[_]]
  private val extracted = ExtractedHeadersImpl(Some(badgeIdentifier), VersionOne, ApiSubscriptionFieldsTestData.clientId)
  private val validatedHeadersRequest = ValidatedHeadersRequest(
    conversationId, correlationId, extracted.maybeBadgeIdentifier, extracted.requestedApiVersion, extracted.clientId, mockRequest
  )

  trait SetUp {
    val mockLogger = mock[ExportsLogger2]
    val mockHeaderValidator = mock[HeaderValidator]
    val validateAndExtractHeadersAction = new ValidateAndExtractHeadersAction(mockHeaderValidator, mockLogger)
  }

  val headersTable =
    Table(
      ("description", "ValidationResult", "Expected response"),
      ("Valid Headers", Right(extracted), Right(validatedHeadersRequest)),
      ("Invalid header", Left(ErrorContentTypeHeaderInvalid),
        Left(ErrorContentTypeHeaderInvalid.XmlResult.withHeaders(RequestHeaders.X_CONVERSATION_ID_NAME -> conversationIdValue)))
    )

  "HeaderValidatorAction" should  {
    forAll(headersTable) { (description, validationResult, expectedResult) =>
      s"$description" in new SetUp() {
        val correlationIdsRequest = CorrelationIdsRequest(conversationId, correlationId, mockRequest)
        when(mockHeaderValidator.validateHeaders(any[CorrelationIdsRequest[_]])).thenReturn(validationResult)

        val actualResult: Either[Result, ValidatedHeadersRequest[_]] = await(validateAndExtractHeadersAction.refine(correlationIdsRequest))

        actualResult shouldBe expectedResult
      }
    }
  }
}
