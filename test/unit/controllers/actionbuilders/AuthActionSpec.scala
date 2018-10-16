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

import org.scalatest.mockito.MockitoSugar
import play.api.http.Status
import play.api.http.Status.UNAUTHORIZED
import play.api.test.FakeRequest
import uk.gov.hmrc.customs.api.common.controllers.ErrorResponse
import uk.gov.hmrc.customs.api.common.controllers.ErrorResponse.{ErrorInternalServerError, UnauthorizedCode, errorBadRequest}
import uk.gov.hmrc.customs.inventorylinking.export.controllers.CustomHeaderNames
import uk.gov.hmrc.customs.inventorylinking.export.controllers.actionbuilders.AuthAction
import uk.gov.hmrc.customs.inventorylinking.export.logging.ExportsLogger
import uk.gov.hmrc.customs.inventorylinking.export.model.Eori
import uk.gov.hmrc.customs.inventorylinking.export.model.actionbuilders.ActionBuilderModelHelper._
import uk.gov.hmrc.customs.inventorylinking.export.model.actionbuilders.ConversationIdRequest
import uk.gov.hmrc.play.test.UnitSpec
import util.RequestHeaders.X_EORI_IDENTIFIER_NAME_CAMEL_CASE
import util.TestData._
import util.{AuthConnectorStubbing, RequestHeaders}

class AuthActionSpec extends UnitSpec with MockitoSugar {

  private val errorResponseUnauthorisedGeneral =
    ErrorResponse(Status.UNAUTHORIZED, UnauthorizedCode, "Unauthorised request")
  private val errorResponseBadgeIdentifierHeaderMissing =
    errorBadRequest(s"${CustomHeaderNames.XBadgeIdentifierHeaderName} header is missing or invalid")
  private val errorResponseEoriIdentifierHeaderMissing =
    errorBadRequest(s"${CustomHeaderNames.XEoriIdentifierHeaderName} header is missing or invalid")
  private val errorResponseEoriIdentifierHeaderInvalid =
    errorBadRequest(s"${CustomHeaderNames.XEoriIdentifierHeaderName} header is invalid")

  private lazy val errorResponseEoriNotFoundInCustomsEnrolment =
    ErrorResponse(UNAUTHORIZED, UnauthorizedCode, "EORI number not found in Customs Enrolment")

  private lazy val validatedHeadersRequestWithValidBadgeIdButNoEoriIdentifier =
    ConversationIdRequest(conversationId, testFakeRequestWithBadgeId()).toValidatedHeadersRequest(TestExtractedHeaders)

  private lazy val validatedHeadersRequestWithValidBadgeIdAndEoriIdentifier =
    ConversationIdRequest(conversationId, testFakeRequestWithBadgeIdAndEoriId()).toValidatedHeadersRequest(TestExtractedHeaders)

  private val eoriTooLong = "GB9988776655787656"

  private lazy val validatedHeadersRequestWithValidBadgeIdAndEoriIdTooLong =
    ConversationIdRequest(conversationId, testFakeRequestWithBadgeIdAndEoriId(eoriIdString = eoriTooLong)).toValidatedHeadersRequest(TestExtractedHeaders)

  private lazy val validatedHeadersRequestWithValidBadgeIdAndEoriIdTooShort =
    ConversationIdRequest(conversationId, testFakeRequestWithBadgeIdAndEoriId(eoriIdString = "")).toValidatedHeadersRequest(TestExtractedHeaders)

  private lazy val validatedHeadersRequestWithInValidBadgeIdTooLong =
    ConversationIdRequest(conversationId, testFakeRequestWithBadgeId(badgeIdString = "INVALID_BADGE_IDENTIFIER_TOO_LONG")).toValidatedHeadersRequest(TestExtractedHeaders)

  private lazy val validatedHeadersRequestWithInValidBadgeIdLowerCase =
    ConversationIdRequest(conversationId, testFakeRequestWithBadgeId(badgeIdString = "lowercase")).toValidatedHeadersRequest(TestExtractedHeaders)

