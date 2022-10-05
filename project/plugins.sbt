resolvers += "HMRC-open-artefacts-maven" at "https://open.artefacts.tax.service.gov.uk/maven2"
resolvers += Resolver.url("HMRC-open-artefacts-ivy", url("https://open.artefacts.tax.service.gov.uk/ivy2"))(Resolver.ivyStylePatterns)
resolvers += Resolver.jcenterRepo

addSbtPlugin("com.github.sbt"    %  "sbt-release"           % "1.0.15")
addSbtPlugin("com.typesafe.play" %  "sbt-plugin"            % "2.8.16")
addSbtPlugin("uk.gov.hmrc"       %  "sbt-settings"          % "4.12.0")
addSbtPlugin("uk.gov.hmrc"       %  "sbt-distributables"    % "2.1.0")
addSbtPlugin("uk.gov.hmrc"       %  "sbt-git-stamp"         % "6.2.0")
addSbtPlugin("net.virtual-void"  %  "sbt-dependency-graph"  % "0.10.0-RC1")
addSbtPlugin("org.scoverage"     %  "sbt-scoverage"         % "1.8.1")
addSbtPlugin("org.scalastyle"    %% "scalastyle-sbt-plugin" % "1.0.0")
addSbtPlugin("uk.gov.hmrc"       %  "sbt-auto-build"        % "3.8.0")
addSbtPlugin("net.vonbuchholtz"  %  "sbt-dependency-check"  % "3.1.1")
addSbtPlugin("ch.epfl.scala"     %  "sbt-scalafix"          % "0.9.18-1")



