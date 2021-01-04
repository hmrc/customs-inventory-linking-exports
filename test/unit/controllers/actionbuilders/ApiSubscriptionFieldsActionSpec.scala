/*
 * Copyright 2021 HM Revenue & Customs
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

package unit.controllers.actionbuilders

import java.net.URLEncoder

import org.mockito.ArgumentMatchers.{eq => ameq, _}
import org.mockito.Mockito._
import org.scalatestplus.mockito.MockitoSugar
import play.api.mvc.{AnyContentAsXml, Result}
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.customs.api.common.controllers.ErrorResponse
import uk.gov.hmrc.customs.inventorylinking.export.connectors.ApiSubscriptionFieldsConnector
import uk.gov.hmrc.customs.inventorylinking.export.controllers.actionbuilders.ApiSubscriptionFieldsAction
import uk.gov.hmrc.customs.inventorylinking.export.logging.ExportsLogger
import uk.gov.hmrc.customs.inventorylinking.export.model.actionbuilders.ActionBuilderModelHelper._
import uk.gov.hmrc.customs.inventorylinking.export.model.actionbuilders.{ApiSubscriptionFieldsRequest, ApiVersionRequest, ValidatedHeadersRequest}
import uk.gov.hmrc.customs.inventorylinking.export.model.{ApiSubscriptionKey, VersionOne}
import util.CustomsMetricsTestData.EventStart
import util.TestData.{TestExtractedHeaders, TestValidatedHeadersRequestV2, conversationId, declarantEori, testFakeRequestWithMaybeBadgeIdAndMaybeSubmitterId}
import util.{ApiSubscriptionFieldsTestData, TestData, UnitSpec}

import scala.concurrent.{ExecutionContext, Future}

class ApiSubscriptionFieldsActionSpec extends UnitSpec with MockitoSugar {
  private val apiContextEncoded = URLEncoder.encode("customs/inventory-linking/exports", "UTF-8")
  private implicit val ec: ExecutionContext = Helpers.stubControllerComponents().executionContext

  trait SetUp {
    private[ApiSubscriptionFieldsActionSpec] val connector = mock[ApiSubscriptionFieldsConnector]
    private[ApiSubscriptionFieldsActionSpec] val logger = mock[ExportsLogger]
    private[ApiSubscriptionFieldsActionSpec] val service = new ApiSubscriptionFieldsAction(connector, logger)

    private[ApiSubscriptionFieldsActionSpec] def vhr(request: FakeRequest[AnyContentAsXml]): ValidatedHeadersRequest[AnyContentAsXml] = {
      ApiVersionRequest(conversationId, EventStart, VersionOne, request)
        .toValidatedHeadersRequest(TestExtractedHeaders)
    }

    private[ApiSubscriptionFieldsActionSpec] val vhr: ValidatedHeadersRequest[AnyContentAsXml] =
      vhr(testFakeRequestWithMaybeBadgeIdAndMaybeSubmitterId(maybeSubmitterIdString = Some(declarantEori.value), maybeBadgeIdString = None))
    private[ApiSubscriptionFieldsActionSpec] val key = ApiSubscriptionKey(vhr.clientId, apiContextEncoded, VersionOne)
  }

  "ApiSubscriptionFieldsAction" should {
    "get Right of fields for a valid request" in new SetUp {
      when(connector.getSubscriptionFields(any[ApiSubscriptionKey])(ameq(vhr))).thenReturn(Future.successful(ApiSubscriptionFieldsTestData.apiSubscriptionFields))

      val Right(actual: ApiSubscriptionFieldsRequest[AnyContentAsXml]) = await(service.refine(vhr))

      actual shouldBe vhr.toApiSubscriptionFieldsRequest(ApiSubscriptionFieldsTestData.apiSubscriptionFields)
    }

    "ensure that correct version is used in call to subscription service" in new SetUp {
      when(connector.getSubscriptionFields(any[ApiSubscriptionKey])(ameq(TestValidatedHeadersRequestV2))).thenReturn(Future.successful(ApiSubscriptionFieldsTestData.apiSubscriptionFields))

      val Right(actual: ApiSubscriptionFieldsRequest[AnyContentAsXml]) = await(service.refine(TestValidatedHeadersRequestV2))

      actual shouldBe TestValidatedHeadersRequestV2.toApiSubscriptionFieldsRequest(ApiSubscriptionFieldsTestData.apiSubscriptionFields)
    }

    "return Left of a 500 error result when connector throws an exception" in new SetUp {
      when(connector.getSubscriptionFields(ameq(key))(ameq(vhr))).thenReturn(Future.failed(TestData.emulatedServiceFailure))

      val Left(actual: Result) = await(service.refine(vhr))

      actual shouldBe ErrorResponse.ErrorInternalServerError.XmlResult.withConversationId(vhr)
    }

  }

}
