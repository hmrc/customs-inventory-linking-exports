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

package uk.gov.hmrc.customs.inventorylinking.exports.controllers

import play.api.http.HeaderNames._
import play.api.http.MimeTypes
import play.api.mvc.Headers
import uk.gov.hmrc.customs.inventorylinking.exports.controllers.ErrorResponse.{ErrorContentTypeHeaderInvalid, ErrorInternalServerError, errorBadRequest}
import uk.gov.hmrc.customs.inventorylinking.exports.controllers.CustomHeaderNames._
import uk.gov.hmrc.customs.inventorylinking.exports.logging.ExportsLogger
import uk.gov.hmrc.customs.inventorylinking.exports.model._
import uk.gov.hmrc.customs.inventorylinking.exports.model.actionbuilders.{ApiVersionRequest, ExtractedHeadersImpl, HasConversationId, HasRequest}

import javax.inject.{Inject, Singleton}

@Singleton
class HeaderValidator @Inject()(logger: ExportsLogger) {

  private lazy val validContentTypeHeaders = Seq(MimeTypes.XML + ";charset=utf-8", MimeTypes.XML + "; charset=utf-8")

  private lazy val xBadgeIdentifierRegex = "^[0-9A-Z]{6,12}$".r

  private val errorResponseBadgeIdentifierHeaderMissing = errorBadRequest(s"$XBadgeIdentifierHeaderName header is missing or invalid")
  private val errorResponseEoriIdentifierHeaderInvalid = errorBadRequest(s"$XSubmitterIdentifierHeaderName header is invalid")

  def validateHeaders[A](implicit apiVersionRequest: ApiVersionRequest[A]): Either[ErrorResponse, ExtractedHeadersImpl] = {
    implicit val headers: Headers = apiVersionRequest.headers

    def hasContentType: Either[ErrorResponse, String] = validateHeader(CONTENT_TYPE, s => validContentTypeHeaders.contains(s.toLowerCase()), ErrorContentTypeHeaderInvalid)

    def hasXClientId: Either[ErrorResponse, String] = validateHeader(XClientIdHeaderName, _.forall(!_.isWhitespace), ErrorInternalServerError)

    val theResult: Either[ErrorResponse, ExtractedHeadersImpl] = for {
      contentTypeValue <- hasContentType
      xClientIdValue <- hasXClientId
    } yield {
      logger.debug(
        s"\n$CONTENT_TYPE header passed validation: $contentTypeValue"
          + s"\n$XClientIdHeaderName header passed validation: $xClientIdValue")
      ExtractedHeadersImpl(ClientId(xClientIdValue))
    }
    theResult
  }

  private def validateHeader[A](headerName: String, rule: String => Boolean, errorResponse: ErrorResponse)(implicit apiVersionRequest: ApiVersionRequest[A], headers: Headers): Either[ErrorResponse, String] = {
    headers.get(headerName) match {
      case Some(value) if rule(value) =>
        Right(value)
      case Some(invalidValue) =>
        logger.error(s"Error - header '$headerName' value '$invalidValue' is not valid")
        Left(errorResponse)
      case None =>
        logger.error(s"Error - header '$headerName' not present")
        Left(errorResponse)
    }
  }

  def eitherBadgeIdentifier[A](implicit vhr: HasRequest[A] with HasConversationId): Either[ErrorResponse, Option[BadgeIdentifier]] = {
    val maybeBadgeId = vhr.request.headers.toSimpleMap.get(XBadgeIdentifierHeaderName)
    logger.debug(s"maybeBadgeId => $maybeBadgeId")

    maybeBadgeId match {
      case Some(badgeId) if xBadgeIdentifierRegex.pattern.matcher(badgeId).matches =>
        logger.info(s"$XBadgeIdentifierHeaderName header passed validation: $badgeId")
        Right(Some(BadgeIdentifier(badgeId)))
      case Some(_) =>
        logger.error(s"$XBadgeIdentifierHeaderName invalid or not present for CSP")
        Left(errorResponseBadgeIdentifierHeaderMissing)
      case None =>
        logger.info(s"$XBadgeIdentifierHeaderName header empty and allowed")
        Right(None)
    }
  }

  def eoriMustBeValidIfPresent[A](implicit vhr: HasRequest[A] with HasConversationId): Either[ErrorResponse, Option[Eori]] = {
    val maybeEoriHeader: Option[String] = vhr.request.headers.toSimpleMap.get(XSubmitterIdentifierHeaderName).filter(!_.isBlank)
    logger.debug(s"maybeEori => $maybeEoriHeader")

    maybeEoriHeader match {
      case Some(unvalidatedEori) =>
        Eori.fromString(unvalidatedEori) match {
          case Some(eori) =>
            logger.info(s"$XSubmitterIdentifierHeaderName header passed validation: $eori")
            Right(Some(eori))
          case None =>
            logger.error(s"$XSubmitterIdentifierHeaderName header is invalid for CSP: $unvalidatedEori")
            Left(errorResponseEoriIdentifierHeaderInvalid)
        }
      case None =>
        logger.info(s"$XSubmitterIdentifierHeaderName header not present or is empty")
        Right(None)
    }
  }
}