  private lazy val validatedHeadersRequestWithInValidBadgeIdTooShort =
    ConversationIdRequest(conversationId, testFakeRequestWithBadgeId(badgeIdString = "SHORT")).toValidatedHeadersRequest(TestExtractedHeaders)

  private lazy val validatedHeadersRequestWithInValidBadgeIdInvalidChars =
    ConversationIdRequest(conversationId, testFakeRequestWithBadgeId(badgeIdString = "(*&*(^&*&%")).toValidatedHeadersRequest(TestExtractedHeaders)

  private lazy val validatedHeadersRequestWithValidEoriIdCamelCase =
    ConversationIdRequest(conversationId, FakeRequest().withXmlBody(TestXmlPayload).withHeaders(X_EORI_IDENTIFIER_NAME_CAMEL_CASE -> declarantEori.value)).toValidatedHeadersRequest(TestExtractedHeaders)

  private lazy val validatedHeadersRequestWithValidEoriId =
    ConversationIdRequest(conversationId, testFakeRequestWithEoriId(eoriId = declarantEori.value)).toValidatedHeadersRequest(TestExtractedHeaders)

  private lazy val validatedHeadersRequestWithInvalidEoriId =
    ConversationIdRequest(conversationId, testFakeRequestWithEoriId(eoriId = "eeee")).toValidatedHeadersRequest(TestExtractedHeaders)

  private lazy val validatedHeadersRequestWithInvalidEoriIdCamelCase =
    ConversationIdRequest(conversationId, FakeRequest().withXmlBody(TestXmlPayload).withHeaders(X_EORI_IDENTIFIER_NAME_CAMEL_CASE -> "aaaaa")).toValidatedHeadersRequest(TestExtractedHeaders)

  trait SetUp extends AuthConnectorStubbing {
    val mockExportsLogger: ExportsLogger = mock[ExportsLogger]
    val authAction: AuthAction = new AuthAction(mockAuthConnector, mockExportsLogger)
  }

