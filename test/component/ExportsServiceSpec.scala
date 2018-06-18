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

package component

import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, Matchers, OptionValues}
import play.api.mvc.{AnyContentAsXml, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import util.RequestHeaders.X_CONVERSATION_ID_NAME
import util.TestData._
import util.externalservices.{ApiSubscriptionFieldsService, AuthService, InventoryLinkingExportsService}

import scala.concurrent.Future

class ExportsServiceSpec extends ComponentTestSpec
  with Matchers
  with OptionValues
  with BeforeAndAfterAll
  with BeforeAndAfterEach
  with InventoryLinkingExportsService
  with ApiSubscriptionFieldsService
  with AuthService {

  private val internalServerError =
    """<?xml version="1.0" encoding="UTF-8"?>
      |<errorResponse>
      |  <code>INTERNAL_SERVER_ERROR</code>
      |  <message>Internal server error</message>
      |</errorResponse>
    """.stripMargin

  private val unauthorisedError =
    """<?xml version="1.0" encoding="UTF-8"?>
      |<errorResponse>
      |  <code>UNAUTHORIZED</code>
      |  <message>Unauthorised request</message>
      |</errorResponse>
    """.stripMargin

  private val malformedXmlAndNonXmlPayloadError =
    """<?xml version="1.0" encoding="UTF-8"?>
      |<errorResponse>
      |  <code>BAD_REQUEST</code>
      |  <message>Request body does not contain a well-formed XML document.</message>
      |</errorResponse>""".stripMargin

  private val badRequestError =
    s"""<?xml version="1.0" encoding="UTF-8"?>
       |<errorResponse>
       |    <code>BAD_REQUEST</code>
       |    <message>Payload is not valid according to schema</message>
       |    <errors>
       |        <error>
       |            <code>xml_validation_error</code>
       |            <message>cvc-complex-type.3.2.2: Attribute 'foo' is not allowed to appear in element
       |                'inventoryLinkingMovementRequest'.
       |            </message>
       |        </error>
       |    </errors>
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

  feature("CSPs and Software Houses submits a message") {

    scenario("A valid message is submitted and successfully forwarded to the backend") {
      Given("a CSP is authorised to use the API endpoint")
      authServiceAuthorizesCSP()

      And("the backend service will return a successful response")
      startApiSubscriptionFieldsService()
      startInventoryLinkingExportsService()

      When("a valid message is submitted with valid headers")
      val result: Future[Result] = route(app, ValidRequest.fromCsp).get

      And("an Accepted (202) response is returned")
      status(result) shouldBe ACCEPTED
      header(X_CONVERSATION_ID_NAME, result).get shouldNot be("")
      verifyAuthServiceCalledForCsp()
    }

    scenario("A non-CSP successfully submits a message on behalf of somebody with a Customs enrolment") {
      Given("A Software House wants to submit a valid message")
      authServiceUnauthorisesScopeForCSP(nonCspBearerToken)

      And("declarant is enrolled with Customs having an EORI number")
      authServiceAuthorizesNonCspWithEori()

      When("a valid message is submitted with valid headers")
      val result: Future[Result] = route(app = app, ValidRequest.fromNonCsp).value

      Then("a response with a 202 (ACCEPTED) status is received")
      status(result) shouldBe ACCEPTED

      And("the response body is empty")
      contentAsString(result) shouldBe 'empty

      And("the request was authorised with AuthService")
      verifyAuthServiceCalledForNonCsp()
    }

    scenario("A valid message is submitted and the backend service fails") {
      Given("a CSP is authorised to use the API endpoint")
      authServiceAuthorizesCSP()

      And("the back end Service will return an error response")
      startApiSubscriptionFieldsService()
      setupInventoryLinkingExportsServiceToReturn(NOT_FOUND)

      When("a valid message request is submitted")
      val result = route(app, ValidRequest.fromCsp).get

      Then("an 500 Internal Server Error response is returned")
      status(result) shouldBe INTERNAL_SERVER_ERROR
      stringToXml(contentAsString(result)) shouldEqual stringToXml(internalServerError)
      header(X_CONVERSATION_ID_NAME, result).get shouldNot be("")
    }
  }

  feature("The endpoint handles errors as expected") {
    scenario("Response status 401 when an unauthorised user submits a valid message") {
      Given("an unauthorised CSP wants to submit a customs message with an invalid XML payload")
      authServiceUnauthorisesScopeForCSP()
      authServiceUnauthorisesCustomsEnrolmentForNonCSP(cspBearerToken)

      When("a POST request with data is sent to the API")
      val result = route(app, ValidRequest.fromCsp)

      Then("a response with a 401 status is returned by the API")
      result shouldBe 'defined

      val resultFuture = result.get

      status(resultFuture) shouldBe UNAUTHORIZED
      headers(resultFuture).get(X_CONVERSATION_ID_NAME) shouldBe 'defined

      And("the response body is a \"invalid xml\" XML")
      stringToXml(contentAsString(resultFuture)) shouldBe stringToXml(unauthorisedError)
    }

    scenario("Response status 400 when user submits a message with an XML payload that does not adhere to schema") {
      Given("an authorised CSP wants to submit a message with an invalid XML payload")
      authServiceAuthorizesCSP()

      When("a POST request with data is sent to the API")
      val result = route(app, InvalidRequest.fromCsp)

      Then("a response with a 400 status is returned by the API")
      result shouldBe 'defined

      val resultFuture = result.get

      status(resultFuture) shouldBe BAD_REQUEST
      headers(resultFuture).get(X_CONVERSATION_ID_NAME) shouldBe 'defined

      And("the response body is a \"Bad request\" XML")
      stringToXml(contentAsString(resultFuture)) shouldBe stringToXml(badRequestError)
    }

    scenario("Response status 400 when user submits a message with a malformed XML payload") {

      Given(s"an authorised CSP wants to submit a message with a malformed XML payload")
      authServiceAuthorizesCSP()

      When("a POST request with data is sent to the API")
      val result = route(app, ValidRequest.withBody("<xm> malformed xml <xm>").fromCsp)

      Then("a response with a 400 status is received")
      result shouldBe 'defined

      val resultFuture = result.get

      status(resultFuture) shouldBe BAD_REQUEST
      headers(resultFuture).get(X_CONVERSATION_ID_NAME) shouldBe 'defined

      And("the response body is a \"malformed xml body\" XML")
      stringToXml(contentAsString(resultFuture)) shouldBe stringToXml(malformedXmlAndNonXmlPayloadError)
    }

    scenario("Response status 400 when user submits a message with a non-xml payload") {
      Given("an authorised CSP wants to submit a message with a non-XML payload")
      authServiceAuthorizesCSP()
      When("a POST request with data is sent to the API")
      val result = route(app, ValidRequest.withBody("""  {"valid": "json payload" }  """).fromCsp)

      Then("a response with a 400 status is received")
      result shouldBe 'defined

      val resultFuture = result.get

      status(resultFuture) shouldBe BAD_REQUEST
      headers(resultFuture).get(X_CONVERSATION_ID_NAME) shouldBe 'defined

      And("the response body is a \"malformed xml body\" XML")
      stringToXml(contentAsString(resultFuture)) shouldBe stringToXml(malformedXmlAndNonXmlPayloadError)
    }

    scenario("A non-CSP is not authorised to submit a message on behalf of somebody without Customs enrolment") {
      Given("A Software House wants to submit a valid IL request")
      val request: FakeRequest[AnyContentAsXml] = ValidRequest.fromNonCsp

      And("declarant is not enrolled with Customs")
      authServiceUnauthorisesScopeForCSP(nonCspBearerToken)
      authServiceUnauthorisesCustomsEnrolmentForNonCSP()

      When("a POST request with data is sent to the API")
      val result: Future[Result] = route(app = app, request).value

      Then("a response with a 401 (UNAUTHORIZED) status is received")
      status(result) shouldBe UNAUTHORIZED

      And("the response body is empty")
      stringToXml(contentAsString(result)) shouldBe stringToXml(unauthorisedError)

      And("the request was authorised with AuthService")
      verifyAuthServiceCalledForNonCsp()
    }
  }

}
