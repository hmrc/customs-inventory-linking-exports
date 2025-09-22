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

package uk.gov.hmrc.customs.inventorylinking.exports.services

import cats.implicits._
import uk.gov.hmrc.customs.inventorylinking.exports.config.CustomsValidatedNel
import uk.gov.hmrc.customs.inventorylinking.exports.config.ConfigValidatedNelAdaptor

import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.customs.inventorylinking.exports.model.ExportsShutterConfig
import uk.gov.hmrc.customs.inventorylinking.exports.logging.ExportsLogger
import uk.gov.hmrc.customs.inventorylinking.exports.model.{ExportsCircuitBreakerConfig, ExportsConfig}

@Singleton
class ExportsConfigService @Inject()(configValidatedNel: ConfigValidatedNelAdaptor,
                                     logger: ExportsLogger) {

  private val root = configValidatedNel.root

  private val apiSubscriptionFieldsService = configValidatedNel.service("api-subscription-fields")
  private val apiSubscriptionFieldsServiceUrlNel = apiSubscriptionFieldsService.serviceUrl
  private val customsMetricsService = configValidatedNel.service("customs-declarations-metrics")
  private val customsMetricsServiceUrlNel = customsMetricsService.serviceUrl
  private val v1ShutteredNel = root.maybeBoolean("shutter.v1")
  private val v2ShutteredNel = root.maybeBoolean("shutter.v2")

  private val numberOfCallsToTriggerStateChangeNel = root.int("circuitBreaker.numberOfCallsToTriggerStateChange")
  private val unavailablePeriodDurationInMillisNel = root.int("circuitBreaker.unavailablePeriodDurationInMillis")
  private val unstablePeriodDurationInMillisNel = root.int("circuitBreaker.unstablePeriodDurationInMillis")

  private val validatedExportsConfig: CustomsValidatedNel[ExportsConfig] = (apiSubscriptionFieldsServiceUrlNel, customsMetricsServiceUrlNel) mapN ExportsConfig

  private val validatedExportsShutterConfig: CustomsValidatedNel[ExportsShutterConfig] = (
    v1ShutteredNel, v2ShutteredNel
  ) mapN ExportsShutterConfig

  private val validatedExportsCircuitBreakerConfig: CustomsValidatedNel[ExportsCircuitBreakerConfig] = (
    numberOfCallsToTriggerStateChangeNel, unavailablePeriodDurationInMillisNel, unstablePeriodDurationInMillisNel
  ) mapN ExportsCircuitBreakerConfig

  private val exportsConfigHolder =
    (validatedExportsConfig, validatedExportsShutterConfig, validatedExportsCircuitBreakerConfig) mapN ExportsConfigHolder fold(
      { nel =>
        // error case exposes a NEL
        val errorMsg = nel.toList.mkString("\n", "\n", "")
        logger.errorWithoutRequestContext(errorMsg)
        throw new IllegalStateException(errorMsg)
      },
      identity
    )

  val exportsConfig: ExportsConfig = exportsConfigHolder.exportsConfig

  val exportsShutterConfig: ExportsShutterConfig = exportsConfigHolder.exportsShutterConfig

  val exportsCircuitBreakerConfig: ExportsCircuitBreakerConfig = exportsConfigHolder.exportsCircuitBreakerConfig

  private case class ExportsConfigHolder(exportsConfig: ExportsConfig,
                                         exportsShutterConfig: ExportsShutterConfig,
                                         exportsCircuitBreakerConfig: ExportsCircuitBreakerConfig)

}
