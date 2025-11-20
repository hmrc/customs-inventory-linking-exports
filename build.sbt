import com.typesafe.sbt.web.PathMapping
import com.typesafe.sbt.web.pipeline.Pipeline
import play.sbt.PlayImport.PlayKeys.playDefaultPort
import sbt.Keys.*
import sbt.Tests.{Group, SubProcess}
import sbt.{Test, inConfig}
import uk.gov.hmrc.DefaultBuildSettings.addTestReportOption
import uk.gov.hmrc.gitstamp.GitStampPlugin.*

import java.text.SimpleDateFormat
import java.util.Calendar
import scala.language.postfixOps

name := "customs-inventory-linking-exports"



def forkedJvmPerTestConfig(tests: Seq[TestDefinition], packages: String*): Seq[Group] =
  tests.groupBy(_.name.takeWhile(_ != '.')).filter(packageAndTests => packages contains packageAndTests._1) map {
    case (packg, theTests) =>
      Group(packg, theTests, SubProcess(ForkOptions()))
  } toSeq

lazy val microservice = (project in file("."))
  .enablePlugins(PlayScala)
  .enablePlugins(SbtDistributablesPlugin)
  .disablePlugins(sbt.plugins.JUnitXmlReportPlugin)
  .settings(
    playDefaultPort := 9823,
    scalaVersion := "3.3.6",
    commonSettings,
    unitTestSettings,
    it,
    scoverageSettings,
    scalacOptions ++= Seq(
      "-Wconf:src=routes/.*:s",
      "-Wconf:src=views/.*:s",
      "-Wconf:msg=Flag.*repeatedly:s"
    )
  )
  .settings(majorVersion := 1)
  .settings(playDefaultPort := 9823)


lazy val unitTestSettings =
  inConfig(Test)(Defaults.testTasks) ++
    Seq(
      Test / testOptions := Seq(Tests.Filter(unitTestFilter)),
      Test / unmanagedSourceDirectories := Seq((Test / baseDirectory).value / "test"),
      addTestReportOption(Test, "test-reports")
    )

lazy val it = (project in file("it"))
  .enablePlugins(PlayScala)
  .dependsOn(microservice % "test->test;compile->compile")
  .settings(
    scalaVersion := "3.3.6",
    majorVersion := 1,
    Test / testOptions := Seq(Tests.Filter(integrationComponentTestFilter)),
    Test / parallelExecution := false,
    addTestReportOption(Test, "int-comp-test-reports"),
    Test / testGrouping := forkedJvmPerTestConfig((Test / definedTests).value, "integration", "component"),
    libraryDependencies ++= AppDependencies.test
  )

lazy val commonSettings: Seq[Setting[_]] = gitStampSettings

lazy val scoverageSettings: Seq[Setting[_]] = Seq(
  coverageExcludedPackages := "<empty>;models/.data/..*;uk.gov.hmrc.customs.inventorylinking.views.*;models.*;config.*;.*(Reverse|AuthService|BuildInfo|Routes).*",
  coverageMinimumStmtTotal := 97,
  coverageFailOnMinimum := false,
  coverageHighlighting := true,
  Test / parallelExecution := false
)

def integrationComponentTestFilter(name: String): Boolean = (name startsWith "integration") || (name startsWith "component")
def unitTestFilter(name: String): Boolean = name startsWith "unit"

scalastyleConfig := baseDirectory.value / "project" / "scalastyle-config.xml"

Compile / unmanagedResourceDirectories += baseDirectory.value / "public"
(Runtime / managedClasspath) += (Assets / packageBin).value

libraryDependencies ++= AppDependencies.compile ++ AppDependencies.test

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
        println(s"Check the yaml renders correctly file://${dir.getParent}/${dir.getName}/application.yaml")
        println("")
        zipFile
      }
  zipFiles.pair(Path.relativeTo(targetDir)) ++ mappings
}

pipelineStages := Seq(zipXsds)


// TODO: unnecessary? To resolve a bug with version 2.x.x of the scoverage plugin - https://github.com/sbt/sbt/issues/6997
libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always