  "CspAuthAction" should {
    "authorise as CSP when authorised by auth API and both badge identifier and eori headers exist" in new SetUp {
      authoriseCsp()

      private val actual = await(authAction.refine(validatedHeadersRequestWithValidBadgeIdAndEoriIdentifier))
      actual shouldBe Right(validatedHeadersRequestWithValidBadgeIdAndEoriIdentifier.toCspAuthorisedRequest(badgeEoriPair))
      verifyNonCspAuthorisationNotCalled
    }

    "return 400 response with ConversationId when authorised by auth API but badge identifier does not exist" in new SetUp {
      authoriseCsp()

      private val actual = await(authAction.refine(TestValidatedHeadersRequest))

      actual shouldBe Left(errorResponseBadgeIdentifierHeaderMissing.XmlResult.withHeaders(RequestHeaders.X_CONVERSATION_ID_NAME -> conversationId.toString))
      verifyNonCspAuthorisationNotCalled
    }

    "return 400 response with ConversationId when authorised by auth API and badge identifier exists, but eori header doesn't" in new SetUp {
      authoriseCsp()

      private val actual = await(authAction.refine(validatedHeadersRequestWithValidBadgeIdButNoEoriIdentifier))

      actual shouldBe Left(errorResponseEoriIdentifierHeaderMissing.XmlResult.withHeaders(RequestHeaders.X_CONVERSATION_ID_NAME -> conversationId.toString))
      verifyNonCspAuthorisationNotCalled
    }


    "return 400 response with ConversationId when authorised by auth API but badge identifier exists but is too long" in new SetUp {
      authoriseCsp()

      private val actual = await(authAction.refine(validatedHeadersRequestWithInValidBadgeIdTooLong))

      actual shouldBe Left(errorResponseBadgeIdentifierHeaderMissing.XmlResult.withHeaders(RequestHeaders.X_CONVERSATION_ID_NAME -> conversationId.toString))
      verifyNonCspAuthorisationNotCalled
    }

    "return 400 response with ConversationId when authorised by auth API and badge identifier exists but is too long but eori identifier is too long" in new SetUp {
      authoriseCsp()

      private val actual = await(authAction.refine(validatedHeadersRequestWithValidBadgeIdAndEoriIdTooLong))

      actual shouldBe Left(errorResponseEoriIdentifierHeaderMissing.XmlResult.withHeaders(RequestHeaders.X_CONVERSATION_ID_NAME -> conversationId.toString))
      verifyNonCspAuthorisationNotCalled
    }

    "return 400 response with ConversationId when authorised by auth API but badge identifier exists but is too short" in new SetUp {
      authoriseCsp()

      private val actual = await(authAction.refine(validatedHeadersRequestWithValidBadgeIdAndEoriIdTooShort))

      actual shouldBe Left(errorResponseEoriIdentifierHeaderMissing.XmlResult.withHeaders(RequestHeaders.X_CONVERSATION_ID_NAME -> conversationId.toString))
      verifyNonCspAuthorisationNotCalled
    }

    "return 400 response with ConversationId when authorised by auth API, badge identifier exists but eori identifier is too short" in new SetUp {
      authoriseCsp()

      private val actual = await(authAction.refine(validatedHeadersRequestWithInValidBadgeIdTooShort))

      actual shouldBe Left(errorResponseBadgeIdentifierHeaderMissing.XmlResult.withHeaders(RequestHeaders.X_CONVERSATION_ID_NAME -> conversationId.toString))
      verifyNonCspAuthorisationNotCalled
    }

    "return 400 response with ConversationId when authorised by auth API but badge identifier exists but contains invalid chars" in new SetUp {
      authoriseCsp()

      private val actual = await(authAction.refine(validatedHeadersRequestWithInValidBadgeIdInvalidChars))

      actual shouldBe Left(errorResponseBadgeIdentifierHeaderMissing.XmlResult.withHeaders(RequestHeaders.X_CONVERSATION_ID_NAME -> conversationId.toString))
      verifyNonCspAuthorisationNotCalled
    }

    "return 400 response with ConversationId when authorised by auth API but badge identifier exists but contains all lowercase chars" in new SetUp {
      authoriseCsp()

      private val actual = await(authAction.refine(validatedHeadersRequestWithInValidBadgeIdLowerCase))

      actual shouldBe Left(errorResponseBadgeIdentifierHeaderMissing.XmlResult.withHeaders(RequestHeaders.X_CONVERSATION_ID_NAME -> conversationId.toString))
      verifyNonCspAuthorisationNotCalled
    }

    "return 500 response with ConversationId when not authorised by auth API" in new SetUp {
      authoriseCspError()

      private val actual = await(authAction.refine(TestValidatedHeadersRequest))

      actual shouldBe Left(ErrorInternalServerError.XmlResult.withHeaders(RequestHeaders.X_CONVERSATION_ID_NAME -> conversationId.toString))
      verifyNonCspAuthorisationNotCalled
    }

  }

