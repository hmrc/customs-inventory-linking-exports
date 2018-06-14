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
import scalaz.ValidationNel
import scalaz.syntax.apply._
import scalaz.syntax.traverse._
import uk.gov.hmrc.customs.api.common.config.ConfigValidationNelAdaptor
import uk.gov.hmrc.customs.inventorylinking.export.logging.ExportsLogger
import uk.gov.hmrc.customs.inventorylinking.export.model.{ExportsCircuitBreakerConfig, ExportsConfig}


@Singleton
class ExportsConfigService @Inject()(configuration: Configuration,
                                     configValidationNel: ConfigValidationNelAdaptor,
                                     logger: ExportsLogger) {

  private val root = configValidationNel.root

  private val apiSubscriptionFieldsService = configValidationNel.service("api-subscription-fields")
  private val apiSubscriptionFieldsServiceUrlNel = apiSubscriptionFieldsService.serviceUrl
  private val numberOfCallsToTriggerStateChangeNel = root.int("circuitBreaker.numberOfCallsToTriggerStateChange")
  private val unavailablePeriodDurationInMillisNel = root.int("circuitBreaker.unavailablePeriodDurationInMillis")
  private val unstablePeriodDurationInMillisNel = root.int("circuitBreaker.unstablePeriodDurationInMillis")

  private val validatedExportsConfig: ValidationNel[String, ExportsConfig] = apiSubscriptionFieldsServiceUrlNel.map(ExportsConfig.apply)

  private val validatedExportsCircuitBreakerConfig: ValidationNel[String, ExportsCircuitBreakerConfig] = (
    numberOfCallsToTriggerStateChangeNel |@| unavailablePeriodDurationInMillisNel |@| unstablePeriodDurationInMillisNel
    ) (ExportsCircuitBreakerConfig.apply)

  private val exportsConfigHolder =
    (validatedExportsConfig |@| validatedExportsCircuitBreakerConfig) (ExportsConfigHolder.apply) fold(
      fail = { nel =>
        // error case exposes nel (a NotEmptyList)
        val errorMsg = nel.toList.mkString("\n", "\n", "")
        logger.errorWithoutRequestContext(errorMsg)
        throw new IllegalStateException(errorMsg)
      },
      succ = identity
    )

  val exportsConfig: ExportsConfig = exportsConfigHolder.exportsConfig

  val exportsCircuitBreakerConfig: ExportsCircuitBreakerConfig = exportsConfigHolder.exportsCircuitBreakerConfig

  private case class ExportsConfigHolder(exportsConfig: ExportsConfig,
                                         exportsCircuitBreakerConfig: ExportsCircuitBreakerConfig)

}
