/*
 * Copyright 2019 HM Revenue & Customs
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

import model.ApiSubscriptionFields
import org.scalatest.mockito.MockitoSugar
import play.api.http.Status
import play.api.http.Status.UNAUTHORIZED
import play.api.mvc.AnyContentAsXml
import play.api.test.FakeRequest
import uk.gov.hmrc.customs.api.common.controllers.ErrorResponse
import uk.gov.hmrc.customs.api.common.controllers.ErrorResponse.{ErrorInternalServerError, UnauthorizedCode, errorBadRequest}
import uk.gov.hmrc.customs.inventorylinking.export.controllers.CustomHeaderNames
import uk.gov.hmrc.customs.inventorylinking.export.controllers.actionbuilders.AuthAction
import uk.gov.hmrc.customs.inventorylinking.export.logging.ExportsLogger
import uk.gov.hmrc.customs.inventorylinking.export.model.Eori
import uk.gov.hmrc.customs.inventorylinking.export.model.actionbuilders.ActionBuilderModelHelper._
import uk.gov.hmrc.customs.inventorylinking.export.model.actionbuilders.{ApiSubscriptionFieldsRequest, ConversationIdRequest}
import uk.gov.hmrc.play.test.UnitSpec
import util.RequestHeaders.X_SUBMITTER_IDENTIFIER_NAME_CAMEL_CASE
import util.TestData._
import util.{ApiSubscriptionFieldsTestData, AuthConnectorStubbing, RequestHeaders}

class AuthActionSpec extends UnitSpec with MockitoSugar {

  private val errorResponseUnauthorisedGeneral =
    ErrorResponse(Status.UNAUTHORIZED, UnauthorizedCode, "Unauthorised request")
  private val errorResponseBadgeIdentifierHeaderMissing =
    errorBadRequest(s"${CustomHeaderNames.XBadgeIdentifierHeaderName} header is missing or invalid")
  private val errorResponseSubmitterIdentifierHeaderMissing =
    errorBadRequest(s"${CustomHeaderNames.XSubmitterIdentifierHeaderName} header is missing or invalid")
  private val errorResponseSubmitterIdentifierHeaderInvalid =
    errorBadRequest(s"${CustomHeaderNames.XSubmitterIdentifierHeaderName} header is invalid")

  private lazy val errorResponseEoriNotFoundInCustomsEnrolment =
    ErrorResponse(UNAUTHORIZED, UnauthorizedCode, "EORI number not found in Customs Enrolment")


  private def request(request: FakeRequest[AnyContentAsXml], fields: ApiSubscriptionFields = ApiSubscriptionFieldsTestData.apiSubscriptionFields): ApiSubscriptionFieldsRequest[AnyContentAsXml] = {
    ConversationIdRequest(conversationId, request)
      .toValidatedHeadersRequest(TestExtractedHeaders)
      .toApiSubscriptionFieldsRequest(fields)
  }

  private val eoriTooLong = "GB9988776655787656"

  private lazy val requestWithValidSubmitterIdCamelCase =
    ConversationIdRequest(conversationId, FakeRequest().withXmlBody(TestXmlPayload)
      .withHeaders(X_SUBMITTER_IDENTIFIER_NAME_CAMEL_CASE -> declarantEori.value))
      .toValidatedHeadersRequest(TestExtractedHeaders)
      .toApiSubscriptionFieldsRequest(ApiSubscriptionFieldsTestData.apiSubscriptionFields)
  private lazy val requestWithValidSubmitterId = request(testFakeRequestWithSubmitterId(submitterId = declarantEori.value))
  private lazy val requestWithInvalidSubmitterId = request(testFakeRequestWithSubmitterId(submitterId = "eeee"))
  private lazy val requestWithInvalidSubmitterIdCamelCase =
    ConversationIdRequest(conversationId, FakeRequest().withXmlBody(TestXmlPayload)
      .withHeaders(X_SUBMITTER_IDENTIFIER_NAME_CAMEL_CASE -> "aaaaa"))
      .toValidatedHeadersRequest(TestExtractedHeaders)
      .toApiSubscriptionFieldsRequest(ApiSubscriptionFieldsTestData.apiSubscriptionFields)

  trait SetUp extends AuthConnectorStubbing {
    val mockExportsLogger: ExportsLogger = mock[ExportsLogger]
    val authAction: AuthAction = new AuthAction(mockAuthConnector, mockExportsLogger)
  }

  "CspAuthAction" should {
    "authorise as CSP when authorised by auth API and both badge identifier and submitter headers exist" in new SetUp {
      authoriseCsp()

      private val actual = await(authAction.refine(request(testFakeRequestWithBadgeIdAndSubmitterId())))
      actual shouldBe Right(request(testFakeRequestWithBadgeIdAndSubmitterId()).toCspAuthorisedRequest(badgeEoriPair))
      verifyNonCspAuthorisationNotCalled
    }

    "return 400 response with ConversationId when authorised by auth API but badge identifier does not exist" in new SetUp {
      authoriseCsp()

      private val actual = await(authAction.refine(TestApiSubscriptionFieldsRequest))

      actual shouldBe Left(errorResponseBadgeIdentifierHeaderMissing.XmlResult.withHeaders(RequestHeaders.X_CONVERSATION_ID_NAME -> conversationId.toString))
      verifyNonCspAuthorisationNotCalled
    }

    "return 500 response with ConversationId when authorised by auth API and badge identifier exists, but submitter not present and authenticated EORI not present" in new SetUp {
      authoriseCsp()

      private val actual = await(authAction.refine(request(testFakeRequestWithBadgeId(), ApiSubscriptionFieldsTestData.apiSubscriptionFieldsNoAuthenticatedEori)))

      actual shouldBe Left(ErrorInternalServerError.XmlResult.withHeaders(RequestHeaders.X_CONVERSATION_ID_NAME -> conversationId.toString))
      verifyNonCspAuthorisationNotCalled
    }

    "return 500 response with ConversationId when authorised by auth API and badge identifier exists, but submitter not present and authenticated EORI contains only whitespace" in new SetUp {
      authoriseCsp()

      private val actual = await(authAction.refine(request(testFakeRequestWithBadgeId(), ApiSubscriptionFieldsTestData.apiSubscriptionFieldsBlankAuthenticatedEori)))

      actual shouldBe Left(ErrorInternalServerError.XmlResult.withHeaders(RequestHeaders.X_CONVERSATION_ID_NAME -> conversationId.toString))
      verifyNonCspAuthorisationNotCalled
    }

    "authorise as CSP when authorised by auth API and badge identifier exists, but submitter not present and authenticated EORI is present" in new SetUp {
      authoriseCsp()

      private val actual = await(authAction.refine(request(testFakeRequestWithBadgeId())))

      actual shouldBe Right(request(testFakeRequestWithBadgeId()).toCspAuthorisedRequest(badgeAuthenticatedEoriPair))
      verifyNonCspAuthorisationNotCalled
    }


    "return 400 response with ConversationId when authorised by auth API but badge identifier exists but is too long" in new SetUp {
      authoriseCsp()

      private val actual = await(authAction.refine(request(testFakeRequestWithBadgeId(badgeIdString = "INVALID_BADGE_IDENTIFIER_TOO_LONG"))))

      actual shouldBe Left(errorResponseBadgeIdentifierHeaderMissing.XmlResult.withHeaders(RequestHeaders.X_CONVERSATION_ID_NAME -> conversationId.toString))
      verifyNonCspAuthorisationNotCalled
    }

    "return 400 response with ConversationId when authorised by auth API and badge identifier exists but is too long but submitter identifier is too long" in new SetUp {
      authoriseCsp()

      private val actual = await(authAction.refine(request(testFakeRequestWithBadgeIdAndSubmitterId(submitterIdString = eoriTooLong))))

      actual shouldBe Left(errorResponseSubmitterIdentifierHeaderInvalid.XmlResult.withHeaders(RequestHeaders.X_CONVERSATION_ID_NAME -> conversationId.toString))
      verifyNonCspAuthorisationNotCalled
    }

    "return 400 response with ConversationId when authorised by auth API, badge identifier exists but submitter identifier is too short" in new SetUp {
      authoriseCsp()

      private val actual = await(authAction.refine(request(testFakeRequestWithBadgeId(badgeIdString = "SHORT"))))

      actual shouldBe Left(errorResponseBadgeIdentifierHeaderMissing.XmlResult.withHeaders(RequestHeaders.X_CONVERSATION_ID_NAME -> conversationId.toString))
      verifyNonCspAuthorisationNotCalled
    }

    "return 400 response with ConversationId when authorised by auth API but badge identifier exists but contains invalid chars" in new SetUp {
      authoriseCsp()

      private val actual = await(authAction.refine(request(testFakeRequestWithBadgeId(badgeIdString = "(*&*(^&*&%"))))

      actual shouldBe Left(errorResponseBadgeIdentifierHeaderMissing.XmlResult.withHeaders(RequestHeaders.X_CONVERSATION_ID_NAME -> conversationId.toString))
      verifyNonCspAuthorisationNotCalled
    }

    "return 400 response with ConversationId when authorised by auth API but badge identifier exists but contains all lowercase chars" in new SetUp {
      authoriseCsp()

      private val actual = await(authAction.refine(request(testFakeRequestWithBadgeId(badgeIdString = "lowercase"))))

      actual shouldBe Left(errorResponseBadgeIdentifierHeaderMissing.XmlResult.withHeaders(RequestHeaders.X_CONVERSATION_ID_NAME -> conversationId.toString))
      verifyNonCspAuthorisationNotCalled
    }

    "return 500 response with ConversationId when not authorised by auth API" in new SetUp {
      authoriseCspError()

      private val actual = await(authAction.refine(TestApiSubscriptionFieldsRequest))

      actual shouldBe Left(ErrorInternalServerError.XmlResult.withHeaders(RequestHeaders.X_CONVERSATION_ID_NAME -> conversationId.toString))
      verifyNonCspAuthorisationNotCalled
    }

  }

  "NonCspAuthAction" should {
    "authorise as non-CSP when authorised by auth API (without submitter header in request) " in new SetUp {
      authoriseNonCsp(Some(declarantEori))

      private val actual = await(authAction.refine(TestApiSubscriptionFieldsRequest))

      actual shouldBe Right(TestApiSubscriptionFieldsRequest.toNonCspAuthorisedRequest(declarantEori))
      verifyCspAuthorisationCalled(1)
      verifyNonCspAuthorisationCalled(1)
    }

    "authorise as non-CSP when authorised by auth API (with submitter header matching our records) " in new SetUp {
      authoriseNonCsp(Some(declarantEori))

      private val actual = await(authAction.refine(requestWithValidSubmitterId))

      actual shouldBe Right(requestWithValidSubmitterId.toNonCspAuthorisedRequest(declarantEori))
      verifyCspAuthorisationCalled(1)
      verifyNonCspAuthorisationCalled(1)
    }


    "authorise as non-CSP when authorised by auth API (with submitter header matching our records and header name is camel case) " in new SetUp {
      authoriseNonCsp(Some(declarantEori))

      private val actual = await(authAction.refine(requestWithValidSubmitterIdCamelCase))

      actual shouldBe Right(requestWithValidSubmitterId.toNonCspAuthorisedRequest(declarantEori))
      verifyCspAuthorisationCalled(1)
      verifyNonCspAuthorisationCalled(1)
    }

    "authorise as non-CSP when authorised by auth API when authorised by auth API and ignore Submitter in the header that doesn't match" in new SetUp {
      authoriseNonCsp(Some(declarantEori))

      private val actual = await(authAction.refine(requestWithInvalidSubmitterId))

      actual shouldBe Right(requestWithInvalidSubmitterId.toNonCspAuthorisedRequest(declarantEori))
      verifyCspAuthorisationCalled(1)
      verifyNonCspAuthorisationCalled(1)
    }

    "authorise as non-CSP when authorised by auth API when authorised by auth API and ignore Submitter in the header that doesn't match and header name is camel case" in new SetUp {
      authoriseNonCsp(Some(declarantEori))

      private val actual = await(authAction.refine(requestWithInvalidSubmitterIdCamelCase))

      actual shouldBe Right(requestWithInvalidSubmitterIdCamelCase.toNonCspAuthorisedRequest(declarantEori))
      verifyCspAuthorisationCalled(1)
      verifyNonCspAuthorisationCalled(1)
    }

    "return 400 response with ConversationId when authorised by auth API but Submitter (provided in header) is too long" in new SetUp {
      authoriseNonCsp(Some(Eori(eoriTooLong)))

      private val actual = await(authAction.refine(request(testFakeRequestWithBadgeIdAndSubmitterId(submitterIdString = eoriTooLong))))

      actual shouldBe Left(errorResponseSubmitterIdentifierHeaderInvalid.XmlResult.withHeaders(RequestHeaders.X_CONVERSATION_ID_NAME -> conversationId.toString))
      verifyCspAuthorisationCalled(1)
      verifyNonCspAuthorisationCalled(1)
    }

    "return 401 response with ConversationId when authorised by auth API but Submitter does not exist" in new SetUp {
      authoriseNonCsp(maybeEori = None)

      private val actual = await(authAction.refine(TestApiSubscriptionFieldsRequest))

      actual shouldBe Left(errorResponseEoriNotFoundInCustomsEnrolment.XmlResult.withHeaders(RequestHeaders.X_CONVERSATION_ID_NAME -> conversationId.toString))
      verifyCspAuthorisationCalled(1)
      verifyNonCspAuthorisationCalled(1)
    }

    "return 401 response with ConversationId when not authorised as non-CSP" in new SetUp {
      unauthoriseCsp()
      unauthoriseNonCspOnly()

      private val actual = await(authAction.refine(TestApiSubscriptionFieldsRequest))

      actual shouldBe Left(errorResponseUnauthorisedGeneral.XmlResult.withHeaders(RequestHeaders.X_CONVERSATION_ID_NAME -> conversationId.toString))
      verifyCspAuthorisationCalled(1)
      verifyNonCspAuthorisationCalled(1)
    }

    "return 500 response with ConversationId when not authorised by auth API" in new SetUp {
      unauthoriseCsp()
      authoriseNonCspOnlyError()

      private val actual = await(authAction.refine(TestApiSubscriptionFieldsRequest))

      actual shouldBe Left(ErrorInternalServerError.XmlResult.withHeaders(RequestHeaders.X_CONVERSATION_ID_NAME -> conversationId.toString))
      verifyCspAuthorisationCalled(1)
      verifyNonCspAuthorisationCalled(1)
    }
  }

}
