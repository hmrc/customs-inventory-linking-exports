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

package uk.gov.hmrc.customs.inventorylinking.export.services

import javax.inject.{Inject, Singleton}

import play.api.Configuration
import uk.gov.hmrc.customs.api.common.config.ConfigValidationNelAdaptor
import uk.gov.hmrc.customs.inventorylinking.export.logging.ExportsLogger
import uk.gov.hmrc.customs.inventorylinking.export.model.{ApiDefinitionConfig, CustomsEnrolmentConfig, OverridesConfig}

import scalaz.ValidationNel
import scalaz.syntax.apply._
import scalaz.syntax.traverse._

@Singleton
class CustomsConfigService @Inject()(configuration: Configuration,
                                     configValidationNel: ConfigValidationNelAdaptor,
                                     logger: ExportsLogger) {

  private val root = configValidationNel.root

  private val validatedDefinitionConfig: ValidationNel[String, ApiDefinitionConfig] = (
    root.string("inventory-linking.definition.api-context") |@|
      root.string("inventory-linking.definition.api-scope") |@|
      getStringSeq("api.access.version-1.0.whitelistedApplicationIds")
    ) (ApiDefinitionConfig.apply)

  private val validatedCustomsEnrolmentConfig: ValidationNel[String, CustomsEnrolmentConfig] = (
    root.string("inventory-linking.enrolment.name") |@|
      root.string("inventory-linking.enrolment.eori-identifier")
    ) (CustomsEnrolmentConfig.apply)

  private val validatedOverridesConfig: ValidationNel[String, OverridesConfig] =
    getOptionalString("override.clientID") map OverridesConfig.apply

  private val customsConfigHolder = (
    validatedDefinitionConfig |@|
      validatedCustomsEnrolmentConfig |@|
      validatedOverridesConfig
    ) (CustomsConfigHolder.apply) fold(
    fail = { nel =>
      val errorMsg = nel.toList.mkString("\n", "\n", "")
      logger.errorWithoutHeaderCarrier(errorMsg)
      throw new IllegalStateException(errorMsg)
    },
    succ = identity
  )

  val apiDefinitionConfig: ApiDefinitionConfig = customsConfigHolder.apiDefinitionConfig

  val customsEnrolmentConfig: CustomsEnrolmentConfig = customsConfigHolder.customsEnrolmentConfig

  val overridesConfig: OverridesConfig = customsConfigHolder.overridesConfig

  private case class CustomsConfigHolder(apiDefinitionConfig: ApiDefinitionConfig,
                                         customsEnrolmentConfig: CustomsEnrolmentConfig,
                                         overridesConfig: OverridesConfig)

  private def getOptionalString(configKey: String): ValidationNel[String, Option[String]] =
    scalaz.Success(configuration.getString(configKey))

  private def getStringSeq(configKey: String): ValidationNel[String, Seq[String]] =
    scalaz.Success(configuration.getStringSeq(configKey).getOrElse(Nil))

}
