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

import org.joda.time.{DateTime, DateTimeZone}
import play.api.http.HeaderNames._
import play.api.http.MimeTypes
import play.api.mvc.AnyContentAsXml
import play.api.test.FakeRequest
import uk.gov.hmrc.customs.inventorylinking.export.model._
import uk.gov.hmrc.customs.inventorylinking.export.model.actionbuilders.ActionBuilderModelHelper._
import uk.gov.hmrc.customs.inventorylinking.export.model.actionbuilders._
import uk.gov.hmrc.customs.inventorylinking.export.services.{UniqueIdsService, UuidService}
import util.RequestHeaders._
import util.TestData._
import util.XMLTestData._

import scala.xml.Elem

object TestData {

  val conversationIdValue = "28e5aa87-3f89-4f12-b1b1-60f2b2de66f1"
  val conversationIdUuid: UUID = UUID.fromString(conversationIdValue)
  val conversationId = ConversationId(conversationIdUuid)

  val correlationIdValue = "e61f8eee-812c-4b8f-b193-06aedc60dca2"
  val correlationIdUuid: UUID = UUID.fromString(correlationIdValue)
  val correlationId = CorrelationId(correlationIdUuid)

  val validBadgeIdentifierValue = "BADGEID"
  val badgeIdentifier: BadgeIdentifier = BadgeIdentifier(validBadgeIdentifierValue)

  val declarantEoriValue = "ZZ123456789000"
  val declarantEori = Eori(declarantEoriValue)

  val badgeEoriPair = BadgeIdentifierEoriPair(badgeIdentifier, declarantEori)

  val dateTime: DateTime = DateTime.now(DateTimeZone.UTC)
  val dateTimeFormat = "YYYY-MM-dd'T'HH:mm:ss'Z'"

  val cspBearerToken = "CSP-Bearer-Token"
  val nonCspBearerToken = "Software-House-Bearer-Token"

  type EmulatedServiceFailure = UnsupportedOperationException
  val emulatedServiceFailure = new EmulatedServiceFailure("Emulated service failure.")

  val xsdLocations = List(
    "/api/conf/1.0/schemas/exports/request/inventoryLinkingRequestExternal.xsd")

  lazy val ValidRequest: FakeRequest[AnyContentAsXml] = FakeRequest("POST", "/")
    .withHeaders(
      X_CLIENT_ID_HEADER,
      ACCEPT_HMRC_XML_HEADER,
      CONTENT_TYPE_HEADER,
      API_SUBSCRIPTION_FIELDS_ID_HEADER,
      X_BADGE_IDENTIFIER_HEADER,
      X_EORI_IDENTIFIER_HEADER
    )
    .withXmlBody(ValidInventoryLinkingMovementRequestXML)

  lazy val InvalidRequest: FakeRequest[AnyContentAsXml] = ValidRequest.withXmlBody(InvalidXML)

  implicit class FakeRequestOps[R](val fakeRequest: FakeRequest[R]) extends AnyVal {
    def fromCsp: FakeRequest[R] = fakeRequest.withHeaders(AUTHORIZATION -> s"Bearer $cspBearerToken")

    def fromNonCsp: FakeRequest[R] = fakeRequest.withHeaders(AUTHORIZATION -> s"Bearer $nonCspBearerToken")
  }

  // note we can not mock service methods that return value classes - however IMHO it results in cleaner code (less mocking noise)
  val stubUniqueIdsService: UniqueIdsService = new UniqueIdsService(new UuidService()) {
    override def conversation: ConversationId = conversationId
    override def correlation: CorrelationId = correlationId
  }

  val TestXmlPayload: Elem = <foo>bar</foo>
  val TestFakeRequest: FakeRequest[AnyContentAsXml] = FakeRequest().withXmlBody(TestXmlPayload)

  def testFakeRequestWithBadgeId(badgeIdString: String = badgeIdentifier.value): FakeRequest[AnyContentAsXml] =
    FakeRequest().withXmlBody(TestXmlPayload).withHeaders(X_BADGE_IDENTIFIER_NAME -> badgeIdString)

