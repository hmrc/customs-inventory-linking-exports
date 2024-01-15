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

package uk.gov.hmrc.customs.inventorylinking.export.controllers.actionbuilders

import java.net.URLEncoder

import javax.inject.{Inject, Singleton}
import play.api.mvc.{ActionRefiner, Result}
import uk.gov.hmrc.customs.api.common.controllers.ErrorResponse
import uk.gov.hmrc.customs.inventorylinking.export.connectors.ApiSubscriptionFieldsConnector
import uk.gov.hmrc.customs.inventorylinking.export.logging.ExportsLogger
import uk.gov.hmrc.customs.inventorylinking.export.model.actionbuilders.ActionBuilderModelHelper._
import uk.gov.hmrc.customs.inventorylinking.export.model.actionbuilders.{ApiSubscriptionFieldsRequest, ValidatedHeadersRequest}
import uk.gov.hmrc.customs.inventorylinking.export.model.{ApiSubscriptionFields, ApiSubscriptionKey}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Left
import scala.util.control.NonFatal

@Singleton
class ApiSubscriptionFieldsAction @Inject()(connector: ApiSubscriptionFieldsConnector,
                                            logger: ExportsLogger)
                                           (implicit ec: ExecutionContext) extends ActionRefiner[ValidatedHeadersRequest, ApiSubscriptionFieldsRequest] {

  protected def executionContext: ExecutionContext = ec
  private val apiContextEncoded = URLEncoder.encode("customs/inventory-linking/exports", "UTF-8")

  override def refine[A](vhr: ValidatedHeadersRequest[A]): Future[Either[Result, ApiSubscriptionFieldsRequest[A]]] = {
    implicit val i = vhr

    (connector.getSubscriptionFields(ApiSubscriptionKey(vhr.clientId, apiContextEncoded, vhr.requestedApiVersion)) map {
      fields: ApiSubscriptionFields =>
        Right(ApiSubscriptionFieldsRequest(
          vhr.conversationId,
          vhr.start,
          vhr.requestedApiVersion,
          vhr.clientId,
          fields,
          vhr.request
        ))
    }).recover[Either[Result, ApiSubscriptionFieldsRequest[A]]] {
      case NonFatal(e) =>
        logger.error(s"Subscriptions fields lookup call failed: ${e.getMessage}", e)
        Left(ErrorResponse.ErrorInternalServerError.XmlResult.withConversationId)
    }
  }
}
