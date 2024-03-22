import sbt._

object AppDependencies {
  object Version {
    val Bootstrap = "8.0.0"
  }

  private val testScope = "test,it"

  val compile = Seq(
    "org.typelevel" %% "cats-core" % "2.10.0",
    "uk.gov.hmrc"              %% "bootstrap-backend-play-28"           % Version.Bootstrap,
  )

  val test: Seq[ModuleID] = Seq(
    "org.scalatestplus.play"                    %% "scalatestplus-play"     % "5.1.0"     % testScope,
    "org.scalatestplus"                         %% "scalatestplus-mockito"  % "1.0.0-M2"  % testScope,
    "com.github.tomakehurst"                    %  "wiremock-standalone"    % "2.27.2"    % testScope,
    "org.mockito"                               % "mockito-core"            % "5.3.1"     % testScope,
    "com.vladsch.flexmark"                      % "flexmark-all"            % "0.35.10"   % testScope,
    "com.fasterxml.jackson.module"              %%  "jackson-module-scala"  % "2.15.0"    % testScope,
    "uk.gov.hmrc"                  %% "bootstrap-test-play-28"  % Version.Bootstrap      % testScope,
  )

}