  def testFakeRequestWithBadgeIdAndEoriId(badgeIdString: String = badgeIdentifier.value, eoriIdString: String = declarantEori.value): FakeRequest[AnyContentAsXml] =
    FakeRequest().withXmlBody(TestXmlPayload).withHeaders(X_BADGE_IDENTIFIER_NAME -> badgeIdString, X_EORI_IDENTIFIER_NAME -> eoriIdString)

  val TestConversationIdRequest = ConversationIdRequest(conversationId, TestFakeRequest)
  val TestExtractedHeaders = ExtractedHeadersImpl(VersionOne, ApiSubscriptionFieldsTestData.clientId)
  val TestValidatedHeadersRequest: ValidatedHeadersRequest[AnyContentAsXml] = TestConversationIdRequest.toValidatedHeadersRequest(TestExtractedHeaders)
  val TestCspAuthorisedRequest: AuthorisedRequest[AnyContentAsXml] = TestValidatedHeadersRequest.toCspAuthorisedRequest(badgeEoriPair)
  val TestCspValidatedPayloadRequest: ValidatedPayloadRequest[AnyContentAsXml] = TestValidatedHeadersRequest.toCspAuthorisedRequest(badgeEoriPair).toValidatedPayloadRequest(xmlBody = TestXmlPayload)
  val TestNonCspValidatedPayloadRequest: ValidatedPayloadRequest[AnyContentAsXml] = TestValidatedHeadersRequest.toNonCspAuthorisedRequest(declarantEori).toValidatedPayloadRequest(xmlBody = TestXmlPayload)
}

object RequestHeaders {

  val X_CONVERSATION_ID_NAME = "X-Conversation-ID"
  val X_CONVERSATION_ID_HEADER: (String, String) = X_CONVERSATION_ID_NAME -> conversationIdUuid.toString

  val API_SUBSCRIPTION_FIELDS_ID_NAME = "api-subscription-fields-id"
  val API_SUBSCRIPTION_FIELDS_ID_HEADER: (String, String) = API_SUBSCRIPTION_FIELDS_ID_NAME -> ApiSubscriptionFieldsTestData.fieldsId

  val X_CLIENT_ID_NAME = "X-Client-ID"
  val X_CLIENT_ID_HEADER: (String, String) = X_CLIENT_ID_NAME -> ApiSubscriptionFieldsTestData.xClientIdValue
  val X_CLIENT_ID_HEADER_INVALID: (String, String) = X_CLIENT_ID_NAME -> "This is not a UUID"

  val X_BADGE_IDENTIFIER_NAME = "X-Badge-Identifier"
  val X_BADGE_IDENTIFIER_HEADER: (String, String) = X_BADGE_IDENTIFIER_NAME -> validBadgeIdentifierValue
  val X_BADGE_IDENTIFIER_HEADER_INVALID: (String, String) = X_BADGE_IDENTIFIER_NAME -> "SHORT"

  val X_EORI_IDENTIFIER_NAME = "X-EORI-Identifier"
  val X_EORI_IDENTIFIER_HEADER: (String, String) = X_EORI_IDENTIFIER_NAME -> validBadgeIdentifierValue
  val X_EORI_IDENTIFIER_HEADER_INVALID: (String, String) = X_EORI_IDENTIFIER_NAME -> ""

  val CONTENT_TYPE_HEADER: (String, String) = CONTENT_TYPE -> (MimeTypes.XML + "; charset=utf-8")
  val CONTENT_TYPE_HEADER_INVALID: (String, String) = CONTENT_TYPE -> "somethinginvalid"

  val ACCEPT_HMRC_XML_HEADER: (String, String) = ACCEPT -> "application/vnd.hmrc.1.0+xml"

  val ACCEPT_HEADER_INVALID: (String, String) = ACCEPT -> MimeTypes.XML

  val ValidHeaders = Map(
    X_CLIENT_ID_HEADER,
    CONTENT_TYPE_HEADER,
    ACCEPT_HMRC_XML_HEADER,
    API_SUBSCRIPTION_FIELDS_ID_HEADER,
    X_BADGE_IDENTIFIER_HEADER)
}
