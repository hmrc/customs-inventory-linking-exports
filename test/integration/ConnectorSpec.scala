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

package integration

import java.util.UUID

import org.joda.time.DateTime
import org.scalatest.mockito.MockitoSugar
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers._
import uk.gov.hmrc.circuitbreaker.UnhealthyServiceException
import uk.gov.hmrc.customs.inventorylinking.export.connectors.MdgExportsConnector
import uk.gov.hmrc.customs.inventorylinking.export.model.actionbuilders.ValidatedPayloadRequest
import uk.gov.hmrc.http._
import uk.gov.hmrc.http.logging.Authorization
import util.ExternalServicesConfig.{AuthToken, Host, Port}
import util.XMLTestData.ValidInventoryLinkingMovementRequestXML
import util.externalservices.InventoryLinkingExportsService
import util.{ExportsExternalServicesConfig, TestData}

import scala.xml.NodeSeq

class ConnectorSpec extends IntegrationTestSpec with GuiceOneAppPerSuite with MockitoSugar
  with InventoryLinkingExportsService with TableDrivenPropertyChecks {

  private val numberOfCallsToTriggerStateChange = 5
  private val unstablePeriodDurationInMillis = 200
  private val unavailablePeriodDurationInMillis = 250

  override implicit lazy val app: Application =
    new GuiceApplicationBuilder().configure(Map(
      "circuitBreaker.numberOfCallsToTriggerStateChange" -> numberOfCallsToTriggerStateChange,
      "circuitBreaker.unstablePeriodDurationInMillis" -> unstablePeriodDurationInMillis,
      "circuitBreaker.unavailablePeriodDurationInMillis" -> unavailablePeriodDurationInMillis,
      "microservice.services.mdg-exports.host" -> Host,
      "microservice.services.mdg-exports.port" -> Port,
      "microservice.services.mdg-exports.context" -> ExportsExternalServicesConfig.MdgExportsServiceContext,
      "microservice.services.mdg-exports.bearer-token" -> AuthToken
    )).build()

  private lazy val connector = app.injector.instanceOf[MdgExportsConnector]

  private val incomingBearerToken = "some_client's_bearer_token"
  private val incomingAuthToken = s"Bearer $incomingBearerToken"
  private val correlationId = UUID.randomUUID()
  private implicit val vpr = TestData.TestCspValidatedPayloadRequest

  private implicit val hc: HeaderCarrier = HeaderCarrier(authorization = Some(Authorization(incomingAuthToken)))

  override protected def beforeAll() {
    startMockServer()
  }

  override protected def afterEach(): Unit = {
    resetMockServer()
  }

  override protected def afterAll() {
    stopMockServer()
  }

    "ExportsConnector" should {

      "make a correct request" in {
        startInventoryLinkingExportsService()

        await(sendValidXml(ValidInventoryLinkingMovementRequestXML))

        verifyInventoryLinkingExportsServiceWasCalledWith(ValidInventoryLinkingMovementRequestXML.toString())
      }

      "return a failed future when service returns 404" in {
        setupInventoryLinkingExportsServiceToReturn(NOT_FOUND)

        intercept[RuntimeException](await(sendValidXml(ValidInventoryLinkingMovementRequestXML))).getCause.getClass shouldBe classOf[NotFoundException]
      }

      "return a failed future when service returns 400" in {
        setupInventoryLinkingExportsServiceToReturn(BAD_REQUEST)

        intercept[RuntimeException](await(sendValidXml(ValidInventoryLinkingMovementRequestXML))).getCause.getClass shouldBe classOf[BadRequestException]
      }

      "return a failed future when service returns 500" in {
        setupInventoryLinkingExportsServiceToReturn(INTERNAL_SERVER_ERROR)

        intercept[Upstream5xxResponse](await(sendValidXml(ValidInventoryLinkingMovementRequestXML)))
      }

      "cause circuit breaker to trip after specified number of failures" in {
        Thread.sleep(unavailablePeriodDurationInMillis)

        setupInventoryLinkingExportsServiceToReturn(INTERNAL_SERVER_ERROR)

        1 to numberOfCallsToTriggerStateChange foreach { _ =>
          val k = intercept[Upstream5xxResponse](await(sendValidXml(ValidInventoryLinkingMovementRequestXML)))
          k.reportAs shouldBe BAD_GATEWAY
        }

        1 to 3 foreach { _ =>
          val k = intercept[UnhealthyServiceException](await(sendValidXml(ValidInventoryLinkingMovementRequestXML)))
          k.getMessage shouldBe "customs-inventory-linking-exports"
        }

        resetMockServer()
        startInventoryLinkingExportsService()

        Thread.sleep(unavailablePeriodDurationInMillis)

        1 to 5 foreach { _ =>
          resetMockServer()
          startInventoryLinkingExportsService()
          await(sendValidXml(ValidInventoryLinkingMovementRequestXML))
          verifyInventoryLinkingExportsServiceWasCalledWith(ValidInventoryLinkingMovementRequestXML.toString())
        }
      }

      "return a failed future when connection with backend service fails" in {
        stopMockServer()

        intercept[RuntimeException](await(sendValidXml(ValidInventoryLinkingMovementRequestXML))).getCause.getClass shouldBe classOf[BadGatewayException]

        startMockServer()
      }

  }

  private def sendValidXml(xml:NodeSeq)(implicit vpr: ValidatedPayloadRequest[_]) = {
    connector.send(xml, new DateTime(), correlationId)
  }

}
