import sbt._

object AppDependencies {

  val hmrcTestVersion = "3.9.0-play-26"
  val scalaTestVersion = "3.0.8"
  val scalatestplusVersion = "3.1.2"
  val mockitoVersion = "3.0.0"
  val wireMockVersion = "2.23.2"
  val customsApiCommonVersion = "1.42.0"
  val circuitBreakerVersion = "3.3.0"
  val testScope = "test,it"

  val hmrcTest = "uk.gov.hmrc" %% "hmrctest" % hmrcTestVersion % testScope

  val scalaTest = "org.scalatest" %% "scalatest" % scalaTestVersion % testScope

  val scalaTestPlusPlay = "org.scalatestplus.play" %% "scalatestplus-play" % scalatestplusVersion % testScope

  val wireMock = "com.github.tomakehurst" % "wiremock-jre8" % wireMockVersion % testScope

  val mockito =  "org.mockito" % "mockito-core" % mockitoVersion % testScope

  val customsApiCommon = "uk.gov.hmrc" %% "customs-api-common" % customsApiCommonVersion withSources()

  val customsApiCommonTests = "uk.gov.hmrc" %% "customs-api-common" % customsApiCommonVersion % testScope classifier "tests"

  val circuitBreaker = "uk.gov.hmrc" %% "reactive-circuit-breaker" % circuitBreakerVersion
}
