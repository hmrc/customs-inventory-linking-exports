/*
 * Copyright 2019 HM Revenue & Customs
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

import cats.implicits._
import javax.inject.{Inject, Singleton}
import play.api.Configuration
import uk.gov.hmrc.customs.api.common.config.{ConfigValidatedNelAdaptor, CustomsValidatedNel}
import uk.gov.hmrc.customs.inventorylinking.export.logging.ExportsLogger
import uk.gov.hmrc.customs.inventorylinking.export.model.{ExportsCircuitBreakerConfig, ExportsConfig}


@Singleton
class ExportsConfigService @Inject()(configuration: Configuration,
                                     configValidatedNel: ConfigValidatedNelAdaptor,
                                     logger: ExportsLogger) {

  private val root = configValidatedNel.root
  private val whiteListedCspApplicationIds = root.stringSeq("api.access.version-1.0.whitelistedApplicationIds")

  private val apiSubscriptionFieldsService = configValidatedNel.service("api-subscription-fields")
  private val apiSubscriptionFieldsServiceUrlNel = apiSubscriptionFieldsService.serviceUrl
  private val numberOfCallsToTriggerStateChangeNel = root.int("circuitBreaker.numberOfCallsToTriggerStateChange")
  private val unavailablePeriodDurationInMillisNel = root.int("circuitBreaker.unavailablePeriodDurationInMillis")
  private val unstablePeriodDurationInMillisNel = root.int("circuitBreaker.unstablePeriodDurationInMillis")

  private val validatedExportsConfig: CustomsValidatedNel[ExportsConfig] = (
    whiteListedCspApplicationIds, apiSubscriptionFieldsServiceUrlNel
  ) mapN ExportsConfig.apply


  private val validatedExportsCircuitBreakerConfig: CustomsValidatedNel[ExportsCircuitBreakerConfig] = (
    numberOfCallsToTriggerStateChangeNel, unavailablePeriodDurationInMillisNel, unstablePeriodDurationInMillisNel
  ) mapN ExportsCircuitBreakerConfig

  private val exportsConfigHolder =
    (validatedExportsConfig, validatedExportsCircuitBreakerConfig) mapN ExportsConfigHolder fold(
      // error
      { nel =>
        // error case exposes nel (a NotEmptyList)
        val errorMsg = nel.toList.mkString("\n", "\n", "")
        logger.errorWithoutRequestContext(errorMsg)
        throw new IllegalStateException(errorMsg)
      },
      // success
      identity
    )

  val exportsConfig: ExportsConfig = exportsConfigHolder.exportsConfig

  val exportsCircuitBreakerConfig: ExportsCircuitBreakerConfig = exportsConfigHolder.exportsCircuitBreakerConfig

  private case class ExportsConfigHolder(exportsConfig: ExportsConfig,
                                         exportsCircuitBreakerConfig: ExportsCircuitBreakerConfig)

}
