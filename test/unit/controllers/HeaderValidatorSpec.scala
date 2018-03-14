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
import play.api.mvc.Results._
import play.api.mvc.{Action, AnyContent, Result}
import play.api.test.FakeRequest
import uk.gov.hmrc.customs.api.common.controllers.ErrorResponse._
import uk.gov.hmrc.customs.inventorylinking.export.controllers.HeaderValidator
import uk.gov.hmrc.play.test.UnitSpec
import util.RequestHeaders._

class HeaderValidatorSpec extends UnitSpec {

  val expectedResult: Result = Ok("as expected")

  val validator = new HeaderValidator {}

  val acceptAction: Action[AnyContent] = validator.validateAccept(validator.acceptHeaderValidation) {
    expectedResult
  }

  val contentTypeAction: Action[AnyContent] = validator.validateContentType(validator.contentTypeValidation) {
    expectedResult
  }

  val xBadgeIdentifierAction: Action[AnyContent] = validator.validateXBadgeIdentifier(validator.badgeIdentifierValidation) {
    expectedResult
  }

  private def requestWithHeaders(headers: Map[String, String]) =
    FakeRequest().withHeaders(headers.toSeq: _*)

  "HeaderValidatorAction" should {
    "return processing result when request headers contain valid values" in {
      await(acceptAction.apply(requestWithHeaders(ValidHeaders))) shouldBe expectedResult
      await(contentTypeAction.apply(requestWithHeaders(ValidHeaders))) shouldBe expectedResult
      await(xBadgeIdentifierAction.apply(requestWithHeaders(ValidHeaders))) shouldBe expectedResult
    }

    "return processing result when BadgeIdentifier header is not present" in {
      await(xBadgeIdentifierAction.apply(requestWithHeaders(ValidHeaders - X_BADGE_IDENTIFIER_NAME))) shouldBe expectedResult
    }

    "return Error result when the Accept header does not exist" in {
      await(acceptAction.apply(requestWithHeaders(ValidHeaders - ACCEPT))) shouldBe ErrorAcceptHeaderInvalid.XmlResult
    }

    "return Error result when Accept header does not contain expected value" in {
      await(acceptAction.apply(requestWithHeaders(ValidHeaders + ACCEPT_HEADER_INVALID))) shouldBe ErrorAcceptHeaderInvalid.XmlResult
    }

    "return Error result when the Content-Type header does not exist" in {
      await(contentTypeAction.apply(requestWithHeaders(ValidHeaders - CONTENT_TYPE))) shouldBe ErrorContentTypeHeaderInvalid.XmlResult
    }

    "return Error result when Content-Type header does not contain expected value" in {
      await(contentTypeAction.apply(requestWithHeaders(ValidHeaders + CONTENT_TYPE_HEADER_INVALID))) shouldBe ErrorContentTypeHeaderInvalid.XmlResult
    }

    "return Error result when BadgeIdentifier header contains too short a value" in {
      await(xBadgeIdentifierAction.apply(requestWithHeaders(ValidHeaders + (X_BADGE_IDENTIFIER_NAME -> "SHORT")))) shouldBe errorBadRequest("X-Badge-Identifier header is missing or invalid").XmlResult
    }

    "return Error result when BadgeIdentifier header contains too long a value" in {
      await(xBadgeIdentifierAction.apply(requestWithHeaders(ValidHeaders + (X_BADGE_IDENTIFIER_NAME -> "TOOLONGBADGEIDENTIFIER")))) shouldBe errorBadRequest("X-Badge-Identifier header is missing or invalid").XmlResult
    }

    "return Error result when BadgeIdentifier header contains lower case letters" in {
      await(xBadgeIdentifierAction.apply(requestWithHeaders(ValidHeaders + (X_BADGE_IDENTIFIER_NAME -> "lowercase")))) shouldBe errorBadRequest("X-Badge-Identifier header is missing or invalid").XmlResult
    }

    "return Error result when BadgeIdentifier header contains invalid characters" in {
      await(xBadgeIdentifierAction.apply(requestWithHeaders(ValidHeaders + (X_BADGE_IDENTIFIER_NAME -> "ABC123456-")))) shouldBe errorBadRequest("X-Badge-Identifier header is missing or invalid").XmlResult
    }
  }
}
