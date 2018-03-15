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

import uk.gov.hmrc.customs.api.common.controllers.ErrorResponse.{ErrorAcceptHeaderInvalid, ErrorContentTypeHeaderInvalid, errorBadRequest}
import play.api.http.{HeaderNames, MimeTypes}
import play.api.mvc.{ActionBuilder, Request, Result, Results}

import scala.concurrent.Future

trait HeaderValidator extends Results {

  type Validation = Option[String] => Boolean

  lazy val XBadgeIdentifier = "X-Badge-Identifier"
  private lazy val validAcceptHeaders = Seq("application/vnd.hmrc.1.0+xml")
  private lazy val validContentTypeHeaders = Seq(MimeTypes.XML, MimeTypes.XML + "; charset=utf-8")
  lazy val ErrorResponseBadgeIdentifierHeaderMissing = errorBadRequest("X-Badge-Identifier header is missing or invalid")

  val acceptHeaderValidation: Validation = _ exists validAcceptHeaders.contains
  val contentTypeValidation: Validation = _ exists (header => validContentTypeHeaders.contains(header.toLowerCase))
  val badgeIdentifierValidation: Validation = _.fold(true)(header => "^[0-9A-Z]{6,12}$".r.findFirstIn(header).isDefined)

  def validateAccept(rules: Validation): ActionBuilder[Request] = new ActionBuilder[Request] {
    def invokeBlock[A](request: Request[A], block: (Request[A]) => Future[Result]): Future[Result] = {
      val headerIsValid = rules(request.headers.get(HeaderNames.ACCEPT))
      getResponse(request, block, headerIsValid, ErrorAcceptHeaderInvalid.XmlResult)
    }
  }

  def validateContentType(rules: Validation): ActionBuilder[Request] = new ActionBuilder[Request] {
    def invokeBlock[A](request: Request[A], block: (Request[A]) => Future[Result]): Future[Result] = {
      val headerIsValid = rules(request.headers.get(HeaderNames.CONTENT_TYPE))
      getResponse(request, block, headerIsValid, ErrorContentTypeHeaderInvalid.XmlResult)
    }
  }

  def validateXBadgeIdentifier(rules: Validation): ActionBuilder[Request] = new ActionBuilder[Request] {
    def invokeBlock[A](request: Request[A], block: (Request[A]) => Future[Result]): Future[Result] = {
      val headerIsValid = rules(request.headers.get(XBadgeIdentifier))
      getResponse(request, block, headerIsValid, ErrorResponseBadgeIdentifierHeaderMissing.XmlResult)
    }
  }

  private def getResponse[A](request: Request[A], block: (Request[A]) => Future[Result], headerIsValid: Boolean, error: => Result) = {
    if (headerIsValid) {
      block(request)
    } else {
      Future.successful(error)
    }
  }

}
