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

import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, get, getRequestedFor, urlEqualTo}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.http.Status.{NOT_FOUND, OK}
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.customs.inventorylinking.exports.connectors.ApiSubscriptionFieldsConnector
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.test.WireMockSupport
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.play.audit.http.HttpAuditing
import uk.gov.hmrc.play.bootstrap.http.{DefaultHttpAuditing, HttpClientV2Provider}
import util.ExternalServicesConfig._
import util.{ApiSubscriptionFieldsTestData, TestData, UnitSpec}

class ApiSubscriptionFieldsConnectorSpec extends UnitSpec
  with GuiceOneAppPerSuite
  with WireMockSupport
  with ApiSubscriptionFieldsTestData {

  private val expectedUrl = "/field/application/SOME_X_CLIENT_ID/context/some/api/context/version/1.0"

  private implicit val vhr = TestData.TestValidatedHeadersRequest

  override lazy val app: Application = new GuiceApplicationBuilder()
    .configure(
      "microservice.services.api-subscription-fields.host" -> Host,
      "microservice.services.api-subscription-fields.port" -> Port,
    ).overrides(
      bind[HttpAuditing].to[DefaultHttpAuditing],
      bind[HttpClientV2].toProvider[HttpClientV2Provider]
    ).build()

  private val connector: ApiSubscriptionFieldsConnector = app.injector.instanceOf[ApiSubscriptionFieldsConnector]

  override protected def beforeEach(): Unit = {
    wireMockServer.resetMappings()
    wireMockServer.resetRequests()
  }

  "ApiSubscriptionFieldsConnector" can {
    "when making a successful request" should {
      "use the correct URL for valid path parameters and config" in {
        returnResponseForRequest(HttpResponse(OK, responseJsonString))
        val response = await(connector.getSubscriptionFields(apiSubscriptionKey))
        wireMockServer.verify(1, getRequestedFor(urlEqualTo(expectedUrl)))
        response shouldBe Some(apiSubscriptionFields)
      }
    }

    "when making an failing request" should {
      "return a None when api subscription fields call fails with an http error" in {
        returnResponseForRequest(HttpResponse(NOT_FOUND, ""))
        val response = await(connector.getSubscriptionFields(apiSubscriptionKey))
        wireMockServer.verify(1, getRequestedFor(urlEqualTo(expectedUrl)))
        response shouldBe None
      }
    }
  }

  private def returnResponseForRequest(eventualResponse: HttpResponse): Unit = {
    wireMockServer.stubFor(get(urlEqualTo(expectedUrl))
      .willReturn(
        aResponse()
          .withBody(eventualResponse.body)
          .withStatus(eventualResponse.status)))
  }
}
