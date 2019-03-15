/*
 * Copyright 2019 HM Revenue & Customs
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

import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.customs.inventorylinking.export.logging.ExportsLogger
import uk.gov.hmrc.customs.inventorylinking.export.model.{ApiSubscriptionFields, ApiSubscriptionKey}
import uk.gov.hmrc.customs.inventorylinking.export.model.actionbuilders.ValidatedHeadersRequest
import uk.gov.hmrc.customs.inventorylinking.export.services.ExportsConfigService
import uk.gov.hmrc.http.{HeaderCarrier, HttpException}
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ApiSubscriptionFieldsConnector @Inject()(http: HttpClient,
                                               logger: ExportsLogger,
                                               config: ExportsConfigService)
                                              (implicit ec: ExecutionContext) {

  def getSubscriptionFields[A](apiSubsKey: ApiSubscriptionKey)(implicit vhr: ValidatedHeadersRequest[A]): Future[ApiSubscriptionFields] = {
    val url = ApiSubscriptionFieldsPath.url(config.exportsConfig.apiSubscriptionFieldsBaseUrl, apiSubsKey)
    get(url)
  }

  private def get[A](url: String)(implicit vhr: ValidatedHeadersRequest[A]): Future[ApiSubscriptionFields] = {
    logger.debug(s"Getting fields id from api-subscription-fields service. url = $url")
    implicit val hc = HeaderCarrier()

    http.GET[ApiSubscriptionFields](url)
      .recoverWith {
        case httpError: HttpException => Future.failed(new RuntimeException(httpError))
      }
      .recoverWith {
        case e: Throwable =>
          logger.error(s"Call to get api subscription fields failed. url = $url", e)
          Future.failed(e)
      }
  }
}
