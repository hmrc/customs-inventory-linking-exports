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

package uk.gov.hmrc.customs.inventorylinking.export.services

import play.api.mvc.Result
import play.mvc.Http.Status.{FORBIDDEN, INTERNAL_SERVER_ERROR}
import uk.gov.hmrc.customs.inventorylinking.export.controllers.ErrorResponse
import uk.gov.hmrc.customs.inventorylinking.export.controllers.ErrorResponse.{ErrorInternalServerError, ErrorPayloadForbidden, errorInternalServerError}
import uk.gov.hmrc.customs.inventorylinking.export.connectors.ExportsConnector.RetryError
import uk.gov.hmrc.customs.inventorylinking.export.connectors.ExportsConnector
import uk.gov.hmrc.customs.inventorylinking.export.connectors.ExportsConnector._
import uk.gov.hmrc.customs.inventorylinking.export.logging.ExportsLogger
import uk.gov.hmrc.customs.inventorylinking.export.model._
import uk.gov.hmrc.customs.inventorylinking.export.model.actionbuilders.ActionBuilderModelHelper._
import uk.gov.hmrc.customs.inventorylinking.export.model.actionbuilders.ValidatedPayloadRequest
import uk.gov.hmrc.customs.inventorylinking.export.xml.PayloadDecorator
import uk.gov.hmrc.http.HeaderCarrier

import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class BusinessService @Inject()(logger: ExportsLogger,
                                connector: ExportsConnector,
                                wrapper: PayloadDecorator,
                                dateTimeProvider: DateTimeService,
                                uniqueIdsService: UniqueIdsService,
                                configService: ExportsConfigService)
                               (implicit ec: ExecutionContext) {
  def send[A](implicit vpr: ValidatedPayloadRequest[A], hc: HeaderCarrier): Future[Either[Result, Unit]] = {
    val subscriptionFieldsId = SubscriptionFieldsId(vpr.apiSubscriptionFields.fieldsId.toString)
    val dateTime = dateTimeProvider.getUtcNow
    val correlationId = uniqueIdsService.correlation
    val xmlToSend = wrapper.decorate(vpr.xmlBody, subscriptionFieldsId, correlationId, dateTime)

    connector.send(xmlToSend, dateTime, UUID.fromString(correlationId.toString)).map {
      case Right(_) =>
        Right(())
      case Left(RetryError) =>
        handleError("Unhealthy state entered", INTERNAL_SERVER_ERROR, errorInternalServerError("This service is currently unavailable"))
      case Left(Non2xxResponseError(FORBIDDEN)) =>
        handleError("Forbidden", FORBIDDEN, ErrorPayloadForbidden)
      case Left(Non2xxResponseError(status)) =>
        handleError(s"Received status code [$status]", INTERNAL_SERVER_ERROR, ErrorInternalServerError)
      case Left(UnexpectedError(t)) =>
        handleError(s"Unexpected error: ${t.getMessage}", INTERNAL_SERVER_ERROR, ErrorInternalServerError)
    }
  }

  private def handleError[A](message: String, statusToReturn: Int, errorResponse: ErrorResponse)
                            (implicit vpr: ValidatedPayloadRequest[A]): Left[Result, Nothing] = {
    logger.error(s"exports connector call failed: $message, returning status code [$statusToReturn]")
    Left(errorResponse.XmlResult.withConversationId)
  }
}
