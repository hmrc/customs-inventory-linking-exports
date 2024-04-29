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

package util.externalservices

import com.github.tomakehurst.wiremock.client.WireMock._
import play.api.test.Helpers._
import uk.gov.hmrc.customs.inventorylinking.`export`.model.AcceptanceTestScenario
import util.RequestHeaders.GOV_TEST_SCENARIO_VALUE
import util.{ExternalServicesConfig, WireMockRunner}

trait InventoryLinkingExportsService extends WireMockRunner {
  private val urlMatchingRequestPath = urlMatching(ExportsExternalServicesConfig.ExportsServiceContext)

  def startBackendService(): Unit = setupBackendServiceToReturn(ACCEPTED)

  def setupBackendServiceToReturn(status: Int): Unit =
    stubFor(post(urlMatchingRequestPath).
      willReturn(
        aResponse()
          .withStatus(status)))

  def verifyInventoryLinkingExportsServiceWasCalledWith(requestBody: String,
                                                        expectedAuthToken: String = ExternalServicesConfig.AuthToken,
                                                        maybeUnexpectedAuthToken: Option[String] = None): Unit = {
    verify(1, postRequestedFor(urlMatchingRequestPath)
      .withHeader(CONTENT_TYPE, equalTo(XML + "; charset=UTF-8"))
      .withHeader(ACCEPT, equalTo(XML))
      .withHeader(AcceptanceTestScenario.HeaderName, equalTo(GOV_TEST_SCENARIO_VALUE))
      .withHeader(AUTHORIZATION, equalTo(s"Bearer $expectedAuthToken"))
      .withHeader(DATE, notMatching(""))
      .withHeader("X-Correlation-ID", notMatching(""))
      .withHeader(X_FORWARDED_HOST, equalTo("MDTP"))
      .withRequestBody(equalToXml(requestBody))
      )

    maybeUnexpectedAuthToken foreach { unexpectedAuthToken =>
      verify(0, postRequestedFor(urlMatchingRequestPath).withHeader(AUTHORIZATION, equalTo(s"Bearer $unexpectedAuthToken")))
    }
  }
}
