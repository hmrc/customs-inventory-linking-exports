/*
 * Copyright 2020 HM Revenue & Customs
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

import org.scalatest._
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import util.ExternalServicesConfig
import util.externalservices.ExportsExternalServicesConfig

import scala.util.control.NonFatal
import scala.xml.{Node, Utility, XML}

trait ComponentTestSpec extends FeatureSpec with GivenWhenThen with GuiceOneAppPerSuite
  with BeforeAndAfterAll with BeforeAndAfterEach {

  override def fakeApplication(): Application = new GuiceApplicationBuilder().configure(Map(
    "microservice.services.auth.host" -> ExternalServicesConfig.Host,
    "microservice.services.auth.port" -> ExternalServicesConfig.Port,
    "microservice.services.mdg-exports.host" -> ExternalServicesConfig.Host,
    "microservice.services.mdg-exports.port" -> ExternalServicesConfig.Port,
    "microservice.services.mdg-exports.context" -> ExportsExternalServicesConfig.ExportsServiceContext,
    "microservice.services.mdg-exports.bearer-token" -> ExternalServicesConfig.AuthToken,
    "microservice.services.api-subscription-fields.host" -> ExternalServicesConfig.Host,
    "microservice.services.api-subscription-fields.port" -> ExternalServicesConfig.Port,
    "microservice.services.api-subscription-fields.context" -> ExportsExternalServicesConfig.ApiSubscriptionFieldsContext
  )).build()

  protected def stringToXml(s: String): Node = {
    val xml = try {
      XML.loadString(s)
    } catch {
      case NonFatal(thr) => fail("Not an xml: " + s, thr)
    }
    Utility.trim(xml)
  }
}
