/*
 * Copyright 2022 HM Revenue & Customs
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

import akka.stream.Materializer
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatest.{Assertion, BeforeAndAfterEach}
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.mockito.MockitoSugar
import play.api.mvc._
import play.api.test.Helpers
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.customs.api.common.controllers.ErrorResponse
import uk.gov.hmrc.customs.api.common.controllers.ErrorResponse.{ErrorInternalServerError, errorBadRequest, errorInternalServerError}
import uk.gov.hmrc.customs.inventorylinking.export.services.ExportsConfigService
import uk.gov.hmrc.customs.inventorylinking.export.connectors.{ApiSubscriptionFieldsConnector, CustomsMetricsConnector}
import uk.gov.hmrc.customs.inventorylinking.export.controllers.actionbuilders._
import uk.gov.hmrc.customs.inventorylinking.export.controllers.{HeaderValidator, InventoryLinkingExportController}
import uk.gov.hmrc.customs.inventorylinking.export.logging.ExportsLogger
import uk.gov.hmrc.customs.inventorylinking.export.model.actionbuilders.ActionBuilderModelHelper._
import uk.gov.hmrc.customs.inventorylinking.export.model.actionbuilders.{HasConversationId, ValidatedHeadersRequest, ValidatedPayloadRequest}
import uk.gov.hmrc.customs.inventorylinking.export.model.{ApiSubscriptionKey, CustomsMetricsRequest, Eori}
import uk.gov.hmrc.customs.inventorylinking.export.services.{BusinessService, CustomsAuthService, DateTimeService, XmlValidationService}
import uk.gov.hmrc.http.HeaderCarrier
import util.CustomsMetricsTestData.{EventEnd, EventStart}
import util.RequestHeaders._
import util.TestData.{allVersionsUnshuttered, _}
import util.{ApiSubscriptionFieldsTestData, AuthConnectorStubbing, UnitSpec}

import scala.concurrent.{ExecutionContext, Future}
import scala.xml.NodeSeq

class InventoryLinkingExportControllerSpec extends UnitSpec
  with Matchers with MockitoSugar with BeforeAndAfterEach {

  trait SetUp extends AuthConnectorStubbing {
    override val mockAuthConnector: AuthConnector = mock[AuthConnector]

    protected val mockExportsLogger: ExportsLogger = mock[ExportsLogger]
    protected val headerValidator = new HeaderValidator(mockExportsLogger)
    protected val mockBusinessService: BusinessService = mock[BusinessService]
    protected val mockErrorResponse: ErrorResponse = mock[ErrorResponse]
    protected val mockResult: Result = mock[Result]
    protected val mockXmlValidationService: XmlValidationService = mock[XmlValidationService]
    protected val mockApiSubscriptionFieldsConnector: ApiSubscriptionFieldsConnector = mock[ApiSubscriptionFieldsConnector]
    protected val mockMetricsConnector: CustomsMetricsConnector = mock[CustomsMetricsConnector]
    private implicit val ec: ExecutionContext = Helpers.stubControllerComponents().executionContext
    protected val customsAuthService = new CustomsAuthService(mockAuthConnector, mockExportsLogger)
    protected val mockDateTimeService: DateTimeService = mock[DateTimeService]
    protected val mockExportsConfigService: ExportsConfigService = mock[ExportsConfigService]

    protected val stubConversationIdAction: ConversationIdAction = new ConversationIdAction(stubUniqueIdsService, mockDateTimeService, mockExportsLogger)
    protected val stubShutterCheckAction = new ShutterCheckAction(mockExportsLogger, mockExportsConfigService)
    protected val stubFieldsAction: ApiSubscriptionFieldsAction = new ApiSubscriptionFieldsAction(mockApiSubscriptionFieldsConnector, mockExportsLogger)
    protected val stubAuthAction: AuthAction = new AuthAction(customsAuthService, headerValidator, mockExportsLogger)
    protected val stubValidateAndExtractHeadersAction: ValidateAndExtractHeadersAction = new ValidateAndExtractHeadersAction(new HeaderValidator(mockExportsLogger))
    protected val stubPayloadValidationAction: PayloadValidationAction = new PayloadValidationAction(mockXmlValidationService, mockExportsLogger)

    protected val controller: InventoryLinkingExportController = new InventoryLinkingExportController(Helpers.stubControllerComponents(),
      stubConversationIdAction, stubShutterCheckAction, stubValidateAndExtractHeadersAction, stubFieldsAction, stubAuthAction, stubPayloadValidationAction,
      mockBusinessService, mockMetricsConnector, mockExportsLogger)

    protected def awaitSubmit(request: Request[AnyContent]): Result = {
      await(controller.post().apply(request))
    }

    protected def submit(request: Request[AnyContent]): Future[Result] = {
      controller.post().apply(request)
    }

    protected def verifyMetrics: Assertion = {
      val captor: ArgumentCaptor[CustomsMetricsRequest] = ArgumentCaptor.forClass(classOf[CustomsMetricsRequest])
      verify(mockMetricsConnector).post(captor.capture())(any[HasConversationId])
      captor.getValue.eventType shouldBe "ILE"
      captor.getValue.conversationId shouldBe conversationId
      captor.getValue.eventStart shouldBe EventStart
      captor.getValue.eventEnd shouldBe EventEnd
    }

    when(mockXmlValidationService.validate(any[NodeSeq])(any[ExecutionContext])).thenReturn(Future.successful(()))
    when(mockBusinessService.send(any[ValidatedPayloadRequest[_]], any[HeaderCarrier])).thenReturn(Future.successful(Right(())))
    when(mockApiSubscriptionFieldsConnector.getSubscriptionFields(any[ApiSubscriptionKey])(any[ValidatedHeadersRequest[_]])).thenReturn(Future.successful(ApiSubscriptionFieldsTestData.apiSubscriptionFields))
    when(mockExportsConfigService.exportsShutterConfig).thenReturn(allVersionsUnshuttered)
  }

  private val errorResultEoriNotFoundInCustomsEnrolment = ErrorResponse(UNAUTHORIZED, errorCode = "UNAUTHORIZED",
    message = "EORI number not found in Customs Enrolment").XmlResult.withHeaders(X_CONVERSATION_ID_HEADER)

  private val errorResultUnauthorised = ErrorResponse(UNAUTHORIZED, errorCode = "UNAUTHORIZED",
    message = "Unauthorised request").XmlResult.withHeaders(X_CONVERSATION_ID_HEADER)

  private val errorResultBadgeIdentifier = errorBadRequest("X-Badge-Identifier header is missing or invalid").XmlResult.withHeaders(X_CONVERSATION_ID_HEADER)

  private val internalServerError = ErrorInternalServerError.XmlResult.withConversationId(TestConversationIdRequest)

  private val errorResultSubmitterIdentifierInvalid = errorBadRequest("X-Submitter-Identifier header is invalid").XmlResult.withHeaders(X_CONVERSATION_ID_HEADER)

  private lazy val missingEoriResult = errorInternalServerError("Missing authenticated eori in service lookup. Alternately, use X-Badge-Identifier or X-Submitter-Identifier headers.").XmlResult.withHeaders(X_CONVERSATION_ID_HEADER)

  "InventoryLinkingExportController" should {
    "process CSP request when call is authorised for CSP (valid badge and submitter id headers present)" in new SetUp() {
      authoriseCsp()

      val result: Future[Result] = submit(ValidRequestWithSubmitterHeader)

      status(result) shouldBe ACCEPTED
      header(X_CONVERSATION_ID_NAME, result) shouldBe Some(conversationIdValue)
      verifyCspAuthorisationCalled(numberOfTimes = 1)
      verifyNonCspAuthorisationCalled(numberOfTimes = 0)
    }

    "process CSP request when call is authorised for CSP (valid badge present but submitter missing and authenticated EORI present)" in new SetUp() {
      authoriseCsp()

      val result: Future[Result] = submit(ValidRequestWithoutSubmitterHeader)

      status(result) shouldBe ACCEPTED
      header(X_CONVERSATION_ID_NAME, result) shouldBe Some(conversationIdValue)
      verifyCspAuthorisationCalled(numberOfTimes = 1)
      verifyNonCspAuthorisationCalled(numberOfTimes = 0)
    }

    "process a non-CSP request when call is unauthorised for CSP but authorised for non-CSP" in new SetUp() {
      authoriseNonCsp(Some(declarantEori))

      val result: Future[Result] = submit(ValidRequestWithoutSubmitterHeader)

      status(result) shouldBe ACCEPTED
      header(X_CONVERSATION_ID_NAME, result) shouldBe Some(conversationIdValue)
      verifyCspAuthorisationCalled(numberOfTimes = 1)
      verifyNonCspAuthorisationCalled(numberOfTimes = 1)
    }

    "process CSP request when call is authorised for CSP (valid submitter header present but missing X-Badge-Identifier and authenticated EORI present)" in new SetUp() {
      authoriseCsp()

      val result: Result = awaitSubmit(ValidRequestWithSubmitterHeader.withHeaders(ValidRequestWithSubmitterHeader.headers.remove(X_BADGE_IDENTIFIER_NAME)))

      status(result) shouldBe ACCEPTED
      header(X_CONVERSATION_ID_NAME, result) shouldBe Some(conversationIdValue)
      verifyCspAuthorisationCalled(numberOfTimes = 1)
      verifyNonCspAuthorisationCalled(numberOfTimes = 0)
    }

    "log metrics" in new SetUp() {
      authoriseCsp()
      when(mockDateTimeService.zonedDateTimeUtc).thenReturn(EventStart, EventEnd)

      val result: Future[Result] = awaitSubmit(ValidRequestWithSubmitterHeader)

      status(result) shouldBe ACCEPTED
      verifyMetrics
    }

    "respond with status 500 for a CSP request with no X-Submitter-Identifier header, no X-Badge-Identifier header and no authenticated EORI" in new SetUp() {
      when(mockApiSubscriptionFieldsConnector.getSubscriptionFields(any[ApiSubscriptionKey])(any[ValidatedHeadersRequest[_]])).thenReturn(Future.successful(ApiSubscriptionFieldsTestData.apiSubscriptionFieldsNoAuthenticatedEori))
      authoriseCsp()

      val result: Result = awaitSubmit(ValidRequestWithSubmitterHeader.withHeaders(ValidRequestWithSubmitterHeader.headers.remove(X_SUBMITTER_IDENTIFIER_NAME).remove(X_BADGE_IDENTIFIER_NAME)))

      result shouldBe missingEoriResult
      verifyNoMoreInteractions(mockBusinessService)
      verifyNoMoreInteractions(mockXmlValidationService)
    }

    "respond with status 500 for a request with a missing X-Client-ID" in new SetUp() {
      authoriseCsp()

      val result: Result = awaitSubmit(ValidRequestWithSubmitterHeader.withHeaders(ValidRequestWithSubmitterHeader.headers.remove(X_CLIENT_ID_NAME)))

      result shouldBe internalServerError
      verifyNoMoreInteractions(mockBusinessService)
      verifyNoMoreInteractions(mockXmlValidationService)
    }

    "respond with status 400 for a request with an invalid X-Badge-Identifier" in new SetUp() {
      authoriseCsp()

      val result: Result = awaitSubmit(ValidRequestWithSubmitterHeader.withHeaders((ValidHeaders + X_BADGE_IDENTIFIER_HEADER_INVALID).toSeq: _*))

      result shouldBe errorResultBadgeIdentifier
      bodyAsString(result) shouldBe bodyAsString(errorResultBadgeIdentifier)
      verifyNoMoreInteractions(mockBusinessService)
      verifyNoMoreInteractions(mockXmlValidationService)
    }

    "respond with status 400 for a request with an invalid X-Submitter-Identifier" in new SetUp() {
      authoriseCsp()

      val result: Result = awaitSubmit(ValidRequestWithSubmitterHeader.withHeaders((ValidHeaders + X_SUBMITTER_IDENTIFIER_HEADER_INVALID).toSeq: _*))

      result shouldBe errorResultSubmitterIdentifierInvalid
      bodyAsString(result) shouldBe bodyAsString(errorResultSubmitterIdentifierInvalid)
      verifyNoMoreInteractions(mockBusinessService)
      verifyNoMoreInteractions(mockXmlValidationService)
    }

    "respond with status 400 for a request with an invalid X-Submitter-Identifier (camel case)" in new SetUp() {
      authoriseCsp()

      val result: Result = awaitSubmit(ValidRequestWithSubmitterHeader.withHeaders((ValidHeaders + X_SUBMITTER_IDENTIFIER_HEADER_INVALID).toSeq: _*))

      result shouldBe errorResultSubmitterIdentifierInvalid
      bodyAsString(result) shouldBe bodyAsString(errorResultSubmitterIdentifierInvalid)
      verifyNoMoreInteractions(mockBusinessService)
      verifyNoMoreInteractions(mockXmlValidationService)
    }

    "respond with status 202 and conversationId in header for a processed valid non-CSP request (without submitter id)" in new SetUp() {
      authoriseNonCsp(Some(declarantEori))

      val result: Future[Result] = submit(ValidRequestWithoutSubmitterHeader)

      status(result) shouldBe ACCEPTED
      header(X_CONVERSATION_ID_NAME, result) shouldBe Some(conversationIdValue)
    }

    "respond with status 202 and conversationId in header for a processed valid non-CSP request with submitter id that matches our records and header name is camel case" in new SetUp() {
      authoriseNonCsp(Some(declarantEori))

      val result: Future[Result] = submit(ValidRequestWithSubmitterHeaderCamelCase)

      status(result) shouldBe ACCEPTED
      header(X_CONVERSATION_ID_NAME, result) shouldBe Some(conversationIdValue)
    }


    "respond with status 202 and conversationId in header for a processed valid non-CSP request and ignoring the submitter id in the header that doesn't match our records" in new SetUp() {
      authoriseNonCsp(Some(Eori("whatever")))

      val result: Result = awaitSubmit(ValidRequestWithSubmitterHeader)

      status(result) shouldBe ACCEPTED
      header(X_CONVERSATION_ID_NAME, result) shouldBe Some(conversationIdValue)
    }

    "return result 401 UNAUTHORISED and conversationId in header when call is unauthorised for both CSP and non-CSP submissions" in new SetUp() {
      unauthoriseCsp()
      unauthoriseNonCspOnly()

      val result: Future[Result] = submit(ValidRequestWithSubmitterHeader)

      await(result) shouldBe errorResultUnauthorised
      bodyAsString(result) shouldBe bodyAsString(errorResultUnauthorised)
      header(X_CONVERSATION_ID_NAME, result) shouldBe Some(conversationIdValue)
      verifyNoMoreInteractions(mockBusinessService)
      verifyNoMoreInteractions(mockXmlValidationService)
    }

    "return result 401 UNAUTHORISED and conversationId in header when there's no Customs enrolment retrieved for an enrolled non-CSP call" in new SetUp() {
      unauthoriseCsp()
      authoriseNonCspButDontRetrieveCustomsEnrolment()

      val result: Future[Result] = submit(ValidRequestWithSubmitterHeader.fromNonCsp)

      await(result) shouldBe errorResultEoriNotFoundInCustomsEnrolment
      bodyAsString(result) shouldBe bodyAsString(errorResultEoriNotFoundInCustomsEnrolment)
      header(X_CONVERSATION_ID_NAME, result) shouldBe Some(conversationIdValue)
      verifyNoMoreInteractions(mockBusinessService)
      verifyNoMoreInteractions(mockXmlValidationService)
    }

    "return result 401 UNAUTHORISED and conversationId in header when there's no EORI number in Customs enrolment for a non-CSP call" in new SetUp() {
      unauthoriseCsp()
      authoriseNonCsp(maybeEori = None)

      val result: Future[Result] = submit(ValidRequestWithSubmitterHeader)

      await(result) shouldBe errorResultEoriNotFoundInCustomsEnrolment
      bodyAsString(result) shouldBe bodyAsString(errorResultEoriNotFoundInCustomsEnrolment)
      header(X_CONVERSATION_ID_NAME, result) shouldBe Some(conversationIdValue)
      verifyNoMoreInteractions(mockBusinessService)
      verifyNoMoreInteractions(mockXmlValidationService)
    }

    "return the error response returned from the Communication service" in new SetUp() {
      when(mockBusinessService.send(any[ValidatedPayloadRequest[_]], any[HeaderCarrier]))
        .thenReturn(Future.successful(Left(mockResult)))
      authoriseCsp()

      val result: Result = awaitSubmit(ValidRequestWithSubmitterHeader)

      result shouldBe mockResult
    }

  }

  private def bodyAsString(r: Result) = {
    bodyOf(r)(mock[Materializer])
  }

}
