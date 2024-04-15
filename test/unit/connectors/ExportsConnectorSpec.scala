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

package unit.connectors

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.pattern.CircuitBreakerOpenException
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.{eq => ameq, _}
import org.mockito.Mockito._
import org.mockito.stubbing.OngoingStubbing
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.Eventually
import org.scalatestplus.mockito.MockitoSugar
import play.api.http.HeaderNames
import play.api.test.Helpers
import play.mvc.Http.MimeTypes
import uk.gov.hmrc.customs.inventorylinking.export.connectors.ExportsConnector.RetryError
import uk.gov.hmrc.customs.inventorylinking.export.config.{ServiceConfig, ServiceConfigProvider}
import uk.gov.hmrc.customs.inventorylinking.export.connectors.ExportsConnector
import uk.gov.hmrc.customs.inventorylinking.export.logging.CdsLogger
import uk.gov.hmrc.customs.inventorylinking.export.model.ExportsCircuitBreakerConfig
import uk.gov.hmrc.customs.inventorylinking.export.model.actionbuilders.ValidatedPayloadRequest
import uk.gov.hmrc.customs.inventorylinking.export.services.ExportsConfigService
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpReads, HttpResponse}
import unit.logging.StubExportsLogger
import util.TestData._
import util.{RequestHeaders, UnitSpec}

import java.time.{Instant, LocalDateTime}
import scala.concurrent.duration.{FiniteDuration, MILLISECONDS}
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
  private implicit lazy val hc = HeaderCarrier().withExtraHeaders(RequestHeaders.API_SUBSCRIPTION_FIELDS_ID_HEADER)

  override protected def beforeEach(): Unit = {
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
  private val date = LocalDateTime.of(year, monthOfYear, dayOfMonth, hourOfDay, minuteOfHour)


  private val httpFormattedDate = "Tue, 04 Jul 2017 13:45:00 UTC"

  private implicit val vpr = TestCspValidatedPayloadRequestWithEori

  "ExportsConnector" can {

    "when making a successful request" should {


      "ensure URL is retrieved from config" in {
        returnResponseForRequest(Future.successful(mockResponse))

        awaitRequest()

        verify(mockWsPost).POSTString(ameq(serviceConfig.url), anyString, any[Seq[(String, String)]])(
          any[HttpReads[HttpResponse]](), any[HeaderCarrier](), any[ExecutionContext])
      }

      "ensure xml payload is included in the request body" in {
        returnResponseForRequest(Future.successful(mockResponse))

        awaitRequest()

        verify(mockWsPost).POSTString(anyString, ameq(xml.toString()), any[Seq[(String, String)]])(
          any[HttpReads[HttpResponse]](), any[HeaderCarrier](), any[ExecutionContext])
      }

      "ensure the content type header is passed through in the request" in {
        returnResponseForRequest(Future.successful(mockResponse))

        awaitRequest()

        val headersCaptor: ArgumentCaptor[Seq[(String, String)]] = ArgumentCaptor.forClass(classOf[Seq[(String, String)]])
        verify(mockWsPost).POSTString(anyString, anyString, headersCaptor.capture())(
          any[HttpReads[HttpResponse]](), any[HeaderCarrier], any[ExecutionContext])
        headersCaptor.getValue should contain(HeaderNames.CONTENT_TYPE -> (MimeTypes.XML + "; charset=UTF-8"))
      }

      "ensure the accept header is passed through in the request" in {
        returnResponseForRequest(Future.successful(mockResponse))

        awaitRequest()

        val headersCaptor: ArgumentCaptor[Seq[(String, String)]] = ArgumentCaptor.forClass(classOf[Seq[(String, String)]])
        verify(mockWsPost).POSTString(anyString, anyString, headersCaptor.capture())(
          any[HttpReads[HttpResponse]](), any[HeaderCarrier], any[ExecutionContext])
        headersCaptor.getValue should contain(HeaderNames.ACCEPT -> MimeTypes.XML)
      }

      "ensure the date header is passed through in the request" in {
        returnResponseForRequest(Future.successful(mockResponse))

        awaitRequest()

        val headersCaptor: ArgumentCaptor[Seq[(String, String)]] = ArgumentCaptor.forClass(classOf[Seq[(String, String)]])
        verify(mockWsPost).POSTString(anyString, anyString, headersCaptor.capture())(
          any[HttpReads[HttpResponse]](), any[HeaderCarrier], any[ExecutionContext])
        headersCaptor.getValue should contain(HeaderNames.DATE -> httpFormattedDate)
      }

      "ensure the X-FORWARDED_HOST header is passed through in the request" in {
        returnResponseForRequest(Future.successful(mockResponse))

        awaitRequest()

        val headersCaptor: ArgumentCaptor[Seq[(String, String)]] = ArgumentCaptor.forClass(classOf[Seq[(String, String)]])
        verify(mockWsPost).POSTString(anyString, anyString, headersCaptor.capture())(
          any[HttpReads[HttpResponse]](), any[HeaderCarrier], any[ExecutionContext])
        headersCaptor.getValue should contain(HeaderNames.X_FORWARDED_HOST -> "MDTP")
      }

      "ensure the X-Correlation-Id header is passed through in the request" in {
        returnResponseForRequest(Future.successful(mockResponse))

        awaitRequest()

        val headersCaptor: ArgumentCaptor[Seq[(String, String)]] = ArgumentCaptor.forClass(classOf[Seq[(String, String)]])
        verify(mockWsPost).POSTString(anyString, anyString, headersCaptor.capture())(
          any[HttpReads[HttpResponse]](), any[HeaderCarrier], any[ExecutionContext])
        headersCaptor.getValue should contain("X-Correlation-ID" -> correlationIdValue)
      }
    }

    "when making a failing request the connector" should {
      "when configuration is absent" should {
        "throw an exception when no config is found" in {
          when(mockServiceConfigProvider.getConfig("mdg-exports")).thenReturn(null)

          val caught = intercept[IllegalArgumentException] {
            awaitRequest()
          }
          caught.getMessage shouldBe "config not found"
        }
      }

      "when bearer token is absent" should {
        "throw an exception when no config is found" in {
          val serviceConfig = ServiceConfig("the-url", None, "default")
          when(mockServiceConfigProvider.getConfig("mdg-exports")).thenReturn(serviceConfig)

          val caught = intercept[IllegalStateException] {
            awaitRequest()
          }
          caught.getMessage shouldBe "no bearer token was found in config"
        }
      }

      "when CircuitBreakerOpenException is threw" in {
        returnResponseForRequest(Future.failed(new CircuitBreakerOpenException(new FiniteDuration(5, MILLISECONDS))))
        val result = awaitRequest()
        result.isLeft shouldBe true
        result shouldBe Left(RetryError)
      }
    }
  }

  private def awaitRequest[A]()(implicit vpr: ValidatedPayloadRequest[A]): Either[ExportsConnector.Error, HttpResponse] = {
    await(connector.send(xml, date, correlationIdUuid))
  }

  private def returnResponseForRequest(eventualResponse: Future[HttpResponse]): OngoingStubbing[Future[HttpResponse]] = {
    when(mockWsPost.POSTString(anyString, anyString, any[Seq[(String, String)]])(
      any[HttpReads[HttpResponse]](), any[HeaderCarrier](), any[ExecutionContext]))
      .thenReturn(eventualResponse)
  }
}
