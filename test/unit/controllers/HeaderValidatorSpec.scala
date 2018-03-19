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

import play.api.http.HeaderNames.{ACCEPT, CONTENT_TYPE}
import play.api.http.MimeTypes
import play.api.test.FakeRequest
import uk.gov.hmrc.customs.api.common.controllers.ErrorResponse._
import uk.gov.hmrc.customs.inventorylinking.export.controllers.HeaderValidator
import uk.gov.hmrc.play.test.UnitSpec
import util.RequestHeaders._

class HeaderValidatorSpec extends UnitSpec {

  private val validator = new HeaderValidator {}

  private def requestWithHeaders(headers: Map[String, String]) =
    FakeRequest().withHeaders(headers.toSeq: _*)

  "HeaderValidatorAction" should {
    "return processing result when request headers contain valid values" in {
      await(validator.validateAccept()(requestWithHeaders(ValidHeaders))) shouldBe None
    }

    "return processing result when Content-Type header contains charset" in {
      await(validator.validateContentType()(requestWithHeaders(ValidHeaders - CONTENT_TYPE + (CONTENT_TYPE -> (MimeTypes.XML + "; charset=UTF-8"))))) shouldBe None
    }

    "return Error result when the Accept header does not exist" in {
      await(validator.validateAccept()(requestWithHeaders(ValidHeaders - ACCEPT))) shouldBe Some(ErrorAcceptHeaderInvalid)
    }

    "return Error result when Accept header does not contain expected value" in {
      await(validator.validateAccept()(requestWithHeaders(ValidHeaders + ACCEPT_HEADER_INVALID))) shouldBe Some(ErrorAcceptHeaderInvalid)
    }

    "return Error result when the Content-Type header does not exist" in {
      await(validator.validateContentType()(requestWithHeaders(ValidHeaders - CONTENT_TYPE))) shouldBe Some(ErrorContentTypeHeaderInvalid)
    }

    "return Error result when Content-Type header does not contain expected value" in {
      await(validator.validateContentType()(requestWithHeaders(ValidHeaders + CONTENT_TYPE_HEADER_INVALID))) shouldBe Some(ErrorContentTypeHeaderInvalid)
    }

    "return Error result when BadgeIdentifier header value length is too long" in {
      await(validator.validateBadgeIdentifier()(requestWithHeaders(ValidHeaders + (X_BADGE_IDENTIFIER_NAME -> "INVALIDBADGEID123456789")))) shouldBe Some(errorBadRequest("X-Badge-Identifier header is missing or invalid"))
    }

    "return Error result when BadgeIdentifier header value length is too short" in {
      await(validator.validateBadgeIdentifier()(requestWithHeaders(ValidHeaders + (X_BADGE_IDENTIFIER_NAME -> "12345")))) shouldBe Some(errorBadRequest("X-Badge-Identifier header is missing or invalid"))
    }

    "return Error result when BadgeIdentifier header contains invalid characters" in {
      await(validator.validateBadgeIdentifier()(requestWithHeaders(ValidHeaders + (X_BADGE_IDENTIFIER_NAME -> "Invalid^&&(")))) shouldBe Some(errorBadRequest("X-Badge-Identifier header is missing or invalid"))
    }

    "return Error result when BadgeIdentifier header contains lowercase characters" in {
      await(validator.validateBadgeIdentifier()(requestWithHeaders(ValidHeaders + (X_BADGE_IDENTIFIER_NAME -> "BadgeId123")))) shouldBe Some(errorBadRequest("X-Badge-Identifier header is missing or invalid"))
    }

    "return processing result when BadgeIdentifier header is not present" in {
      await(validator.validateBadgeIdentifier()(requestWithHeaders(ValidHeaders - X_BADGE_IDENTIFIER_NAME))) shouldBe None
    }
  }
}
