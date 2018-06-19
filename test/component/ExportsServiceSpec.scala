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
import play.api.mvc.Result
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
    startBackendService()
    startApiSubscriptionFieldsService()
  }

  override protected def afterAll() {
    stopMockServer()
  }

  feature("CSPs and Software Houses submit a message") {

    scenario("A valid message is submitted and successfully forwarded to the backend") {
      Given("a CSP is authorised to use the API endpoint")
      authServiceAuthorisesCSP()

      And("the backend service will return a successful response")
      startApiSubscriptionFieldsService()

      When("a valid message is submitted with valid headers")
      val result: Future[Result] = route(app, ValidRequest.fromCsp).get

      And("an Accepted (202) response is returned")
      status(result) shouldBe ACCEPTED
      header(X_CONVERSATION_ID_NAME, result).get shouldNot be("")

      And("the response body is empty")
      contentAsString(result) shouldBe 'empty

      And("the request was authorised with AuthService")
      verifyAuthServiceCalledForCsp()
    }

    scenario("A valid message is submitted and the backend service fails") {
      Given("a CSP is authorised to use the API endpoint")
      authServiceAuthorisesCSP()

      And("the back end Service will return an error response")
      startApiSubscriptionFieldsService()
      setupBackendServiceToReturn(NOT_FOUND)

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
      authServiceAuthorisesCSP()

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

  }

}
