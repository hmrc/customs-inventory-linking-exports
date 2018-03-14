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

package util

import java.util.UUID

import com.google.inject.AbstractModule
import org.scalatest.mockito.MockitoSugar
import play.api.http.HeaderNames._
import play.api.http.MimeTypes
import play.api.inject.guice.GuiceableModule
import play.api.mvc.{AnyContentAsText, AnyContentAsXml}
import play.api.test.FakeRequest
import play.api.test.Helpers.POST
import uk.gov.hmrc.customs.inventorylinking.export.model.{BadgeIdentifier, ConversationId}
import uk.gov.hmrc.customs.inventorylinking.export.services.UuidService
import util.RequestHeaders._
import util.TestData._
import util.XMLTestData._

import scala.util.Random

object TestData {
  val conversationIdUuid: UUID = UUID.fromString(conversationIdValue)
  val conversationId = ConversationId(conversationIdUuid.toString)

  val validBadgeIdentifierValue = "BADGEID"
  val invalidBadgeIdentifierValue = "InvalidBadgeId"
  val invalidBadgeIdentifier: BadgeIdentifier = BadgeIdentifier(invalidBadgeIdentifierValue)
  val badgeIdentifier: BadgeIdentifier = BadgeIdentifier(validBadgeIdentifierValue)

  val validBasicAuthToken = s"Basic ${Random.alphanumeric.take(18).mkString}=="

  val cspBearerToken = "CSP-Bearer-Token"
  val nonCspBearerToken = "Software-House-Bearer-Token"

  type EmulatedServiceFailure = UnsupportedOperationException
  val emulatedServiceFailure = new EmulatedServiceFailure("Emulated service failure.")

  val xsdLocations = List(
    "/api/conf/1.0/schemas/exports/request/inventoryLinkingRequestExternal.xsd")

  object TestModule extends AbstractModule {
    def configure(): Unit = {
      bind(classOf[UuidService]) toInstance MockitoSugar.mock[UuidService]
    }

    def asGuiceableModule: GuiceableModule = GuiceableModule.guiceable(this)
  }

  lazy val ValidRequest: FakeRequest[AnyContentAsXml] = FakeRequest()
    .withHeaders(RequestHeaders.ACCEPT_HMRC_XML_HEADER,
      RequestHeaders.CONTENT_TYPE_HEADER,
      RequestHeaders.API_SUBSCRIPTION_FIELDS_ID_HEADER,
      RequestHeaders.X_BADGE_IDENTIFIER_HEADER)
    .withXmlBody(ValidInventoryLinkingMovementRequestXML)

  lazy val ValidRequestWithXClientIdHeader: FakeRequest[AnyContentAsXml] = ValidRequest
    .copyFakeRequest(headers =
      ValidRequest.headers.remove(API_SUBSCRIPTION_FIELDS_ID_NAME).add(X_CLIENT_ID_HEADER))

  lazy val NoClientIdIdHeaderRequest: FakeRequest[AnyContentAsXml] = ValidRequest
    .copyFakeRequest(headers = InvalidRequest.headers.remove(API_SUBSCRIPTION_FIELDS_ID_NAME))

  lazy val InvalidRequest: FakeRequest[AnyContentAsXml] = ValidRequest.withXmlBody(InvalidXML)

  lazy val InvalidRequestWith3Errors: FakeRequest[AnyContentAsXml] = InvalidRequest.withXmlBody(InvalidXMLWith3Errors)

  lazy val MalformedXmlRequest: FakeRequest[AnyContentAsText] = InvalidRequest.withTextBody("<xml><non_well_formed></xml>")

  lazy val NoAcceptHeaderRequest: FakeRequest[AnyContentAsXml] = InvalidRequest
    .copyFakeRequest(headers = InvalidRequest.headers.remove(ACCEPT))

  lazy val InvalidAcceptHeaderRequest: FakeRequest[AnyContentAsXml] = InvalidRequest
    .withHeaders(RequestHeaders.ACCEPT_HEADER_INVALID)

