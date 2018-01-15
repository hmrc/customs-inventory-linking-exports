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

package unit.services

import java.io.FileNotFoundException

import org.mockito.ArgumentMatchers.{eq => ameq}
import org.mockito.Mockito.{reset, verify, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mockito.MockitoSugar
import org.scalatest.prop.TableDrivenPropertyChecks._
import play.api.Configuration
import uk.gov.hmrc.customs.inventorylinking.export.services.XmlValidationService
import uk.gov.hmrc.play.test.UnitSpec
import util.TestData._
import util.XMLTestData._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.xml.{Node, SAXException}

class XmlValidationServiceSpec extends UnitSpec with MockitoSugar with BeforeAndAfterEach {

  private val MockConfiguration = mock[Configuration]
  private val MockXml = mock[Node]

  private def testService(test: XmlValidationService => Unit) = {
    test(new XmlValidationService(MockConfiguration))
  }

  override protected def beforeEach() {
    reset(MockConfiguration)
    when(MockConfiguration.getStringSeq("xsd.locations")).thenReturn(Some(xsdLocations))
    when(MockConfiguration.getInt("xml.max-errors")).thenReturn(None)
  }

  "XmlValidationService" should {
    "get location of xsd resource files from configuration" in testService { xmlValidationService =>
      await(xmlValidationService.validate(ValidInventoryLinkingMovementRequestXML))
      verify(MockConfiguration).getStringSeq(ameq("xsd.locations"))
    }

    "fail the future when in configuration there are no locations of xsd resource files" in testService {
      xmlValidationService =>
        when(MockConfiguration.getStringSeq("xsd.locations")).thenReturn(None)

        val caught = intercept[IllegalStateException] {
          await(xmlValidationService.validate(MockXml))
        }
        caught.getMessage shouldBe "application.conf is missing mandatory property 'xsd.locations'"
    }

    "fail the future when in configuration there is an empty list for locations of xsd resource files" in testService {
      xmlValidationService =>
        when(MockConfiguration.getStringSeq("xsd.locations")).thenReturn(Some(Nil))

        val caught = intercept[IllegalStateException] {
          await(xmlValidationService.validate(MockXml))
        }
        caught.getMessage shouldBe "application.conf is missing mandatory property 'xsd.locations'"
    }

    "fail the future when a configured xsd resource file cannot be found" in testService { xmlValidationService =>
      when(MockConfiguration.getStringSeq("xsd.locations")).thenReturn(Some(List("there/is/no/such/file")))

      val caught = intercept[FileNotFoundException] {
        await(xmlValidationService.validate(MockXml))
      }
      caught.getMessage shouldBe "XML Schema resource file: there/is/no/such/file"
    }

    forAll(xmlRequests) { (linkingType, xml) => {
        s"successfully validate a correct $linkingType" in testService { xmlValidationService =>
          val result = await(xmlValidationService.validate(xml))
          result should be(())
        }
      }
    }

    "fail the future with SAXException when there is an error in XML" in testService { xmlValidationService =>
      val caught = intercept[SAXException] {
        await(xmlValidationService.validate(InvalidXML))
      }
      caught.getMessage shouldBe "cvc-complex-type.3.2.2: Attribute 'foo' is not allowed to appear in element 'inventoryLinkingMovementRequest'."
      Option(caught.getException) shouldBe None
    }

    "fail the future with wrapped SAXExceptions when there are multiple errors in XML" in testService { xmlValidationService =>
      val caught = intercept[SAXException] {
        await(xmlValidationService.validate(InvalidXMLWith3Errors))
      }
      caught.getMessage shouldBe "cvc-type.3.1.2: Element 'goodsLocation' is a simple type, so it must have no element information item [children]."

      Option(caught.getException) shouldBe 'nonEmpty
      val wrapped1 = caught.getException
      wrapped1.getMessage shouldBe "cvc-type.3.1.1: Element 'goodsLocation' is a simple type, so it cannot have attributes, excepting those whose namespace name is identical to 'http://www.w3.org/2001/XMLSchema-instance' and whose [local name] is one of 'type', 'nil', 'schemaLocation' or 'noNamespaceSchemaLocation'. However, the attribute, 'random' was found."
      wrapped1.isInstanceOf[SAXException] shouldBe true

      Option(wrapped1.asInstanceOf[SAXException].getException) shouldBe 'nonEmpty
      val wrapped2 = wrapped1.asInstanceOf[SAXException].getException
      wrapped2.getMessage shouldBe "cvc-complex-type.3.2.2: Attribute 'foo' is not allowed to appear in element 'inventoryLinkingMovementRequest'."
      wrapped2.isInstanceOf[SAXException] shouldBe true

      Option(wrapped2.asInstanceOf[SAXException].getException) shouldBe None
    }

    "fail the future with configured number of wrapped SAXExceptions when there are multiple errors in XML" in testService {
      xmlValidationService =>
        when(MockConfiguration.getInt("xml.max-errors")).thenReturn(Some(2))

        val caught = intercept[SAXException] {
          await(xmlValidationService.validate(InvalidXMLWith3Errors))
        }
        verify(MockConfiguration).getInt("xml.max-errors")

        caught.getMessage shouldBe "cvc-type.3.1.1: Element 'goodsLocation' is a simple type, so it cannot have attributes, excepting those whose namespace name is identical to 'http://www.w3.org/2001/XMLSchema-instance' and whose [local name] is one of 'type', 'nil', 'schemaLocation' or 'noNamespaceSchemaLocation'. However, the attribute, 'random' was found."

        Option(caught.getException) shouldBe 'nonEmpty
        val wrapped1 = caught.getException
        wrapped1.getMessage shouldBe "cvc-complex-type.3.2.2: Attribute 'foo' is not allowed to appear in element 'inventoryLinkingMovementRequest'."
        wrapped1.isInstanceOf[SAXException] shouldBe true

        Option(wrapped1.asInstanceOf[SAXException].getException) shouldBe None
    }

    "fail the future with system error when a configured maximum of xml errors is not a positive number" in testService {
      xmlValidationService =>
        when(MockConfiguration.getInt("xml.max-errors")).thenReturn(Some(0))

        val caught = intercept[IllegalArgumentException] {
          await(xmlValidationService.validate(MockXml))
        }
        caught.getMessage shouldBe "requirement failed: maxErrors should be a positive number but 0 was provided instead."
    }
  }

}
