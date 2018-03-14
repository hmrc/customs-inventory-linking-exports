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

import org.mockito.ArgumentMatchers.{any, eq => ameq}
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfterEach, Matchers}
import play.api.libs.json.Json
import play.api.mvc._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.AuthProvider.{GovernmentGateway, PrivilegedApplication}
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.{EmptyRetrieval, Retrievals}
import uk.gov.hmrc.customs.api.common.controllers.ErrorResponse
import uk.gov.hmrc.customs.api.common.controllers.ErrorResponse.errorBadRequest
import uk.gov.hmrc.customs.inventorylinking.export.connectors.InventoryLinkingAuthConnector
import uk.gov.hmrc.customs.inventorylinking.export.controllers.InventoryLinkingExportController
import uk.gov.hmrc.customs.inventorylinking.export.logging.ExportsLogger
import uk.gov.hmrc.customs.inventorylinking.export.model.{ApiDefinitionConfig, BadgeIdentifier, CustomsEnrolmentConfig, Eori}
import uk.gov.hmrc.customs.inventorylinking.export.services.{CustomsConfigService, ExportsBusinessService}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec
import util.MockitoPassByNameHelper.PassByNameVerifier
import util.RequestHeaders
import util.RequestHeaders.{ValidHeaders, X_BADGE_IDENTIFIER_NAME}
import util.TestData._
import util.XMLTestData._

import scala.concurrent.{ExecutionContext, Future}
import scala.xml.NodeSeq
class InventoryLinkingExportControllerSpec extends UnitSpec with Matchers with MockitoSugar with BeforeAndAfterEach {

  private val mockAuthConnector = mock[InventoryLinkingAuthConnector]
  private val mockExportsLogger = mock[ExportsLogger]
  private val mockCustomsConfigService = mock[CustomsConfigService]
  private val mockBusinessService = mock[ExportsBusinessService]

  val controller = new InventoryLinkingExportController(mockAuthConnector, mockCustomsConfigService, mockBusinessService, mockExportsLogger)

  private val apiScope = "scope-in-api-definition"
  private val customsEnrolmentName = "HMRC-CUS-ORG"
  private val eoriIdentifier = "EORINumber"
  private val mockApiDefinitionConfig = mock[ApiDefinitionConfig]
  private val customsEnrolmentConfig = CustomsEnrolmentConfig(customsEnrolmentName, eoriIdentifier)

  private val errorResultInternalServer = ErrorResponse(INTERNAL_SERVER_ERROR, errorCode = "INTERNAL_SERVER_ERROR",
    message = "Internal server error").XmlResult

  private val errorResultEoriNotFoundInCustomsEnrolment = ErrorResponse(UNAUTHORIZED, errorCode = "UNAUTHORIZED",
    message = "EORI number not found in Customs Enrolment").XmlResult

  private val errorResultUnauthorised = ErrorResponse(UNAUTHORIZED, errorCode = "UNAUTHORIZED",
    message = "Unauthorised request").XmlResult

  private val errorResultBadgeIdentifier = errorBadRequest("X-Badge-Identifier header is missing or invalid").XmlResult

  private val mockErrorResponse = mock[ErrorResponse]
  private val mockResult = mock[Result]

  private val cspAuthPredicate = Enrolment(apiScope) and AuthProviders(PrivilegedApplication)
  private val nonCspAuthPredicate = Enrolment(customsEnrolmentName) and AuthProviders(GovernmentGateway)

  override protected def beforeEach() {
    reset(mockExportsLogger, mockAuthConnector, mockBusinessService)

    when(mockApiDefinitionConfig.apiScope).thenReturn(apiScope)
    when(mockCustomsConfigService.apiDefinitionConfig).thenReturn(mockApiDefinitionConfig)
    when(mockCustomsConfigService.customsEnrolmentConfig).thenReturn(customsEnrolmentConfig)

    when(mockBusinessService.authorisedCspSubmission(any[NodeSeq], any[Option[BadgeIdentifier]])(any[HeaderCarrier])).thenReturn(Right(conversationId))
    when(mockBusinessService.authorisedNonCspSubmission(any[NodeSeq])(any[HeaderCarrier])).thenReturn(Right(conversationId))
  }

