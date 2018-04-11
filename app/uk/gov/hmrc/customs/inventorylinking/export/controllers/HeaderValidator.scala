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
import uk.gov.hmrc.customs.api.common.logging.CdsLogger
import uk.gov.hmrc.customs.inventorylinking.export.model.HeaderConstants._
import uk.gov.hmrc.customs.inventorylinking.export.model.actionbuilders.{CorrelationIdsRequest, ExtractedHeadersImpl}
import uk.gov.hmrc.customs.inventorylinking.export.model.{BadgeIdentifier, ClientId, HeaderConstants, VersionOne}

@Singleton
class HeaderValidator @Inject()(logger: CdsLogger) {

  private lazy val validAcceptHeaders = Seq(Version1AcceptHeaderValue)
  private lazy val validContentTypeHeaders = Seq(MimeTypes.XML + ";charset=utf-8", MimeTypes.XML + "; charset=utf-8")
  private lazy val xClientIdRegex = "^\\S+$".r
  private lazy val xBadgeIdentifierRegex = "^[0-9A-Z]{6,12}$".r
  private val errorResponseBadgeIdentifierHeaderMissing = errorBadRequest(s"${HeaderConstants.XBadgeIdentifierHeaderName} header is missing or invalid")


  def validateHeaders[A](implicit correlationIdsRequest: CorrelationIdsRequest[A]): Either[ErrorResponse, ExtractedHeadersImpl] = {
    implicit val headers = correlationIdsRequest.headers

    def hasAccept = validateHeader(ACCEPT, validAcceptHeaders.contains(_), ErrorAcceptHeaderInvalid)

    def hasContentType = validateHeader(CONTENT_TYPE, s => validContentTypeHeaders.contains(s.toLowerCase()), ErrorContentTypeHeaderInvalid)

    def hasXClientId = validateHeader(XClientIdHeaderName, xClientIdRegex.findFirstIn(_).nonEmpty, ErrorInternalServerError)

    def maybeHasXBadgeIdentifier = validateOptionalHeader(XBadgeIdentifierHeaderName, xBadgeIdentifierRegex.findFirstIn(_).nonEmpty, errorResponseBadgeIdentifierHeaderMissing)

    val theResult: Either[ErrorResponse, ExtractedHeadersImpl] = for {
      acceptValue <- hasAccept.right
      contentTypeValue <- hasContentType.right
      xClientIdValue <- hasXClientId.right
      maybeXBadgeIdentifierValue <- maybeHasXBadgeIdentifier.right
    } yield {
      logger.debug(
        s"$ACCEPT header passed validation: $acceptValue\n"
      + s"$CONTENT_TYPE header passed validation: $contentTypeValue\n"
      + s"$XClientIdHeaderName header passed validation: $xClientIdValue\n"
      + s"$XBadgeIdentifierHeaderName header passed validation: $maybeXBadgeIdentifierValue")
      ExtractedHeadersImpl(maybeXBadgeIdentifierValue.map(s => BadgeIdentifier(s)), VersionOne, ClientId(xClientIdValue))
    }
    theResult
  }

  private def validateHeader(headerName: String, rule: String => Boolean, errorResponse: ErrorResponse)(implicit h: Headers): Either[ErrorResponse, String] = {
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

  private def validateOptionalHeader(headerName: String, rule: String => Boolean, errorResponse: ErrorResponse)(implicit h: Headers): Either[ErrorResponse, Option[String]] = {
    val left = Left(errorResponse)
    def leftForInvalidHeaderValue(headerName: String, value: String) = {
      logger.error(s"Error - header '$headerName' value '$value' is not valid")
      left
    }

    h.get(headerName).fold[Either[ErrorResponse, Option[String]]]{
      Right(None)
    }{
      v =>
        if (rule(v)) Right(Some(v)) else leftForInvalidHeaderValue(headerName, v)
    }
  }
}

