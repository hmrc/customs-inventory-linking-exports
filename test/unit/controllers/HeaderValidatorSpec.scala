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

package unit.controllers

import org.scalatest.prop.{TableDrivenPropertyChecks, TableFor3}
import org.scalatestplus.mockito.MockitoSugar
import play.api.http.HeaderNames._
import play.api.test.FakeRequest
import uk.gov.hmrc.customs.inventorylinking.export.model.AcceptanceTestScenario
import uk.gov.hmrc.customs.inventorylinking.export.controllers.ErrorResponse
import uk.gov.hmrc.customs.inventorylinking.export.controllers.ErrorResponse.{ErrorContentTypeHeaderInvalid, ErrorInternalServerError}
import uk.gov.hmrc.customs.inventorylinking.export.controllers.CustomHeaderNames._
import uk.gov.hmrc.customs.inventorylinking.export.controllers.HeaderValidator
import uk.gov.hmrc.customs.inventorylinking.export.logging.ExportsLogger
import uk.gov.hmrc.customs.inventorylinking.export.model.VersionOne
import uk.gov.hmrc.customs.inventorylinking.export.model.actionbuilders.{ApiVersionRequest, ExtractedHeadersImpl}
import util.CustomsMetricsTestData.EventStart
import util.RequestHeaders._
import util.{ApiSubscriptionFieldsTestData, TestData, UnitSpec}

class HeaderValidatorSpec extends UnitSpec with TableDrivenPropertyChecks with MockitoSugar {

  private val extractedHeaders = ExtractedHeadersImpl(ApiSubscriptionFieldsTestData.clientId, Some(AcceptanceTestScenario(GOV_TEST_SCENARIO_VALUE)))

  trait SetUp {
    val mockExportsLogger: ExportsLogger = mock[ExportsLogger]
    val validator = new HeaderValidator(mockExportsLogger)
  }

  val headersTable: TableFor3[String, Map[String, String], Either[ErrorResponse, ExtractedHeadersImpl]] =
    Table(
      ("description", "Headers", "Expected response"),
      ("Valid Headers", ValidHeaders, Right(extractedHeaders)),
      ("Valid content type XML with no space header", ValidHeaders + (CONTENT_TYPE -> "application/xml;charset=utf-8"), Right(extractedHeaders)),
      ("Missing content type header", ValidHeaders - CONTENT_TYPE, Left(ErrorContentTypeHeaderInvalid)),
      ("Missing X-Client-ID header", ValidHeaders - XClientIdHeaderName, Left(ErrorInternalServerError)),
      ("Invalid content type header JSON header", ValidHeaders + CONTENT_TYPE_HEADER_INVALID, Left(ErrorContentTypeHeaderInvalid)),
      ("Invalid content type XML without UTF-8 header", ValidHeaders + (CONTENT_TYPE -> "application/xml"), Left(ErrorContentTypeHeaderInvalid)),
      ("Invalid X-Client-ID header", ValidHeaders + X_CLIENT_ID_HEADER_INVALID, Left(ErrorInternalServerError))
    )

  "HeaderValidatorAction" should {
    forAll(headersTable) { (description, headers, response) =>
      s"$description" in new SetUp {
        private val apiVersionRequest: ApiVersionRequest[_] = ApiVersionRequest(TestData.conversationId, EventStart, VersionOne, FakeRequest().withHeaders(headers.toSeq: _*))

        validator.validateHeaders(apiVersionRequest) shouldBe response
      }
    }
  }
}
