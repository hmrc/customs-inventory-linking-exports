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
import uk.gov.hmrc.customs.inventorylinking.export.model.ExportsConfig

import scalaz.syntax.traverse._

@Singleton
class ExportsConfigService @Inject()(configuration: Configuration,
                                     configValidationNel: ConfigValidationNelAdaptor,
                                     logger: ExportsLogger) extends ExportsConfig {

  private val exportsConfig =
    configValidationNel.service("api-subscription-fields").serviceUrl.map(ExportsConfigImpl.apply) fold(
    fail = { nel =>
      val errorMsg = nel.toList.mkString("\n", "\n", "")
      logger.errorWithoutRequestContext(errorMsg)
      throw new IllegalStateException(errorMsg)
    },
    succ = identity
  )

  val apiSubscriptionFieldsBaseUrl: String = exportsConfig.apiSubscriptionFieldsBaseUrl

  private case class ExportsConfigImpl(apiSubscriptionFieldsBaseUrl: String) extends ExportsConfig

}
