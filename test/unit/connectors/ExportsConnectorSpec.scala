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

package unit.connectors

import akka.actor.ActorSystem
import org.joda.time.{DateTime, DateTimeZone}
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.{eq => ameq, _}
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.Eventually
import org.scalatestplus.mockito.MockitoSugar
import play.api.http.HeaderNames
import play.api.test.Helpers
import play.mvc.Http.MimeTypes
import uk.gov.hmrc.customs.api.common.config.{ServiceConfig, ServiceConfigProvider}
import uk.gov.hmrc.customs.api.common.logging.CdsLogger
import uk.gov.hmrc.customs.inventorylinking.export.connectors.ExportsConnector
import uk.gov.hmrc.customs.inventorylinking.export.model.ExportsCircuitBreakerConfig
import uk.gov.hmrc.customs.inventorylinking.export.model.actionbuilders.ValidatedPayloadRequest
import uk.gov.hmrc.customs.inventorylinking.export.services.ExportsConfigService
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, HttpResponse}
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import util.UnitSpec
import unit.logging.StubExportsLogger
import util.RequestHeaders
import util.TestData._

import scala.concurrent.{ExecutionContext, Future}

class ExportsConnectorSpec extends UnitSpec with MockitoSugar with BeforeAndAfterEach with Eventually {

  private val mockWsPost = mock[HttpClient]
  private val stubExportsLogger = new StubExportsLogger(mock[CdsLogger])
  private val mockServiceConfigProvider = mock[ServiceConfigProvider]
  private val mockExportsConfigService = mock[ExportsConfigService]
  private val mockExportsCircuitBreakerConfig = mock[ExportsCircuitBreakerConfig]
  private val mockResponse = mock[HttpResponse]
  private val cdsLogger = mock[CdsLogger]
  private val actorSystem = ActorSystem("mockActorSystem")
  private implicit val ec = Helpers.stubControllerComponents().executionContext

  private val connector = new ExportsConnector(mockWsPost, stubExportsLogger, mockServiceConfigProvider, mockExportsConfigService, cdsLogger, actorSystem)

  private val serviceConfig = ServiceConfig("the-url", Some("bearerToken"), "default")

  private val xml = <xml></xml>
  private implicit val hc = HeaderCarrier().withExtraHeaders(RequestHeaders.API_SUBSCRIPTION_FIELDS_ID_HEADER)

  override protected def beforeEach() {
    reset(mockWsPost, mockServiceConfigProvider)
    when(mockExportsConfigService.exportsCircuitBreakerConfig).thenReturn(mockExportsCircuitBreakerConfig)
    when(mockServiceConfigProvider.getConfig("mdg-exports")).thenReturn(serviceConfig)
    when(mockResponse.body).thenReturn("<foo/>")
    when(mockResponse.status).thenReturn(200)
  }

  private val year = 2017
  private val monthOfYear = 7
  private val dayOfMonth = 4
  private val hourOfDay = 13
  private val minuteOfHour = 45
  private val date = new DateTime(year, monthOfYear, dayOfMonth, hourOfDay, minuteOfHour, DateTimeZone.UTC)

  private val httpFormattedDate = "Tue, 04 Jul 2017 13:45:00 UTC"

  private implicit val vpr = TestCspValidatedPayloadRequest