  "InventoryLinkingExportController" should {
    "process CSP request when call is authorised for CSP" in {
      authoriseCsp()
      testSubmitResult(ValidRequest) { result =>
        await(result)
        verifyCspAuthorisationCalled(numberOfTimes = 1)
        verifyNonCspAuthorisationCalled(numberOfTimes = 0)
        verify(mockBusinessService).authorisedCspSubmission(ameq(ValidInventoryLinkingMovementRequestXML), ameq(Some(badgeIdentifier)))(any[HeaderCarrier])
        verify(mockBusinessService, never).authorisedNonCspSubmission(any[NodeSeq])(any[HeaderCarrier])
      }
    }

    "process a non-CSP request when call is unauthorised for CSP but authorised for non-CSP" in {
      authoriseNonCsp(Some(declarantEori))
      testSubmitResult(ValidRequest) { result =>
        await(result)
        verifyCspAuthorisationCalled(numberOfTimes = 1)
        verifyNonCspAuthorisationCalled(numberOfTimes = 1)
        verify(mockBusinessService, never).authorisedCspSubmission(any[NodeSeq], any[Option[BadgeIdentifier]])(any[HeaderCarrier])
        verify(mockBusinessService).authorisedNonCspSubmission(ameq(ValidInventoryLinkingMovementRequestXML))(any[HeaderCarrier])
      }
    }

    "respond with status 202 and conversationId in header for a processed valid CSP request" in {
      authoriseCsp()
      testSubmitResult(ValidRequest) { result =>
        status(result) shouldBe ACCEPTED
        header("X-Conversation-ID", result) shouldBe Some(conversationIdValue)
      }
    }

    "respond with status 400 for a CSP request with a missing X-Badge-Identifier" in {
      authoriseCsp()
      testSubmitResult(ValidRequest.copyFakeRequest(headers = ValidRequest.headers.remove(X_BADGE_IDENTIFIER_NAME))) { result =>
        await(result) shouldBe errorResultBadgeIdentifier
        verifyZeroInteractions(mockBusinessService)
        PassByNameVerifier(mockExportsLogger, "error")
          .withByNameParam[String]("Header validation failed because X-Badge-Identifier header is missing or invalid")
          .withAnyHeaderCarrierParam
          .verify()

      }
    }

    "respond with status 400 for a request with an invalid X-Badge-Identifier" in {
      authoriseCsp()
      testSubmitResult(ValidRequest.withHeaders((ValidHeaders + (X_BADGE_IDENTIFIER_NAME -> invalidBadgeIdentifierValue)).toSeq: _*)) { result =>
        await(result) shouldBe errorResultBadgeIdentifier
        verifyZeroInteractions(mockBusinessService)
      }
    }

    "respond with status 202 and conversationId in header for a processed valid non-CSP request" in {
      authoriseNonCsp(Some(declarantEori))
      testSubmitResult(ValidRequest) { result =>
        status(result) shouldBe ACCEPTED
        header("X-Conversation-ID", result) shouldBe Some(conversationIdValue)
      }
    }

    "return result 401 UNAUTHORISED when call is unauthorised for both CSP and non-CSP submissions" in {
      unauthoriseCsp()
      unauthoriseNonCspOnly()
      testSubmitResult(ValidRequest) { result =>
        await(result) shouldBe errorResultUnauthorised
        verifyZeroInteractions(mockBusinessService)
      }
    }

    "return result 401 UNAUTHORISED when there's no Customs enrolment retrieved for an enrolled non-CSP call" in {
      unauthoriseCsp()
      authoriseNonCspButDontRetrieveCustomsEnrolment()
      testSubmitResult(ValidRequest.fromNonCsp) { result =>
        await(result) shouldBe errorResultEoriNotFoundInCustomsEnrolment
        verifyZeroInteractions(mockBusinessService)
        PassByNameVerifier(mockExportsLogger, "warn")
          .withByNameParam[String](s"Customs enrolment $customsEnrolmentName not retrieved for authorised non-CSP call")
          .withAnyHeaderCarrierParam
          .verify()
      }
    }

    "return result 401 UNAUTHORISED when there's no EORI number in Customs enrolment for a non-CSP call" in {
      unauthoriseCsp()
      authoriseNonCsp(maybeEori = None)
      testSubmitResult(ValidRequest) { result =>
        await(result) shouldBe errorResultEoriNotFoundInCustomsEnrolment
        verifyZeroInteractions(mockBusinessService)
      }
    }

    "respond with status 500 when a CSP request processing fails with a system error" in {
      when(mockBusinessService.authorisedCspSubmission(any[NodeSeq], any[Option[BadgeIdentifier]])(any[HeaderCarrier]))
        .thenReturn(Future.failed(emulatedServiceFailure))

      authoriseCsp()
      testSubmitResult(ValidRequest) { result =>
        await(result) shouldBe errorResultInternalServer
      }
    }

    "respond with status 500 when a non-CSP request processing fails with a system error" in {
      when(mockBusinessService.authorisedNonCspSubmission(any[NodeSeq])(any[HeaderCarrier]))
        .thenReturn(Future.failed(emulatedServiceFailure))

      authoriseNonCsp(Some(declarantEori))
      testSubmitResult(ValidRequest) { result =>
        await(result) shouldBe errorResultInternalServer
      }
    }

    "return xml-result of the error response returned from CSP request processing" in {
      when(mockBusinessService.authorisedCspSubmission(any[NodeSeq], any[Option[BadgeIdentifier]])(any[HeaderCarrier]))
        .thenReturn(Left(mockErrorResponse))
      when(mockErrorResponse.XmlResult).thenReturn(mockResult)

      authoriseCsp()
      testSubmitResult(ValidRequest) { result =>
        await(result) shouldBe mockResult
      }
    }

    "return xml-result of the error response returned from non-CSP request processing" in {
      when(mockBusinessService.authorisedNonCspSubmission(any[NodeSeq])(any[HeaderCarrier]))
        .thenReturn(Left(mockErrorResponse))
      when(mockErrorResponse.XmlResult).thenReturn(mockResult)

      authoriseNonCsp(Some(declarantEori))
      testSubmitResult(ValidRequest) { result =>
        await(result) shouldBe mockResult
      }
    }

    "return xml-result of the error response if request doesn't contain a well formed xml" in {
      when(mockBusinessService.authorisedNonCspSubmission(any[NodeSeq])(any[HeaderCarrier]))
        .thenReturn(Left(mockErrorResponse))
      when(mockErrorResponse.XmlResult).thenReturn(mockResult)
      val invalidRequest = FakeRequest()
        .withHeaders(RequestHeaders.ACCEPT_HMRC_XML_HEADER,
          RequestHeaders.CONTENT_TYPE_HEADER,
          RequestHeaders.API_SUBSCRIPTION_FIELDS_ID_HEADER)
        .withJsonBody(Json.parse("{}"))
      authoriseNonCsp(Some(declarantEori))
      testSubmitResult(invalidRequest) { result =>
        status(result) shouldBe BAD_REQUEST
      }
    }
  }

