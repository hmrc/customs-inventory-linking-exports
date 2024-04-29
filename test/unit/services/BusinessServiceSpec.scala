/*
 * Copyright 2024 HM Revenue & Customs
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

import org.mockito.ArgumentMatchers.{eq => meq, _}
import org.mockito.Mockito.{verify, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import play.api.http.Status.FORBIDDEN
import play.api.mvc.{AnyContentAsXml, Result}
import play.api.test.Helpers
import uk.gov.hmrc.customs.inventorylinking.export.controllers.ErrorResponse
import uk.gov.hmrc.customs.inventorylinking.export.controllers.ErrorResponse.errorInternalServerError
import uk.gov.hmrc.customs.inventorylinking.export.connectors.ExportsConnector.{Non2xxResponseError, RetryError}
import uk.gov.hmrc.customs.inventorylinking.export.connectors.ExportsConnector
import uk.gov.hmrc.customs.inventorylinking.export.logging.ExportsLogger
import uk.gov.hmrc.customs.inventorylinking.export.model._
import uk.gov.hmrc.customs.inventorylinking.export.model.actionbuilders.ActionBuilderModelHelper._
import uk.gov.hmrc.customs.inventorylinking.export.model.actionbuilders.ValidatedPayloadRequest
import uk.gov.hmrc.customs.inventorylinking.export.services._
import uk.gov.hmrc.customs.inventorylinking.export.xml.PayloadDecorator
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import util.TestData._
import util.XMLTestData._
import util.{ApiSubscriptionFieldsTestData, RequestHeaders, UnitSpec}

import java.time.LocalDateTime
import java.util.UUID
import scala.concurrent.Future
import scala.xml.NodeSeq

class BusinessServiceSpec extends UnitSpec with MockitoSugar with BeforeAndAfterEach with ApiSubscriptionFieldsTestData {

  private val dateTime = LocalDateTime.now()
  private implicit val ec = Helpers.stubControllerComponents().executionContext
  private val headerCarrier: HeaderCarrier = HeaderCarrier()
    .withExtraHeaders(RequestHeaders.API_SUBSCRIPTION_FIELDS_ID_HEADER)
  private implicit val vpr: ValidatedPayloadRequest[AnyContentAsXml] = TestCspValidatedPayloadRequestWithEori
  private val errorResponseServiceUnavailable = errorInternalServerError("This service is currently unavailable")

  trait SetUp {
    protected val mockExportsConfig: ExportsConfig = mock[ExportsConfig]
    protected val mockLogger: ExportsLogger = mock[ExportsLogger]
    protected val mockExportsConnector: ExportsConnector = mock[ExportsConnector]
    protected val mockPayloadDecorator: PayloadDecorator = mock[PayloadDecorator]
    protected val mockDateTimeProvider: DateTimeService = mock[DateTimeService]
    protected val mockHttpResponse: HttpResponse = mock[HttpResponse]

    protected lazy val service: BusinessService = new BusinessService(mockLogger, mockExportsConnector,
      mockPayloadDecorator, mockDateTimeProvider, stubUniqueIdsService)

    protected def send(vpr: ValidatedPayloadRequest[AnyContentAsXml] = TestCspValidatedPayloadRequestWithEori, hc: HeaderCarrier = headerCarrier): Either[Result, Unit] = {
      await(service.send(vpr, hc))
    }
    // https://stackoverflow.com/questions/27289757/mockito-matchers-scala-value-class-and-nullpointerexception
    // Mockito matching was having problems so had to use the eq type then as instance of. Important that the 1st type is the
    // type of the value contained in the value class i.e. for CorrelationId the value is UUID so needs to meq type of UUID
    when(mockPayloadDecorator.decorate(meq(TestXmlPayload), meq[String](TestSubscriptionFieldsId.value).asInstanceOf[SubscriptionFieldsId], meq[UUID](correlationIdUuid).asInstanceOf[CorrelationId], any[LocalDateTime])(any[ValidatedPayloadRequest[_]])).thenReturn(wrappedValidXML)
    when(mockDateTimeProvider.getUtcNow).thenReturn(dateTime)
    when(mockExportsConnector.send(any[NodeSeq], meq(dateTime), any[UUID])(any[ValidatedPayloadRequest[_]], any[HeaderCarrier])).thenReturn(Right(mockHttpResponse))
  }

  "BusinessService" should {

    "send transformed xml to connector" in new SetUp() {
      send() shouldBe Right(())

      verify(mockExportsConnector).send(meq(wrappedValidXML), any[LocalDateTime], any[UUID])(any[ValidatedPayloadRequest[_]], any[HeaderCarrier])
    }

    "get utc date time and pass to connector" in new SetUp() {
      send() shouldBe Right(())

      verify(mockExportsConnector).send(any[NodeSeq], meq(dateTime), any[UUID])(any[ValidatedPayloadRequest[_]], any[HeaderCarrier])
    }

    "call payload decorator passing incoming xml" in new SetUp() {
      // https://stackoverflow.com/questions/27289757/mockito-matchers-scala-value-class-and-nullpointerexception
      // Mockito matching was having problems so had to use the eq type then as instance of. Important that the 1st type is the
      // type of the value contained in the value class i.e. for CorrelationId the value is UUID so needs to meq type of UUID
      send() shouldBe Right(())

      verify(mockPayloadDecorator)
        .decorate(meq(TestXmlPayload),
          meq[String](TestSubscriptionFieldsId.value).asInstanceOf[SubscriptionFieldsId],
          meq[UUID](correlationIdUuid).asInstanceOf[CorrelationId],
          any[LocalDateTime])(any[ValidatedPayloadRequest[_]])
    }

    "return InternalServerError ErrorResponse when backend circuit breaker trips" in new SetUp() {
      when(mockExportsConnector.send(any[NodeSeq], any[LocalDateTime], any[UUID])(any[ValidatedPayloadRequest[_]], any[HeaderCarrier]))
        .thenReturn(Future.successful(Left(RetryError)))

      send() shouldBe Left(errorResponseServiceUnavailable.XmlResult.withConversationId)
    }

    "return Forbidden ErrorResponse when backend returns 403" in new SetUp() {
      when(mockExportsConnector.send(any[NodeSeq], any[LocalDateTime], any[UUID])(any[ValidatedPayloadRequest[_]], any[HeaderCarrier]))
        .thenReturn(Future.successful(Left(Non2xxResponseError(FORBIDDEN))))

      send() shouldBe Left(ErrorResponse.ErrorPayloadForbidden.XmlResult.withConversationId)
    }
  }
}
