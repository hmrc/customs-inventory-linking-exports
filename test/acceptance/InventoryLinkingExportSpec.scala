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

package acceptance

import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, Matchers, OptionValues}
import play.api.mvc._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.customs.inventorylinking.export.model.{ApiSubscriptionKey, VersionOne}
import util.RequestHeaders.X_CONVERSATION_ID_NAME
import util.externalservices.{ApiSubscriptionFieldsService, AuthService, InventoryLinkingExportsService}
import util.TestData._

import scala.concurrent.Future

class InventoryLinkingExportSpec extends AcceptanceTestSpec
  with Matchers
  with OptionValues
  with BeforeAndAfterAll
  with BeforeAndAfterEach
  with InventoryLinkingExportsService
  with ApiSubscriptionFieldsService
  with AuthService {

  private val endpoint = "/"

  private val apiSubscriptionKeyForXClientId =
    ApiSubscriptionKey(clientId , context = "customs%2Finventory-linking%2Fexports", VersionOne)

  private val UnauthorisedError =
    """<?xml version="1.0" encoding="UTF-8"?>
      |<errorResponse>
      |  <code>UNAUTHORIZED</code>
      |  <message>Unauthorised request</message>
      |</errorResponse>
    """.stripMargin

  override protected def beforeAll() {
    startMockServer()
  }

  override protected def beforeEach() {
    resetMockServer()
    startInventoryLinkingExportsService()
    startApiSubscriptionFieldsService()
  }

  override protected def afterAll() {
    stopMockServer()
  }

  feature("Inventory Linking API authorises submissions from CSPs and Software Houses") {

    scenario("An authorised CSP successfully submits an IL request") {
      Given("A CSP wants to submit a valid IL request")
      val request: FakeRequest[AnyContentAsXml] = ValidRequest.fromCsp.postTo(endpoint)

      And("the CSP is authorised with its privileged application")
      authServiceAuthorizesCSP()

      When("a POST request with data is sent to the API")
      val result: Future[Result] = route(app = app, request).value

      Then("a response with a 202 (ACCEPTED) status is received")
      status(result) shouldBe ACCEPTED

      And("the response body is empty")
      contentAsString(result) shouldBe 'empty

      And("the request was authorised with AuthService")
      verifyAuthServiceCalledForCsp()
    }

    scenario("An unauthorised CSP is not allowed to submit an IL request") {
      Given("A CSP wants to submit a valid IL request")
      val request: FakeRequest[AnyContentAsXml] = ValidRequest.fromCsp.postTo(endpoint)

      And("the CSP is unauthorised with its privileged application")
      authServiceUnauthorisesScopeForCSP()
      authServiceUnauthorisesCustomsEnrolmentForNonCSP(cspBearerToken)

      When("a POST request with data is sent to the API")
      val result: Future[Result] = route(app = app, request).value

      Then("a response with a 401 (UNAUTHORIZED) status is received")
      status(result) shouldBe UNAUTHORIZED

      And("a conversationId header is defined")
      headers(result).get(X_CONVERSATION_ID_NAME) shouldBe 'defined

      And("the response body is empty")
      string2xml(contentAsString(result)) shouldBe string2xml(UnauthorisedError)

      And("the request was authorised with AuthService")
      verifyAuthServiceCalledForCsp()
    }

    scenario("A non-CSP successfully submits an IL request on behalf of somebody with Customs enrolment") {
      Given("A Software House wants to submit a valid IL request")
      val request: FakeRequest[AnyContentAsXml] = ValidRequest.fromNonCsp.postTo(endpoint)

      And("declarant is enrolled with Customs having an EORI number")
      authServiceUnauthorisesScopeForCSP(nonCspBearerToken)
      authServiceAuthorizesNonCspWithEori()

      When("a POST request with data is sent to the API")
      val result: Future[Result] = route(app = app, request).value

      Then("a response with a 202 (ACCEPTED) status is received")
      status(result) shouldBe ACCEPTED

      And("the response body is empty")
      contentAsString(result) shouldBe 'empty

      And("the request was authorised with AuthService")
      verifyAuthServiceCalledForNonCsp()
    }

    scenario("A non-CSP is not authorised to submit an IL request on behalf of somebody without Customs enrolment") {
      Given("A Software House wants to submit a valid IL request")
      val request: FakeRequest[AnyContentAsXml] = ValidRequest.fromNonCsp.postTo(endpoint)

      And("declarant is not enrolled with Customs")
      authServiceUnauthorisesScopeForCSP(nonCspBearerToken)
      authServiceUnauthorisesCustomsEnrolmentForNonCSP()

      When("a POST request with data is sent to the API")
      val result: Future[Result] = route(app = app, request).value

      Then("a response with a 401 (UNAUTHORIZED) status is received")
      status(result) shouldBe UNAUTHORIZED

      And("the response body is empty")
      string2xml(contentAsString(result)) shouldBe string2xml(UnauthorisedError)

      And("the request was authorised with AuthService")
      verifyAuthServiceCalledForNonCsp()
    }
  }


  feature("When fields id header is absent, IL API uses X-Client-ID header to retrieve fields id from api-subscription-fields service") {

    scenario("An authorised CSP successfully submits an IL request having X-Client-ID request header") {
      Given("A CSP wants to submit a valid IL request and API Gateway provides X-Client-ID header only")
      val request: FakeRequest[AnyContentAsXml] = ValidRequestWithXClientIdHeader.fromCsp.postTo(endpoint)

      And("the CSP is authorised with its privileged application")
      authServiceAuthorizesCSP()

      When("a POST request with data is sent to the API")
      val result: Future[Result] = route(app = app, request).value

      Then("a response with a 202 (ACCEPTED) status is received")
      status(result) shouldBe ACCEPTED

      And("the response body is empty")
      contentAsString(result) shouldBe 'empty

      And("the request was authorised with AuthService")
      verifyAuthServiceCalledForCsp()

      And("the api-subscription-fields service was called with value of X-Client-ID header")
      verifyGetSubscriptionFieldsCalled(apiSubsKey = apiSubscriptionKeyForXClientId)
    }

    scenario("A non-CSP successfully submits an IL request on behalf of somebody with Customs enrolment having X-Client-ID request header") {
      Given("A Software House wants to submit a valid IL request and API Gateway provides X-Client-ID header only")
      val request: FakeRequest[AnyContentAsXml] = ValidRequestWithXClientIdHeader.fromNonCsp.postTo(endpoint)

      And("declarant is enrolled with Customs having an EORI number")
      authServiceUnauthorisesScopeForCSP(nonCspBearerToken)
      authServiceAuthorizesNonCspWithEori()

      When("a POST request with data is sent to the API")
      val result: Future[Result] = route(app = app, request).value

      Then("a response with a 202 (ACCEPTED) status is received")
      status(result) shouldBe ACCEPTED

      And("the response body is empty")
      contentAsString(result) shouldBe 'empty

      And("the request was authorised with AuthService")
      verifyAuthServiceCalledForNonCsp()

      And("the api-subscription-fields service was called with value of X-Client-ID header")
      verifyGetSubscriptionFieldsCalled(apiSubsKey = apiSubscriptionKeyForXClientId)
    }
  }

}