  "NonCspAuthAction" should {
    "authorise as non-CSP when authorised by auth API (without eori header in request) " in new SetUp {
      authoriseNonCsp(Some(declarantEori))

      private val actual = await(authAction.refine(TestValidatedHeadersRequest))

      actual shouldBe Right(TestValidatedHeadersRequest.toNonCspAuthorisedRequest(declarantEori))
      verifyCspAuthorisationCalled(1)
      verifyNonCspAuthorisationCalled(1)
    }

    "authorise as non-CSP when authorised by auth API (with eori header matching our records) " in new SetUp {
      authoriseNonCsp(Some(declarantEori))

      private val actual = await(authAction.refine(validatedHeadersRequestWithValidEoriId))

      actual shouldBe Right(validatedHeadersRequestWithValidEoriId.toNonCspAuthorisedRequest(declarantEori))
      verifyCspAuthorisationCalled(1)
      verifyNonCspAuthorisationCalled(1)
    }


    "authorise as non-CSP when authorised by auth API (with eori header matching our records and header name is camel case) " in new SetUp {
      authoriseNonCsp(Some(declarantEori))

      private val actual = await(authAction.refine(validatedHeadersRequestWithValidEoriIdCamelCase))

      actual shouldBe Right(validatedHeadersRequestWithValidEoriId.toNonCspAuthorisedRequest(declarantEori))
      verifyCspAuthorisationCalled(1)
      verifyNonCspAuthorisationCalled(1)
    }

    "authorise as non-CSP when authorised by auth API when authorised by auth API and ignore Eori in the header that doesn't match" in new SetUp {
      authoriseNonCsp(Some(declarantEori))

      private val actual = await(authAction.refine(validatedHeadersRequestWithInvalidEoriId))

      actual shouldBe Right(validatedHeadersRequestWithInvalidEoriId.toNonCspAuthorisedRequest(declarantEori))
      verifyCspAuthorisationCalled(1)
      verifyNonCspAuthorisationCalled(1)
    }

    "authorise as non-CSP when authorised by auth API when authorised by auth API and ignore Eori in the header that doesn't match and header name is camel case" in new SetUp {
      authoriseNonCsp(Some(declarantEori))

      private val actual = await(authAction.refine(validatedHeadersRequestWithInvalidEoriIdCamelCase))

      actual shouldBe Right(validatedHeadersRequestWithInvalidEoriIdCamelCase.toNonCspAuthorisedRequest(declarantEori))
      verifyCspAuthorisationCalled(1)
      verifyNonCspAuthorisationCalled(1)
    }

    "return 400 response with ConversationId when authorised by auth API but Eori (provided in header) is too long" in new SetUp {
      authoriseNonCsp(Some(Eori(eoriTooLong)))

      private val actual = await(authAction.refine(validatedHeadersRequestWithValidBadgeIdAndEoriIdTooLong))

      actual shouldBe Left(errorResponseEoriIdentifierHeaderInvalid.XmlResult.withHeaders(RequestHeaders.X_CONVERSATION_ID_NAME -> conversationId.toString))
      verifyCspAuthorisationCalled(1)
      verifyNonCspAuthorisationCalled(1)
    }

    "return 401 response with ConversationId when authorised by auth API but Eori does not exist" in new SetUp {
      authoriseNonCsp(maybeEori = None)

      private val actual = await(authAction.refine(TestValidatedHeadersRequest))

      actual shouldBe Left(errorResponseEoriNotFoundInCustomsEnrolment.XmlResult.withHeaders(RequestHeaders.X_CONVERSATION_ID_NAME -> conversationId.toString))
      verifyCspAuthorisationCalled(1)
      verifyNonCspAuthorisationCalled(1)
    }

    "return 401 response with ConversationId when not authorised as non-CSP" in new SetUp {
      unauthoriseCsp()
      unauthoriseNonCspOnly()

      private val actual = await(authAction.refine(TestValidatedHeadersRequest))

      actual shouldBe Left(errorResponseUnauthorisedGeneral.XmlResult.withHeaders(RequestHeaders.X_CONVERSATION_ID_NAME -> conversationId.toString))
      verifyCspAuthorisationCalled(1)
      verifyNonCspAuthorisationCalled(1)
    }

    "return 500 response with ConversationId when not authorised by auth API" in new SetUp {
      unauthoriseCsp()
      authoriseNonCspOnlyError()

      private val actual = await(authAction.refine(TestValidatedHeadersRequest))

      actual shouldBe Left(ErrorInternalServerError.XmlResult.withHeaders(RequestHeaders.X_CONVERSATION_ID_NAME -> conversationId.toString))
      verifyCspAuthorisationCalled(1)
      verifyNonCspAuthorisationCalled(1)
    }
  }

}
