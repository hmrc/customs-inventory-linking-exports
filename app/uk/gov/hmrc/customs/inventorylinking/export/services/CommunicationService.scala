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

package uk.gov.hmrc.customs.inventorylinking.export.services

import java.net.URLEncoder
import java.util.UUID
import javax.inject.{Inject, Singleton}

import org.joda.time.DateTime
import uk.gov.hmrc.customs.inventorylinking.export.connectors.{ApiSubscriptionFieldsConnector, MdgExportsConnector}
import uk.gov.hmrc.customs.inventorylinking.export.logging.ExportsLogger
import uk.gov.hmrc.customs.inventorylinking.export.model.{ApiSubscriptionKey, BadgeIdentifier, ConversationId}
import uk.gov.hmrc.customs.inventorylinking.export.xml.MdgPayloadDecorator
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.xml.NodeSeq
import uk.gov.hmrc.customs.inventorylinking.export.controllers.CustomHeaderNames._

@Singleton
class CommunicationService @Inject()(logger: ExportsLogger,
                                     connector: MdgExportsConnector,
                                     apiSubFieldsConnector: ApiSubscriptionFieldsConnector,
                                     wrapper: MdgPayloadDecorator,
                                     uuidService: UuidService,
                                     dateTimeProvider: DateTimeService,
                                     customsConfigService: CustomsConfigService) {

  private val apiContextEncoded = URLEncoder.encode(customsConfigService.apiDefinitionConfig.apiContext, "UTF-8")

  lazy val futureMaybeClientIdFromConfiguration: Future[Option[String]] = {
    Future.successful(customsConfigService.overridesConfig.clientId)
  }

  def prepareAndSend(inboundXml: NodeSeq, maybeBadgeIdentifier: Option[BadgeIdentifier])(implicit hc: HeaderCarrier): Future[ConversationId] = {
    val conversationId = uuidService.uuid()
    val correlationId = uuidService.uuid()
    val dateTime = dateTimeProvider.getUtcNow

    logger.info("preparing and sending payload")

    for {
      clientId <- futureClientId
      xmlToSend = preparePayload(inboundXml, conversationId, correlationId, clientId, maybeBadgeIdentifier, dateTime)
      conversationId <- connector.send(xmlToSend, dateTime, correlationId).map(_ => ConversationId(conversationId.toString))
    } yield conversationId
  }

  private def futureClientId(implicit hc: HeaderCarrier): Future[String] = {
    lazy val futureMaybeFromHeaders = Future.successful(findHeaderValue(API_SUBSCRIPTION_FIELDS_ID_HEADER_NAME))
    val foConfigOrHeader = orElse(futureMaybeClientIdFromConfiguration, futureMaybeFromHeaders)

    orElse(foConfigOrHeader, futureApiSubFieldsId) flatMap {
      case Some(clientId) => Future.successful(clientId)
      case _ =>
        val msg = "No value found for clientID."
        logger.error(msg)
        Future.failed(new IllegalStateException(msg))
    }
  }

  private def orElse(fo1: Future[Option[String]], fo2: => Future[Option[String]]): Future[Option[String]] = {
    fo1.flatMap[Option[String]] {
      case None => fo2
      case some => Future.successful(some)
    }
  }

  private def preparePayload(xml: NodeSeq, conversationId: UUID, correlationId: UUID, clientId: String, maybeBadgeIdentifier: Option[BadgeIdentifier], dateTime: DateTime)(implicit hc: HeaderCarrier): NodeSeq = {
    logger.debug(s"preparePayload called")
    wrapper.decorate(xml, conversationId.toString, correlationId.toString, clientId, maybeBadgeIdentifier, dateTime)
  }

  private def futureApiSubFieldsId(implicit hc: HeaderCarrier): Future[Option[String]] = {
    val maybeXClientId: Option[String] = findHeaderValue(X_CLIENT_ID_HEADER_NAME)

    maybeXClientId.fold[Future[Option[String]]](Future.successful(None)) { xClientId =>
      apiSubFieldsConnector.getSubscriptionFields(ApiSubscriptionKey(xClientId, apiContextEncoded, "1.0")) map (response => Some(response.fieldsId.toString))
    }
  }

  private def findHeaderValue(headerName: String)(implicit hc: HeaderCarrier): Option[String] = {
    hc.headers.collectFirst {
      case (`headerName`, headerValue) => headerValue
    }
  }
}
