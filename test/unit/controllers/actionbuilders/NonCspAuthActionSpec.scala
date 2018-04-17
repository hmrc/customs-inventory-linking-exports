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

import org.scalatest.mockito.MockitoSugar
import play.api.http.Status
import play.api.http.Status.UNAUTHORIZED
import play.api.mvc.Result
import uk.gov.hmrc.customs.api.common.controllers.ErrorResponse
import uk.gov.hmrc.customs.api.common.controllers.ErrorResponse.UnauthorizedCode
import uk.gov.hmrc.customs.inventorylinking.export.controllers.actionbuilders.NonCspAuthAction
import uk.gov.hmrc.customs.inventorylinking.export.logging.ExportsLogger2
import uk.gov.hmrc.customs.inventorylinking.export.model.actionbuilders.ActionBuilderModelHelper._
import uk.gov.hmrc.customs.inventorylinking.export.model.actionbuilders.AuthorisedRequest
import uk.gov.hmrc.play.test.UnitSpec
import util.TestData._
import util.{AuthConnectorStubbing, RequestHeaders, TestData}
class NonCspAuthActionSpec extends UnitSpec with MockitoSugar {

  private type EitherResultOrAuthRequest[A] = Either[Result, AuthorisedRequest[A]]

  private val vhrWithoutBadge = vhr(maybeBadgeIdentifier = None)
  private val unAuthorisedRequest = ar(vhrWithoutBadge, maybeAuthorised = None)
  private val cspAuthorisedRequest = unAuthorisedRequest.asCsp
  private val expectedNonCspAuthorisedRequest = unAuthorisedRequest.asNonCsp
  private val errorResponseUnauthorisedGeneral =
    ErrorResponse(Status.UNAUTHORIZED, UnauthorizedCode, "Unauthorised request")
  private lazy val errorResponseEoriNotFoundInCustomsEnrolment =
    ErrorResponse(UNAUTHORIZED, UnauthorizedCode, "EORI number not found in Customs Enrolment")

  trait SetUp extends AuthConnectorStubbing {
    val mockExportsLogger = mock[ExportsLogger2]
    val nonCspAuthAction = new NonCspAuthAction(mockAuthConnector, mockExportsLogger)
  }

  "NonCspAuthAction" should {
    "Authorise Non CSP when authorised by auth API " in new SetUp {
      authoriseNonCsp(Some(declarantEori))

      val actual = await(nonCspAuthAction.refine(unAuthorisedRequest))

      actual shouldBe Right(expectedNonCspAuthorisedRequest)
    }

    "pass through request when already authorised" in new SetUp {
      authoriseNonCsp(Some(declarantEori))

      val actual = await(nonCspAuthAction.refine(cspAuthorisedRequest))

      actual shouldBe Right(cspAuthorisedRequest)
    }

    "Return 401 when authorised by auth API but Eori not exists" in new SetUp {
      authoriseNonCsp(maybeEori = None)

      val actual = await(nonCspAuthAction.refine(unAuthorisedRequest))

      actual shouldBe Left(errorResponseEoriNotFoundInCustomsEnrolment.XmlResult.withHeaders(RequestHeaders.X_CONVERSATION_ID_NAME -> conversationId.value))
    }

    "Return 401 when not authorised as NonCsp" in new SetUp {
      unauthoriseNonCspOnly()

      val actual = await(nonCspAuthAction.refine(unAuthorisedRequest))

      actual shouldBe Left(errorResponseUnauthorisedGeneral.XmlResult.withHeaders(RequestHeaders.X_CONVERSATION_ID_NAME -> conversationId.value))
    }

    "propagate exceptional errors in auth API" in new SetUp {
      authoriseNonCspOnlyError()

      val caught = intercept[Throwable](await(nonCspAuthAction.refine(unAuthorisedRequest)))

      caught shouldBe emulatedServiceFailure
    }
  }
}
