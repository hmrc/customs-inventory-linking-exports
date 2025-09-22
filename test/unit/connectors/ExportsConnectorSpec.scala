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

import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, equalTo, post, postRequestedFor, urlEqualTo}
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.pattern.CircuitBreakerOpenException
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.*
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.Eventually
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.http.HeaderNames.{AUTHORIZATION, CONTENT_TYPE, X_FORWARDED_HOST}
import play.api.http.Status.OK
import play.api.http.{HeaderNames, MimeTypes}
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.AnyContentAsXml
import play.api.test.Helpers
import play.api.test.Helpers.ACCEPT
import uk.gov.hmrc.customs.inventorylinking.exports.connectors.CircuitBreakerConnector
import uk.gov.hmrc.customs.inventorylinking.exports.connectors.ExportsConnector.RetryError
import uk.gov.hmrc.customs.inventorylinking.exports.config.{ServiceConfig, ServiceConfigProvider}
import uk.gov.hmrc.customs.inventorylinking.exports.connectors.ExportsConnector
import uk.gov.hmrc.customs.inventorylinking.exports.logging.CdsLogger
import uk.gov.hmrc.customs.inventorylinking.exports.model.ExportsCircuitBreakerConfig
import uk.gov.hmrc.customs.inventorylinking.exports.model.actionbuilders.ValidatedPayloadRequest
import uk.gov.hmrc.customs.inventorylinking.exports.services.ExportsConfigService
import uk.gov.hmrc.http.client.{HttpClientV2, RequestBuilder}
import uk.gov.hmrc.http.test.{HttpClientV2Support, WireMockSupport}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.http.HttpClientV2Provider
import unit.logging.StubExportsLogger
import util.ExternalServicesConfig.{Host, Port}
import util.TestData.*
import util.{RequestHeaders, UnitSpec}

import java.time.LocalDateTime
import scala.concurrent.duration.{FiniteDuration, MILLISECONDS}
import scala.concurrent.{ExecutionContext, Future}

