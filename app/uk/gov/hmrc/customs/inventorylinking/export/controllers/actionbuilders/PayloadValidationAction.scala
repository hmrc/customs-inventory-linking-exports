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

import javax.inject.{Inject, Singleton}
import play.api.mvc.{ActionRefiner, AnyContentAsXml, Result}
import uk.gov.hmrc.customs.inventorylinking.export.controllers.{ErrorResponse, ResponseContents}
import uk.gov.hmrc.customs.inventorylinking.export.logging.ExportsLogger
import uk.gov.hmrc.customs.inventorylinking.export.model.actionbuilders.ActionBuilderModelHelper._
import uk.gov.hmrc.customs.inventorylinking.export.model.actionbuilders.{AuthorisedRequest, ValidatedPayloadRequest}
import uk.gov.hmrc.customs.inventorylinking.export.services.XmlValidationService

import scala.collection.immutable.Seq
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import scala.xml.{NodeSeq, SAXException}

@Singleton
class PayloadValidationAction @Inject() (xmlValidationService: XmlValidationService,
                                         logger: ExportsLogger)
                                        (implicit ec: ExecutionContext) extends ActionRefiner[AuthorisedRequest, ValidatedPayloadRequest] {

  protected def executionContext: ExecutionContext = ec

  override def refine[A](ar: AuthorisedRequest[A]): Future[Either[Result, ValidatedPayloadRequest[A]]] = {
    implicit val implicitAr = ar
    lazy val errorMessage = "Request body does not contain a well-formed XML document."

    ar.body match {
      case content: AnyContentAsXml => validateXml(content.xml)
      case _ => Future.successful(Left(errorResponse(errorMessage)))
    }
  }

  private def validateXml[A](xml: NodeSeq)(implicit ar: AuthorisedRequest[A]): Future[Either[Result, ValidatedPayloadRequest[A]]] = {
    xmlValidationService.validate(xml)
      .map { _ =>
        logger.debug("XML payload validated")
        Right(ar.toValidatedPayloadRequest(xml))
      }
      .recover {
        case saxe: SAXException =>
          val msg = "Payload is not valid according to schema"
          logger.debug(s"$msg:\n${xml.toString()}", saxe)
          Left(errorResponse(msg, xmlValidationErrors(saxe): _*))
        case NonFatal(e) =>
          val msg = "Error validating payload."
          logger.debug(s"$msg:\n${xml.toString()}", e)
          logger.warn(msg)
          Left(ErrorResponse.ErrorInternalServerError.XmlResult.withConversationId)
      }
  }

  private def xmlValidationErrors(saxe: SAXException): Seq[ResponseContents] = {
    @annotation.tailrec
    def loop(thr: Exception, acc: List[ResponseContents]): List[ResponseContents] = {
      val newAcc = ResponseContents("xml_validation_error", thr.getMessage) :: acc
      thr match {
        case saxError: SAXException if Option(saxError.getException).isDefined => loop(saxError.getException, newAcc)
        case _ => newAcc
      }
    }

    loop(saxe, Nil)
  }

  private def errorResponse[A](msg: String, contents: ResponseContents*)(implicit ar: AuthorisedRequest[A]): Result = {
    logger.warn(msg)
    ErrorResponse.errorBadRequest(msg)
      .withErrors(contents: _*)
      .XmlResult
      .withConversationId
  }
}
