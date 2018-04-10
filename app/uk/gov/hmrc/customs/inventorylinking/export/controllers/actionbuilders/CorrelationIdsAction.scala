/*
 * Copyright 2018 HM Revenue & Customs
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

import javax.inject.{Inject, Singleton}

import play.api.mvc.{ActionTransformer, Request}
import uk.gov.hmrc.customs.inventorylinking.export.model.actionbuilders.CorrelationIdsRequest
import uk.gov.hmrc.customs.inventorylinking.export.services.CorrelationIdsService

import scala.concurrent.Future

@Singleton
class CorrelationIdsAction @Inject() (correlationIdService: CorrelationIdsService) extends ActionTransformer[Request, CorrelationIdsRequest] {

  override def transform[A](request: Request[A]): Future[CorrelationIdsRequest[A]] = {

    Future.successful(CorrelationIdsRequest(correlationIdService.conversation, correlationIdService.correlation, request))
  }
}