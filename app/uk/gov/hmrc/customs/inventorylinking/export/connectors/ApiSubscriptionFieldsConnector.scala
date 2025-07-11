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

package uk.gov.hmrc.customs.inventorylinking.export.connectors

import play.api.http.Status
import play.api.libs.json.Json
import uk.gov.hmrc.customs.inventorylinking.export.logging.ExportsLogger
import uk.gov.hmrc.customs.inventorylinking.export.model.actionbuilders.ValidatedHeadersRequest
import uk.gov.hmrc.customs.inventorylinking.export.model.{ApiSubscriptionFields, ApiSubscriptionKey}
import uk.gov.hmrc.customs.inventorylinking.export.services.ExportsConfigService
import uk.gov.hmrc.http.HttpReads.Implicits.readRaw
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, StringContextOps}

import java.net.URL
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ApiSubscriptionFieldsConnector @Inject()(http: HttpClientV2,
                                               logger: ExportsLogger,
                                               config: ExportsConfigService)
                                              (implicit ec: ExecutionContext) {
  def getSubscriptionFields[A](apiSubsKey: ApiSubscriptionKey)(implicit vhr: ValidatedHeadersRequest[A]): Future[Option[ApiSubscriptionFields]] = {
    val url = new URL(ApiSubscriptionFieldsPath.url(config.exportsConfig.apiSubscriptionFieldsBaseUrl, apiSubsKey))
    logger.debug(s"Getting fields id from api subscription fields service. url=$url")
    implicit val hc = HeaderCarrier()

    http.get(url"$url").execute
      .map { response =>
        response.status match {
          case status if Status.isSuccessful(status) =>
            Json.parse(response.body).asOpt[ApiSubscriptionFields] match {
              case Some(value) =>
                Some(value)
              case None =>
                logger.error(s"Could not parse subscription fields response. url=$url")
                None
            }
          case status =>
            logger.error(s"Subscriptions fields lookup call failed. url=$url HttpStatus=$status")
            None
        }
      }
  }
}
