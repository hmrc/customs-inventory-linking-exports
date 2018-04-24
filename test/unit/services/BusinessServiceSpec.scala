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

import java.util.UUID

import org.joda.time.DateTime
import org.mockito.ArgumentMatchers.{eq => meq, _}
import org.mockito.Mockito.{verify, verifyZeroInteractions, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mockito.MockitoSugar
import play.api.mvc.{AnyContentAsXml, Result}
import uk.gov.hmrc.customs.api.common.controllers.ErrorResponse
import uk.gov.hmrc.customs.inventorylinking.export.connectors.{ApiSubscriptionFieldsConnector, MdgExportsConnector}
import uk.gov.hmrc.customs.inventorylinking.export.logging.ExportsLogger
import uk.gov.hmrc.customs.inventorylinking.export.model._
import uk.gov.hmrc.customs.inventorylinking.export.model.actionbuilders.ActionBuilderModelHelper._
import uk.gov.hmrc.customs.inventorylinking.export.model.actionbuilders.ValidatedPayloadRequest
import uk.gov.hmrc.customs.inventorylinking.export.services.{BusinessService, _}
import uk.gov.hmrc.customs.inventorylinking.export.xml.MdgPayloadDecorator
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.test.UnitSpec
import util.TestData._
import util.XMLTestData._
import util.{ApiSubscriptionFieldsTestData, RequestHeaders}

import scala.concurrent.Future
import scala.xml.NodeSeq

class BusinessServiceSpec extends UnitSpec with MockitoSugar with BeforeAndAfterEach with ApiSubscriptionFieldsTestData {

  private val dateTime = new DateTime()
  private val headerCarrier: HeaderCarrier = HeaderCarrier()
    .withExtraHeaders(RequestHeaders.API_SUBSCRIPTION_FIELDS_ID_HEADER)
  private val expectedApiSubscriptionKey = ApiSubscriptionKey(xClientIdValue, "customs%2Finventory-linking%2Fexports", "1.0")
  private implicit val vpr: ValidatedPayloadRequest[AnyContentAsXml] = TestCspValidatedPayloadRequest


  trait SetUp {
    protected val mockLogger: ExportsLogger = mock[ExportsLogger]
    protected val mockMdgExportsConnector: MdgExportsConnector = mock[MdgExportsConnector]
    protected val mockApiSubscriptionFieldsConnector: ApiSubscriptionFieldsConnector = mock[ApiSubscriptionFieldsConnector]
    protected val mockPayloadDecorator: MdgPayloadDecorator = mock[MdgPayloadDecorator]
    protected val mockDateTimeProvider: DateTimeService = mock[DateTimeService]
    protected val mockCustomsConfigService: ExportsConfigService = mock[ExportsConfigService]
    protected val mockApiDefinitionConfig: ApiDefinitionConfig = mock[ApiDefinitionConfig]
    protected val mockHttpResponse: HttpResponse = mock[HttpResponse]

    protected lazy val service: BusinessService = new BusinessService(mockLogger, mockMdgExportsConnector, mockApiSubscriptionFieldsConnector,
      mockPayloadDecorator, mockDateTimeProvider, stubUniqueIdsService, mockCustomsConfigService)

    protected def send(vpr: ValidatedPayloadRequest[AnyContentAsXml] = TestCspValidatedPayloadRequest, hc: HeaderCarrier = headerCarrier): Either[Result, Unit] = {
      await(service.send(vpr, hc))
    }

    when(mockPayloadDecorator.decorate(meq(TestXmlPayload), meq(TestSubscriptionFieldsId.value), meq(correlationIdValue), any[DateTime])(any[ValidatedPayloadRequest[_]])).thenReturn(wrappedValidXML)
    when(mockDateTimeProvider.getUtcNow).thenReturn(dateTime)
    when(mockApiDefinitionConfig.apiContext).thenReturn("customs/inventory-linking/exports")
    when(mockCustomsConfigService.apiDefinitionConfig).thenReturn(mockApiDefinitionConfig)
    when(mockMdgExportsConnector.send(any[NodeSeq], meq(dateTime), any[UUID])(any[ValidatedPayloadRequest[_]])).thenReturn(mockHttpResponse)
    when(mockApiSubscriptionFieldsConnector.getSubscriptionFields(any[ApiSubscriptionKey])(any[ValidatedPayloadRequest[_]], any[HeaderCarrier])).thenReturn(Future.successful(apiSubscriptionFieldsResponse))
  }

  "BusinessService" should {

    "send transformed xml to connector" in new SetUp() {

      val result: Either[Result, Unit] = send()

      result shouldBe Right(())
      verify(mockMdgExportsConnector).send(meq(wrappedValidXML), any[DateTime], any[UUID])(any[ValidatedPayloadRequest[_]])
    }

    "get utc date time and pass to connector" in new SetUp() {

      val result: Either[Result, Unit] = send()

      result shouldBe Right(())
      verify(mockMdgExportsConnector).send(any[NodeSeq], meq(dateTime), any[UUID])(any[ValidatedPayloadRequest[_]])
    }

    "call payload decorator passing incoming xml" in new SetUp() {

      val result: Either[Result, Unit] = send()

      result shouldBe Right(())
      verify(mockPayloadDecorator).decorate(meq(TestXmlPayload), meq(TestSubscriptionFieldsId.value), meq(correlationIdValue), any[DateTime])(any[ValidatedPayloadRequest[_]])
      verify(mockApiSubscriptionFieldsConnector).getSubscriptionFields(meq(expectedApiSubscriptionKey))(any[ValidatedPayloadRequest[_]], any[HeaderCarrier])
    }

    "return Left of error Result when subscription fields call fails" in new SetUp() {
      when(mockApiSubscriptionFieldsConnector.getSubscriptionFields(any[ApiSubscriptionKey])(any[ValidatedPayloadRequest[_]], any[HeaderCarrier])).thenReturn(Future.failed(emulatedServiceFailure))

      val result: Either[Result, Unit] = send()

      result shouldBe Left(ErrorResponse.ErrorInternalServerError.XmlResult.withConversationId)
      verifyZeroInteractions(mockPayloadDecorator)
      verifyZeroInteractions(mockMdgExportsConnector)
    }

    "return Left of error Result when MDG call fails" in new SetUp() {
      when(mockMdgExportsConnector.send(any[NodeSeq], any[DateTime], any[UUID])(any[ValidatedPayloadRequest[_]])).thenReturn(Future.failed(emulatedServiceFailure))

      val result: Either[Result, Unit] = send()

      result shouldBe Left(ErrorResponse.ErrorInternalServerError.XmlResult.withConversationId)
    }

  }


}