  private def authoriseCsp(): Unit = {
    when(mockAuthConnector.authorise(ameq(cspAuthPredicate), ameq(EmptyRetrieval))(any[HeaderCarrier], any[ExecutionContext]))
      .thenReturn(())
  }

  private def unauthoriseCsp(authException: AuthorisationException = new InsufficientEnrolments): Unit = {
    when(mockAuthConnector.authorise(ameq(cspAuthPredicate), ameq(EmptyRetrieval))(any[HeaderCarrier], any[ExecutionContext]))
      .thenReturn(Future.failed(authException))
  }

  private def authoriseNonCsp(maybeEori: Option[Eori]): Unit = {
    unauthoriseCsp()
    val customsEnrolment = maybeEori.fold(ifEmpty = Enrolment(customsEnrolmentName)) { eori =>
      Enrolment(customsEnrolmentName).withIdentifier(eoriIdentifier, eori.value)
    }
    when(mockAuthConnector.authorise(ameq(nonCspAuthPredicate), ameq(Retrievals.authorisedEnrolments))(any[HeaderCarrier], any[ExecutionContext]))
      .thenReturn(Enrolments(Set(customsEnrolment)))
  }

  private def authoriseNonCspButDontRetrieveCustomsEnrolment(): Unit = {
    unauthoriseCsp()
    when(mockAuthConnector.authorise(ameq(nonCspAuthPredicate), ameq(Retrievals.authorisedEnrolments))(any[HeaderCarrier], any[ExecutionContext]))
      .thenReturn(Enrolments(Set.empty))
  }

  private def unauthoriseNonCspOnly(authException: AuthorisationException = new InsufficientEnrolments): Unit = {
    when(mockAuthConnector.authorise(ameq(nonCspAuthPredicate), ameq(Retrievals.authorisedEnrolments))(any[HeaderCarrier], any[ExecutionContext]))
      .thenReturn(Future.failed(authException))
  }

  private def verifyCspAuthorisationCalled(numberOfTimes: Int) = {
    verify(mockAuthConnector, times(numberOfTimes))
      .authorise(ameq(cspAuthPredicate), ameq(EmptyRetrieval))(any[HeaderCarrier], any[ExecutionContext])
  }

  private def verifyNonCspAuthorisationCalled(numberOfTimes: Int) = {
    verify(mockAuthConnector, times(numberOfTimes))
      .authorise(ameq(nonCspAuthPredicate), ameq(Retrievals.authorisedEnrolments))(any[HeaderCarrier], any[ExecutionContext])
  }

  private def testSubmitResult(request: Request[AnyContent])(test: Future[Result] => Unit) {
    val result = controller.post().apply(request)
    test(result)
  }
}
