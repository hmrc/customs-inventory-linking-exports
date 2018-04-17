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

package unit.controllers.actionbuilders

import org.mockito.ArgumentMatchers.{eq => ameq}
import org.scalatest.mockito.MockitoSugar
import play.api.mvc.Result
import uk.gov.hmrc.customs.api.common.controllers.ErrorResponse.errorBadRequest
import uk.gov.hmrc.customs.inventorylinking.export.controllers.actionbuilders.CspAuthAction
import uk.gov.hmrc.customs.inventorylinking.export.logging.ExportsLogger2
import uk.gov.hmrc.customs.inventorylinking.export.model.actionbuilders.AuthorisedRequest
import uk.gov.hmrc.customs.inventorylinking.export.model.{AuthorisedAs, _}
import uk.gov.hmrc.play.test.UnitSpec
import util.TestData._
import util.{AuthConnectorStubbing, RequestHeaders}

//TODO extract classes
class CspAuthActionSpec extends UnitSpec with MockitoSugar {

  private type EitherResultOrAuthRequest[A] = Either[Result, AuthorisedRequest[A]]

  private val vhrWithBadge = vhr(Some(badgeIdentifier))
  private val vhrWithoutBadge = vhr(None)
  private val expectedCspAuthorisedRequestWithBadge = ar(vhrWithBadge, maybeAuthorised = Some(AuthorisedAs.Csp))
  private val expectedUnAuthorisedRequest = ar(vhrWithoutBadge, maybeAuthorised = None)
  private val errorResponseBadgeIdentifierHeaderMissing =
    errorBadRequest(s"${HeaderConstants.XBadgeIdentifierHeaderName} header is missing or invalid")

  trait SetUp extends AuthConnectorStubbing {
    val mockExportsLogger = mock[ExportsLogger2]
    val cspAuthAction = new CspAuthAction(mockAuthConnector, mockExportsLogger)
  }

  "CspAuthAction" should {
    "Return Right of AuthorisedRequest with maybeAuthorised as CSP when authorised by auth API and badge identifier exists" in new SetUp {
      authoriseCsp()

      val actual = await(cspAuthAction.refine(vhrWithBadge))

      actual shouldBe Right(expectedCspAuthorisedRequestWithBadge)
    }

    "Return Left of 401 Result when authorised by auth API but badge identifier does not exists" in new SetUp {
      authoriseCsp()

      val actual = await(cspAuthAction.refine(vhrWithoutBadge))

      actual shouldBe Left(errorResponseBadgeIdentifierHeaderMissing.XmlResult.withHeaders(RequestHeaders.X_CONVERSATION_ID_NAME -> conversationId.value))
    }

    "Return Right of AuthorisedRequest with maybeAuthorised as None when not authorised" in new SetUp {
      unauthoriseCsp()

      val actual = await(cspAuthAction.refine(vhrWithoutBadge))

      actual shouldBe Right(expectedUnAuthorisedRequest)
    }

    "propagate exceptional errors in auth API" in new SetUp {
      authoriseCspError()

      val caught = intercept[Throwable](await(cspAuthAction.refine(vhrWithoutBadge)))

      caught shouldBe emulatedServiceFailure
    }
  }
}


