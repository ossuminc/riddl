resolvers += Resolver.url(
  "GitHub Package Registry",
  url("https://maven.pkg.github.com/ossuminc/sbt-ossuminc")
)(Resolver.ivyStylePatterns)
// resolvers += "GitHub Package Registry" at "https://maven.pkg.github.com/ossuminc/_"
addSbtPlugin("com.ossuminc" % "sbt-ossuminc" % "0.20.3")

// This enables sbt-bloop to create bloop config files for Metals editors
// Uncomment locally if you use metals, otherwise don't slow down other
// people's builds by leaving it commented in the repo.
// addSbtPlugin("ch.epfl.scala" % "sbt-bloop" % "1.4.6")

ThisBuild / libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % "always"

libraryDependencies += "org.scala-js" %% "scalajs-env-jsdom-nodejs" % "1.0.0"
