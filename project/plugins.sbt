addSbtPlugin("com.ossuminc" % "sbt-ossuminc" % "0.10.2-6-635025f3-20240727-1924")

// This enables sbt-bloop to create bloop config files for Metals editors
// Uncomment locally if you use metals, otherwise don't slow down other
// people's builds by leaving it commented in the repo.
// addSbtPlugin("ch.epfl.scala" % "sbt-bloop" % "1.4.6")

ThisBuild / libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % "always"
