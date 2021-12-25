import sbt.Keys.scalaVersion

import sbtbuildinfo.BuildInfoOption.BuildTime
import sbtbuildinfo.BuildInfoOption.ToMap

Global / onChangedBuildSource := ReloadOnSourceChanges
ThisBuild / versionScheme := Some("semver-spec")

// NEVER  SET  THIS: version := "0.1"
// IT IS HANDLED BY: sbt-dynver
ThisBuild / dynverSeparator := "-"
ThisBuild / scalafmtOnCompile := true
ThisBuild / organization := "com.yoppworks"
ThisBuild / scalaVersion := "2.13.7"
buildInfoOptions := Seq(ToMap, BuildTime)
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
)

lazy val scala2_13_Options = Seq(
  "-target:17",
  "-Xsource:3",
  "-Wdead-code",
  "-deprecation",
  "-feature",
  "-Werror",
  "-Wunused:imports",   // Warn if an import selector is not referenced.
  "-Wunused:patvars", // Warn if a variable bound in a pattern is unused.
  "-Wunused:privates", // Warn if a private member is unused.
  "-Wunused:locals", // Warn if a local definition is unused.
  "-Wunused:explicits", // Warn if an explicit parameter is unused.
  "-Wunused:implicits", // Warn if an implicit parameter is unused.
  "-Wunused:params", // Enable -Wunused:explicits,implicits.
  "-Xlint:nonlocal-return", // A return statement used an exception for flow control.
  "-Xlint:implicit-not-found", // Check @implicitNotFound and @implicitAmbiguous messages.
  "-Xlint:serial", // @SerialVersionUID on traits and non-serializable classes.
  "-Xlint:valpattern", // Enable pattern checks in val definitions.
  "-Xlint:eta-zero", // Warn on eta-expansion (rather than auto-application) of zero-ary method.
  "-Xlint:eta-sam", // Warn on eta-expansion to meet a Java-defined functional
                    // interface that is not explicitly annotated with @FunctionalInterface.
  "-Xlint:deprecation" // Enable linted deprecations.
)

lazy val compileCheck = taskKey[Unit]("compile and then scalastyle")

lazy val riddl = (project in file(".")).settings(publish := {}, publishLocal := {})
  .aggregate(language, translator, riddlc, doc, `sbt-riddl` )

lazy val doc = project.in(file("doc")).enablePlugins(SitePlugin).enablePlugins(HugoPlugin)
  .enablePlugins(SiteScaladocPlugin).settings(
    name := "riddl-doc",
    publishTo := Some(Resolver.defaultLocal),
    Hugo / sourceDirectory := sourceDirectory.value / "hugo",
    // minimumHugoVersion := "0.89.4",
    publishSite
  ).dependsOn(language % "test->compile;test->test")

lazy val riddlc = project.in(file("riddlc"))
  .enablePlugins(BuildInfoPlugin)
  .settings(
    name := "riddlc",
    mainClass := Some("com.yoppworks.ossum.riddl.RIDDLC"),
    scalacOptions := scala2_13_Options,
    libraryDependencies ++= Seq(
      "com.github.scopt" %% "scopt" % "4.0.1",
      "com.typesafe" % "config" % "1.4.1"
    ),
    buildInfoObject := "BuildInfo",
    buildInfoPackage := "com.yoppworks.ossum.riddl",
    buildInfoUsePackageAsPath := true
  ).dependsOn(translator, language)

lazy val language = project.in(file("language"))
  .enablePlugins(BuildInfoPlugin)
  .settings(
    name := "riddl-languge",
    buildInfoObject := "BuildInfo",
    buildInfoPackage := "com.yoppworks.ossum.riddl.language",
    buildInfoUsePackageAsPath := true,
    scalacOptions := scala2_13_Options,
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-core" % "2.7.0",
      "com.lihaoyi" %% "fastparse" % "2.3.3",
      "com.github.pureconfig" %% "pureconfig" % "0.17.1",
      "org.scalactic" %% "scalactic" % "3.2.9",
      "org.scalatest" %% "scalatest" % "3.2.9" % "test",
      "org.scalacheck" %% "scalacheck" % "1.15.4" % "test"
    ),
    Compile / compileCheck := {
      Def.sequential(Compile / compile, (Compile / scalastyle).toTask("")).value
    }
  )

lazy val translator = (project in file("translator"))
  .enablePlugins(BuildInfoPlugin)
  .settings(
    name := "riddl-translator",
    scalacOptions := scala2_13_Options,
    buildInfoPackage := "com.yoppworks.ossum.riddl.translator",
    buildInfoUsePackageAsPath := true,
    libraryDependencies ++= Seq(
      "org.jfree" % "jfreesvg" % "3.4.2",
      "org.scalactic" %% "scalactic" % "3.2.9",
      "org.scalatest" %% "scalatest" % "3.2.9" % "test",
      "org.scalacheck" %% "scalacheck" % "1.15.4" % "test",
      "com.github.pureconfig" %% "pureconfig" % "0.17.1"
    )
  ).dependsOn(language % "test->test;compile->compile")


lazy val `sbt-riddl` = (project in file("sbt-riddl"))
  .enablePlugins(SbtPlugin)
  .enablePlugins(BuildInfoPlugin)
  .settings(
    name := "sbt-riddl",
    sbtPlugin := true,
    scalaVersion := "2.12.13",
    buildInfoPackage := "com.yoppworks.ossum.riddl.sbt.plugin"
  )

(Global / excludeLintKeys) ++=
  Set(buildInfoPackage, buildInfoKeys, buildInfoOptions, mainClass)
