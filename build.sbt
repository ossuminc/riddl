name := "idddl"

// Never set this, handled by sbt-dynver: version := "0.1"

scalaVersion := "2.12.10"
scalafmtOnCompile := true
dynverSeparator in ThisBuild := "-"

lazy val parser = (project in file("parser")).settings(
  name := "idddl-parser",
  resolvers ++= Seq(
    "Artima Maven Repository" at "https://repo.artima.com/releases"
  ),
  libraryDependencies ++= Seq(
    "com.lihaoyi" %% "fastparse" % "2.1.3",
    "org.scalactic" %% "scalactic" % "3.0.8",
    "org.scalatest" %% "scalatest" % "3.0.8" % "test",
    "org.scalacheck" %% "scalacheck" % "1.14.0" % "test"
  )
)

lazy val root = (project in file("."))
  .enablePlugins(ParadoxPlugin)
  .enablePlugins(ParadoxSitePlugin)
  .enablePlugins(ParadoxMaterialThemePlugin)
  .settings(
    name := "idddl",
    paradoxTheme := Some(builtinParadoxTheme("generic"))
  )
  .dependsOn(parser)
  .aggregate(parser)
