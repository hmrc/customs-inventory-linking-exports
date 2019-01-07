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

package unit.controllers

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfterEach, Matchers}
import play.api.mvc._
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.customs.api.common.controllers.ErrorResponse
import uk.gov.hmrc.customs.api.common.controllers.ErrorResponse.errorBadRequest
import uk.gov.hmrc.customs.inventorylinking.export.controllers.actionbuilders._
import uk.gov.hmrc.customs.inventorylinking.export.controllers.{HeaderValidator, InventoryLinkingExportController}
import uk.gov.hmrc.customs.inventorylinking.export.logging.ExportsLogger
import uk.gov.hmrc.customs.inventorylinking.export.model.Eori
import uk.gov.hmrc.customs.inventorylinking.export.model.actionbuilders.ValidatedPayloadRequest
import uk.gov.hmrc.customs.inventorylinking.export.services.{BusinessService, XmlValidationService}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec
import util.AuthConnectorStubbing
import util.RequestHeaders._
import util.TestData._

import scala.concurrent.{ExecutionContext, Future}
import scala.xml.NodeSeq

class InventoryLinkingExportControllerSpec extends UnitSpec
  with Matchers with MockitoSugar with BeforeAndAfterEach {

  trait SetUp extends AuthConnectorStubbing {
    override val mockAuthConnector: AuthConnector = mock[AuthConnector]

    protected val mockExportsLogger: ExportsLogger = mock[ExportsLogger]
    protected val mockBusinessService: BusinessService = mock[BusinessService]
    protected val mockErrorResponse: ErrorResponse = mock[ErrorResponse]
    protected val mockResult: Result = mock[Result]
    protected val mockXmlValidationService: XmlValidationService = mock[XmlValidationService]

    protected val stubConversationIdAction: ConversationIdAction = new ConversationIdAction(stubUniqueIdsService, mockExportsLogger)
    protected val stubAuthAction: AuthAction = new AuthAction(mockAuthConnector, mockExportsLogger)
    protected val stubValidateAndExtractHeadersAction: ValidateAndExtractHeadersAction = new ValidateAndExtractHeadersAction(new HeaderValidator(mockExportsLogger), mockExportsLogger)
    protected val stubPayloadValidationAction: PayloadValidationAction = new PayloadValidationAction(mockXmlValidationService, mockExportsLogger)

    protected val controller: InventoryLinkingExportController = new InventoryLinkingExportController(
      stubConversationIdAction, stubAuthAction, stubValidateAndExtractHeadersAction, stubPayloadValidationAction,
      mockBusinessService, mockExportsLogger)

    protected def awaitSubmit(request: Request[AnyContent]): Result = {
      await(controller.post().apply(request))
    }

    protected def submit(request: Request[AnyContent]): Future[Result] = {
      controller.post().apply(request)
    }

    when(mockXmlValidationService.validate(any[NodeSeq])(any[ExecutionContext])).thenReturn(Future.successful(()))
    when(mockBusinessService.send(any[ValidatedPayloadRequest[_]], any[HeaderCarrier])).thenReturn(Future.successful(Right(())))
  }

  private val errorResultEoriNotFoundInCustomsEnrolment = ErrorResponse(UNAUTHORIZED, errorCode = "UNAUTHORIZED",
    message = "EORI number not found in Customs Enrolment").XmlResult.withHeaders(X_CONVERSATION_ID_HEADER)

  private val errorResultUnauthorised = ErrorResponse(UNAUTHORIZED, errorCode = "UNAUTHORIZED",
    message = "Unauthorised request").XmlResult.withHeaders(X_CONVERSATION_ID_HEADER)

  private val errorResultBadgeIdentifier = errorBadRequest("X-Badge-Identifier header is missing or invalid").XmlResult.withHeaders(X_CONVERSATION_ID_HEADER)

  private val errorResultSubmitterIdentifier = errorBadRequest("X-Submitter-Identifier header is missing or invalid").XmlResult.withHeaders(X_CONVERSATION_ID_HEADER)

  "InventoryLinkingExportController" should {
    "process CSP request when call is authorised for CSP" in new SetUp() {
      authoriseCsp()

      val result: Future[Result] = submit(ValidRequestWithSubmitterHeader)

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

    "respond with status 400 for a CSP request with a missing X-Badge-Identifier" in new SetUp() {
      authoriseCsp()

      val result: Result = awaitSubmit(ValidRequestWithSubmitterHeader.copyFakeRequest(headers = ValidRequestWithSubmitterHeader.headers.remove(X_BADGE_IDENTIFIER_NAME)))
      result shouldBe errorResultBadgeIdentifier
      verifyZeroInteractions(mockBusinessService)
      verifyZeroInteractions(mockXmlValidationService)
    }

    "respond with status 400 for a CSP request with a missing X-Submitter-Identifier" in new SetUp() {
      authoriseCsp()

      val result: Result = awaitSubmit(ValidRequestWithSubmitterHeader.copyFakeRequest(headers = ValidRequestWithSubmitterHeader.headers.remove(X_SUBMITTER_IDENTIFIER_NAME)))
      result shouldBe errorResultSubmitterIdentifier
      verifyZeroInteractions(mockBusinessService)
      verifyZeroInteractions(mockXmlValidationService)
    }

    "respond with status 500 for a request with a missing X-Client-ID" in new SetUp() {
      authoriseCsp()

      val result: Result = awaitSubmit(ValidRequestWithSubmitterHeader.copyFakeRequest(headers = ValidRequestWithSubmitterHeader.headers.remove(X_CLIENT_ID_NAME)))
      status(result) shouldBe INTERNAL_SERVER_ERROR
      verifyZeroInteractions(mockBusinessService)
      verifyZeroInteractions(mockXmlValidationService)
    }

    "respond with status 400 for a request with an invalid X-Badge-Identifier" in new SetUp() {
      authoriseCsp()

      val result: Result = awaitSubmit(ValidRequestWithSubmitterHeader.withHeaders((ValidHeaders + X_BADGE_IDENTIFIER_HEADER_INVALID).toSeq: _*))

      result shouldBe errorResultBadgeIdentifier
      verifyZeroInteractions(mockBusinessService)
      verifyZeroInteractions(mockXmlValidationService)
    }

    "respond with status 400 for a request with an invalid X-Submitter-Identifier" in new SetUp() {
      authoriseCsp()

      val result: Result = awaitSubmit(ValidRequestWithSubmitterHeader.withHeaders((ValidHeaders + X_SUBMITTER_IDENTIFIER_HEADER_INVALID).toSeq: _*))

      result shouldBe errorResultSubmitterIdentifier
      verifyZeroInteractions(mockBusinessService)
      verifyZeroInteractions(mockXmlValidationService)
    }

    "respond with status 400 for a request with an invalid X-Submitter-Identifier (camel case)" in new SetUp() {
      authoriseCsp()

      val result: Result = awaitSubmit(ValidRequestWithSubmitterHeader.withHeaders((ValidHeaders + X_SUBMITTER_IDENTIFIER_HEADER_INVALID).toSeq: _*))

      result shouldBe errorResultSubmitterIdentifier
      verifyZeroInteractions(mockBusinessService)
      verifyZeroInteractions(mockXmlValidationService)
    }

    "respond with status 202 and conversationId in header for a processed valid non-CSP request (without submitter id)" in new SetUp() {
      authoriseNonCsp(Some(declarantEori))

      val result: Future[Result] = submit(ValidRequestWithoutSubmitterHeader)

      status(result) shouldBe ACCEPTED
      header(X_CONVERSATION_ID_NAME, result) shouldBe Some(conversationIdValue)
    }

    "respond with status 202 and conversationId in header for a processed valid non-CSP request with submitter id that matches our records" in new SetUp() {
      authoriseNonCsp(Some(declarantEori))

      val result: Future[Result] = submit(ValidRequestWithSubmitterHeader)

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
      header(X_CONVERSATION_ID_NAME, result) shouldBe Some(conversationIdValue)
      verifyZeroInteractions(mockBusinessService)
      verifyZeroInteractions(mockXmlValidationService)
    }

    "return result 401 UNAUTHORISED and conversationId in header when there's no Customs enrolment retrieved for an enrolled non-CSP call" in new SetUp() {
      unauthoriseCsp()
      authoriseNonCspButDontRetrieveCustomsEnrolment()

      val result: Future[Result] = submit(ValidRequestWithSubmitterHeader.fromNonCsp)

      await(result) shouldBe errorResultEoriNotFoundInCustomsEnrolment
      header(X_CONVERSATION_ID_NAME, result) shouldBe Some(conversationIdValue)
      verifyZeroInteractions(mockBusinessService)
      verifyZeroInteractions(mockXmlValidationService)
    }

    "return result 401 UNAUTHORISED and conversationId in header when there's no EORI number in Customs enrolment for a non-CSP call" in new SetUp() {
      unauthoriseCsp()
      authoriseNonCsp(maybeEori = None)

      val result: Future[Result] = submit(ValidRequestWithSubmitterHeader)

      await(result) shouldBe errorResultEoriNotFoundInCustomsEnrolment
      header(X_CONVERSATION_ID_NAME, result) shouldBe Some(conversationIdValue)
      verifyZeroInteractions(mockBusinessService)
      verifyZeroInteractions(mockXmlValidationService)
    }

    "return the error response returned from the Communication service" in new SetUp() {
      when(mockBusinessService.send(any[ValidatedPayloadRequest[_]], any[HeaderCarrier]))
        .thenReturn(Future.successful(Left(mockResult)))
      authoriseCsp()

      val result: Result = awaitSubmit(ValidRequestWithSubmitterHeader)

      result shouldBe mockResult
    }

  }

}
