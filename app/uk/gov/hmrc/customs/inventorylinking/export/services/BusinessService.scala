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

package uk.gov.hmrc.customs.inventorylinking.export.services

import java.util.UUID

import javax.inject.{Inject, Singleton}
import org.joda.time.DateTime
import play.api.mvc.Result
import uk.gov.hmrc.circuitbreaker.UnhealthyServiceException
import uk.gov.hmrc.customs.api.common.controllers.ErrorResponse
import uk.gov.hmrc.customs.api.common.controllers.ErrorResponse.errorInternalServerError
import uk.gov.hmrc.customs.inventorylinking.export.connectors.ExportsConnector
import uk.gov.hmrc.customs.inventorylinking.export.logging.ExportsLogger
import uk.gov.hmrc.customs.inventorylinking.export.model._
import uk.gov.hmrc.customs.inventorylinking.export.model.actionbuilders.ActionBuilderModelHelper._
import uk.gov.hmrc.customs.inventorylinking.export.model.actionbuilders.ValidatedPayloadRequest
import uk.gov.hmrc.customs.inventorylinking.export.xml.PayloadDecorator
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Left
import scala.util.control.NonFatal
import scala.xml.NodeSeq

@Singleton
class BusinessService @Inject()(logger: ExportsLogger,
                                connector: ExportsConnector,
                                wrapper: PayloadDecorator,
                                dateTimeProvider: DateTimeService,
                                uniqueIdsService: UniqueIdsService,
                                customsConfigService: ExportsConfigService)
                               (implicit ec: ExecutionContext) {

  private val errorResponseServiceUnavailable = errorInternalServerError("This service is currently unavailable")

  def send[A](implicit vpr: ValidatedPayloadRequest[A], hc: HeaderCarrier): Future[Either[Result, Unit]] = {

    callBackend(SubscriptionFieldsId(vpr.apiSubscriptionFields.fieldsId.toString))
  }

  private def callBackend[A](subscriptionFieldsId: SubscriptionFieldsId)
                            (implicit vpr: ValidatedPayloadRequest[A], hc: HeaderCarrier): Future[Either[Result, Unit]] = {
    val dateTime = dateTimeProvider.getUtcNow
    val correlationId = uniqueIdsService.correlation
    val xmlToSend = preparePayload(vpr.xmlBody, subscriptionFieldsId, correlationId, dateTime)

    connector.send(xmlToSend, dateTime, UUID.fromString(correlationId.toString)).map{
      logger.info("Inventory linking export request processed successfully")
      _ => Right(())
    }.recover[Either[Result, Unit]]{
      case _: UnhealthyServiceException =>
        logger.error("unhealthy state entered")
        Left(errorResponseServiceUnavailable.XmlResult)
      case NonFatal(e) =>
        logger.error(s"Inventory linking export request failed: ${e.getMessage}", e)
        Left(ErrorResponse.ErrorInternalServerError.XmlResult.withConversationId)
    }
  }

  private def preparePayload[A](xml: NodeSeq, clientId: SubscriptionFieldsId, correlationId: CorrelationId, dateTime: DateTime)
                               (implicit vpr: ValidatedPayloadRequest[A], hc: HeaderCarrier): NodeSeq = {
    logger.debug("preparePayload called")
    wrapper.decorate(xml, clientId, correlationId, dateTime)
  }

}
