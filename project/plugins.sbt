
ThisBuild / resolvers ++= Seq(
  "Artima Maven Repository" at "https://repo.artima.com/releases"
  // , "jetbrains-sbt" at "https://dl.bintray.com/jetbrains/sbt-plugins"
)

addSbtPlugin("com.timushev.sbt" % "sbt-updates" % "0.5.0")
addSbtPlugin("org.jetbrains" % "sbt-ide-settings" % "1.0.0")
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.4.4")
addSbtPlugin("de.heikoseeberger" % "sbt-header" % "5.6.5")
addSbtPlugin("com.dwijnand" % "sbt-dynver" % "4.0.0")
addSbtPlugin("com.typesafe.sbt" % "sbt-site" % "1.4.1")

addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.10.0")
addSbtPlugin("com.typesafe.sbt" % "sbt-git" % "1.0.2")
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.9.2")
addSbtPlugin("org.scalastyle" %% "scalastyle-sbt-plugin" % "1.0.0")
addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "1.9.9")
addSbtPlugin("com.github.sbt" % "sbt-pgp" % "2.1.2")


// This enables sbt-bloop to create bloop config files for Metals editors
// Uncomment locally if you use metals, otherwise don't slow down other
// people's builds by leaving it commented in the repo.
// addSbtPlugin("ch.epfl.scala" % "sbt-bloop" % "1.4.6")
