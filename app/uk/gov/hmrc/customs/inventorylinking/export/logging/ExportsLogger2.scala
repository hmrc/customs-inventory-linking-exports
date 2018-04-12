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
import uk.gov.hmrc.customs.inventorylinking.export.logging.LoggingHelper2._
import uk.gov.hmrc.customs.inventorylinking.export.model.actionbuilders.{CorrelationIds, CorrelationIdsRequest, ExtractedHeaders}

@Singleton
class ExportsLogger2 @Inject()(logger: CdsLogger) {

  def debug(s: => String)(implicit r: CorrelationIds with ExtractedHeaders): Unit = logger.debug(formatDebug(s, r))
  def debug(s: => String, e: => Throwable)(implicit r: CorrelationIds with ExtractedHeaders): Unit = logger.debug(formatDebug(s, r), e)
  def debugFull(s: => String)(implicit r: CorrelationIdsRequest[_]): Unit = logger.debug(formatDebugFull(s, r))
  def info(s: => String)(implicit r: CorrelationIds with ExtractedHeaders): Unit = logger.info(formatInfo(s, r))
  def warn(s: => String)(implicit r: CorrelationIds with ExtractedHeaders): Unit = logger.warn(formatWarn(s, r))
  def error(s: => String)(implicit r: CorrelationIds with ExtractedHeaders): Unit = logger.error(formatError(s, r))
}