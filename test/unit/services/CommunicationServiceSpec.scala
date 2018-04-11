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
import org.mockito.Mockito.{reset, verify, verifyZeroInteractions, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mockito.MockitoSugar
import uk.gov.hmrc.customs.inventorylinking.export.connectors.{ApiSubscriptionFieldsConnector, MdgExportsConnector}
import uk.gov.hmrc.customs.inventorylinking.export.logging.ExportsLogger
import uk.gov.hmrc.customs.inventorylinking.export.model._
import uk.gov.hmrc.customs.inventorylinking.export.services.{CommunicationService, CustomsConfigService, DateTimeService}
import uk.gov.hmrc.customs.inventorylinking.export.xml.MdgPayloadDecorator
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.test.UnitSpec
import util.TestData._
import util.XMLTestData._
import util.{ApiSubscriptionFieldsTestData, RequestHeaders}

import scala.concurrent.Future
import scala.xml.NodeSeq

class CommunicationServiceSpec extends UnitSpec with MockitoSugar with BeforeAndAfterEach with ApiSubscriptionFieldsTestData {

  private val mockLogger = mock[ExportsLogger]
  private val mockMdgExportsConnector = mock[MdgExportsConnector]
  private val mockApiSubscriptionFieldsConnector = mock[ApiSubscriptionFieldsConnector]
  private val mockPayloadDecorator = mock[MdgPayloadDecorator]
  private val mockDateTimeProvider = mock[DateTimeService]
  private val mockCustomsConfigService = mock[CustomsConfigService]
  private val mockApiDefinitionConfig = mock[ApiDefinitionConfig]
  private val mockOverridesConfig = mock[OverridesConfig]
  private val mockHttpResponse = mock[HttpResponse]

  private val dateTime = new DateTime()
  private val clientIdOverride = s"OVERRIDE_$xClientIdValue"

  private val headerCarrier: HeaderCarrier = HeaderCarrier()
    .withExtraHeaders(RequestHeaders.API_SUBSCRIPTION_FIELDS_ID_HEADER)

  private val expectedApiSubscriptionKey = ApiSubscriptionKey(xClientIdValue, "customs%2Finventory-linking%2Fexports", "1.0")

  private def testService(test: CommunicationService => Unit) {
    test(new CommunicationService(mockLogger, mockMdgExportsConnector, mockApiSubscriptionFieldsConnector,
      mockPayloadDecorator, mockDateTimeProvider, mockCustomsConfigService))
  }

  override protected def beforeEach(): Unit = {
    reset(mockLogger, mockMdgExportsConnector, mockApiSubscriptionFieldsConnector, mockPayloadDecorator,
      mockDateTimeProvider, mockCustomsConfigService, mockApiDefinitionConfig, mockOverridesConfig)
    when(mockDateTimeProvider.getUtcNow).thenReturn(dateTime)
    when(mockOverridesConfig.clientId).thenReturn(None)
    when(mockApiDefinitionConfig.apiContext).thenReturn("customs/inventory-linking/exports")
    when(mockCustomsConfigService.apiDefinitionConfig).thenReturn(mockApiDefinitionConfig)
    when(mockCustomsConfigService.overridesConfig).thenReturn(mockOverridesConfig)
    when(mockMdgExportsConnector.send(any[NodeSeq], meq(dateTime), any[UUID])).thenReturn(mockHttpResponse)
  }

  "CommunicationService" should {
    "send transformed xml to connector" in testService {
      service =>
        setupMockXmlWrapper
        prepareAndSendValidXml(service)
        verify(mockMdgExportsConnector).send(meq(wrappedValidXML), any[DateTime], any[UUID])
    }

    "get utc date time and pass to connector" in testService {
      service =>
        setupMockXmlWrapper
        prepareAndSendValidXml(service)
        verify(mockMdgExportsConnector).send(any[NodeSeq], meq(dateTime), any[UUID])
    }

    "call payload decorator passing incoming xml" in testService {
      service =>
        prepareAndSendValidXml(service)
        verify(mockPayloadDecorator).decorate(meq(ValidInventoryLinkingMovementRequestXML), any[Ids], anyString, any[DateTime])
    }

    "call payload decorator passing api-subscription-fields-id header as clientId" in testService{
      service =>
        prepareAndSendValidXml(service)
        verify(mockPayloadDecorator).decorate(any[NodeSeq], meq(ids), meq(fieldsId), any[DateTime])
        verifyZeroInteractions(mockApiSubscriptionFieldsConnector)
    }

    "when configured, use hardcoded value as clientID instead of api-subscription-fields-id header" in testService {
      service =>
        when(mockOverridesConfig.clientId).thenReturn(Some(clientIdOverride))

        prepareAndSendValidXml(service)
        verify(mockCustomsConfigService).overridesConfig
        verify(mockOverridesConfig).clientId
        verify(mockPayloadDecorator).decorate(any[NodeSeq], meq(ids), meq(clientIdOverride), any[DateTime])
        verifyZeroInteractions(mockApiSubscriptionFieldsConnector)
    }

    "use fieldsId returned from api subscription service when only X-Client-ID header present." in testService {
      service =>
        val hc = HeaderCarrier().withExtraHeaders(RequestHeaders.X_CLIENT_ID_HEADER)
        when(mockApiSubscriptionFieldsConnector.getSubscriptionFields(any[ApiSubscriptionKey])(any[HeaderCarrier])).thenReturn(Future.successful(apiSubscriptionFieldsResponse))

        prepareAndSendValidXml(service, hc)
        verify(mockCustomsConfigService).apiDefinitionConfig
        verify(mockApiDefinitionConfig).apiContext
        verify(mockPayloadDecorator).decorate(any[NodeSeq], meq(ids), meq(fieldsId), any[DateTime])
        verify(mockApiSubscriptionFieldsConnector).getSubscriptionFields(meq(expectedApiSubscriptionKey))(any[HeaderCarrier])
    }

    "when hardcoded value as clientID NOT hardcoded in configuration AND both api-subscription-fields-id and X-Client-ID headers present, use api-subscription-fields-id header" in testService {
      service =>
        val hc = HeaderCarrier().withExtraHeaders(RequestHeaders.API_SUBSCRIPTION_FIELDS_ID_HEADER, RequestHeaders.X_CLIENT_ID_HEADER)
        when(mockApiSubscriptionFieldsConnector.getSubscriptionFields(any[ApiSubscriptionKey])(any[HeaderCarrier])).thenReturn(Future.successful(apiSubscriptionFieldsResponse))

        prepareAndSendValidXml(service, hc)
        verify(mockPayloadDecorator).decorate(any[NodeSeq], meq(ids), meq(fieldsId), any[DateTime])
        verifyZeroInteractions(mockApiSubscriptionFieldsConnector)
    }

    "when clientId not specified in configuration or headers then IllegalStateException should be thrown" in testService {
      service =>
        val emptyHeaderCarrier = HeaderCarrier()
        when(mockApiSubscriptionFieldsConnector.getSubscriptionFields(any[ApiSubscriptionKey])(any[HeaderCarrier])).thenReturn(Future.failed(emulatedServiceFailure))

        val caught = intercept[IllegalStateException](prepareAndSendValidXml(service, emptyHeaderCarrier))
        caught.getMessage shouldBe "No value found for clientID."
    }

    "when hardcoded value as clientID NOT hardcoded in configuration AND api-subscription-fields-id header not present and api subscription service returns failed future" in testService {
      service =>
        val hc = HeaderCarrier().withExtraHeaders(RequestHeaders.X_CLIENT_ID_HEADER)
        when(mockApiSubscriptionFieldsConnector.getSubscriptionFields(any[ApiSubscriptionKey])(any[HeaderCarrier])).thenReturn(Future.failed(emulatedServiceFailure))

        val caught = intercept[EmulatedServiceFailure](prepareAndSendValidXml(service, hc))
        caught shouldBe emulatedServiceFailure
    }

    "call payload decorator passing conversationId and correlationId" in testService {
      service =>
        prepareAndSendValidXml(service)
        verify(mockPayloadDecorator).decorate(any[NodeSeq], any[Ids], anyString, any[DateTime])
    }

    "call payload decorator passing dateTime" in testService {
      service =>
        prepareAndSendValidXml(service)
        verify(mockPayloadDecorator).decorate(any[NodeSeq], any[Ids], anyString, meq(dateTime))
    }
  }

  private def setupMockXmlWrapper = {
    when(mockPayloadDecorator.decorate(meq(ValidInventoryLinkingMovementRequestXML), any[Ids], anyString, any[DateTime])).thenReturn(wrappedValidXML)
  }

  private def prepareAndSendValidXml(service: CommunicationService, hc: HeaderCarrier = headerCarrier): Ids = {
    await(service.prepareAndSend(ValidInventoryLinkingMovementRequestXML, ids)(hc))
  }
}