  "ExportsConnector" can {

    "when making a successful request" should {


      "ensure URL is retrieved from config" in {
        returnResponseForRequest(Future.successful(mockResponse))

        awaitRequest

        verify(mockWsPost).POSTString(ameq(serviceConfig.url), anyString, any[Seq[(String, String)]])(
          any[HttpReads[HttpResponse]](), any[HeaderCarrier](), any[ExecutionContext])
      }

      "ensure xml payload is included in the request body" in {
        returnResponseForRequest(Future.successful(mockResponse))

        awaitRequest

        verify(mockWsPost).POSTString(anyString, ameq(xml.toString()), any[Seq[(String, String)]])(
          any[HttpReads[HttpResponse]](), any[HeaderCarrier](), any[ExecutionContext])
      }

      "ensure the content type header is passed through in the request" in {
        returnResponseForRequest(Future.successful(mockResponse))

        awaitRequest

        val headersCaptor: ArgumentCaptor[HeaderCarrier] = ArgumentCaptor.forClass(classOf[HeaderCarrier])
        verify(mockWsPost).POSTString(anyString, anyString, any[Seq[(String, String)]])(
          any[HttpReads[HttpResponse]](), headersCaptor.capture(), any[ExecutionContext])
        headersCaptor.getValue.extraHeaders should contain(HeaderNames.CONTENT_TYPE -> (MimeTypes.XML + "; charset=UTF-8"))
      }

      "ensure the accept header is passed through in the request" in {
        returnResponseForRequest(Future.successful(mockResponse))

        awaitRequest

        val headersCaptor: ArgumentCaptor[HeaderCarrier] = ArgumentCaptor.forClass(classOf[HeaderCarrier])
        verify(mockWsPost).POSTString(anyString, anyString, any[Seq[(String, String)]])(
          any[HttpReads[HttpResponse]](), headersCaptor.capture(), any[ExecutionContext])
        headersCaptor.getValue.extraHeaders should contain(HeaderNames.ACCEPT -> MimeTypes.XML)
      }

      "ensure the date header is passed through in the request" in {
        returnResponseForRequest(Future.successful(mockResponse))

        awaitRequest

        val headersCaptor: ArgumentCaptor[HeaderCarrier] = ArgumentCaptor.forClass(classOf[HeaderCarrier])
        verify(mockWsPost).POSTString(anyString, anyString, any[Seq[(String, String)]])(
          any[HttpReads[HttpResponse]](), headersCaptor.capture(), any[ExecutionContext])
        headersCaptor.getValue.extraHeaders should contain(HeaderNames.DATE -> httpFormattedDate)
      }

      "ensure the X-FORWARDED_HOST header is passed through in the request" in {
        returnResponseForRequest(Future.successful(mockResponse))

        awaitRequest

        val headersCaptor: ArgumentCaptor[HeaderCarrier] = ArgumentCaptor.forClass(classOf[HeaderCarrier])
        verify(mockWsPost).POSTString(anyString, anyString, any[Seq[(String, String)]])(
          any[HttpReads[HttpResponse]](), headersCaptor.capture(), any[ExecutionContext])
        headersCaptor.getValue.extraHeaders should contain(HeaderNames.X_FORWARDED_HOST -> "MDTP")
      }

      "ensure the X-Correlation-Id header is passed through in the request" in {
        returnResponseForRequest(Future.successful(mockResponse))

        awaitRequest

        val headersCaptor: ArgumentCaptor[HeaderCarrier] = ArgumentCaptor.forClass(classOf[HeaderCarrier])
        verify(mockWsPost).POSTString(anyString, anyString, any[Seq[(String, String)]])(
          any[HttpReads[HttpResponse]](), headersCaptor.capture(), any[ExecutionContext])
        headersCaptor.getValue.extraHeaders should contain("X-Correlation-ID" -> correlationIdValue)
      }
    }

    "when making a failing request the connector" should {
      "propagate an underlying error when backend call fails with a non-http exception" in {
        returnResponseForRequest(Future.failed(emulatedServiceFailure))

        val caught = intercept[EmulatedServiceFailure] {
          awaitRequest
        }

        caught shouldBe emulatedServiceFailure
      }

      "when configuration is absent" should {
        "throw an exception when no config is found" in {
          when(mockServiceConfigProvider.getConfig("mdg-exports")).thenReturn(null)

          val caught = intercept[IllegalArgumentException] {
            awaitRequest
          }
          caught.getMessage shouldBe "config not found"
        }
      }
    }
  }

  private def awaitRequest[A](implicit vpr: ValidatedPayloadRequest[A]) = {
    await(connector.send(xml, date, correlationIdUuid))
  }

  private def returnResponseForRequest(eventualResponse: Future[HttpResponse]) = {
    when(mockWsPost.POSTString(anyString, anyString, any[Seq[(String, String)]])(
      any[HttpReads[HttpResponse]](), any[HeaderCarrier](), any[ExecutionContext]))
      .thenReturn(eventualResponse)
  }
}
