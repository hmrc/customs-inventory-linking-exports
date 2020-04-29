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

package unit.services

import java.util.UUID
import java.util.concurrent.TimeUnit

import akka.pattern.CircuitBreakerOpenException
import org.joda.time.DateTime
import org.mockito.ArgumentMatchers.{eq => meq, _}
import org.mockito.Mockito.{verify, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import play.api.mvc.{AnyContentAsXml, Result}
import play.api.test.Helpers
import uk.gov.hmrc.customs.api.common.controllers.ErrorResponse
import uk.gov.hmrc.customs.api.common.controllers.ErrorResponse.errorInternalServerError
import uk.gov.hmrc.customs.inventorylinking.export.connectors.ExportsConnector
import uk.gov.hmrc.customs.inventorylinking.export.logging.ExportsLogger
import uk.gov.hmrc.customs.inventorylinking.export.model._
import uk.gov.hmrc.customs.inventorylinking.export.model.actionbuilders.ActionBuilderModelHelper._
import uk.gov.hmrc.customs.inventorylinking.export.model.actionbuilders.ValidatedPayloadRequest
import uk.gov.hmrc.customs.inventorylinking.export.services.{BusinessService, _}
import uk.gov.hmrc.customs.inventorylinking.export.xml.PayloadDecorator
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import util.TestData._
import util.XMLTestData._
import util.{ApiSubscriptionFieldsTestData, RequestHeaders, UnitSpec}

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.xml.NodeSeq

class BusinessServiceSpec extends UnitSpec with MockitoSugar with BeforeAndAfterEach with ApiSubscriptionFieldsTestData {

  private val dateTime = new DateTime()
  private implicit val ec = Helpers.stubControllerComponents().executionContext
  private val headerCarrier: HeaderCarrier = HeaderCarrier()
    .withExtraHeaders(RequestHeaders.API_SUBSCRIPTION_FIELDS_ID_HEADER)
  private implicit val vpr: ValidatedPayloadRequest[AnyContentAsXml] = TestCspValidatedPayloadRequest
  private val errorResponseServiceUnavailable = errorInternalServerError("This service is currently unavailable")

  trait SetUp {
    protected val mockLogger: ExportsLogger = mock[ExportsLogger]
    protected val mockExportsConnector: ExportsConnector = mock[ExportsConnector]
    protected val mockPayloadDecorator: PayloadDecorator = mock[PayloadDecorator]
    protected val mockDateTimeProvider: DateTimeService = mock[DateTimeService]
    protected val mockCustomsConfigService: ExportsConfigService = mock[ExportsConfigService]
    protected val mockHttpResponse: HttpResponse = mock[HttpResponse]

    protected lazy val service: BusinessService = new BusinessService(mockLogger, mockExportsConnector,
      mockPayloadDecorator, mockDateTimeProvider, stubUniqueIdsService, mockCustomsConfigService)

    protected def send(vpr: ValidatedPayloadRequest[AnyContentAsXml] = TestCspValidatedPayloadRequest, hc: HeaderCarrier = headerCarrier): Either[Result, Unit] = {
      await(service.send(vpr, hc))
    }
    // https://stackoverflow.com/questions/27289757/mockito-matchers-scala-value-class-and-nullpointerexception
    // Mockito matching was having problems so had to use the eq type then as instance of. Important that the 1st type is the
    // type of the value contained in the value class i.e. for CorrelationId the value is UUID so needs to meq type of UUID
    when(mockPayloadDecorator.decorate(meq(TestXmlPayload), meq[String](TestSubscriptionFieldsId.value).asInstanceOf[SubscriptionFieldsId], meq[UUID](correlationIdUuid).asInstanceOf[CorrelationId], any[DateTime])(any[ValidatedPayloadRequest[_]])).thenReturn(wrappedValidXML)
    when(mockDateTimeProvider.getUtcNow).thenReturn(dateTime)
    when(mockExportsConnector.send(any[NodeSeq], meq(dateTime), any[UUID])(any[ValidatedPayloadRequest[_]], any[HeaderCarrier])).thenReturn(mockHttpResponse)
  }

  "BusinessService" should {

    "send transformed xml to connector" in new SetUp() {
      send() shouldBe Right(())

      verify(mockExportsConnector).send(meq(wrappedValidXML), any[DateTime], any[UUID])(any[ValidatedPayloadRequest[_]], any[HeaderCarrier])
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

      verify(mockPayloadDecorator).decorate(meq(TestXmlPayload), meq[String](TestSubscriptionFieldsId.value).asInstanceOf[SubscriptionFieldsId], meq[UUID](correlationIdUuid).asInstanceOf[CorrelationId], any[DateTime])(any[ValidatedPayloadRequest[_]])
    }

    "return InternalServerError ErrorResponse when backend call fails" in new SetUp() {
      when(mockExportsConnector.send(any[NodeSeq], any[DateTime], any[UUID])(any[ValidatedPayloadRequest[_]], any[HeaderCarrier])).thenReturn(Future.failed(emulatedServiceFailure))

      send() shouldBe Left(ErrorResponse.ErrorInternalServerError.XmlResult.withConversationId)
    }

    "return InternalServerError ErrorResponse when backend circuit breaker trips" in new SetUp() {
      when(mockExportsConnector.send(any[NodeSeq], any[DateTime], any[UUID])(any[ValidatedPayloadRequest[_]], any[HeaderCarrier])).thenReturn(Future.failed(new CircuitBreakerOpenException(FiniteDuration(10, TimeUnit.SECONDS))))

      send() shouldBe Left(errorResponseServiceUnavailable.XmlResult)
    }

  }

}
