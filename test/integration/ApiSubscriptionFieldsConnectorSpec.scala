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

import org.scalatest.BeforeAndAfterAll
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.AnyContentAsXml
import play.api.test.Helpers.*
import uk.gov.hmrc.customs.inventorylinking.exports.connectors.ApiSubscriptionFieldsConnector
import uk.gov.hmrc.customs.inventorylinking.exports.model.ApiSubscriptionFields
import uk.gov.hmrc.customs.inventorylinking.exports.model.actionbuilders.ValidatedHeadersRequest
import util.ExternalServicesConfig.{Host, Port}
import util.TestData.*
import util.*
import util.externalservices.{ApiSubscriptionFieldsService, ExportsExternalServicesConfig}

class ApiSubscriptionFieldsConnectorSpec extends IntegrationTestSpec with GuiceOneAppPerSuite with MockitoSugar
  with BeforeAndAfterAll with ApiSubscriptionFieldsService with ApiSubscriptionFieldsTestData {

  private lazy val connector = app.injector.instanceOf[ApiSubscriptionFieldsConnector]

  private implicit val vhr: ValidatedHeadersRequest[AnyContentAsXml] = TestValidatedHeadersRequest

  override protected def beforeAll(): Unit = {
    startMockServer()
  }

  override protected def beforeEach(): Unit = {
    resetMockServer()
  }

  override protected def afterAll(): Unit = {
    stopMockServer()
  }

  override implicit lazy val app: Application =
    GuiceApplicationBuilder().configure(Map(
      "microservice.services.api-subscription-fields.host" -> Host,
      "microservice.services.api-subscription-fields.port" -> Port,
      "microservice.services.api-subscription-fields.context" -> ExportsExternalServicesConfig.ApiSubscriptionFieldsContext,
      "metrics.enabled" -> false
    )).build()

  "ApiSubscriptionFieldsConnector" should {

    "make a correct request" in {
      setupGetSubscriptionFieldsToReturn()

      val response = getApiSubscriptionFields()

      response shouldBe Some(apiSubscriptionFields)
      verifyGetSubscriptionFieldsCalled()
    }

    "return a None when external service returns 404" in {
      setupGetSubscriptionFieldsToReturn(NOT_FOUND)
      val response = getApiSubscriptionFields()

      response shouldBe None
    }

    "return a None when external service returns 400" in {
      setupGetSubscriptionFieldsToReturn(BAD_REQUEST)
      val response = getApiSubscriptionFields()

      response shouldBe None
    }

    "return a None when external service returns any 4xx response other than 400" in {
      setupGetSubscriptionFieldsToReturn(FORBIDDEN)
      val response = getApiSubscriptionFields()

      response shouldBe None
    }

    "return a None when external service returns 500" in {
      setupGetSubscriptionFieldsToReturn(INTERNAL_SERVER_ERROR)
      val response = getApiSubscriptionFields()

      response shouldBe None
    }
  }

  private def getApiSubscriptionFields(): Option[ApiSubscriptionFields] = {
    await(connector.getSubscriptionFields(apiSubscriptionKey))
  }
}
