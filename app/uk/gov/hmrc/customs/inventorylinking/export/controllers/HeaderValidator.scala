/*
 * Copyright 2020 HM Revenue & Customs
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
import uk.gov.hmrc.customs.inventorylinking.export.model.actionbuilders.{ApiVersionRequest, ExtractedHeadersImpl, HasConversationId, HasRequest}
import uk.gov.hmrc.customs.inventorylinking.export.model.{ClientId, _}

@Singleton
class HeaderValidator @Inject()(logger: ExportsLogger) {

  private lazy val validContentTypeHeaders = Seq(MimeTypes.XML + ";charset=utf-8", MimeTypes.XML + "; charset=utf-8")
  private lazy val xClientIdRegex = "^\\S+$".r

  private lazy val xBadgeIdentifierRegex = "^[0-9A-Z]{6,12}$".r
  private lazy val InvalidEoriHeaderRegex = "(^[\\s]*$|^.{18,}$)".r

  private val errorResponseBadgeIdentifierHeaderMissing = errorBadRequest(s"$XBadgeIdentifierHeaderName header is missing or invalid")
  private val errorResponseEoriIdentifierHeaderInvalid = errorBadRequest(s"$XSubmitterIdentifierHeaderName header is invalid")

  def validateHeaders[A](implicit apiVersionRequest: ApiVersionRequest[A]): Either[ErrorResponse, ExtractedHeadersImpl] = {
    implicit val headers: Headers = apiVersionRequest.headers

    def hasContentType: Either[ErrorResponse, String] = validateHeader(CONTENT_TYPE, s => validContentTypeHeaders.contains(s.toLowerCase()), ErrorContentTypeHeaderInvalid)

    def hasXClientId: Either[ErrorResponse, String] = validateHeader(XClientIdHeaderName, xClientIdRegex.findFirstIn(_).nonEmpty, ErrorInternalServerError)

    val theResult: Either[ErrorResponse, ExtractedHeadersImpl] = for {
      contentTypeValue <- hasContentType.right
      xClientIdValue <- hasXClientId.right
    } yield {
      logger.debug(
      s"\n$CONTENT_TYPE header passed validation: $contentTypeValue"
      + s"\n$XClientIdHeaderName header passed validation: $xClientIdValue")
      ExtractedHeadersImpl(ClientId(xClientIdValue))
    }
    theResult
  }

  private def validateHeader[A](headerName: String, rule: String => Boolean, errorResponse: ErrorResponse)(implicit apiVersionRequest: ApiVersionRequest[A], h: Headers): Either[ErrorResponse, String] = {
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

  def eitherBadgeIdentifier[A](allowNone: Boolean)(implicit vhr: HasRequest[A] with HasConversationId): Either[ErrorResponse, Option[BadgeIdentifier]] = {
    val maybeBadgeId: Option[String] = vhr.request.headers.toSimpleMap.get(XBadgeIdentifierHeaderName)

    if (allowNone && maybeBadgeId.isEmpty) {
      logger.info(s"$XBadgeIdentifierHeaderName header empty and allowed")
      Right(None)
    } else {
      maybeBadgeId.filter(xBadgeIdentifierRegex.findFirstIn(_).nonEmpty).map { b =>
        logger.info(s"$XBadgeIdentifierHeaderName header passed validation: $b")
        Some(BadgeIdentifier(b))
      }.toRight[ErrorResponse] {
        logger.error(s"$XBadgeIdentifierHeaderName invalid or not present for CSP")
        errorResponseBadgeIdentifierHeaderMissing
      }
    }
  }

  private def validEori(eori: String) = InvalidEoriHeaderRegex.findFirstIn(eori).isEmpty

  private def convertEmptyHeaderToNone(eori: Option[String]) = {
    if (eori.isDefined && eori.get.trim.isEmpty) {
      None
    } else {
      eori
    }
  }

  def eoriMustBeValidIfPresent[A](implicit vhr: HasRequest[A] with HasConversationId): Either[ErrorResponse, Option[Eori]] = {
    val maybeEoriHeader: Option[String] = vhr.request.headers.toSimpleMap.get(XSubmitterIdentifierHeaderName)
    logger.debug(s"maybeEori => $maybeEoriHeader")
    val maybeEori = convertEmptyHeaderToNone(maybeEoriHeader)

    maybeEori match {
      case Some(eori) => if (validEori(eori)) {
        logger.info(s"$XSubmitterIdentifierHeaderName header passed validation: $eori")
        Right(Some(Eori(eori)))
      } else {
        logger.error(s"$XSubmitterIdentifierHeaderName header is invalid for CSP: $eori")
        Left(errorResponseEoriIdentifierHeaderInvalid)
      }
      case None =>
        logger.info(s"$XSubmitterIdentifierHeaderName header not present or is empty")
        Right(None)
    }
  }
}
