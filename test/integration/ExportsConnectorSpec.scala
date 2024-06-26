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

package integration

import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers._
import uk.gov.hmrc.customs.inventorylinking.export.connectors.ExportsConnector
import uk.gov.hmrc.customs.inventorylinking.export.connectors.ExportsConnector._
import uk.gov.hmrc.http.{Authorization, _}
import util.ExternalServicesConfig.{AuthToken, Host, Port}
import util.TestData
import util.XMLTestData.ValidInventoryLinkingMovementRequestXML
import util.externalservices.{ExportsExternalServicesConfig, InventoryLinkingExportsService}

import java.time.LocalDateTime
import java.util.UUID

class ExportsConnectorSpec extends IntegrationTestSpec with GuiceOneAppPerSuite with MockitoSugar
  with InventoryLinkingExportsService with TableDrivenPropertyChecks {

  private val numberOfCallsToTriggerStateChange = 5
  private val unstablePeriodDurationInMillis = 10000
  private val unavailablePeriodDurationInMillis = 250

  override implicit lazy val app: Application =
    new GuiceApplicationBuilder().configure(Map(
      "circuitBreaker.numberOfCallsToTriggerStateChange" -> numberOfCallsToTriggerStateChange,
      "circuitBreaker.unstablePeriodDurationInMillis" -> unstablePeriodDurationInMillis,
      "circuitBreaker.unavailablePeriodDurationInMillis" -> unavailablePeriodDurationInMillis,
      "microservice.services.mdg-exports.host" -> Host,
      "microservice.services.mdg-exports.port" -> Port,
      "microservice.services.mdg-exports.context" -> ExportsExternalServicesConfig.ExportsServiceContext,
      "microservice.services.mdg-exports.bearer-token" -> AuthToken,
      "metrics.enabled" -> false
    )).build()

  private lazy val connector = app.injector.instanceOf[ExportsConnector]

  private val incomingBearerToken = "some_client's_bearer_token"
  private val incomingAuthToken = s"Bearer $incomingBearerToken"
  private val correlationId = UUID.randomUUID()
  private implicit val vpr = TestData.TestCspValidatedPayloadRequestWithEori

  private implicit val hc: HeaderCarrier = HeaderCarrier(authorization = Some(Authorization(incomingAuthToken)))

  override protected def beforeAll(): Unit = {
    startMockServer()
  }

  override protected def afterEach(): Unit = {
    resetMockServer()
  }

  override protected def afterAll() : Unit = {
    stopMockServer()
  }

  "ExportsConnector" should {

    //wait to clear the circuit breaker state that may of been tripped by previous tests
    Thread.sleep(unavailablePeriodDurationInMillis)

    "make a correct request" in {
      startBackendService()

      await(connector.send(ValidInventoryLinkingMovementRequestXML, LocalDateTime.now(), correlationId))

      verifyInventoryLinkingExportsServiceWasCalledWith(ValidInventoryLinkingMovementRequestXML.toString())
    }

    "return a failed future when service returns 404" in {
      setupBackendServiceToReturn(NOT_FOUND)

      val response = await(connector.send(ValidInventoryLinkingMovementRequestXML, LocalDateTime.now(), correlationId))

      response shouldBe Left(Non2xxResponseError(NOT_FOUND))
    }

    "return a failed future when service returns 400" in {
      setupBackendServiceToReturn(BAD_REQUEST)

      val response = await(connector.send(ValidInventoryLinkingMovementRequestXML, LocalDateTime.now(), correlationId))

      response shouldBe Left(Non2xxResponseError(BAD_REQUEST))
    }

    "return a failed future when service returns 500" in {
      setupBackendServiceToReturn(INTERNAL_SERVER_ERROR)

      val response = await(connector.send(ValidInventoryLinkingMovementRequestXML, LocalDateTime.now(), correlationId))

      response shouldBe Left(Non2xxResponseError(INTERNAL_SERVER_ERROR))
    }
  }
}
