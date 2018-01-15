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

package uk.gov.hmrc.customs.inventorylinking.export.logging

import javax.inject.Singleton

import com.google.inject.Inject
import uk.gov.hmrc.customs.api.common.logging.CdsLogger
import uk.gov.hmrc.customs.inventorylinking.export.logging.LoggingHelper._
import uk.gov.hmrc.http.HeaderCarrier

@Singleton
class ExportsLogger @Inject()(logger: CdsLogger) {

  def debug(msg: => String, url: => String, payload: => String)(implicit hc: HeaderCarrier): Unit = logger.debug(formatDebug(msg, Some(url), Some(payload)))

  def debug(msg: => String, payload: => String)(implicit hc: HeaderCarrier): Unit = logger.debug(formatDebug(msg, None, Some(payload)))

  def debug(msg: => String)(implicit hc: HeaderCarrier): Unit = logger.debug(formatDebug(msg))

  def info(msg: => String)(implicit hc: HeaderCarrier): Unit = logger.info(formatInfo(msg))

  def warn(msg: => String)(implicit hc: HeaderCarrier): Unit = logger.warn(formatWarn(msg))

  def error(msg: => String, e: => Throwable)(implicit hc: HeaderCarrier): Unit = logger.error(formatError(msg), e)

  def error(msg: => String)(implicit hc: HeaderCarrier): Unit = logger.error(formatError(msg))

  def errorWithoutHeaderCarrier(msg: => String): Unit = logger.error(msg)
}
