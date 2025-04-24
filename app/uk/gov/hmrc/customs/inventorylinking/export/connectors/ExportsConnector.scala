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

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.pattern.CircuitBreakerOpenException
import com.google.inject._
import play.api.http.HeaderNames.{ACCEPT, CONTENT_TYPE, DATE, X_FORWARDED_HOST}
import play.api.http.{MimeTypes, Status}
import uk.gov.hmrc.customs.inventorylinking.`export`.services.DateTimeService
import uk.gov.hmrc.customs.inventorylinking.export.config.ServiceConfigProvider
import uk.gov.hmrc.customs.inventorylinking.export.connectors.ExportsConnector._
import uk.gov.hmrc.customs.inventorylinking.export.logging.CdsLogger
import uk.gov.hmrc.customs.inventorylinking.export.logging.ExportsLogger
import uk.gov.hmrc.customs.inventorylinking.export.model.actionbuilders.ValidatedPayloadRequest
import uk.gov.hmrc.customs.inventorylinking.export.services.ExportsConfigService
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http._
import uk.gov.hmrc.http.client.HttpClientV2

import java.time.format.DateTimeFormatter
import java.time.{LocalDateTime, ZoneOffset}
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import scala.xml.NodeSeq

@Singleton
class ExportsConnector @Inject()(http: HttpClientV2,
                                 logger: ExportsLogger,
                                 serviceConfigProvider: ServiceConfigProvider,
                                 config: ExportsConfigService,
                                 override val cdsLogger: CdsLogger,
                                 override val actorSystem: ActorSystem)
                                (implicit override val ec: ExecutionContext) extends CircuitBreakerConnector with HttpErrorFunctions  with HeaderUtil {

  override val configKey = "mdg-exports"

  override lazy val numberOfCallsToTriggerStateChange = config.exportsCircuitBreakerConfig.numberOfCallsToTriggerStateChange
  override lazy val unstablePeriodDurationInMillis = config.exportsCircuitBreakerConfig.unstablePeriodDurationInMillis
  override lazy val unavailablePeriodDurationInMillis = config.exportsCircuitBreakerConfig.unavailablePeriodDurationInMillis

  def send[A](xml: NodeSeq,
              date: LocalDateTime,
              correlationId: UUID)
             (implicit vpr: ValidatedPayloadRequest[A], hc: HeaderCarrier): Future[Either[ExportsConnector.Error, HttpResponse]] = {
    val config = Option(serviceConfigProvider.getConfig(s"${vpr.requestedApiVersion.configPrefix}$configKey")).getOrElse(throw new IllegalArgumentException("config not found"))
    val bearerToken = "Bearer " + config.bearerToken.getOrElse(throw new IllegalStateException("no bearer token was found in config"))

    val exportHeaders = hc.extraHeaders ++
      getHeaders(date, correlationId) ++
      Seq(HeaderNames.authorisation -> bearerToken) ++
      getCustomsApiStubExtraHeaders(hc)

    case class Non2xxResponseException(status: Int) extends Throwable
    val url = config.url
    withCircuitBreaker {
      logger.debug(s"Posting inventory linking exports.\nurl = $url\npayload = \n${xml.toString}")
      implicit val hcWithoutAuth: HeaderCarrier = hc.copy(authorization = None)
      http
        .post(url"$url")(hcWithoutAuth)
//        .setHeader(exportHeaders)
        .withBody(xml.toString())
        .execute[HttpResponse]
        .map { response =>
          response.status match {
            case status if Status.isSuccessful(status) =>
              Right(response)
            case status => // Refactor out usage of exceptions 'eventually', but for now maintaining breakOnException() triggering behaviour
              throw Non2xxResponseException(status)
          }
        }
    }.recover {
      case _: CircuitBreakerOpenException =>
        Left(RetryError)
      case Non2xxResponseException(status) =>
        Left(Non2xxResponseError(status))
      case NonFatal(t) =>
        Left(UnexpectedError(t))
    }
  }

  private def getHeaders(date: LocalDateTime, correlationId: UUID) = {
    val utcDateFormat: DateTimeFormatter = new DateTimeService().utcFormattedDate
    Seq(
      (ACCEPT, MimeTypes.XML),
      (CONTENT_TYPE, MimeTypes.XML + "; charset=UTF-8"),
      (DATE, date.atOffset(ZoneOffset.UTC).format(utcDateFormat)),
      (X_FORWARDED_HOST, "MDTP"),
      ("X-Correlation-ID", correlationId.toString))
  }
}

object ExportsConnector {
  sealed trait Error

  case class Non2xxResponseError(status: Int) extends Error

  case object RetryError extends Error

  case class UnexpectedError(t: Throwable) extends Error
}
