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

package unit.logging

import org.scalatest.mockito.MockitoSugar
import play.api.mvc.AnyContentAsXml
import play.api.test.FakeRequest
import uk.gov.hmrc.customs.api.common.logging.CdsLogger
import uk.gov.hmrc.customs.inventorylinking.export.logging.ExportsLogger
import uk.gov.hmrc.customs.inventorylinking.export.model.actionbuilders.ConversationIdRequest
import uk.gov.hmrc.play.test.UnitSpec
import util.MockitoPassByNameHelper.PassByNameVerifier
import util.TestData.{TestXmlPayload, conversationId, emulatedServiceFailure}


class ExportsLoggerSpec extends UnitSpec with MockitoSugar {

  trait SetUp {
    val mockCdsLogger = mock[CdsLogger]
    val logger = new ExportsLogger(mockCdsLogger)
    implicit val implicitVpr = ConversationIdRequest(conversationId, FakeRequest()
      .withXmlBody(TestXmlPayload).withHeaders("Content-Type" -> "Some-Content-Type"))
  }

  trait SetUpWithMixedCaseHeader extends SetUp {
    override implicit val implicitVpr =  ConversationIdRequest(conversationId, FakeRequest()
      .withXmlBody(TestXmlPayload).withHeaders("ConTenT-Type" -> "Some-Content-Type"))
  }

  "ExportsLogger" should {
    "debug(s: => String)" in new SetUp {
      logger.debug("msg")

      PassByNameVerifier(mockCdsLogger, "debug")
        .withByNameParam("[conversationId=28e5aa87-3f89-4f12-b1b1-60f2b2de66f1] msg")
        .verify()
    }

    "debug(s: => String, e: => Throwable)" in new SetUp {
      logger.debug("msg", emulatedServiceFailure)

      PassByNameVerifier(mockCdsLogger, "debug")
        .withByNameParam("[conversationId=28e5aa87-3f89-4f12-b1b1-60f2b2de66f1] msg")
        .withByNameParam(emulatedServiceFailure)
        .verify()
    }

    "debugFull(s: => String)" in new SetUp {
      logger.debugFull("msg")

      PassByNameVerifier(mockCdsLogger, "debug")
        .withByNameParam("[conversationId=28e5aa87-3f89-4f12-b1b1-60f2b2de66f1] msg headers=Map(Content-Type -> Some-Content-Type)")
        .verify()
    }

    "debugFull(s: => String) mixed case headers" in new SetUpWithMixedCaseHeader {
      logger.debugFull("msg")

      PassByNameVerifier(mockCdsLogger, "debug")
        .withByNameParam("[conversationId=28e5aa87-3f89-4f12-b1b1-60f2b2de66f1] msg headers=Map(ConTenT-Type -> Some-Content-Type)")
        .verify()
    }

    "info(s: => String)" in new SetUp {
      logger.info("msg")

      PassByNameVerifier(mockCdsLogger, "info")
        .withByNameParam("[conversationId=28e5aa87-3f89-4f12-b1b1-60f2b2de66f1] msg")
        .verify()
    }

    "warn(s: => String)" in new SetUp {
      logger.warn("msg")

      PassByNameVerifier(mockCdsLogger, "warn")
        .withByNameParam("[conversationId=28e5aa87-3f89-4f12-b1b1-60f2b2de66f1] msg")
        .verify()
    }

    "error(s: => String, e: => Throwable)" in new SetUp {
      logger.error("msg", emulatedServiceFailure)

      PassByNameVerifier(mockCdsLogger, "error")
        .withByNameParam("[conversationId=28e5aa87-3f89-4f12-b1b1-60f2b2de66f1] msg")
        .withByNameParam(emulatedServiceFailure)
        .verify()
    }

    "error(s: => String)" in new SetUp {
      logger.error("msg")

      PassByNameVerifier(mockCdsLogger, "error")
        .withByNameParam("[conversationId=28e5aa87-3f89-4f12-b1b1-60f2b2de66f1] msg")
        .verify()
    }

    "errorWithoutRequestContext(s: => String)" in new SetUp {
      logger.errorWithoutRequestContext("msg")

      PassByNameVerifier(mockCdsLogger, "error")
        .withByNameParam("msg")
        .verify()
    }
  }
}
