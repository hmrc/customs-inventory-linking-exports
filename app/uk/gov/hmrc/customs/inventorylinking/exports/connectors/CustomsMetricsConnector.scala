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

package uk.gov.hmrc.customs.inventorylinking.exports.connectors

import play.api.libs.json.Json
import play.mvc.Http.HeaderNames.{ACCEPT, CONTENT_TYPE}
import play.mvc.Http.MimeTypes.JSON
import uk.gov.hmrc.customs.inventorylinking.exports.logging.ExportsLogger
import uk.gov.hmrc.customs.inventorylinking.exports.model.CustomsMetricsRequest
import uk.gov.hmrc.customs.inventorylinking.exports.model.actionbuilders.HasConversationId
import uk.gov.hmrc.customs.inventorylinking.exports.services.ExportsConfigService
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpErrorFunctions, HttpException, HttpResponse, StringContextOps}
import play.api.libs.ws.JsonBodyWritables.writeableOf_JsValue

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@Singleton
class CustomsMetricsConnector @Inject()(http: HttpClientV2,
                                        logger: ExportsLogger,
                                        config: ExportsConfigService)
                                       (implicit ec: ExecutionContext) extends HttpErrorFunctions {

  private implicit val hc: HeaderCarrier = HeaderCarrier(
    extraHeaders = Seq(ACCEPT -> JSON, CONTENT_TYPE -> JSON)
  )

  def post[A](request: CustomsMetricsRequest)(implicit hasConversationId: HasConversationId): Future[Unit] = {
    val url = config.exportsConfig.customsMetricsBaseUrl
    logger.debug(s"Sending request to customs metrics. url=$url payload=${request.toString}")
    http
      .post(url"$url")
      .withBody(Json.toJson(request))
      .execute[HttpResponse]
      .map { response =>
      response.status match {
        case status if is2xx(status) =>
          logger.debug("customs metrics sent successfully")
        case status => //1xx, 3xx, 4xx, 5xx
          logger.error(s"Call to customs metrics failed. url=$url, status=$status, error=received a non 2XX response")
      }
      ()
    }.recoverWith {
      case httpError: HttpException =>
        logger.error(s"Call to customs metrics failed. url=$url, status=${httpError.responseCode}, error=${httpError.message}")
        Future.failed(new RuntimeException(httpError))
      case NonFatal(e) =>
        logger.error(s"Call to customs metrics failed. url=$url")
        Future.failed(e)
    }
  }
}
