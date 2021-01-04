/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.customs.inventorylinking.export.controllers

import javax.inject.{Inject, Singleton}
import play.api.http.MimeTypes
import play.api.mvc._
import uk.gov.hmrc.customs.inventorylinking.export.connectors.CustomsMetricsConnector
import uk.gov.hmrc.customs.inventorylinking.export.model.CustomsMetricsRequest
import uk.gov.hmrc.customs.inventorylinking.export.controllers.actionbuilders._
import uk.gov.hmrc.customs.inventorylinking.export.logging.ExportsLogger
import uk.gov.hmrc.customs.inventorylinking.export.model.actionbuilders.ActionBuilderModelHelper._
import uk.gov.hmrc.customs.inventorylinking.export.model.actionbuilders.ValidatedPayloadRequest
import uk.gov.hmrc.customs.inventorylinking.export.services.BusinessService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.ExecutionContext

@Singleton
class InventoryLinkingExportController @Inject()(cc: ControllerComponents,
                                                 conversationIdAction: ConversationIdAction,
                                                 shutterCheckAction: ShutterCheckAction,
                                                 validateAndExtractHeadersAction: ValidateAndExtractHeadersAction,
                                                 fieldsAction: ApiSubscriptionFieldsAction,
                                                 authAction: AuthAction,
                                                 payloadValidationAction: PayloadValidationAction,
                                                 businessService: BusinessService,
                                                 metricsConnector: CustomsMetricsConnector,
                                                 logger: ExportsLogger)
                                                (implicit ec: ExecutionContext)
  extends BackendController(cc) {

  private def xmlOrEmptyBody: BodyParser[AnyContent] = BodyParser(rq => parse.xml(rq).map {
    case Right(xml) =>
      Right(AnyContentAsXml(xml))
    case _ =>
      Right(AnyContentAsEmpty)
  })

  def post(): Action[AnyContent] = (
    Action andThen
    conversationIdAction andThen
    shutterCheckAction andThen
    validateAndExtractHeadersAction andThen
    fieldsAction andThen
    authAction andThen
    payloadValidationAction
    )
    .async(bodyParser = xmlOrEmptyBody) {

      implicit vpr: ValidatedPayloadRequest[AnyContent] =>

        logger.debug(s"Request received. Payload = ${vpr.body.toString} headers = ${vpr.headers.headers}")
        logger.info("Inventory linking exports request received")

        businessService.send map {
          case Right(_) =>
            metricsConnector.post(CustomsMetricsRequest(
              "ILE", vpr.conversationId, vpr.start, conversationIdAction.timeService.zonedDateTimeUtc))
            Accepted.as(MimeTypes.XML).withConversationId
          case Left(errorResult) =>
            errorResult
        }

  }

}
