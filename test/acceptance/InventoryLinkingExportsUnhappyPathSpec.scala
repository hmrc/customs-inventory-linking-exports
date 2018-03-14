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

import org.scalatest.{Matchers, OptionValues}
import play.api.libs.json.{JsObject, JsString}
import play.api.mvc._
import play.api.test.Helpers._
import util.AuditService
import util.TestData._
import util.externalservices.{AuthService, InventoryLinkingExportsService}

import scala.concurrent.Future

class InventoryLinkingExportsUnhappyPathSpec extends AcceptanceTestSpec
  with Matchers with OptionValues with AuthService with InventoryLinkingExportsService with AuditService {

  private val endpoint = "/"

  private val BadRequestError =
    """<?xml version="1.0" encoding="UTF-8"?>
      |<errorResponse>
      |  <code>BAD_REQUEST</code>
      |  <message>Payload is not valid according to schema</message>
      |  <errors>
      |     <error>
      |       <code>xml_validation_error</code>
      |       <message>cvc-complex-type.3.2.2: Attribute 'foo' is not allowed to appear in element 'inventoryLinkingMovementRequest'.</message>
      |     </error>
      |  </errors>
      |</errorResponse>
    """.stripMargin

  private val BadRequestErrorWith2Errors =
    """<?xml version="1.0" encoding="UTF-8"?>
      |    <errorResponse>
      |    <code>BAD_REQUEST</code> <message>Payload is not valid according to schema</message> <errors>
      |      <error>
      |        <code>xml_validation_error</code> <message>cvc-complex-type.3.2.2: Attribute 'foo' is not allowed to appear in element 'inventoryLinkingMovementRequest'.</message>
      |      </error> <error>
      |        <code>xml_validation_error</code> <message>cvc-type.3.1.1: Element 'goodsLocation' is a simple type, so it cannot have attributes, excepting those whose namespace name is identical to 'http://www.w3.org/2001/XMLSchema-instance' and whose [local name] is one of 'type', 'nil', 'schemaLocation' or 'noNamespaceSchemaLocation'. However, the attribute, 'random' was found.</message>
      |      </error>
      |    </errors>
      |  </errorResponse>
    """.stripMargin

  private val MalformedXmlBodyError =
    """<?xml version="1.0" encoding="UTF-8"?>
      |<errorResponse>
      |  <code>BAD_REQUEST</code>
      |  <message>Request body does not contain well-formed XML.</message>
      |</errorResponse>
    """.stripMargin

  private val InvalidAcceptHeaderError =
    """<?xml version="1.0" encoding="UTF-8"?>
      |<errorResponse>
      |  <code>ACCEPT_HEADER_INVALID</code>
      |  <message>The accept header is missing or invalid</message>
      |</errorResponse>
    """.stripMargin

  private val InvalidContentTypeHeaderError =
    """<?xml version="1.0" encoding="UTF-8"?>
      |<errorResponse>
      |  <code>UNSUPPORTED_MEDIA_TYPE</code>
      |  <message>The content type header is missing or invalid</message>
      |</errorResponse>
    """.stripMargin

  private val InvalidXBadgeIdentifierHeaderError =
    """<?xml version="1.0" encoding="UTF-8"?>
      |<errorResponse>
      |  <code>BAD_REQUEST</code>
      |  <message>X-Badge-Identifier header is missing or invalid</message>
      |</errorResponse>
    """.stripMargin

  private val InternalServerError =
    """<?xml version="1.0" encoding="UTF-8"?>
      |<errorResponse>
      |  <code>INTERNAL_SERVER_ERROR</code>
      |  <message>Internal server error</message>
      |</errorResponse>
    """.stripMargin

  override protected def beforeAll() {
    startMockServer()
    stubAuditService()
    authServiceAuthorizesCSP()
    startInventoryLinkingExportsService()
  }

  override protected def afterAll() {
    stopMockServer()
  }

  feature("The API handles errors as expected") {

    scenario("Response status 400 when user submits an xml payload that does not adhere to schema") {
      Given("the API is available")
      val request = InvalidRequest.fromCsp.postTo(endpoint)

      When("a POST request with data is sent to the API")
      val result: Option[Future[Result]] = route(app = app, request)

      Then(s"a response with a 400 status is received")
      result shouldBe 'defined
      val resultFuture = result.value

      status(resultFuture) shouldBe BAD_REQUEST

      And("the response body is an \"invalid xml\" XML")
      string2xml(contentAsString(resultFuture)) shouldBe string2xml(BadRequestError)
    }

    scenario("Response status 400 when user submits an xml payload that does not adhere to schema having multiple errors") {
      Given("the API is available")
      val request = InvalidRequestWith3Errors.fromCsp.postTo(endpoint)

      When("a POST request with data is sent to the API")
      val result: Option[Future[Result]] = route(app = app, request)

      Then(s"a response with a 400 status is received")
      result shouldBe 'defined
      val resultFuture = result.value

      status(resultFuture) shouldBe BAD_REQUEST

      And("the response body is an \"invalid xml\" XML")
      string2xml(contentAsString(resultFuture)) shouldBe string2xml(BadRequestErrorWith2Errors)
    }

    scenario("Response status 400 when user submits a malformed xml payload") {
      Given("the API is available")
      val request = MalformedXmlRequest.copyFakeRequest(method = POST, uri = endpoint)

      When("a POST request with data is sent to the API")
      val result: Option[Future[Result]] = route(app = app, request)

      Then(s"a response with a 400 status is received")
      result shouldBe 'defined
      val resultFuture = result.value

      status(resultFuture) shouldBe BAD_REQUEST

      And("the response body is a \"malformed xml body\" XML")
      string2xml(contentAsString(resultFuture)) shouldBe string2xml(MalformedXmlBodyError)
    }

    scenario("Response status 400 when user submits a non-xml payload") {
      Given("the API is available")
      val request = ValidRequest
        .withJsonBody(JsObject(Seq("something" -> JsString("I am a json"))))
        .copyFakeRequest(method = POST, uri = endpoint)

      When("a POST request with data is sent to the API")
      val result: Option[Future[Result]] = route(app = app, request)

      Then(s"a response with a 400 status is received")
      result shouldBe 'defined
      val resultFuture = result.value

      status(resultFuture) shouldBe BAD_REQUEST

      And("the response body is a \"malformed xml body\" XML")
      string2xml(contentAsString(resultFuture)) shouldBe string2xml(MalformedXmlBodyError)
    }

    scenario("Response status 500 when user submits a request without any client id headers") {
      Given("the API is available")
      val request = NoClientIdIdHeaderRequest.fromCsp.postTo(endpoint)

      When("a POST request with data is sent to the API")
      val result: Option[Future[Result]] = route(app = app, request)

      Then("a response with a 500 status is received")
      result shouldBe 'defined
      val resultFuture = result.value

      status(resultFuture) shouldBe INTERNAL_SERVER_ERROR
    }

    scenario("Response status 406 when user submits a request without Accept header") {
      Given("the API is available")
      val request = NoAcceptHeaderRequest.copyFakeRequest(method = POST, uri = endpoint)

      When("a POST request with data is sent to the API")
      val result: Option[Future[Result]] = route(app = app, request)

      Then(s"a response with a 406 status is received")
      result shouldBe 'defined
      val resultFuture = result.value

      status(resultFuture) shouldBe NOT_ACCEPTABLE

      And("the response body is an \"invalid Accept header\" XML")
      string2xml(contentAsString(resultFuture)) shouldBe string2xml(InvalidAcceptHeaderError)
    }

    scenario("Response status 406 when user submits a request with an invalid Accept header") {
      Given("the API is available")
      val request = InvalidAcceptHeaderRequest.copyFakeRequest(method = POST, uri = endpoint)

      When("a POST request with data is sent to the API")
      val result: Option[Future[Result]] = route(app = app, request)

      Then(s"a response with a 406 status is received")
      result shouldBe 'defined
      val resultFuture = result.value

      status(resultFuture) shouldBe NOT_ACCEPTABLE

      And("the response body is an \"invalid Accept header\" XML")
      string2xml(contentAsString(resultFuture)) shouldBe string2xml(InvalidAcceptHeaderError)
    }

    scenario("Response status 415 when user submits a request with an invalid Content-Type header") {
      Given("the API is available")
      val request = InvalidContentTypeHeaderRequest.copyFakeRequest(method = POST, uri = endpoint)

      When("a POST request with data is sent to the API")
      val result: Option[Future[Result]] = route(app = app, request)

      Then(s"a response with a 406 status is received")
      result shouldBe 'defined
      val resultFuture = result.value

      status(resultFuture) shouldBe UNSUPPORTED_MEDIA_TYPE

      And("the response body is an \"invalid Accept header\" XML")
      string2xml(contentAsString(resultFuture)) shouldBe string2xml(InvalidContentTypeHeaderError)
    }

    scenario("Response status 400 when a CSP user submits a request without an X-Badge-Identifier header") {
      Given("the API is available")
      val request = NoXBadgeIdentifierHeaderRequest.copyFakeRequest(method = POST, uri = endpoint).fromCsp

      When("a POST request with data is sent to the API")
      val result: Option[Future[Result]] = route(app = app, request)

      Then(s"a response with a 400 status is received")
      result shouldBe 'defined
      val resultFuture = result.value

      status(resultFuture) shouldBe BAD_REQUEST

      And("the response body is a \"missing X-Badge-Identifier header\" XML")
      string2xml(contentAsString(resultFuture)) shouldBe string2xml(InvalidXBadgeIdentifierHeaderError)
    }

    scenario("Response status 400 when a user submits a request with an invalid X-Badge-Identifier header") {
      Given("the API is available")
      val request = InvalidXBadgeIdentifierHeaderRequest.copyFakeRequest(method = POST, uri = endpoint)

      When("a POST request with data is sent to the API")
      val result: Option[Future[Result]] = route(app = app, request)

      Then(s"a response with a 400 status is received")
      result shouldBe 'defined
      val resultFuture = result.value

      status(resultFuture) shouldBe BAD_REQUEST

      And("the response body is an \"invalid X-Badge-Identifier header\" XML")
      string2xml(contentAsString(resultFuture)) shouldBe string2xml(InvalidXBadgeIdentifierHeaderError)
    }

    scenario("Response status 500 when user submits a valid request but downstream call to DMS fails with an HTTP error") {

      Given("the API is available")
      val request = ValidRequest.fromCsp.postTo(endpoint)

      When("a POST request with data is sent to the API")
      setupInventoryLinkingExportsServiceToReturn(status = NOT_FOUND)
      val result: Option[Future[Result]] = route(app = app, request)

      Then(s"a response with a 500 status is received")
      result shouldBe 'defined
      val resultFuture = result.value

      status(resultFuture) shouldBe INTERNAL_SERVER_ERROR

      And("the response body is an \"Internal server error\" XML")
      string2xml(contentAsString(resultFuture)) shouldBe string2xml(InternalServerError)
    }
  }

}
