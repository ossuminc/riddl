import sbt.Keys.scalaVersion
import sbtbuildinfo.BuildInfoOption.BuildTime
import sbtbuildinfo.BuildInfoOption.ToMap

// NEVER  SET  THIS: version := "0.1"
// IT IS HANDLED BY: sbt-dynver
dynverSeparator in ThisBuild := "-"
scalafmtOnCompile in ThisBuild := true
organization in ThisBuild := "com.yoppworks"
buildInfoOptions in ThisBuild := Seq(ToMap, BuildTime)
buildInfoKeys in ThisBuild := Seq[BuildInfoKey](
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
)

def standardScalaCOptions(is2_13: => Boolean) = {
  Seq(
    "-encoding",
    "utf8",
    "-Xfatal-warnings",
    "-deprecation",
    "-unchecked",
    "-language:implicitConversions",
    "-language:higherKinds",
    "-language:existentials",
    "-language:postfixOps",
    { if (is2_13) "-Wdead-code" else "" }
  )
}

lazy val root = (project in file("."))
  .enablePlugins(ParadoxPlugin)
  .enablePlugins(ParadoxSitePlugin)
  .enablePlugins(ParadoxMaterialThemePlugin)
  .enablePlugins(BuildInfoPlugin)
  .settings(
    name := "riddlc",
    mainClass := Some("com.yoppworks.ossum.riddl.RIDDL"),
    paradoxTheme := Some(builtinParadoxTheme("generic")),
    libraryDependencies ++= Seq(
      "com.github.scopt" %% "scopt" % "4.0.0-RC2",
      "com.typesafe" % "config" % "1.4.0"
    ),
    crossScalaVersions := Seq("2.13.1", "2.12.10"),
    scalacOptions ++= standardScalaCOptions(scalaVersion.value == "2.13.1"),
    buildInfoPackage := "com.yoppworks.ossum.riddl"
  )
  .dependsOn(translator)
  .aggregate(language, translator)

lazy val language = (project in file("language")).settings(
  name := "riddl-languge",
  crossScalaVersions := Seq("2.13.1", "2.12.10"),
  scalacOptions ++= standardScalaCOptions(scalaVersion.value == "2.13.1"),
  buildInfoPackage := "com.yoppworks.ossum.riddl.language",
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
    crossScalaVersions := Seq("2.13.1", "2.12.10"),
    scalacOptions ++= standardScalaCOptions(scalaVersion.value == "2.13.1"),
    buildInfoPackage := "com.yoppworks.ossum.riddl.translator",
    libraryDependencies ++= Seq(
      "org.scalactic" %% "scalactic" % "3.0.8",
      "org.scalatest" %% "scalatest" % "3.0.8" % "test",
      "org.scalacheck" %% "scalacheck" % "1.14.0" % "test",
      "com.github.pureconfig" %% "pureconfig" % "0.12.1"
    )
  )
  .dependsOn(language % "test->test;compile->compile")

lazy val idea = (project in file("idea-plugin"))
  .enablePlugins(BuildInfoPlugin)
  .settings(
    name := "riddl-idea-plugin",
    mainClass := Some("com.yoppworks.ossum.riddl.idea.plugin.Main"),
    libraryDependencies ++= Seq(
      "com.github.scopt" %% "scopt" % "4.0.0-RC2",
      "com.typesafe" % "config" % "1.4.0"
    ),
    buildInfoPackage := "com.yoppworks.ossum.riddl.idea.plugin",
    buildInfoOptions := Seq(ToMap, BuildTime)
  )
  .dependsOn(language)

lazy val sbt_riddl = (project in file("sbt-riddl"))
  .enablePlugins(SbtPlugin)
  .settings(
    name := "sbt-riddl",
    sbtPlugin := true,
    scalaVersion := "2.12.10",
    scalacOptions ++= standardScalaCOptions(false),
    buildInfoPackage := "com.yoppworks.ossum.riddl.sbt.plugin"
  )
  .dependsOn(translator)
