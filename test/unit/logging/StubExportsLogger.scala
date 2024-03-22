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

package unit.logging

import com.google.inject.Inject
import uk.gov.hmrc.customs.inventorylinking.export.logging.CdsLogger

import javax.inject.Singleton
import uk.gov.hmrc.customs.inventorylinking.export.logging.ExportsLogger
import uk.gov.hmrc.customs.inventorylinking.export.model.actionbuilders.{ConversationIdRequest, HasConversationId}

@Singleton
class StubExportsLogger @Inject()(logger: CdsLogger) extends ExportsLogger(logger) {

  override def debug(s: => String)(implicit r: HasConversationId): Unit =
    println(s)

  override def debug(s: => String, e: => Throwable)(implicit r: HasConversationId): Unit =
    println(s)

  override def debugFull(s: => String)(implicit r: ConversationIdRequest[_]): Unit =
    println(s)

  override def info(s: => String)(implicit r: HasConversationId): Unit =
    println(s)

  override def warn(s: => String)(implicit r: HasConversationId): Unit =
    println(s)

  override def error(s: => String, e: => Throwable)(implicit r: HasConversationId): Unit =
    println(s)

  override def error(s: => String)(implicit r: HasConversationId): Unit =
    println(s)

  override def errorWithoutRequestContext(s: => String): Unit =
    println(s)
}
