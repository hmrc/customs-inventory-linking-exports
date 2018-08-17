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

package uk.gov.hmrc.customs.inventorylinking.export.controllers

import javax.inject.{Inject, Singleton}
import play.api.http.HeaderNames._
import play.api.http.MimeTypes
import play.api.mvc.Headers
import uk.gov.hmrc.customs.api.common.controllers.ErrorResponse
import uk.gov.hmrc.customs.api.common.controllers.ErrorResponse._
import uk.gov.hmrc.customs.inventorylinking.export.controllers.CustomHeaderNames._
import uk.gov.hmrc.customs.inventorylinking.export.logging.ExportsLogger
import uk.gov.hmrc.customs.inventorylinking.export.model.actionbuilders.{ConversationIdRequest, ExtractedHeadersImpl}
import uk.gov.hmrc.customs.inventorylinking.export.model.{ClientId, VersionOne}

@Singleton
class HeaderValidator @Inject()(logger: ExportsLogger) {

  private lazy val validAcceptHeaders = Seq("application/vnd.hmrc.1.0+xml")
  private lazy val validContentTypeHeaders = Seq(MimeTypes.XML + ";charset=utf-8", MimeTypes.XML + "; charset=utf-8")
  private lazy val xClientIdRegex = "^\\S+$".r

  def validateHeaders[A](implicit conversationIdRequest: ConversationIdRequest[A]): Either[ErrorResponse, ExtractedHeadersImpl] = {
    implicit val headers: Headers = conversationIdRequest.headers

    def hasAccept: Either[ErrorResponse, String] = validateHeader(ACCEPT, validAcceptHeaders.contains, ErrorAcceptHeaderInvalid)

    def hasContentType: Either[ErrorResponse, String] = validateHeader(CONTENT_TYPE, s => validContentTypeHeaders.contains(s.toLowerCase()), ErrorContentTypeHeaderInvalid)

    def hasXClientId: Either[ErrorResponse, String] = validateHeader(XClientIdHeaderName, xClientIdRegex.findFirstIn(_).nonEmpty, ErrorInternalServerError)

    val theResult: Either[ErrorResponse, ExtractedHeadersImpl] = for {
      acceptValue <- hasAccept.right
      contentTypeValue <- hasContentType.right
      xClientIdValue <- hasXClientId.right
    } yield {
      logger.debug(
        s"\n$ACCEPT header passed validation: $acceptValue"
      + s"\n$CONTENT_TYPE header passed validation: $contentTypeValue"
      + s"\n$XClientIdHeaderName header passed validation: $xClientIdValue")
      ExtractedHeadersImpl(VersionOne, ClientId(xClientIdValue))
    }
    theResult
  }

  private def validateHeader[A](headerName: String, rule: String => Boolean, errorResponse: ErrorResponse)(implicit conversationIdRequest: ConversationIdRequest[A], h: Headers): Either[ErrorResponse, String] = {
    val left = Left(errorResponse)
    def leftWithLog(headerName: String) = {
      logger.error(s"Error - header '$headerName' not present")
      left
    }
    def leftWithLogContainingValue(headerName: String, value: String) = {
      logger.error(s"Error - header '$headerName' value '$value' is not valid")
      left
    }

    h.get(headerName).fold[Either[ErrorResponse, String]]{
      leftWithLog(headerName)
    }{
      v =>
        if (rule(v)) Right(v) else leftWithLogContainingValue(headerName, v)
    }
  }

}

