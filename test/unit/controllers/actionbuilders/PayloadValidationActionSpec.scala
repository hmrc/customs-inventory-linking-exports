/*
 * Copyright 2020 HM Revenue & Customs
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

import org.mockito.Mockito._
import org.scalatestplus.mockito.MockitoSugar
import play.api.mvc.{AnyContentAsText, AnyContentAsXml, Result}
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.customs.api.common.controllers.{ErrorResponse, ResponseContents}
import uk.gov.hmrc.customs.inventorylinking.export.controllers.actionbuilders.PayloadValidationAction
import uk.gov.hmrc.customs.inventorylinking.export.logging.ExportsLogger
import uk.gov.hmrc.customs.inventorylinking.export.model.actionbuilders.ActionBuilderModelHelper._
import uk.gov.hmrc.customs.inventorylinking.export.model.actionbuilders.{AuthorisedRequest, ConversationIdRequest, ValidatedPayloadRequest}
import uk.gov.hmrc.customs.inventorylinking.export.services.XmlValidationService
import util.UnitSpec
import util.ApiSubscriptionFieldsTestData
import util.TestData._

import scala.concurrent.Future
import scala.xml.SAXException

class PayloadValidationActionSpec extends UnitSpec with MockitoSugar {

  trait SetUp {
    implicit val forConversions: ConversationIdRequest[AnyContentAsXml] = TestConversationIdRequest
    implicit val ec = Helpers.stubControllerComponents().executionContext
    val saxException = new SAXException("Boom!")

    val expectedXmlSchemaErrorResult: Result = ErrorResponse
      .errorBadRequest("Payload is not valid according to schema")
      .withErrors(ResponseContents("xml_validation_error", saxException.getMessage)).XmlResult.withConversationId

    val errorNotWellFormedResult: Result = ErrorResponse
      .errorBadRequest("Request body does not contain a well-formed XML document.")
      .XmlResult.withConversationId

    val mockXmlValidationService: XmlValidationService = mock[XmlValidationService]
    val mockExportsLogger: ExportsLogger = mock[ExportsLogger]
    val payloadValidationAction = new PayloadValidationAction(mockXmlValidationService, mockExportsLogger)
  }

  "PayloadValidationAction" should {
    "return a ValidatedPayloadRequest when XML validation is OK" in new SetUp {
      when(mockXmlValidationService.validate(TestCspAuthorisedRequest.body.asXml.get)).thenReturn(Future.successful(()))

      private val actual: Either[Result, ValidatedPayloadRequest[AnyContentAsXml]] = await(payloadValidationAction.refine(TestCspAuthorisedRequest))

      actual shouldBe Right(TestCspValidatedPayloadRequest)
    }

    "return 400 error response when XML is not well formed" in new SetUp {
      when(mockXmlValidationService.validate(TestCspAuthorisedRequest.body.asXml.get)).thenReturn(Future.failed(saxException))

      private val actual: Either[Result, ValidatedPayloadRequest[AnyContentAsXml]] = await(payloadValidationAction.refine(TestCspAuthorisedRequest))

      actual shouldBe Left(expectedXmlSchemaErrorResult)
    }

    "return 400 error response when XML validation fails" in new SetUp {
      val authorisedRequestWithNonWellFormedXml: AuthorisedRequest[AnyContentAsText] = ConversationIdRequest(conversationId, FakeRequest().withTextBody("<foo><foo>"))
        .toValidatedHeadersRequest(TestExtractedHeaders)
        .toApiSubscriptionFieldsRequest(ApiSubscriptionFieldsTestData.apiSubscriptionFields)
        .toCspAuthorisedRequest(cspAuthorisedRequest)

      private val actual = await(payloadValidationAction.refine(authorisedRequestWithNonWellFormedXml))

      actual shouldBe Left(errorNotWellFormedResult)
    }

    "propagates downstream errors by returning a 500 error response" in new SetUp {
      when(mockXmlValidationService.validate(TestCspAuthorisedRequest.body.asXml.get)).thenReturn(Future.failed(emulatedServiceFailure))

      private val actual: Either[Result, ValidatedPayloadRequest[AnyContentAsXml]] = await(payloadValidationAction.refine(TestCspAuthorisedRequest))

      actual shouldBe Left(ErrorResponse.ErrorInternalServerError.XmlResult.withConversationId)
    }
  }

}
