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

package util

import java.util.UUID
import play.api.http.HeaderNames._
import play.api.http.MimeTypes
import play.api.mvc.{AnyContentAsXml, Headers}
import play.api.test.FakeRequest
import uk.gov.hmrc.customs.inventorylinking.exports.model._
import uk.gov.hmrc.customs.inventorylinking.exports.model.actionbuilders.ActionBuilderModelHelper._
import uk.gov.hmrc.customs.inventorylinking.exports.model.actionbuilders._
import uk.gov.hmrc.customs.inventorylinking.exports.services.{UniqueIdsService, UuidService}
import util.CustomsMetricsTestData.{EventStart, UtcZoneId}
import util.RequestHeaders._
import util.TestData._
import util.XMLTestData._

import java.time.LocalDateTime
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
  val authenticatedEoriValue = "AA123456789000"
  val declarantEori = Eori.fromString(declarantEoriValue).get
  val authenticatedEori = Eori.fromString(authenticatedEoriValue).get

  val cspAuthorisedRequestWithEoriAndBadgeIdentifier = CspWithEoriAndBadgeId(declarantEori, badgeIdentifier)
  val cspAuthorisedRequestWithEori = CspWithEori(declarantEori)
  val cspAuthorisedRequestWithBadgeIdentifier = CspWithBadgeId(badgeIdentifier)

  val dateTime: LocalDateTime = LocalDateTime.now(UtcZoneId)
  val dateTimeFormat = "yyyy-MM-dd'T'HH:mm:ssX"

  val cspBearerToken = "CSP-Bearer-Token"
  val nonCspBearerToken = "Software-House-Bearer-Token"

  val allVersionsUnshuttered = ExportsShutterConfig(Some(false), Some(false))

  type EmulatedServiceFailure = UnsupportedOperationException
  val emulatedServiceFailure = new EmulatedServiceFailure("Emulated service failure.")

  val xsdLocations = List(
    "/api/conf/1.0/schemas/exports/inventoryLinkingRequestExternal.xsd")

  lazy val ValidRequestWithSubmitterHeader: FakeRequest[AnyContentAsXml] = FakeRequest("POST", "/")
    .withHeaders(
      X_CLIENT_ID_HEADER,
      ACCEPT_HMRC_XML_HEADER,
      CONTENT_TYPE_HEADER,
      API_SUBSCRIPTION_FIELDS_ID_HEADER,
      X_BADGE_IDENTIFIER_HEADER,
      X_SUBMITTER_IDENTIFIER_HEADER
    )
    .withXmlBody(ValidInventoryLinkingMovementRequestXML)

  lazy val ValidRequestWithSubmitterHeaderCamelCase: FakeRequest[AnyContentAsXml] = FakeRequest("POST", "/")
    .withHeaders(
      X_CLIENT_ID_HEADER,
      ACCEPT_HMRC_XML_HEADER,
      CONTENT_TYPE_HEADER,
      API_SUBSCRIPTION_FIELDS_ID_HEADER,
      X_BADGE_IDENTIFIER_HEADER,
      X_SUBMITTER_IDENTIFIER_HEADER_CAMEL_CASE
    )
    .withXmlBody(ValidInventoryLinkingMovementRequestXML)

  lazy val ValidRequestWithoutSubmitterHeader: FakeRequest[AnyContentAsXml] = FakeRequest("POST", "/")
    .withHeaders(
      X_CLIENT_ID_HEADER,
      ACCEPT_HMRC_XML_HEADER,
      CONTENT_TYPE_HEADER,
      API_SUBSCRIPTION_FIELDS_ID_HEADER,
      X_BADGE_IDENTIFIER_HEADER
    )
    .withXmlBody(ValidInventoryLinkingMovementRequestXML)

  lazy val InvalidRequest: FakeRequest[AnyContentAsXml] = ValidRequestWithSubmitterHeader.withXmlBody(InvalidXML)

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
  val TestFakeRequestWithV1Headers = FakeRequest().withXmlBody(TestXmlPayload).withHeaders(ValidHeaders.toSeq: _*)

  def testFakeRequestWithMaybeBadgeIdAndMaybeSubmitterId(maybeBadgeIdString: Option[String] = Some(badgeIdentifier.value), maybeSubmitterIdString: Option[String] = Some(declarantEori.value)): FakeRequest[AnyContentAsXml] = {
    val headers = Headers(maybeBadgeIdString.fold(("", ""))(badgeId => (X_BADGE_IDENTIFIER_NAME, badgeId)), maybeSubmitterIdString.fold(("", ""))(submitterId => (X_SUBMITTER_IDENTIFIER_NAME, submitterId)))
    FakeRequest().withXmlBody(TestXmlPayload).withHeaders(headers.remove("")) //better to not add empty string tuple in first place
  }

  val TestConversationIdRequest = ConversationIdRequest(conversationId, EventStart, TestFakeRequest)
  val TestConversationIdRequestWithV1Headers = ConversationIdRequest(conversationId, EventStart, TestFakeRequestWithV1Headers)
  val TestConversationIdRequestWithV2Headers = ConversationIdRequest(conversationId, EventStart, TestFakeRequestWithV1Headers.withHeaders(ACCEPT_HMRC_XML_HEADER_V2))
  val TestExtractedHeaders: ExtractedHeadersImpl = ExtractedHeadersImpl(ApiSubscriptionFieldsTestData.clientId)
  val TestValidatedHeadersRequest: ValidatedHeadersRequest[AnyContentAsXml] = TestConversationIdRequest.toApiVersionRequest(VersionOne).toValidatedHeadersRequest(TestExtractedHeaders)
  val TestValidatedHeadersRequestV2: ValidatedHeadersRequest[AnyContentAsXml] = TestConversationIdRequest.toApiVersionRequest(VersionTwo).toValidatedHeadersRequest(TestExtractedHeaders)
  val TestApiSubscriptionFieldsRequest: ApiSubscriptionFieldsRequest[AnyContentAsXml] = TestValidatedHeadersRequest.toApiSubscriptionFieldsRequest(ApiSubscriptionFieldsTestData.apiSubscriptionFields)
  val TestCspAuthorisedRequest: AuthorisedRequest[AnyContentAsXml] = TestApiSubscriptionFieldsRequest.toAuthorisedRequest(cspAuthorisedRequestWithEori)
  val TestCspValidatedPayloadRequestWithEoriAndBadgeIdentifier: ValidatedPayloadRequest[AnyContentAsXml] = TestApiSubscriptionFieldsRequest.toAuthorisedRequest(cspAuthorisedRequestWithEoriAndBadgeIdentifier).toValidatedPayloadRequest(xmlBody = TestXmlPayload)
  val TestCspValidatedPayloadRequestWithEori: ValidatedPayloadRequest[AnyContentAsXml] = TestApiSubscriptionFieldsRequest.toAuthorisedRequest(cspAuthorisedRequestWithEori).toValidatedPayloadRequest(xmlBody = TestXmlPayload)
  val TestCspValidatedPayloadRequestWithBadgeIdentifier: ValidatedPayloadRequest[AnyContentAsXml] = TestApiSubscriptionFieldsRequest.toAuthorisedRequest(cspAuthorisedRequestWithBadgeIdentifier).toValidatedPayloadRequest(xmlBody = TestXmlPayload)
  val TestNonCspValidatedPayloadRequest: ValidatedPayloadRequest[AnyContentAsXml] = TestApiSubscriptionFieldsRequest.toAuthorisedRequest(NonCsp(declarantEori)).toValidatedPayloadRequest(xmlBody = TestXmlPayload)
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

  val X_SUBMITTER_IDENTIFIER_NAME = "X-Submitter-Identifier"
  val X_SUBMITTER_IDENTIFIER_NAME_CAMEL_CASE = "X-Submitter-Identifier"

  val X_SUBMITTER_IDENTIFIER_HEADER: (String, String) = X_SUBMITTER_IDENTIFIER_NAME -> declarantEoriValue
  val X_SUBMITTER_IDENTIFIER_HEADER_CAMEL_CASE: (String, String) = X_SUBMITTER_IDENTIFIER_NAME_CAMEL_CASE -> declarantEoriValue
  val X_SUBMITTER_IDENTIFIER_HEADER_INVALID: (String, String) = X_SUBMITTER_IDENTIFIER_NAME -> "X_SUBMITTER_IDENTIFIER_TOO_LONG"

  val CONTENT_TYPE_HEADER: (String, String) = CONTENT_TYPE -> (MimeTypes.XML + "; charset=utf-8")
  val CONTENT_TYPE_HEADER_INVALID: (String, String) = CONTENT_TYPE -> "somethinginvalid"

  val ACCEPT_HMRC_XML_HEADER: (String, String) = ACCEPT -> "application/vnd.hmrc.1.0+xml"

  val ACCEPT_HMRC_XML_HEADER_V2: (String, String) = ACCEPT -> "application/vnd.hmrc.2.0+xml"

  val ACCEPT_HEADER_INVALID: (String, String) = ACCEPT -> MimeTypes.XML

  val ValidHeaders = Map(
    X_CLIENT_ID_HEADER,
    CONTENT_TYPE_HEADER,
    ACCEPT_HMRC_XML_HEADER,
    API_SUBSCRIPTION_FIELDS_ID_HEADER,
    X_BADGE_IDENTIFIER_HEADER)
}