class ExportsConnectorSpec
  extends UnitSpec
    with MockitoSugar
    with BeforeAndAfterEach
    with Eventually
    with GuiceOneAppPerSuite
    with HttpClientV2Support
    with WireMockSupport {

  private val mockHttpClient                  = mock[HttpClientV2]
  private val stubExportsLogger               = new StubExportsLogger(mock[CdsLogger])
  private val mockServiceConfigProvider       = mock[ServiceConfigProvider]
  private val mockExportsConfigService        = mock[ExportsConfigService]
  private val mockExportsCircuitBreakerConfig = mock[ExportsCircuitBreakerConfig]
  private val cdsLogger                       = mock[CdsLogger]
  private val stubCircuitBreakerConnector     = mock[CircuitBreakerConnector]
  private val mockRequestBuilder              = mock[RequestBuilder]
  private val actorSystem                     = ActorSystem("mockActorSystem")

  private implicit val ec: ExecutionContext = Helpers.stubControllerComponents().executionContext

  override lazy val app: Application = new GuiceApplicationBuilder()
    .configure(
      "microservice.services.mdg-exports.host" -> Host,
      "microservice.services.mdg-exports.port" -> Port,
      "microservice.services.v2.mdg-exports.host" -> Host,
      "microservice.services.v2.mdg-exports.port" -> Port
    ).overrides(
      bind[HttpClientV2].toProvider[HttpClientV2Provider],
      bind[ExportsConfigService].toInstance(mockExportsConfigService),
      bind[ServiceConfigProvider].toInstance(mockServiceConfigProvider),
      bind[StubExportsLogger].toInstance(stubExportsLogger),
      bind[CdsLogger].toInstance(cdsLogger),
      bind[CircuitBreakerConnector].toInstance(stubCircuitBreakerConnector)
    ).build()

  private val connector: ExportsConnector     = app.injector.instanceOf[ExportsConnector]
  private val mockConnector: ExportsConnector = new ExportsConnector(mockHttpClient, stubExportsLogger, mockServiceConfigProvider, mockExportsConfigService, cdsLogger, actorSystem)

  private val serviceConfig = ServiceConfig(s"http://$Host:$Port/inventorylinking/exportsinbound/v2", Some("bearerToken"), "default")
  private val expectedUrl   = "/inventorylinking/exportsinbound/v2"
  private val xml           = <xml></xml>

  private implicit lazy val hc: HeaderCarrier = HeaderCarrier().withExtraHeaders(RequestHeaders.API_SUBSCRIPTION_FIELDS_ID_HEADER)

  override protected def beforeEach(): Unit = {
    reset(mockHttpClient, mockServiceConfigProvider)
    wireMockServer.resetRequests()
    wireMockServer.resetMappings()
    when(mockExportsConfigService.exportsCircuitBreakerConfig).thenReturn(mockExportsCircuitBreakerConfig)
    when(mockServiceConfigProvider.getConfig("mdg-exports")).thenReturn(serviceConfig)
  }

  private val year = 2017
  private val monthOfYear = 7
  private val dayOfMonth = 4
  private val hourOfDay = 13
  private val minuteOfHour = 45
  private val date = LocalDateTime.of(year, monthOfYear, dayOfMonth, hourOfDay, minuteOfHour)


  private val httpFormattedDate = "Tue, 04 Jul 2017 13:45:00 UTC"

  private implicit val vpr: ValidatedPayloadRequest[AnyContentAsXml]  = TestCspValidatedPayloadRequestWithEori

  "ExportsConnector" can {
    "when making a successful request" should {
      "ensure URL is retrieved from config, headers are populated and XML payload is sent" in {
        returnResponseForRequest(HttpResponse(OK))
        awaitRequest()
        wireMockServer.verify(1, postRequestedFor(urlEqualTo(expectedUrl))
          .withRequestBody(equalTo(xml.toString()))
          .withHeader(AUTHORIZATION, equalTo("Bearer bearerToken"))
          .withHeader(ACCEPT, equalTo(MimeTypes.XML))
          .withHeader(CONTENT_TYPE, equalTo(MimeTypes.XML + "; charset=UTF-8"))
          .withHeader(X_FORWARDED_HOST, equalTo("MDTP"))
          .withHeader(HeaderNames.DATE, equalTo(httpFormattedDate))
          .withHeader("X-Correlation-ID", equalTo("e61f8eee-812c-4b8f-b193-06aedc60dca2")))
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
        returnResponseForRequestWithFailedCircuitBreaker()
        val result = await(mockConnector.send(xml, date, correlationIdUuid))
        result.isLeft shouldBe true
        result shouldBe Left(RetryError)
      }
    }
  }

  private def awaitRequest[A]()(implicit vpr: ValidatedPayloadRequest[A]): Either[ExportsConnector.Error, HttpResponse] = {
    await(connector.send(xml, date, correlationIdUuid))
  }

  private def returnResponseForRequest(eventualResponse: HttpResponse): Unit = {
    wireMockServer.stubFor(post(urlEqualTo(expectedUrl))
      .withRequestBody(equalTo(xml.toString()))
      .willReturn(
        aResponse()
          .withBody(eventualResponse.body)
          .withStatus(eventualResponse.status)))
  }

  private def returnResponseForRequestWithFailedCircuitBreaker(): Unit = {
    when(mockHttpClient.post(any)(any))
      .thenReturn(mockRequestBuilder)
    when(mockRequestBuilder.setHeader(any())).thenReturn(mockRequestBuilder)
    when(mockRequestBuilder.withBody(any())(any(), any(), any())).thenReturn(mockRequestBuilder)
    when(mockRequestBuilder.execute[HttpResponse](any(), any()))
      .thenReturn(Future.failed(new CircuitBreakerOpenException(new FiniteDuration(5, MILLISECONDS))))
  }
}