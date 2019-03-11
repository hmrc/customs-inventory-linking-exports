/*
 * Copyright 2019 HM Revenue & Customs
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

import model.ApiSubscriptionFields
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

  implicit class CorrelationIdsRequestOps[A](val cir: ConversationIdRequest[A]) extends AnyVal {
    def toValidatedHeadersRequest(eh: ExtractedHeaders): ValidatedHeadersRequest[A] = ValidatedHeadersRequest(
      cir.conversationId,
      eh.requestedApiVersion,
      eh.clientId,
      cir.request
    )
  }

  implicit class ValidatedHeadersRequestOps[A](val vhr: ValidatedHeadersRequest[A]) extends AnyVal {

    def toApiSubscriptionFieldsRequest(fields: ApiSubscriptionFields): ApiSubscriptionFieldsRequest[A] = ApiSubscriptionFieldsRequest(
        vhr.conversationId,
        vhr.requestedApiVersion,
        vhr.clientId,
        fields,
        vhr.request
      )
  }

  implicit class ApiSubscriptionFieldsRequestOps[A](val asf: ApiSubscriptionFieldsRequest[A]) extends AnyVal {

    def toCspAuthorisedRequest(pair: BadgeIdentifierEoriPair): AuthorisedRequest[A] = toAuthorisedRequest(Csp(pair))

    def toNonCspAuthorisedRequest(eori: Eori): AuthorisedRequest[A] = toAuthorisedRequest(NonCsp(eori))

    def toAuthorisedRequest(authorisedAs: AuthorisedAs): AuthorisedRequest[A] = AuthorisedRequest(
      asf.conversationId,
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
        ar.requestedApiVersion,
        ar.clientId,
        ar.apiSubscriptionFields,
        ar.authorisedAs,
        xmlBody,
        ar.request
      )
  }

}

trait HasConversationId {
  val conversationId: ConversationId
}

trait ExtractedHeaders {
  val requestedApiVersion: ApiVersion
  val clientId: ClientId
}

trait HasAuthorisedAs {
  val authorisedAs: AuthorisedAs
}

trait HasXmlBody {
  val xmlBody: NodeSeq
}

case class ExtractedHeadersImpl(
  requestedApiVersion: ApiVersion,
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
  request: Request[A]
) extends WrappedRequest[A](request) with HasConversationId

// Available after ValidateAndExtractHeadersAction action builder
case class ValidatedHeadersRequest[A](
  conversationId: ConversationId,
  requestedApiVersion: ApiVersion,
  clientId: ClientId,
  request: Request[A]
) extends WrappedRequest[A](request) with HasConversationId with ExtractedHeaders

// Available after ApiSubscriptionFieldsAction action builder
case class ApiSubscriptionFieldsRequest[A](
  conversationId: ConversationId,
  requestedApiVersion: ApiVersion,
  clientId: ClientId,
  apiSubscriptionFields: ApiSubscriptionFields,
  request: Request[A]
) extends WrappedRequest[A](request) with HasConversationId with ExtractedHeaders


// Available after AuthAction builder
case class AuthorisedRequest[A](
  conversationId: ConversationId,
  requestedApiVersion: ApiVersion,
  clientId: ClientId,
  apiSubscriptionFields: ApiSubscriptionFields,
  authorisedAs: AuthorisedAs,
  request: Request[A]
) extends WrappedRequest[A](request) with HasConversationId with ExtractedHeaders with HasAuthorisedAs

// Available after ValidatedPayloadAction builder
case class ValidatedPayloadRequest[A](
  conversationId: ConversationId,
  requestedApiVersion: ApiVersion,
  clientId: ClientId,
  apiSubscriptionFields: ApiSubscriptionFields,
  authorisedAs: AuthorisedAs,
  xmlBody: NodeSeq,
  request: Request[A]
) extends WrappedRequest[A](request) with HasConversationId with ExtractedHeaders with HasAuthorisedAs with HasXmlBody
