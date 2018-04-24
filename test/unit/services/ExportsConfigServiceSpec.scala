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

package unit.services

import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.mockito.MockitoSugar
import play.api.{Configuration, Environment}
import uk.gov.hmrc.customs.api.common.config.ConfigValidationNelAdaptor
import uk.gov.hmrc.customs.inventorylinking.export.logging.ExportsLogger
import uk.gov.hmrc.customs.inventorylinking.export.model.{ApiDefinitionConfig, ExportsEnrolmentConfig, ExportsConfig}
import uk.gov.hmrc.customs.inventorylinking.export.services.ExportsConfigService
import uk.gov.hmrc.play.config.inject.ServicesConfig
import uk.gov.hmrc.play.test.UnitSpec

class ExportsConfigServiceSpec extends UnitSpec with MockitoSugar {
  private val validAppConfig: Config = ConfigFactory.parseString(
    """
      |inventory-linking.definition.api-context = some-api-context
      |inventory-linking.definition.api-scope = some-api-scope
      |inventory-linking.enrolment.name = some-enrolment-name
      |inventory-linking.enrolment.eori-identifier = some-eori
      |api.access.version-1.0.whitelistedApplicationIds.0 = someId-1
      |api.access.version-1.0.whitelistedApplicationIds.1 = someId-2
      |microservice.services.api-subscription-fields.host=some-host
      |microservice.services.api-subscription-fields.port=1111
      |microservice.services.api-subscription-fields.context=/some-context
    """.stripMargin)

  private val emptyAppConfig: Config = ConfigFactory.parseString("")

  private val validServicesConfiguration = Configuration(validAppConfig)
  private val emptyServicesConfiguration = Configuration(emptyAppConfig)
  private val mockExportsLogger = mock[ExportsLogger]

  private def customsConfigService(conf: Configuration): ExportsConfig =
    new ExportsConfigService(conf, new ConfigValidationNelAdaptor(testServicesConfig(conf), conf), mockExportsLogger)

  "ImportsConfigService" should {
    "return config as object model when configuration is valid" in {
      val configService = customsConfigService(validServicesConfiguration)

      configService.apiDefinitionConfig shouldBe ApiDefinitionConfig("some-api-context", "some-api-scope", Seq("someId-1", "someId-2"))
      configService.exportsEnrolmentConfig shouldBe ExportsEnrolmentConfig("some-enrolment-name", "some-eori")
      configService.apiSubscriptionFieldsBaseUrl shouldBe "http://some-host:1111/some-context"
    }

    "throw an exception when configuration is invalid, that contains AGGREGATED error messages" in {
      val expectedErrorMessage =
        "\nCould not find config key 'inventory-linking.definition.api-context'" +
        "\nCould not find config key 'inventory-linking.definition.api-scope'" +
        "\nCould not find config key 'inventory-linking.enrolment.name'" +
        "\nCould not find config key 'inventory-linking.enrolment.eori-identifier'" +
        "\nCould not find config api-subscription-fields.host" +
        "\nService configuration not found for key: api-subscription-fields.context"

      val caught = intercept[IllegalStateException](customsConfigService(emptyServicesConfiguration))

      caught.getMessage shouldBe expectedErrorMessage
    }
  }

  private def testServicesConfig(configuration: Configuration) = new ServicesConfig {
    override val runModeConfiguration: Configuration = configuration
    override val mode = play.api.Mode.Test

    override def environment: Environment = mock[Environment]
  }

}