  lazy val InvalidContentTypeHeaderRequest: FakeRequest[AnyContentAsXml] = InvalidRequest
    .withHeaders(RequestHeaders.ACCEPT_HMRC_XML_HEADER, RequestHeaders.CONTENT_TYPE_HEADER_INVALID)

  lazy val InvalidXBadgeIdentifierHeaderRequest: FakeRequest[AnyContentAsXml] = InvalidRequest
    .withHeaders(RequestHeaders.ACCEPT_HMRC_XML_HEADER, RequestHeaders.X_BADGE_IDENTIFIER_HEADER_INVALID)

  lazy val NoXBadgeIdentifierHeaderRequest: FakeRequest[AnyContentAsXml] = ValidRequest
    .copyFakeRequest(headers = InvalidRequest.headers.remove(RequestHeaders.X_BADGE_IDENTIFIER_HEADER._1))

  lazy val NoApiSubscriptionFieldsIdHeaderRequest: FakeRequest[AnyContentAsXml] = ValidRequest
    .copyFakeRequest(headers = InvalidRequest.headers.remove(RequestHeaders.API_SUBSCRIPTION_FIELDS_ID_HEADER._1))

  implicit class FakeRequestOps[R](val fakeRequest: FakeRequest[R]) extends AnyVal {
    def fromCsp: FakeRequest[R] = fakeRequest.withHeaders(AUTHORIZATION -> s"Bearer $cspBearerToken")

    def fromNonCsp: FakeRequest[R] = fakeRequest.withHeaders(AUTHORIZATION -> s"Bearer $nonCspBearerToken")

    def postTo(endpoint: String): FakeRequest[R] = fakeRequest.copyFakeRequest(method = POST, uri = endpoint)
  }

}

object RequestHeaders {

  val BASIC_AUTH_HEADER: (String, String) = AUTHORIZATION -> validBasicAuthToken

  val CONVERSATION_ID_HEADER: (String, String) = "X-Conversation-ID" -> conversationIdValue

  val API_SUBSCRIPTION_FIELDS_ID_NAME = "api-subscription-fields-id"
  val API_SUBSCRIPTION_FIELDS_ID_HEADER: (String, String) = API_SUBSCRIPTION_FIELDS_ID_NAME -> ApiSubscriptionFieldsTestData.fieldsId

  val X_CLIENT_ID_NAME = "X-Client-ID"
  val X_CLIENT_ID_HEADER: (String, String) = X_CLIENT_ID_NAME -> ApiSubscriptionFieldsTestData.xClientId

  val X_BADGE_IDENTIFIER_NAME = "X-Badge-Identifier"
  val X_BADGE_IDENTIFIER_HEADER: (String, String) = X_BADGE_IDENTIFIER_NAME -> validBadgeIdentifierValue
  val X_BADGE_IDENTIFIER_HEADER_INVALID: (String, String) = X_BADGE_IDENTIFIER_NAME -> "SHORT"

  val CONTENT_TYPE_HEADER: (String, String) = CONTENT_TYPE -> MimeTypes.XML

  val CONTENT_TYPE_HEADER_INVALID: (String, String) = CONTENT_TYPE -> "somethinginvalid"

  val ACCEPT_HMRC_XML_HEADER: (String, String) = ACCEPT -> "application/vnd.hmrc.1.0+xml"

  val ACCEPT_HEADER_INVALID: (String, String) = ACCEPT -> MimeTypes.XML

  val ValidHeaders = Map(
    CONTENT_TYPE_HEADER,
    ACCEPT_HMRC_XML_HEADER,
    API_SUBSCRIPTION_FIELDS_ID_HEADER,
    X_BADGE_IDENTIFIER_HEADER)

  val LoggingHeaders = Seq(API_SUBSCRIPTION_FIELDS_ID_HEADER, X_CLIENT_ID_HEADER, CONVERSATION_ID_HEADER)
  val LoggingHeadersWithAuth = Seq(API_SUBSCRIPTION_FIELDS_ID_HEADER, X_CLIENT_ID_HEADER, CONVERSATION_ID_HEADER, BASIC_AUTH_HEADER)
}
