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

package unit.connectors

import com.typesafe.config.Config
import model.ApiSubscriptionFieldsResponse
import org.mockito.ArgumentMatchers.{eq => ameq, _}
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.Eventually
import org.scalatest.mockito.MockitoSugar
import uk.gov.hmrc.customs.inventorylinking.export.connectors.ApiSubscriptionFieldsConnector
import uk.gov.hmrc.customs.inventorylinking.export.logging.ExportsLogger
import uk.gov.hmrc.customs.inventorylinking.export.model.actionbuilders.ValidatedPayloadRequest
import uk.gov.hmrc.customs.inventorylinking.export.services.ExportsConfigService
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, NotFoundException}
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.play.test.UnitSpec
import util.ExportsExternalServicesConfig._
import util.ExternalServicesConfig._
import util.{ApiSubscriptionFieldsTestData, TestData}

import scala.concurrent.{ExecutionContext, Future}

class ApiSubscriptionFieldsConnectorSpec extends UnitSpec
  with MockitoSugar
  with BeforeAndAfterEach
  with Eventually
  with ApiSubscriptionFieldsTestData {

  private val mockExportsConfigService = mock[ExportsConfigService]
  private val mockWSGetImpl = mock[HttpClient]
  private val mockExportsLogger = mock[ExportsLogger]
  private implicit val hc = HeaderCarrier()

  private val connector = connectorWithConfig(validConfig)

  private val httpException = new NotFoundException("Emulated 404 response from a web call")
  private val expectedUrl = s"http://$Host:$Port$ApiSubscriptionFieldsContext/application/SOME_X_CLIENT_ID/context/some/api/context/version/1.0"

  private implicit val vpr = TestData.TestCspValidatedPayloadRequest

  override protected def beforeEach() {
    reset(mockExportsLogger, mockWSGetImpl, mockExportsConfigService)

    when(mockExportsConfigService.apiSubscriptionFieldsBaseUrl).thenReturn("http://localhost:11111/api-subscription-fields/field")
  }

  "ApiSubscriptionFieldsConnector" can {
    "when making a successful request" should {
      "use the correct URL for valid path parameters and config" in {
        returnResponseForRequest(Future.successful(apiSubscriptionFieldsResponse))
        awaitSubscriptionFields shouldBe apiSubscriptionFieldsResponse
      }
    }

    "when making an failing request" should {
      "propagate an underlying error when api subscription fields call fails with a non-http exception" in {
        returnResponseForRequest(Future.failed(TestData.emulatedServiceFailure))

        val caught = intercept[TestData.EmulatedServiceFailure] {
          awaitSubscriptionFields
        }

        caught shouldBe TestData.emulatedServiceFailure
      }

      "wrap an underlying error when api subscription fields call fails with an http exception" in {
        returnResponseForRequest(Future.failed(httpException))

        val caught = intercept[RuntimeException] {
          awaitSubscriptionFields
        }

        caught.getCause shouldBe httpException
      }
    }
  }

  private def awaitSubscriptionFields[A](implicit vpr: ValidatedPayloadRequest[A]) = {
    await(connector.getSubscriptionFields(apiSubscriptionKey))
  }

  private def returnResponseForRequest(eventualResponse: Future[ApiSubscriptionFieldsResponse], url: String = expectedUrl) = {
    when(mockWSGetImpl.GET[ApiSubscriptionFieldsResponse](ameq(url))
      (any[HttpReads[ApiSubscriptionFieldsResponse]](), any[HeaderCarrier](), any[ExecutionContext])).thenReturn(eventualResponse)
  }

  private def connectorWithConfig(config: Config) = new ApiSubscriptionFieldsConnector(mockWSGetImpl, mockExportsLogger, mockExportsConfigService)

}
