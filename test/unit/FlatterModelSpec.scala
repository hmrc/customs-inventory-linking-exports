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

package unit

import org.scalatest.mockito.MockitoSugar
import play.api.mvc.{Request, WrappedRequest}
import uk.gov.hmrc.customs.inventorylinking.export.model.{BadgeIdentifier, ConversationId, CorrelationId}
import uk.gov.hmrc.play.test.UnitSpec

import scala.xml.NodeSeq

/*
 * A little bit of upfront experiment/design to see what makes sense for the action builder pipeline model
 */
class FlatterModelSpec extends UnitSpec with MockitoSugar {

  sealed trait ApiVersion {
    val value: String
  }
  object VersionOne extends ApiVersion{
    override val value: String = "1.0"
  }
  object VersionTwo extends ApiVersion{
    override val value: String = "2.0"
  }

  case class ClientId(value: String)

  //-------------------------------------------------------------------------------
  // BEGIN traits for use by logging API
  //-------------------------------------------------------------------------------

  trait HasCorrelationIds {
    val conversationId: ConversationId
    val correlationId: CorrelationId
  }

  trait HasExtractedHeaders {
    val badgeIdentifier: BadgeIdentifier
    val requestedApiVersion: ApiVersion
    val clientId: ClientId
  }

  trait HasAuthData {
    val someAuthStuff: String //TODO
  }

  trait HasValidatedPayload {
    val validatedPayload: NodeSeq // This is useful as request.body.asXml returns an Option
  }

  //-------------------------------------------------------------------------------
  // END traits for use by logging API
  //-------------------------------------------------------------------------------


  //-------------------------------------------------------------------------------
  // BEGIN WrappedRequest classes
  //-------------------------------------------------------------------------------

  /*
   * We need multiple WrappedRequest classes to reflect changes in context during the request processing pipeline.
   *
   * There is some repetition in the WrappedRequest classes, but the benefit is we get a flat structure for our data
   * items, reducing the number of case classes and making their use much more convenient, rather than deeply nested stuff
   * eg `r.badgeIdentifier` vs `r.extractedHeaders.badgeIdentifier`
   */

  // Available after CorrelationIdsAction builder
  case class CorrelationIdsRequest[A](
                                       conversationId: ConversationId,
                                       correlationId: CorrelationId,
                                       request: Request[A]
                                     ) extends WrappedRequest[A](request) with HasCorrelationIds

  // Available after ValidatedHeadersAction builder
  case class ValidatedHeadersRequest[A](
    conversationId: ConversationId,
    correlationId: CorrelationId,
    badgeIdentifier: BadgeIdentifier,
    requestedApiVersion: ApiVersion,
    clientId: ClientId,
    request: Request[A]) extends WrappedRequest[A](request) with HasCorrelationIds with HasExtractedHeaders

  // Available after AuthoriseAction builder
  case class AuthorisedRequest[A](
    conversationId: ConversationId,
    correlationId: CorrelationId,
    badgeIdentifier: BadgeIdentifier,
    requestedApiVersion: ApiVersion,
    clientId: ClientId,
    someAuthStuff: String,
    request: Request[A]) extends WrappedRequest[A](request) with HasCorrelationIds with HasExtractedHeaders with HasAuthData

  // Available after ValidatedPayloadAction builder
  case class ValidatedPayloadRequest[A](
    conversationId: ConversationId,
    correlationId: CorrelationId,
    badgeIdentifier: BadgeIdentifier,
    requestedApiVersion: ApiVersion,
    clientId: ClientId,
    someAuthStuff: String,
    validatedPayload: NodeSeq,
    request: Request[A]) extends WrappedRequest[A](request) with HasCorrelationIds with HasExtractedHeaders with HasAuthData with HasValidatedPayload

  //-------------------------------------------------------------------------------
  // END WrappedRequest classes
  //-------------------------------------------------------------------------------

  trait SetUp {
    /*
     * Dummy logging API that uses our marker traits
     */

    def dummyLoggingApi1(implicit x: HasCorrelationIds with HasExtractedHeaders) = {
      println(s" ${x.conversationId} ${x.badgeIdentifier}")
    }

    def dummyLoggingApi2(implicit x: HasCorrelationIds with HasExtractedHeaders with HasValidatedPayload) = {
      println(s" ${x.conversationId} ${x.clientId} payload=${x.validatedPayload.toString}")
    }

    val mockRequest = mock[Request[_]]
    val conversationId = ConversationId("")
    private val badgeIdentifier = BadgeIdentifier("")
    private val correlationId = CorrelationId("")
    private val clientId = ClientId("")
    private val authStuff = ""
    val validatedHeadersRequest = ValidatedHeadersRequest(conversationId, correlationId, badgeIdentifier, VersionOne, clientId, mockRequest)
    val validatedPayloadRequest = ValidatedPayloadRequest(conversationId, correlationId, badgeIdentifier, VersionOne, clientId, authStuff, NodeSeq.Empty, mockRequest)
  }

  "model" should {
    "allow logging when ValidatedHeadersRequest is in scope" in new SetUp {
      implicit val r = validatedHeadersRequest

      dummyLoggingApi1
    }
    "allow logging when ValidatedPayloadRequest is in scope" in new SetUp {
      implicit val r = validatedPayloadRequest

      dummyLoggingApi2
    }
  }

}
