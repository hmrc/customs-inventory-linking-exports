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

package unit.controllers.actionbuilders

import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.customs.inventorylinking.export.services.DateTimeService
import uk.gov.hmrc.customs.inventorylinking.export.controllers.actionbuilders.ConversationIdAction
import uk.gov.hmrc.customs.inventorylinking.export.logging.ExportsLogger
import uk.gov.hmrc.customs.inventorylinking.export.model.actionbuilders.ConversationIdRequest
import util.CustomsMetricsTestData.EventStart
import util.UnitSpec
import util.TestData
import util.TestData.conversationId

import scala.concurrent.ExecutionContext

class ConversationIdActionSpec extends UnitSpec with MockitoSugar {

  trait SetUp {
    private val mockExportsLogger = mock[ExportsLogger]
    protected val mockDateTimeService: DateTimeService = mock[DateTimeService]

    val request = FakeRequest()
    implicit val ec: ExecutionContext = Helpers.stubControllerComponents().executionContext
    val conversationIdAction = new ConversationIdAction(TestData.stubUniqueIdsService, mockDateTimeService, mockExportsLogger)
    val expected = ConversationIdRequest(conversationId, EventStart, request)
  }

  "ConversationIdAction" should {
    "Generate a Request containing a unique correlation id" in new SetUp {
      when(mockDateTimeService.zonedDateTimeUtc).thenReturn(EventStart)

      private val actual = await(conversationIdAction.transform(request))

      actual shouldBe expected
    }
  }

}
