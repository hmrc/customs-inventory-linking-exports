/*
 * Copyright 2023 HM Revenue & Customs
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

package uk.gov.hmrc.customs.inventorylinking.export.model.actionbuilders

import java.time.ZonedDateTime

import play.api.mvc.{Request, Result, WrappedRequest}
import uk.gov.hmrc.customs.inventorylinking.export.controllers.CustomHeaderNames.XConversationIdHeaderName
import uk.gov.hmrc.customs.inventorylinking.export.model.{AuthorisedAs, _}

import scala.xml.NodeSeq

object ActionBuilderModelHelper {

  implicit class AddConversationId(val result: Result) extends AnyVal {
    def withConversationId(implicit c: HasConversationId): Result = {
      result.withHeaders(XConversationIdHeaderName -> c.conversationId.toString)
    }
  }

  implicit class ConversationIdRequestOps[A](val cir: ConversationIdRequest[A]) extends AnyVal {
    def toApiVersionRequest(apiVersion: ApiVersion): ApiVersionRequest[A] = ApiVersionRequest(
      cir.conversationId,
      cir.start,
      apiVersion,
      cir.request
    )
  }

  implicit class ApiVersionRequestOps[A](val avr: ApiVersionRequest[A]) extends AnyVal {
    def toValidatedHeadersRequest(eh: ExtractedHeaders): ValidatedHeadersRequest[A] = ValidatedHeadersRequest(
      avr.conversationId,
      avr.start,
      avr.requestedApiVersion,
      eh.clientId,
      avr.request
    )
  }

  implicit class ValidatedHeadersRequestOps[A](val vhr: ValidatedHeadersRequest[A]) extends AnyVal {

    def toApiSubscriptionFieldsRequest(fields: ApiSubscriptionFields): ApiSubscriptionFieldsRequest[A] = ApiSubscriptionFieldsRequest(
        vhr.conversationId,
        vhr.start,
        vhr.requestedApiVersion,
        vhr.clientId,
        fields,
        vhr.request
      )
  }

  implicit class ApiSubscriptionFieldsRequestOps[A](val asf: ApiSubscriptionFieldsRequest[A]) extends AnyVal {

    def toCspAuthorisedRequest(a: AuthorisedAsCsp): AuthorisedRequest[A] = toAuthorisedRequest(a)

    def toNonCspAuthorisedRequest(eori: Eori): AuthorisedRequest[A] = toAuthorisedRequest(NonCsp(eori))

    def toAuthorisedRequest(authorisedAs: AuthorisedAs): AuthorisedRequest[A] = AuthorisedRequest(
      asf.conversationId,
      asf.start,
      asf.requestedApiVersion,
      asf.clientId,
      asf.apiSubscriptionFields,
      authorisedAs,
      asf.request
    )
  }

  implicit class AuthorisedRequestOps[A](val ar: AuthorisedRequest[A]) extends AnyVal {
    def toValidatedPayloadRequest(xmlBody: NodeSeq): ValidatedPayloadRequest[A] = ValidatedPayloadRequest(
        ar.conversationId,
        ar.start,
        ar.requestedApiVersion,
        ar.clientId,
        ar.apiSubscriptionFields,
        ar.authorisedAs,
        xmlBody,
        ar.request
      )
  }

}

trait HasRequest[A] {
  val request: Request[A]
}

trait HasConversationId {
  val conversationId: ConversationId
}

trait HasApiVersion {
  val requestedApiVersion: ApiVersion
}

trait ExtractedHeaders {
  val clientId: ClientId
}

trait HasAuthorisedAs {
  val authorisedAs: AuthorisedAs
}

trait HasXmlBody {
  val xmlBody: NodeSeq
}

case class ExtractedHeadersImpl(
  clientId: ClientId
) extends ExtractedHeaders

/*
 * We need multiple WrappedRequest classes to reflect additions to context during the request processing pipeline.
 *
 * There is some repetition in the WrappedRequest classes, but the benefit is we get a flat structure for our data
 * items, reducing the number of case classes and making their use much more convenient, rather than deeply nested stuff
 * eg `r.badgeIdentifier` vs `r.requestData.badgeIdentifier`
 */

// Available after ConversationIdAction action builder
case class ConversationIdRequest[A](
  conversationId: ConversationId,
  start: ZonedDateTime,
  request: Request[A]
) extends WrappedRequest[A](request) with HasRequest[A] with HasConversationId

// Available after ShutterCheckAction
case class ApiVersionRequest[A](conversationId: ConversationId,
                                start: ZonedDateTime,
                                requestedApiVersion: ApiVersion,
                                request: Request[A]
) extends WrappedRequest[A](request) with HasRequest[A] with HasConversationId with HasApiVersion

// Available after ValidateAndExtractHeadersAction action builder
case class ValidatedHeadersRequest[A](
  conversationId: ConversationId,
  start: ZonedDateTime,
  requestedApiVersion: ApiVersion,
  clientId: ClientId,
  request: Request[A]
) extends WrappedRequest[A](request) with HasRequest[A] with HasConversationId with HasApiVersion with ExtractedHeaders

// Available after ApiSubscriptionFieldsAction action builder
case class ApiSubscriptionFieldsRequest[A](
  conversationId: ConversationId,
  start: ZonedDateTime,
  requestedApiVersion: ApiVersion,
  clientId: ClientId,
  apiSubscriptionFields: ApiSubscriptionFields,
  request: Request[A]
) extends WrappedRequest[A](request) with HasRequest[A] with HasConversationId with HasApiVersion with ExtractedHeaders

// Available after AuthAction builder
case class AuthorisedRequest[A](
  conversationId: ConversationId,
  start: ZonedDateTime,
  requestedApiVersion: ApiVersion,
  clientId: ClientId,
  apiSubscriptionFields: ApiSubscriptionFields,
  authorisedAs: AuthorisedAs,
  request: Request[A]
) extends WrappedRequest[A](request) with HasConversationId with HasApiVersion with ExtractedHeaders with HasAuthorisedAs

// Available after ValidatedPayloadAction builder
case class ValidatedPayloadRequest[A](
  conversationId: ConversationId,
  start: ZonedDateTime,
  requestedApiVersion: ApiVersion,
  clientId: ClientId,
  apiSubscriptionFields: ApiSubscriptionFields,
  authorisedAs: AuthorisedAs,
  xmlBody: NodeSeq,
  request: Request[A]
) extends WrappedRequest[A](request) with HasConversationId with HasApiVersion with ExtractedHeaders with HasAuthorisedAs with HasXmlBody
