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

package unit.controllers

import org.scalatest.mockito.MockitoSugar
import org.scalatest.prop.TableDrivenPropertyChecks
import play.api.http.HeaderNames._
import play.api.test.FakeRequest
import uk.gov.hmrc.customs.api.common.controllers.ErrorResponse._
import uk.gov.hmrc.customs.api.common.logging.CdsLogger
import uk.gov.hmrc.customs.inventorylinking.export.controllers.HeaderValidator
import uk.gov.hmrc.customs.inventorylinking.export.model.HeaderConstants._
import uk.gov.hmrc.customs.inventorylinking.export.model.VersionOne
import uk.gov.hmrc.customs.inventorylinking.export.model.actionbuilders.{CorrelationIdsRequest, ExtractedHeadersImpl}
import uk.gov.hmrc.play.test.UnitSpec
import util.RequestHeaders._
import util.TestData.badgeIdentifier
import util.{ApiSubscriptionFieldsTestData, RequestHeaders, TestData}

class HeaderValidatorSpec extends UnitSpec with TableDrivenPropertyChecks with MockitoSugar {

  private val extractedHeadersWithBadgeIdentifier = ExtractedHeadersImpl(Some(badgeIdentifier), VersionOne, ApiSubscriptionFieldsTestData.clientId)
  private val extractedHeadersWithoutBadgeIdentifier = extractedHeadersWithBadgeIdentifier.copy(maybeBadgeIdentifier = None)
  private val errorResponseBadgeIdentifierHeaderMissing = errorBadRequest(s"${RequestHeaders.X_BADGE_IDENTIFIER_NAME} header is missing or invalid")

  trait SetUp {
    val loggerMock: CdsLogger = mock[CdsLogger]
    val validator = new HeaderValidator(loggerMock)
  }

  val headersTable =
    Table(
      ("description", "Headers", "Expected response"),
      ("Valid Headers", ValidHeaders, Right(extractedHeadersWithBadgeIdentifier)),
      ("Valid content type XML with no space header", ValidHeaders + (CONTENT_TYPE -> "application/xml;charset=utf-8"), Right(extractedHeadersWithBadgeIdentifier)),
      ("Missing accept header", ValidHeaders - ACCEPT, Left(ErrorAcceptHeaderInvalid)),
      ("Missing content type header", ValidHeaders - CONTENT_TYPE, Left(ErrorContentTypeHeaderInvalid)),
      ("Missing X-Client-ID header", ValidHeaders - XClientIdHeaderName, Left(ErrorInternalServerError)),
      ("Missing X-Badge-Identifier header", ValidHeaders - XBadgeIdentifierHeaderName, Right(extractedHeadersWithoutBadgeIdentifier)),
      ("Invalid accept header", ValidHeaders + ACCEPT_HEADER_INVALID, Left(ErrorAcceptHeaderInvalid)),
      ("Invalid content type header JSON header", ValidHeaders + CONTENT_TYPE_HEADER_INVALID, Left(ErrorContentTypeHeaderInvalid)),
      ("Invalid content type XML without UTF-8 header", ValidHeaders + (CONTENT_TYPE -> "application/xml"), Left(ErrorContentTypeHeaderInvalid)),
      ("Invalid X-Client-ID header", ValidHeaders + X_CLIENT_ID_HEADER_INVALID, Left(ErrorInternalServerError)),
      ("Invalid X-Badge-Identifier header", ValidHeaders + X_BADGE_IDENTIFIER_HEADER_INVALID, Left(errorResponseBadgeIdentifierHeaderMissing))
    )

  "HeaderValidatorAction" should {
    forAll(headersTable) { (description, headers, response) =>
      s"$description" in new SetUp {
        private val correlationIdsRequest: CorrelationIdsRequest[_] = CorrelationIdsRequest(TestData.conversationId, TestData.correlationId, FakeRequest().withHeaders(headers.toSeq: _*))

        validator.validateHeaders(correlationIdsRequest) shouldBe response
      }
    }
  }
}
