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

import org.scalatest.Matchers
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.mvc._
import play.api.test.FakeRequest
import play.api.test.Helpers._

import scala.concurrent.Future

class ApiDefinitionSpec extends ComponentTestSpec with Matchers {

  override implicit lazy val app: Application = new GuiceApplicationBuilder().build()

  feature("Ensure definition file") {

    scenario("is correct") {

      Given("the API is available")
      val request = FakeRequest("GET", "/api/definition")

      When("api definition is requested")
      val result: Option[Future[Result]] = route(app = app, request)

      Then(s"a response with a 200 status is received")
      val resultFuture = result.get
      status(resultFuture) shouldBe OK

      And("the response body is correct")
      contentAsJson(resultFuture) shouldBe Json.parse(
        """
          |{
          |  "scopes": [
          |    {
          |      "key": "write:customs-inventory-linking-exports",
          |      "name": "Inventory Exports Movement Request",
          |      "description": "Submit an Inventory Exports Movement Request"
          |    }
          |  ],
          |  "api": {
          |    "name": "Customs Inventory Linking Exports",
          |    "description": "Customs Inventory Linking Exports",
          |    "context": "customs/inventory-linking/exports",
          |    "versions": [
          |      {
          |        "version": "1.0",
          |        "status": "BETA",
          |        "endpointsEnabled": true,
          |        "access": {
          |          "type": "PRIVATE",
          |          "whitelistedApplicationIds": []
          |        },
          |        "fieldDefinitions": [
          |          {
          |            "name": "callbackUrl",
          |            "description": "The URL of your HTTPS webservice that HMRC calls to notify you regarding request submission.",
          |            "type": "URL"
          |          },
          |          {
          |            "name": "securityToken",
          |            "description": "The full value of Authorization HTTP header that will be used when notifying you.",
          |            "type": "SecureToken"
          |          }
          |        ]
          |      }
          |    ]
          |  }
          |}
        """.stripMargin)
    }
  }
}
