import sbt._

object AppDependencies {

  val playVersion = "play-30"
  val bootstrap = "10.9.0"

  val compile: Seq[ModuleID] = Seq(
    "org.typelevel"      %% "cats-core"                       % "2.13.0",
    "uk.gov.hmrc"        %% s"bootstrap-backend-$playVersion" % bootstrap,
  )

  val test: Seq[ModuleID] = Seq(
    "org.wiremock"                  % "wiremock-standalone"          % "3.13.2"    % Test,
    "org.mockito"                   % "mockito-core"                 % "5.24.0"    % Test,
    "com.fasterxml.jackson.module"  %% "jackson-module-scala"        % "2.23.0"    % Test,
    "org.scalatestplus"             %% "mockito-4-11"                % "3.2.18.0"  % Test,
    "uk.gov.hmrc"                   %% s"bootstrap-test-$playVersion"% bootstrap   % Test
  )

}
