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

import uk.gov.hmrc.customs.inventorylinking.export.controllers.ErrorResponse

import java.net.URLEncoder

//import cats.implicits._
import play.api.mvc.{ActionRefiner, Result}
import uk.gov.hmrc.customs.inventorylinking.export.connectors.ApiSubscriptionFieldsConnector
import uk.gov.hmrc.customs.inventorylinking.export.logging.ExportsLogger
import uk.gov.hmrc.customs.inventorylinking.export.model.ApiSubscriptionKey
import uk.gov.hmrc.customs.inventorylinking.export.model.actionbuilders.ActionBuilderModelHelper._
import uk.gov.hmrc.customs.inventorylinking.export.model.actionbuilders.{ApiSubscriptionFieldsRequest, ValidatedHeadersRequest}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ApiSubscriptionFieldsAction @Inject()(connector: ApiSubscriptionFieldsConnector,
                                            logger: ExportsLogger)
                                           (implicit ec: ExecutionContext) extends ActionRefiner[ValidatedHeadersRequest, ApiSubscriptionFieldsRequest] {

  protected def executionContext: ExecutionContext = ec

  private val apiContextEncoded = URLEncoder.encode("customs/inventory-linking/exports", "UTF-8")

  override def refine[A](vhr: ValidatedHeadersRequest[A]): Future[Either[Result, ApiSubscriptionFieldsRequest[A]]] = {
    implicit val i = vhr

    connector.getSubscriptionFields(ApiSubscriptionKey(vhr.clientId, apiContextEncoded, vhr.requestedApiVersion))
      .map {
        case Some(fields) =>
          Right(ApiSubscriptionFieldsRequest(
            vhr.conversationId,
            vhr.start,
            vhr.requestedApiVersion,
            vhr.clientId,
            vhr.maybeAcceptanceTestScenario,
            fields,
            vhr.request
          ))
        case None =>
          Left(ErrorResponse.ErrorInternalServerError.XmlResult.withConversationId)
      }
  }
}
