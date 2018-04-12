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

import play.api.http.HeaderNames.{ACCEPT, CONTENT_TYPE}
import uk.gov.hmrc.customs.inventorylinking.export.controllers.CustomHeaderNames._
import uk.gov.hmrc.customs.inventorylinking.export.model.actionbuilders.{CorrelationIds, CorrelationIdsRequest, ExtractedHeaders}

object LoggingHelper2 {
  private val headerSet = Set(CONTENT_TYPE, ACCEPT, X_CONVERSATION_ID_HEADER_NAME, X_CLIENT_ID_HEADER_NAME)

  def formatError(msg: String, r: CorrelationIds with ExtractedHeaders): String = {
    formatMessage(msg, r)
  }

  def formatWarn(msg: String, r: CorrelationIds with ExtractedHeaders): String = {
    formatMessage(msg, r)
  }

  def formatInfo(msg: String, r: CorrelationIds with ExtractedHeaders): String = {
    formatMessage(msg, r)
  }

  def formatDebug(msg: String, r: CorrelationIds with ExtractedHeaders): String = {
    formatMessage(msg, r)
  }

  def formatDebugFull(msg: String, r: CorrelationIdsRequest[_]): String = {
    formatMessageFull(msg, r)
  }

  private def formatMessage(msg: String, r: CorrelationIds with ExtractedHeaders): String = {
    s"${format(r)} $msg".trim
  }

  private def format(r: CorrelationIds with ExtractedHeaders): String = {
    s"[conversationId=${r.conversationId.value}][clientId=${r.clientId.value}][requestedApiVersion=${r.requestedApiVersion.value}]"
  }

  def formatMessageFull(msg: String, r: CorrelationIdsRequest[_]): String = {

    val filteredHeaders = r.request.headers.toSimpleMap.filter(keyValTuple => headerSet.contains(keyValTuple._1))

    s"[conversationId=${r.conversationId.value}] $msg headers=$filteredHeaders"
  }


}
