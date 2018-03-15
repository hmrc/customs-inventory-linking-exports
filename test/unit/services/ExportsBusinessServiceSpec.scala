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

package unit.services

import org.mockito.ArgumentMatchers.{eq => ameq, _}
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.{BeforeAndAfterEach, Matchers}
import org.xml.sax.SAXException
import play.api.test.Helpers._
import uk.gov.hmrc.customs.api.common.controllers.{ErrorResponse, ResponseContents}
import uk.gov.hmrc.customs.inventorylinking.export.logging.ExportsLogger
import uk.gov.hmrc.customs.inventorylinking.export.model.{BadgeIdentifier, ConversationId}
import uk.gov.hmrc.customs.inventorylinking.export.services.{CommunicationService, ExportsBusinessService, XmlValidationService}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec
import util.TestData._
import util.XMLTestData._

import scala.concurrent.{ExecutionContext, Future}
import scala.xml.NodeSeq

class ExportsBusinessServiceSpec extends UnitSpec with Matchers with MockitoSugar with BeforeAndAfterEach with TableDrivenPropertyChecks {

  private val mockExportsLogger = mock[ExportsLogger]
  private val mockCommunicationService = mock[CommunicationService]
  private val mockXmlValidationService = mock[XmlValidationService]

  private val service = new ExportsBusinessService(mockExportsLogger,
    mockCommunicationService,
    mockXmlValidationService)

  private implicit val mockHeaderCarrier: HeaderCarrier = mock[HeaderCarrier]

  private val xmlValidationErrorText = "cvc-complex-type.3.2.2: Attribute 'foo' is not allowed to appear in element 'Declaration'."
  private val xmlValidationException = new SAXException(xmlValidationErrorText)

  private val xmlValidationErrorResponse = ErrorResponse(BAD_REQUEST, errorCode = "BAD_REQUEST",
    message = "Payload is not valid according to schema",
    ResponseContents(code = "xml_validation_error", message = xmlValidationErrorText))

  override protected def beforeEach() {
    reset(mockExportsLogger, mockCommunicationService, mockXmlValidationService)
    when(mockXmlValidationService.validate(any[NodeSeq])(any[ExecutionContext])).thenReturn(())
    when(mockCommunicationService.prepareAndSend(any[NodeSeq](), any[Option[BadgeIdentifier]])(any[HeaderCarrier]())).thenReturn(conversationId)
  }

  val allSubmissionModes = Table(("description", "xml submission thunk with service"),
    ("CSP", service.authorisedCspSubmission(_: NodeSeq, Some(badgeIdentifier))),
    ("non-CSP", service.authorisedNonCspSubmission(_: NodeSeq))
  )

  forAll(allSubmissionModes) { case (submissionMode, xmlSubmission) =>

    s"CustomsDeclarationBusinessService when $submissionMode ia submitting" should {

      "validate incoming xml" in {
        testSubmitResult(xmlSubmission(ValidInventoryLinkingMovementRequestXML)) { result =>
          await(result)
          verify(mockXmlValidationService).validate(ameq(ValidInventoryLinkingMovementRequestXML))(any[ExecutionContext])
        }
      }

      "send valid xml to communication service" in {
        testSubmitResult(xmlSubmission(ValidInventoryLinkingMovementRequestXML)) { result =>
          await(result)
          verify(mockCommunicationService).prepareAndSend(ameq(ValidInventoryLinkingMovementRequestXML), any[Option[BadgeIdentifier]])(ameq(mockHeaderCarrier))
        }
      }

      "return conversationId for a processed valid request" in {
        testSubmitResult(xmlSubmission(ValidInventoryLinkingMovementRequestXML)) { result =>
          await(result) shouldBe Right(conversationId)
        }
      }

      "prevent from sending an invalid xml returning xml errors" in {
        when(mockXmlValidationService.validate(any[NodeSeq])(any[ExecutionContext]))
          .thenReturn(Future.failed(xmlValidationException))

        testSubmitResult(xmlSubmission(ValidInventoryLinkingMovementRequestXML)) { result =>
          await(result) shouldBe Left(xmlValidationErrorResponse)
          verifyZeroInteractions(mockCommunicationService)
        }
      }

      "propagate the error when xml validation fails with a system error" in {
        when(mockXmlValidationService.validate(any[NodeSeq])(any[ExecutionContext]))
          .thenReturn(Future.failed(emulatedServiceFailure))

        testSubmitResult(xmlSubmission(InvalidXML)) { result =>
          intercept[EmulatedServiceFailure](await(result)) shouldBe emulatedServiceFailure
          verifyZeroInteractions(mockCommunicationService)
        }
      }

      "propagate the error for a valid request when downstream communication fails" in {
        when(mockCommunicationService.prepareAndSend(any[NodeSeq](), any[Option[BadgeIdentifier]])(any[HeaderCarrier]())).thenReturn(Future.failed(emulatedServiceFailure))

        testSubmitResult(xmlSubmission(ValidInventoryLinkingMovementRequestXML)) { result =>
          intercept[EmulatedServiceFailure](await(result)) shouldBe emulatedServiceFailure
        }
      }

    }
  }


  private def testSubmitResult(xmlSubmission: Future[Either[ErrorResponse, ConversationId]])
                              (test: Future[Either[ErrorResponse, ConversationId]] => Unit) {
    test.apply(xmlSubmission)
  }

}
