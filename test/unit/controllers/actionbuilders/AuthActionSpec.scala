/*
 * Copyright 2023 HM Revenue & Customs
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

import org.scalatestplus.mockito.MockitoSugar
import play.api.http.Status
import play.api.http.Status.UNAUTHORIZED
import play.api.mvc.AnyContentAsXml
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.customs.inventorylinking.export.controllers.ErrorResponse
import uk.gov.hmrc.customs.inventorylinking.export.controllers.ErrorResponse.{ErrorInternalServerError, UnauthorizedCode, errorBadRequest, errorInternalServerError}
import uk.gov.hmrc.customs.inventorylinking.export.controllers.actionbuilders.AuthAction
import uk.gov.hmrc.customs.inventorylinking.export.controllers.{CustomHeaderNames, HeaderValidator}
import uk.gov.hmrc.customs.inventorylinking.export.logging.ExportsLogger
import uk.gov.hmrc.customs.inventorylinking.export.model.{ApiSubscriptionFields, NonCsp, VersionOne}
import uk.gov.hmrc.customs.inventorylinking.export.model.actionbuilders.ActionBuilderModelHelper._
import uk.gov.hmrc.customs.inventorylinking.export.model.actionbuilders.{ApiSubscriptionFieldsRequest, ApiVersionRequest, AuthorisedRequest}
import uk.gov.hmrc.customs.inventorylinking.export.services.CustomsAuthService
import util.CustomsMetricsTestData.EventStart
import util.TestData._
import util.{ApiSubscriptionFieldsTestData, AuthConnectorStubbing, RequestHeaders, UnitSpec}

import scala.concurrent.ExecutionContext

class AuthActionSpec extends UnitSpec with MockitoSugar {

  private val errorResponseUnauthorisedGeneral =
    ErrorResponse(Status.UNAUTHORIZED, UnauthorizedCode, "Unauthorised request")
  private val errorResponseBadgeIdentifierHeaderMissing =
    errorBadRequest(s"${CustomHeaderNames.XBadgeIdentifierHeaderName} header is missing or invalid")
  private val errorResponseSubmitterIdentifierHeaderInvalid =
    errorBadRequest(s"${CustomHeaderNames.XSubmitterIdentifierHeaderName} header is invalid")
  private lazy val missingEoriResult = errorInternalServerError("Missing authenticated eori in service lookup. Alternately, use X-Badge-Identifier or X-Submitter-Identifier headers.")

  private lazy val errorResponseEoriNotFoundInCustomsEnrolment =
    ErrorResponse(UNAUTHORIZED, UnauthorizedCode, "EORI number not found in Customs Enrolment")

  private def request(request: FakeRequest[AnyContentAsXml], fields: ApiSubscriptionFields = ApiSubscriptionFieldsTestData.apiSubscriptionFields): ApiSubscriptionFieldsRequest[AnyContentAsXml] = {
    ApiVersionRequest(conversationId, EventStart, VersionOne, request)
      .toValidatedHeadersRequest(TestExtractedHeaders)
      .toApiSubscriptionFieldsRequest(fields)
  }

  private val eoriTooLong = "GB9988776655787656"

  trait SetUp extends AuthConnectorStubbing {
    implicit val ec: ExecutionContext = Helpers.stubControllerComponents().executionContext
    val mockExportsLogger: ExportsLogger = mock[ExportsLogger]
    val customsAuthService = new CustomsAuthService(mockAuthConnector, mockExportsLogger)
    val headerValidator = new HeaderValidator(mockExportsLogger)
    val authAction: AuthAction = new AuthAction(customsAuthService, headerValidator, mockExportsLogger)
  }

  "CspAuthAction" should {
    "authorise as CSP when authorised by auth API and both badge identifier and submitter headers exist" in new SetUp {
      authoriseCsp()

      private val actual: AuthorisedRequest[AnyContentAsXml] = await(authAction.refine(request(testFakeRequestWithMaybeBadgeIdAndMaybeSubmitterId())).getOrElse(throw new RuntimeException("failed to refine test authAction")))
      private val expected: AuthorisedRequest[AnyContentAsXml] = request(testFakeRequestWithMaybeBadgeIdAndMaybeSubmitterId()).toAuthorisedRequest(cspAuthorisedRequestWithEoriAndBadgeIdentifier)
      actual.authorisedAs shouldBe expected.authorisedAs

      verifyNonCspAuthorisationNotCalled
    }

    "authorise as CSP when authorised by auth API and badge identifier header exists but submitter header does not" in new SetUp {
      authoriseCsp()

      private val actual = await(authAction.refine(request(testFakeRequestWithMaybeBadgeIdAndMaybeSubmitterId(maybeSubmitterIdString = None))).getOrElse(throw new RuntimeException("failed to refine test authAction")))
      private val expected: AuthorisedRequest[AnyContentAsXml] = request(testFakeRequestWithMaybeBadgeIdAndMaybeSubmitterId(maybeSubmitterIdString = None)).toAuthorisedRequest(cspAuthorisedRequestWithBadgeIdentifier)

      actual.authorisedAs shouldBe expected.authorisedAs
      verifyNonCspAuthorisationNotCalled
    }

    "return 500 response with conversationId when authorised by auth API and badge identifier, submitter and authenticated EORI do not exist" in new SetUp {
      authoriseCsp()

      private val actual = await(authAction.refine(request(testFakeRequestWithMaybeBadgeIdAndMaybeSubmitterId(maybeSubmitterIdString = None, maybeBadgeIdString = None), ApiSubscriptionFieldsTestData.apiSubscriptionFieldsNoAuthenticatedEori)))

      actual shouldBe Left(missingEoriResult.XmlResult.withHeaders(RequestHeaders.X_CONVERSATION_ID_NAME -> conversationId.toString))
      verifyNonCspAuthorisationNotCalled
    }

    "authorise as CSP when authorised by auth API and badge identifier exists, but submitter and authenticated EORI not present" in new SetUp {
      authoriseCsp()

      private val actual = await(authAction.refine(request(testFakeRequestWithMaybeBadgeIdAndMaybeSubmitterId(maybeSubmitterIdString = None), ApiSubscriptionFieldsTestData.apiSubscriptionFieldsNoAuthenticatedEori)).getOrElse(throw new RuntimeException("failed to refine test authAction")))
      private val expected = request(testFakeRequestWithMaybeBadgeIdAndMaybeSubmitterId(maybeSubmitterIdString = None)).toAuthorisedRequest(cspAuthorisedRequestWithBadgeIdentifier)

      actual.authorisedAs shouldBe expected.authorisedAs
      verifyNonCspAuthorisationNotCalled
    }

    "authorise as CSP when authorised by auth API and badge identifier exists, but submitter not present and authenticated EORI contains only whitespace" in new SetUp {
      authoriseCsp()

      private val actual = await(authAction.refine(request(testFakeRequestWithMaybeBadgeIdAndMaybeSubmitterId(maybeSubmitterIdString = None), ApiSubscriptionFieldsTestData.apiSubscriptionFieldsBlankAuthenticatedEori)).getOrElse(throw new RuntimeException("failed to refine test authAction")))
      private val expected = request(testFakeRequestWithMaybeBadgeIdAndMaybeSubmitterId(maybeSubmitterIdString = None)).toAuthorisedRequest(cspAuthorisedRequestWithBadgeIdentifier)

      actual.authorisedAs shouldBe expected.authorisedAs
      verifyNonCspAuthorisationNotCalled
    }

    "authorise as CSP when authorised by auth API and submitter header and authenticated EORI exists but badge identifier header does not" in new SetUp {
      authoriseCsp()

      private val actual = await(authAction.refine(request(testFakeRequestWithMaybeBadgeIdAndMaybeSubmitterId(maybeBadgeIdString = None))).getOrElse(throw new RuntimeException("failed to refine test authAction")))
      private val expected = request(testFakeRequestWithMaybeBadgeIdAndMaybeSubmitterId(maybeBadgeIdString = None)).toAuthorisedRequest(cspAuthorisedRequestWithEori)
      actual.authorisedAs shouldBe expected.authorisedAs
      verifyNonCspAuthorisationNotCalled
    }

    "return 400 response with conversationId when authorised by auth API but badge identifier exists but is too long" in new SetUp {
      authoriseCsp()

      private val actual = await(authAction.refine(request(testFakeRequestWithMaybeBadgeIdAndMaybeSubmitterId(maybeBadgeIdString = Some("INVALID_BADGE_IDENTIFIER_TOO_LONG")))))

      actual shouldBe Left(errorResponseBadgeIdentifierHeaderMissing.XmlResult.withHeaders(RequestHeaders.X_CONVERSATION_ID_NAME -> conversationId.toString))
      verifyNonCspAuthorisationNotCalled
    }

    "return 400 response with conversationId when authorised by auth API and badge identifier exists but is too long" in new SetUp {
      authoriseCsp()

      private val actual = await(authAction.refine(request(testFakeRequestWithMaybeBadgeIdAndMaybeSubmitterId(maybeSubmitterIdString = Some(eoriTooLong)))))

      actual shouldBe Left(errorResponseSubmitterIdentifierHeaderInvalid.XmlResult.withHeaders(RequestHeaders.X_CONVERSATION_ID_NAME -> conversationId.toString))
      verifyNonCspAuthorisationNotCalled
    }

    "return 400 response with conversationId when authorised by auth API, badge identifier exists but submitter identifier is too short" in new SetUp {
      authoriseCsp()

      private val actual = await(authAction.refine(request(testFakeRequestWithMaybeBadgeIdAndMaybeSubmitterId(maybeBadgeIdString = Some("SHORT")))))

      actual shouldBe Left(errorResponseBadgeIdentifierHeaderMissing.XmlResult.withHeaders(RequestHeaders.X_CONVERSATION_ID_NAME -> conversationId.toString))
      verifyNonCspAuthorisationNotCalled
    }

    "return 400 response with conversationId when authorised by auth API but badge identifier exists but contains invalid chars" in new SetUp {
      authoriseCsp()

      private val actual = await(authAction.refine(request(testFakeRequestWithMaybeBadgeIdAndMaybeSubmitterId(maybeBadgeIdString = Some("(*&*(^&*&%")))))

      actual shouldBe Left(errorResponseBadgeIdentifierHeaderMissing.XmlResult.withHeaders(RequestHeaders.X_CONVERSATION_ID_NAME -> conversationId.toString))
      verifyNonCspAuthorisationNotCalled
    }

    "return 400 response with conversationId when authorised by auth API but badge identifier exists but contains all lowercase chars" in new SetUp {
      authoriseCsp()

      private val actual = await(authAction.refine(request(testFakeRequestWithMaybeBadgeIdAndMaybeSubmitterId(maybeBadgeIdString = Some("lowercase")))))

      actual shouldBe Left(errorResponseBadgeIdentifierHeaderMissing.XmlResult.withHeaders(RequestHeaders.X_CONVERSATION_ID_NAME -> conversationId.toString))
      verifyNonCspAuthorisationNotCalled
    }

    "return 500 response with conversationId when not authorised by auth API" in new SetUp {
      authoriseCspError()

      private val actual = await(authAction.refine(TestApiSubscriptionFieldsRequest))

      actual shouldBe Left(ErrorInternalServerError.XmlResult.withHeaders(RequestHeaders.X_CONVERSATION_ID_NAME -> conversationId.toString))
      verifyNonCspAuthorisationNotCalled
    }

  }

  "NonCspAuthAction" should {
    "authorise as non-CSP when authorised by auth API and submitter header does not exist " in new SetUp {
      authoriseNonCsp(Some(declarantEori))

      private val actual = await(authAction.refine(TestApiSubscriptionFieldsRequest))

      actual shouldBe Right(TestApiSubscriptionFieldsRequest.toAuthorisedRequest(NonCsp(declarantEori)))
      verifyCspAuthorisationCalled(1)
      verifyNonCspAuthorisationCalled(1)
    }

    "return 401 response with conversationId when authorised by auth API but submitter does not exist" in new SetUp {
      authoriseNonCsp(maybeEori = None)

      private val actual = await(authAction.refine(TestApiSubscriptionFieldsRequest))

      actual shouldBe Left(errorResponseEoriNotFoundInCustomsEnrolment.XmlResult.withHeaders(RequestHeaders.X_CONVERSATION_ID_NAME -> conversationId.toString))
      verifyCspAuthorisationCalled(1)
      verifyNonCspAuthorisationCalled(1)
    }

    "return 401 response with conversationId when not authorised as non-CSP" in new SetUp {
      unauthoriseCsp()
      unauthoriseNonCspOnly()

      private val actual = await(authAction.refine(TestApiSubscriptionFieldsRequest))

      actual shouldBe Left(errorResponseUnauthorisedGeneral.XmlResult.withHeaders(RequestHeaders.X_CONVERSATION_ID_NAME -> conversationId.toString))
      verifyCspAuthorisationCalled(1)
      verifyNonCspAuthorisationCalled(1)
    }

    "return 500 response with conversationId when not authorised by auth API" in new SetUp {
      unauthoriseCsp()
      authoriseNonCspOnlyError()

      private val actual = await(authAction.refine(TestApiSubscriptionFieldsRequest))

      actual shouldBe Left(ErrorInternalServerError.XmlResult.withHeaders(RequestHeaders.X_CONVERSATION_ID_NAME -> conversationId.toString))
      verifyCspAuthorisationCalled(1)
      verifyNonCspAuthorisationCalled(1)
    }
  }

}