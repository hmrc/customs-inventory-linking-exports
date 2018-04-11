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

package uk.gov.hmrc.customs.inventorylinking.export.model

object HeaderConstants {

  val XClientIdHeaderName: String = "X-Client-ID"
  val XConversationIdHeaderName: String = "X-Conversation-ID"
  val XForwardedHostHeaderName: String = "X-Forwarded-Host"
  val XCorrelationIdHeaderName: String = "X-Correlation-ID"
  val XBadgeIdentifierHeaderName: String = "X-Badge-Identifier"

  val Version1AcceptHeaderValue = "application/vnd.hmrc.1.0+xml"
}
