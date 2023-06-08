import AppDependencies._
import com.typesafe.sbt.web.PathMapping
import com.typesafe.sbt.web.pipeline.Pipeline
import play.sbt.PlayImport.PlayKeys.playDefaultPort
import sbt.Keys._
import sbt.Tests.{Group, SubProcess}
import sbt.{Resolver, Test, inConfig, _}
import uk.gov.hmrc.DefaultBuildSettings.{addTestReportOption, targetJvm}
import uk.gov.hmrc.gitstamp.GitStampPlugin._
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin._
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin.publishingSettings

import java.text.SimpleDateFormat
import java.util.Calendar
import scala.language.postfixOps

name := "customs-inventory-linking-exports"
scalaVersion := "2.12.13"
targetJvm := "jvm-1.8"

lazy val CdsIntegrationComponentTest = config("it") extend Test

val testConfig = Seq(CdsIntegrationComponentTest, Test)

def forkedJvmPerTestConfig(tests: Seq[TestDefinition], packages: String*): Seq[Group] =
  tests.groupBy(_.name.takeWhile(_ != '.')).filter(packageAndTests => packages contains packageAndTests._1) map {
    case (packg, theTests) =>
      Group(packg, theTests, SubProcess(ForkOptions()))
  } toSeq

lazy val testAll = TaskKey[Unit]("test-all")
lazy val allTest = Seq(testAll := (CdsIntegrationComponentTest / test).dependsOn(Test / test).value)

lazy val silencerGlobalFilters = List(
  "Unused import",
  "private method apply in object \\w+ is never used"
)

lazy val microservice = (project in file("."))
  .enablePlugins(PlayScala)
  .enablePlugins(SbtDistributablesPlugin)
  .disablePlugins(sbt.plugins.JUnitXmlReportPlugin)
  .configs(testConfig: _*)
  .settings(playDefaultPort := 9823)
  .settings(
    commonSettings,
    unitTestSettings,
    integrationComponentTestSettings,
    playPublishingSettings,
    allTest,
    scoverageSettings
  )
  .settings(majorVersion := 1)
  .settings(scalacOptions += "-P:silencer:pathFilters=routes")
  .settings(scalacOptions += s"-P:silencer:globalFilters=${silencerGlobalFilters.mkString(";")}")


lazy val unitTestSettings =
  inConfig(Test)(Defaults.testTasks) ++
    Seq(
      Test / testOptions := Seq(Tests.Filter(unitTestFilter)),
      Test / unmanagedSourceDirectories := Seq((Test / baseDirectory).value / "test"),
      addTestReportOption(Test, "test-reports")
    )

lazy val integrationComponentTestSettings =
  inConfig(CdsIntegrationComponentTest)(Defaults.testTasks) ++
    Seq(
      CdsIntegrationComponentTest / testOptions := Seq(Tests.Filter(integrationComponentTestFilter)),
      CdsIntegrationComponentTest / parallelExecution := false,
      addTestReportOption(CdsIntegrationComponentTest, "int-comp-test-reports"),
      CdsIntegrationComponentTest / testGrouping := forkedJvmPerTestConfig((Test / definedTests).value, "integration", "component")
    )

lazy val commonSettings: Seq[Setting[_]] = publishingSettings ++ gitStampSettings

lazy val playPublishingSettings: Seq[sbt.Setting[_]] = publishingSettings

lazy val scoverageSettings: Seq[Setting[_]] = Seq(
  coverageExcludedPackages := "<empty>;models/.data/..*;uk.gov.hmrc.customs.inventorylinking.views.*;models.*;config.*;.*(Reverse|AuthService|BuildInfo|Routes).*",
  coverageMinimumStmtTotal := 98,
  coverageFailOnMinimum := false,
  coverageHighlighting := true,
  Test / parallelExecution := false
)

def integrationComponentTestFilter(name: String): Boolean = (name startsWith "integration") || (name startsWith "component")
def unitTestFilter(name: String): Boolean = name startsWith "unit"

scalastyleConfig := baseDirectory.value / "project" / "scalastyle-config.xml"

val compileDependencies = Seq(customsApiCommon, silencerLib, silencerPlugin)

val testDependencies = Seq(scalaTestPlusPlay, scalatestplusMockito, wireMock, mockito, flexmark, Jackson, customsApiCommonTests)

Compile / unmanagedResourceDirectories += baseDirectory.value / "public"
(Runtime / managedClasspath) += (Assets / packageBin).value

libraryDependencies ++= compileDependencies ++ testDependencies

// Task to create a ZIP file containing all xsds for each version, under the version directory
val zipXsds = taskKey[Pipeline.Stage]("Zips up all inventory linking exports XSDs")

zipXsds := { mappings: Seq[PathMapping] =>
  val targetDir = WebKeys.webTarget.value / "zip"
  val zipFiles: Iterable[java.io.File] =
    ((Assets / resourceDirectory).value / "api" / "conf")
      .listFiles
      .filter(_.isDirectory)
      .map { dir =>
        val xsdPaths = Path.allSubpaths(dir / "schemas")
        val exampleMessagesFilter = new SimpleFileFilter(_.getPath.contains("/annotated_XML_samples/"))
        val exampleMessagesPaths = Path.selectSubpaths(dir / "examples", exampleMessagesFilter)
        val zipFile = targetDir / "api" / "conf" / dir.getName / "inventory-linking-exports-schemas.zip"
        IO.zip(xsdPaths ++ exampleMessagesPaths, zipFile, None)
        val sizeInMb = (BigDecimal(zipFile.length) / BigDecimal(1024 * 1024)).setScale(1, BigDecimal.RoundingMode.UP)
        println(s"Created zip $zipFile")
        val today = Calendar.getInstance().getTime()
        val dateFormat = new SimpleDateFormat("dd/MM/YYYY")
        val lastUpdated = dateFormat.format(today)
        println(s"Update the file size in ${dir.getParent}/${dir.getName}/docs/schema.md to be [ZIP, ${sizeInMb}MB last updated $lastUpdated]")
        println(s"Check the raml renders correctly file://${dir.getParent}/${dir.getName}/application.raml")
        println("")
        zipFile
      }
  zipFiles.pair(Path.relativeTo(targetDir)) ++ mappings
}

pipelineStages := Seq(zipXsds)
