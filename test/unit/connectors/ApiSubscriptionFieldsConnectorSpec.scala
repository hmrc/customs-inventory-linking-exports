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

import com.typesafe.config.Config
import org.mockito.ArgumentMatchers.{eq => ameq, _}
import org.mockito.Mockito._
import org.mockito.stubbing.OngoingStubbing
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.Eventually
import org.scalatestplus.mockito.MockitoSugar
import play.api.http.Status.{NOT_FOUND, OK}
import play.api.test.Helpers
import uk.gov.hmrc.customs.inventorylinking.export.connectors.ApiSubscriptionFieldsConnector
import uk.gov.hmrc.customs.inventorylinking.export.logging.ExportsLogger
import uk.gov.hmrc.customs.inventorylinking.export.model.ExportsConfig
import uk.gov.hmrc.customs.inventorylinking.export.services.ExportsConfigService
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpReads, HttpResponse}
import util.ExternalServicesConfig._
import util.externalservices.ExportsExternalServicesConfig._
import util.{ApiSubscriptionFieldsTestData, TestData, UnitSpec}

import java.net.URL
import scala.concurrent.{ExecutionContext, Future}

class ApiSubscriptionFieldsConnectorSpec extends UnitSpec
  with MockitoSugar
  with BeforeAndAfterEach
  with Eventually
  with ApiSubscriptionFieldsTestData {

  private val mockExportsConfigService = mock[ExportsConfigService]
  private val mockExportsConfig = mock[ExportsConfig]
  private val mockWSGetImpl = mock[HttpClient]
  private val mockExportsLogger = mock[ExportsLogger]
  private implicit val ec = Helpers.stubControllerComponents().executionContext
  private val connector = connectorWithConfig(validConfig)

  private val expectedUrl = s"http://$Host:$Port$ApiSubscriptionFieldsContext/application/SOME_X_CLIENT_ID/context/some/api/context/version/1.0"

  private implicit val vhr = TestData.TestValidatedHeadersRequest

  override protected def beforeEach(): Unit = {
    reset(mockExportsLogger, mockWSGetImpl, mockExportsConfigService)
    when(mockExportsConfigService.exportsConfig).thenReturn(mockExportsConfig)
    when(mockExportsConfig.apiSubscriptionFieldsBaseUrl).thenReturn("http://localhost:11111/api-subscription-fields/field")
  }

  "ApiSubscriptionFieldsConnector" can {
    "when making a successful request" should {
      "use the correct URL for valid path parameters and config" in {
        returnResponseForRequest(Future.successful(HttpResponse(OK, responseJsonString)))
        val response = await(connector.getSubscriptionFields(apiSubscriptionKey))
        response shouldBe Some(apiSubscriptionFields)
      }
    }

    "when making an failing request" should {
      "return a None when api subscription fields call fails with an http error" in {
        returnResponseForRequest(Future.successful(HttpResponse(NOT_FOUND, "")))

        val response = await(connector.getSubscriptionFields(apiSubscriptionKey))

        response shouldBe None
      }
    }
  }

  private def returnResponseForRequest(eventualResponse: Future[HttpResponse], url: URL = new URL(expectedUrl)): OngoingStubbing[Future[HttpResponse]] = {
    when(mockWSGetImpl.GET[HttpResponse](ameq(url))
      (any[HttpReads[HttpResponse]](), any[HeaderCarrier](), any[ExecutionContext])).thenReturn(eventualResponse)
  }

  private def connectorWithConfig(config: Config) = new ApiSubscriptionFieldsConnector(mockWSGetImpl, mockExportsLogger, mockExportsConfigService)

}
