import sbt.Keys.scalaVersion
import sbtbuildinfo.BuildInfoOption.BuildTime
import sbtbuildinfo.BuildInfoOption.ToMap

name := "riddl"

// NEVER  SET  THIS: version := "0.1"
// IT IS HANDLED BY: sbt-dynver
dynverSeparator in ThisBuild := "-"
scalafmtOnCompile in ThisBuild := true
scalaVersion in ThisBuild := "2.13.1"
scalacOptions in ThisBuild ++= Seq(
  "-encoding",
  "utf8",
  "-Xfatal-warnings",
  "-deprecation",
  "-unchecked",
  "-language:implicitConversions",
  "-language:higherKinds",
  "-language:existentials",
  "-language:postfixOps",
  "-Wdead-code"
)

lazy val language = (project in file("language")).settings(
  name := "riddl-languge",
  libraryDependencies ++= Seq(
    "org.typelevel" %% "cats-core" % "2.0.0",
    "com.lihaoyi" %% "fastparse" % "2.1.3",
    "org.scalactic" %% "scalactic" % "3.0.8",
    "org.scalatest" %% "scalatest" % "3.0.8" % "test",
    "org.scalacheck" %% "scalacheck" % "1.14.0" % "test"
  )
)

lazy val translator = (project in file("translator"))
  .settings(
    name := "riddl-translator",
    libraryDependencies ++= Seq(
      "org.scalactic" %% "scalactic" % "3.0.8",
      "org.scalatest" %% "scalatest" % "3.0.8" % "test",
      "org.scalacheck" %% "scalacheck" % "1.14.0" % "test"
    )
  )
  .dependsOn(language)

lazy val root = (project in file("."))
  .enablePlugins(ParadoxPlugin)
  .enablePlugins(ParadoxSitePlugin)
  .enablePlugins(ParadoxMaterialThemePlugin)
  .enablePlugins(BuildInfoPlugin)
  .settings(
    name := "riddl",
    mainClass := Some("com.yoppworks.ossum.riddl.RIDDL"),
    paradoxTheme := Some(builtinParadoxTheme("generic")),
    libraryDependencies ++= Seq(
      "com.github.scopt" %% "scopt" % "4.0.0-RC2",
      "com.typesafe" % "config" % "1.4.0"
    ),
    buildInfoKeys := Seq[BuildInfoKey](
      name,
      normalizedName,
      description,
      homepage,
      startYear,
      organization,
      organizationName,
      organizationHomepage,
      version,
      scalaVersion,
      sbtVersion
    ),
    buildInfoPackage := "com.yoppworks.ossum.riddl",
    buildInfoOptions := Seq(ToMap, BuildTime)
  )
  .dependsOn(language)
  .aggregate(language)
