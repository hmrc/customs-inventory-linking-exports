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

package unit.services

import com.typesafe.config.{Config, ConfigFactory}
import org.scalatestplus.mockito.MockitoSugar
import play.api.Configuration
import uk.gov.hmrc.customs.inventorylinking.export.config.ConfigValidatedNelAdaptor
import uk.gov.hmrc.customs.inventorylinking.export.logging.ExportsLogger
import uk.gov.hmrc.customs.inventorylinking.export.services.ExportsConfigService
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import util.UnitSpec

class ExportsConfigServiceSpec extends UnitSpec with MockitoSugar {
  private val validAppConfig: Config = ConfigFactory.parseString(
    """
      |microservice.services.api-subscription-fields.host=some-host
      |microservice.services.api-subscription-fields.port=1111
      |microservice.services.api-subscription-fields.context=/some-context
      |microservice.services.customs-declarations-metrics.host=some-host
      |microservice.services.customs-declarations-metrics.port=1111
      |microservice.services.customs-declarations-metrics.context=/some-context
      |circuitBreaker.numberOfCallsToTriggerStateChange=5
      |circuitBreaker.unstablePeriodDurationInMillis=1000
      |circuitBreaker.unavailablePeriodDurationInMillis=1000
    """.stripMargin)

  private val emptyAppConfig: Config = ConfigFactory.parseString("")

  private val validServicesConfiguration = Configuration(validAppConfig)
  private val emptyServicesConfiguration = Configuration(emptyAppConfig)
  private val mockExportsLogger = mock[ExportsLogger]

  private def customsConfigService(conf: Configuration): ExportsConfigService =
    new ExportsConfigService(new ConfigValidatedNelAdaptor(testServicesConfig(conf), conf), mockExportsLogger)

  "ImportsConfigService" should {
    "return config as object model when configuration is valid" in {
      val configService = customsConfigService(validServicesConfiguration)

      configService.exportsConfig.apiSubscriptionFieldsBaseUrl shouldBe "http://some-host:1111/some-context"
      configService.exportsCircuitBreakerConfig.numberOfCallsToTriggerStateChange shouldBe 5
      configService.exportsCircuitBreakerConfig.unavailablePeriodDurationInMillis shouldBe 1000
      configService.exportsCircuitBreakerConfig.unstablePeriodDurationInMillis shouldBe 1000
    }

    "throw an exception when configuration is invalid, that contains AGGREGATED error messages" in {
      val expectedErrorMessage =
        """
          |Could not find config key 'api-subscription-fields.host'
          |Service configuration not found for key: api-subscription-fields.context
          |Could not find config key 'customs-declarations-metrics.host'
          |Service configuration not found for key: customs-declarations-metrics.context
          |Could not find config key 'circuitBreaker.numberOfCallsToTriggerStateChange'
          |Could not find config key 'circuitBreaker.unavailablePeriodDurationInMillis'
          |Could not find config key 'circuitBreaker.unstablePeriodDurationInMillis'""".stripMargin

      val caught = intercept[IllegalStateException](customsConfigService(emptyServicesConfiguration))

      caught.getMessage shouldBe expectedErrorMessage
    }
  }

  private def testServicesConfig(configuration: Configuration): ServicesConfig = new ServicesConfig(configuration) {}

}
