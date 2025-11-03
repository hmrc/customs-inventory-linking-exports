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

package component

import org.scalatest.OptionValues
import org.scalatest.matchers.should.Matchers
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.Result
import play.api.test.Helpers._
import uk.gov.hmrc.customs.inventorylinking.exports.xml.ValidateXmlAgainstSchema
import util.RequestHeaders.X_CONVERSATION_ID_NAME
import util.TestData._
import util.externalservices.{ApiSubscriptionFieldsService, AuthService, CustomsMetricsService, InventoryLinkingExportsService}

import java.io.StringReader
import javax.xml.transform.stream.StreamSource
import javax.xml.validation.Schema
import scala.concurrent.Future

class ExportsServiceSpec extends ComponentTestSpec
  with Matchers
  with CustomsMetricsService
  with OptionValues
  with InventoryLinkingExportsService
  with ApiSubscriptionFieldsService
  with AuthService {

  protected val xsdErrorLocationV1: String = "/api/conf/1.0/schemas/customs/error.xsd"
  private val schemaErrorV1: Schema = ValidateXmlAgainstSchema.getSchema(xsdErrorLocationV1).get

  private val internalServerError =
    """<?xml version="1.0" encoding="UTF-8"?>
      |<errorResponse>
      |  <code>INTERNAL_SERVER_ERROR</code>
      |  <message>Internal server error</message>
      |</errorResponse>
    """.stripMargin

  private val payloadForbiddenError =
    """<?xml version="1.0" encoding="UTF-8"?>
      |<errorResponse>
      |  <code>PAYLOAD_FORBIDDEN</code>
      |  <message>A firewall rejected the request</message>
      |</errorResponse>
    """.stripMargin

  private val unauthorisedError =
    """<?xml version="1.0" encoding="UTF-8"?>
      |<errorResponse>
      |  <code>UNAUTHORIZED</code>
      |  <message>Unauthorised request</message>
      |</errorResponse>
    """.stripMargin

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

  private val serviceUnavailableError: String =
    """<?xml version='1.0' encoding='UTF-8'?>
      |<errorResponse>
      |      <code>SERVER_ERROR</code>
      |      <message>Service unavailable</message>
      |</errorResponse>
    """.stripMargin

  override protected def beforeAll(): Unit = {
    startMockServer()
  }

  override protected def beforeEach(): Unit = {
    resetMockServer()
    startBackendService()
    startApiSubscriptionFieldsService()
  }

  override protected def afterAll(): Unit = {
    stopMockServer()
  }

  Feature("CSPs and Software Houses submit a message") {

    Scenario("A valid message is submitted and successfully forwarded to the backend") {
      Given("a CSP is authorised to use the API endpoint")
      authServiceAuthorisesCSP()

      And("the backend service will return a successful response")
      startApiSubscriptionFieldsService()

      When("a valid message is submitted with valid headers")
      val result: Future[Result] = route(app, ValidRequestWithSubmitterHeader.fromCsp).get

      And("an Accepted (202) response is returned")
      status(result) shouldBe ACCEPTED
      header(X_CONVERSATION_ID_NAME, result).get shouldNot be("")

      And("the response body is empty")
      contentAsString(result) shouldBe empty

      And("the request was authorised with AuthService")
      verifyAuthServiceCalledForCsp()

      And("Metrics logging call was made")
      eventually(verifyCustomsMetricsServiceWasCalled())
    }

    Scenario("A valid message is submitted and the backend service fails") {
      Given("a CSP is authorised to use the API endpoint")
      authServiceAuthorisesCSP()

      And("the back end Service will return an error response")
      startApiSubscriptionFieldsService()
      setupBackendServiceToReturn(NOT_FOUND)

      When("a valid message request is submitted")
      val result = route(app, ValidRequestWithSubmitterHeader.fromCsp).get

      Then("an 500 Internal Server Error response is returned")
      status(result) shouldBe INTERNAL_SERVER_ERROR
      stringToXml(contentAsString(result)) shouldEqual stringToXml(internalServerError)
      header(`X_CONVERSATION_ID_NAME`, result).get shouldNot be("")
      schemaErrorV1.newValidator().validate(new StreamSource(new StringReader(internalServerError)))
    }

    Scenario(s"Return PAYLOAD_FORBIDDEN response when the Back End service fails with 403") {
      Given("a CSP is authorised to use the API endpoint")
      authServiceAuthorisesCSP()

      And("the Back End Service will return an error response")
      startApiSubscriptionFieldsService()
      setupBackendServiceToReturn(FORBIDDEN)

      When(s"a valid Goods Arrival message request is submitted")
      val result = route(app, ValidRequestWithSubmitterHeader.fromCsp).get

      Then("an 403 Payload Forbidden Error response is returned")
      status(result) shouldBe FORBIDDEN
      stringToXml(contentAsString(result)) shouldEqual stringToXml(payloadForbiddenError)
      header(X_CONVERSATION_ID_NAME, result).get shouldNot be("")
      schemaErrorV1.newValidator().validate(new StreamSource(new StringReader(payloadForbiddenError)))
    }

  }

  Feature("The endpoint handles errors as expected") {
    Scenario("Response status 401 when an unauthorised user submits a valid message") {
      Given("an unauthorised CSP wants to submit a customs message with an invalid XML payload")
      authServiceUnauthorisesScopeForCSP()
      authServiceUnauthorisesCustomsEnrolmentForNonCSP(cspBearerToken)

      When("a POST request with data is sent to the API")
      val result = route(app, ValidRequestWithSubmitterHeader.fromCsp)

      Then("a response with a 401 status is returned by the API")
      result shouldBe defined

      val resultFuture = result.get

      status(resultFuture) shouldBe UNAUTHORIZED
      headers(resultFuture).get(X_CONVERSATION_ID_NAME) shouldBe defined

      And("the response body is a \"invalid xml\" XML")
      stringToXml(contentAsString(resultFuture)) shouldBe stringToXml(unauthorisedError)
      schemaErrorV1.newValidator().validate(new StreamSource(new StringReader(unauthorisedError)))
    }

    Scenario("Response status 400 when user submits a message with an XML payload that does not adhere to schema") {
      Given("an authorised CSP wants to submit a message with an invalid XML payload")
      authServiceAuthorisesCSP()

      When("a POST request with data is sent to the API")
      val result = route(app, InvalidRequest.fromCsp)

      Then("a response with a 400 status is returned by the API")
      result shouldBe defined

      val resultFuture = result.get

      status(resultFuture) shouldBe BAD_REQUEST
      headers(resultFuture).get(X_CONVERSATION_ID_NAME) shouldBe defined

      And("the response body is a \"Bad request\" XML")
      stringToXml(contentAsString(resultFuture)) shouldBe stringToXml(badRequestError)
      schemaErrorV1.newValidator().validate(new StreamSource(new StringReader(badRequestError)))
    }

    Scenario("A valid message is submitted when the service is shuttered") {
      Given("a CSP is authorised to use the API endpoint and submits to a shuttered version")
      implicit lazy val app: Application = new GuiceApplicationBuilder().configure(configMap + ("shutter.v1" -> "true")).build()
      authServiceAuthorisesCSP()

      When("a valid message request is submitted")
      val result = route(app, ValidRequestWithSubmitterHeader.fromCsp).get

      Then("a 503 Service Unavailable response is returned")
      status(result) shouldBe SERVICE_UNAVAILABLE
      stringToXml(contentAsString(result)) shouldEqual stringToXml(serviceUnavailableError)
      header(X_CONVERSATION_ID_NAME, result) shouldBe None
      schemaErrorV1.newValidator().validate(new StreamSource(new StringReader(serviceUnavailableError)))
    }

    Scenario(s"Return PAYLOAD_FORBIDDEN response when the Back End service fails with 403") {
      Given("a CSP is authorised to use the API endpoint")
      authServiceAuthorisesCSP()

      And("the Back End Service will return an error response")
      startApiSubscriptionFieldsService()
      setupBackendServiceToReturn(FORBIDDEN)

      When(s"a valid Validate Movement message request is submitted")
      val result = route(app, ValidRequestWithSubmitterHeader.fromCsp).get

      Then("an 403 Payload Forbidden Error response is returned")
      status(result) shouldBe FORBIDDEN
      stringToXml(contentAsString(result)) shouldEqual stringToXml(payloadForbiddenError)
      header(X_CONVERSATION_ID_NAME, result).get shouldNot be("")
      schemaErrorV1.newValidator().validate(new StreamSource(new StringReader(payloadForbiddenError)))
    }

  }

}
