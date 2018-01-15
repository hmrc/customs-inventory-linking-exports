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
import uk.gov.hmrc.customs.inventorylinking.export.model.{ApiDefinitionConfig, CustomsEnrolmentConfig, OverridesConfig}
import uk.gov.hmrc.customs.inventorylinking.export.services.CustomsConfigService
import uk.gov.hmrc.play.config.inject.ServicesConfig
import uk.gov.hmrc.play.test.UnitSpec
import util.MockitoPassByNameHelper.PassByNameVerifier

class CustomsConfigServiceSpec extends UnitSpec with MockitoSugar {
  private val validAppConfig: Config = ConfigFactory.parseString(
    """
      |inventory-linking = {
      |   definition = {
      |     api-context = "customs/inventory-linking/exports"
      |     api-scope = "write:export"
      |   }
      |
      |   enrolment = {
      |     name = "HMRC-CUS-ORG"
      |     eori-identifier = "EORINumber"
      |   }
      |}
      |
      |api.access.version-1.0.whitelistedApplicationIds = [
      | "9c913363-1827-4df9-9651-2f6562eb6b78",
      | "200b01f9-ec3b-4ede-b263-61b626dde232"
      |]
      |
      |override = {
      | clientID = "200c01f9-ec3b-4ede-b263-61b626dde232"
      |}
    """.stripMargin)

  private val emptyAppConfig: Config = ConfigFactory.parseString("")

  private val validServicesConfiguration = Configuration(validAppConfig)
  private val emptyServicesConfiguration = Configuration(emptyAppConfig)

  private val mockExportsLogger = mock[ExportsLogger]

  private def customsConfigService(configuration: Configuration): CustomsConfigService =
    new CustomsConfigService(configuration, new ConfigValidationNelAdaptor(testServicesConfig(configuration), configuration), mockExportsLogger)

  "CustomsConfigService" should {
    "return config as object model when configuration is valid" in {
      val configService = customsConfigService(validServicesConfiguration)

      configService.apiDefinitionConfig shouldBe ApiDefinitionConfig(apiContext = "customs/inventory-linking/exports",
        apiScope = "write:export",
        whitelistedApplicationIds = Seq("9c913363-1827-4df9-9651-2f6562eb6b78", "200b01f9-ec3b-4ede-b263-61b626dde232"))
      configService.customsEnrolmentConfig shouldBe CustomsEnrolmentConfig("HMRC-CUS-ORG", "EORINumber")
      configService.overridesConfig shouldBe OverridesConfig(clientId = Some("200c01f9-ec3b-4ede-b263-61b626dde232"))
    }

    "throw an exception when configuration is invalid, that contains AGGREGATED error messages" in {
      val expectedErrorMessage =
        """
          |Could not find config key 'inventory-linking.definition.api-context'
          |Could not find config key 'inventory-linking.definition.api-scope'
          |Could not find config key 'inventory-linking.enrolment.name'
          |Could not find config key 'inventory-linking.enrolment.eori-identifier'""".stripMargin

      val caught = intercept[IllegalStateException](customsConfigService(emptyServicesConfiguration))
      caught.getMessage shouldBe expectedErrorMessage

      PassByNameVerifier(mockExportsLogger, "errorWithoutHeaderCarrier")
        .withByNameParam[String](expectedErrorMessage)
        .verify()
    }
  }

  private def testServicesConfig(configuration: Configuration): ServicesConfig = new ServicesConfig {
    override val runModeConfiguration = configuration
    override val mode = play.api.Mode.Test

    override def environment: Environment = mock[Environment]
  }

}
