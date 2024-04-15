import sbt._

object AppDependencies {

  val playVersion = "play-30"
  val bootstrap = "8.5.0"

  val compile = Seq(
    "org.typelevel"      %% "cats-core"                       % "2.10.0",
    "uk.gov.hmrc"        %% s"bootstrap-backend-$playVersion" % bootstrap,
  )

  val test: Seq[ModuleID] = Seq(
    "org.wiremock"                  % "wiremock-standalone"          % "3.5.2"    % Test,
    "org.mockito"                   % "mockito-core"                 % "5.11.0"   % Test,
    "com.fasterxml.jackson.module" %% "jackson-module-scala"         % "2.17.0"   % Test,
    "org.mockito"                  %% "mockito-scala-scalatest"      % "1.17.31"  % Test,
    "uk.gov.hmrc"                  %% s"bootstrap-test-$playVersion" % bootstrap  % Test
  )

}
