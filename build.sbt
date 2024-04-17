import org.scoverage.coveralls.Imports.CoverallsKeys.*
import com.ossuminc.sbt.OssumIncPlugin

Global / onChangedBuildSource := ReloadOnSourceChanges
(Global / excludeLintKeys) ++= Set(mainClass)

enablePlugins(OssumIncPlugin)

lazy val startYear: Int = 2019

lazy val riddl: Project = Root("riddl", startYr = startYear)
  .configure(With.noPublishing, With.git, With.dynver)
  .aggregate(
    utils,
    language,
    passes,
    analyses,
    prettify,
    commands,
    testkit,
    riddlc,
    docsite,
    plugin
  )

lazy val Utils = config("utils")
lazy val utils: Project = Module("utils", "riddl-utils")
  .configure(With.typical, With.build_info, With.coverage(70) /*, With.native()*/ )
  .configure(With.publishing)
  .settings(
    scalaVersion := "3.4.1",
    scalacOptions += "--no-warnings",
    buildInfoPackage := "com.ossuminc.riddl.utils",
    buildInfoObject := "RiddlBuildInfo",
    description := "Various utilities used throughout riddl libraries",
    libraryDependencies ++= Seq(Dep.compress, Dep.lang3) ++ Dep.testing
  )

val Language = config("language")
lazy val language: Project = Module("language", "riddl-language")
  .configure(With.typical, With.coverage(65))
  .configure(With.publishing)
  .settings(
    scalaVersion := "3.4.1",
    scalacOptions ++= Seq("-explain", "--explain-types", "--explain-cyclic", "--no-warnings"),
    coverageExcludedPackages := "<empty>;.*BuildInfo;.*Terminals",
    description := "Abstract Syntax Tree and basic RIDDL language parser",
    libraryDependencies ++= Dep.testing ++ Seq(Dep.fastparse, Dep.commons_io, Dep.jacabi_w3c)
  )
  .dependsOn(utils)

val Passes = config("passes")
lazy val passes = Module("passes", "riddl-passes")
  .configure(With.typical, With.coverage(30))
  .configure(With.publishing)
  .settings(
    scalaVersion := "3.4.1",
    scalacOptions ++= Seq("-explain", "--explain-types", "--explain-cyclic"),
    coverageExcludedPackages := "<empty>;.*BuildInfo;.*Terminals",
    description := "AST Pass infrastructure and essential passes",
    libraryDependencies ++= Dep.testing
  )
  .dependsOn(utils, language % "compile->compile;test->test")

val Analyses = config("analyses")
lazy val analyses: Project = Module("analyses", "riddl-analyses")
  .configure(With.typical)
  .configure(With.coverage(50))
  .configure(With.publishing)
  .settings(
    scalaVersion := "3.4.1",
    description := "Implementation of various AST analyses passes other libraries may use",
    libraryDependencies ++= Seq(Dep.pureconfig) ++ Dep.testing
  )
  .dependsOn(utils, language, passes % "compile->compile;test->test")

val Prettify = config("prettify")
lazy val prettify = Module("prettify", "riddl-prettify")
  .configure(With.typical)
  .configure(With.coverage(65))
  .configure(With.publishing)
  .settings(
    scalaVersion := "3.4.1",
    scalacOptions += "--no-warnings",
    description := "Implementation for the RIDDL prettify command, a code reformatter",
    libraryDependencies ++= Dep.testing
  )
  .dependsOn(utils, language, passes % "compile->compile;test->test")

val Commands = config("commands")
lazy val commands: Project = Module("commands", "riddl-commands")
  .configure(With.typical)
  .configure(With.coverage(50))
  .configure(With.publishing)
  .settings(
    scalaVersion := "3.4.1",
    scalacOptions ++= Seq("-explain", "--explain-types", "--explain-cyclic"),
    description := "RIDDL Command Infrastructure and basic command definitions",
    libraryDependencies ++= Seq(Dep.scopt, Dep.pureconfig) ++ Dep.testing
  )
  .dependsOn(
    utils % "compile->compile;test->test",
    language % "compile->compile;test->test",
    passes % "compile->compile;test->test",
    analyses,
    prettify
  )

val TestKit = config("testkit")
lazy val testkit: Project = Module("testkit", "riddl-testkit")
  .configure(With.typical)
  .configure(With.publishing)
  .settings(
    scalaVersion := "3.4.1",
    scalacOptions += "--no-warnings",
    description := "A Testkit for testing RIDDL code, and a suite of those tests",
    libraryDependencies ++= Dep.testKitDeps
  )
  .dependsOn(
    language % "compile->test;compile->compile;test->test",
    commands % "compile->compile;test->test;compile->test"
  )

lazy val docProjects = List(
  (utils, Utils),
  (language, Language),
  (passes, Passes),
  (analyses, Analyses),
  (prettify, Prettify),
  (commands, Commands),
  (testkit, TestKit),
  (riddlc, Riddlc)
)

lazy val docOutput: File = file("doc") / "src" / "main" / "hugo" / "static" / "apidoc"

lazy val docsite = DocSite("doc", docOutput, docProjects)
  .settings(
    name := "riddl-doc",
    scalaVersion := "3.4.1",
    description := "Generation of the documentation web site",
    libraryDependencies ++= Dep.testing

    /* TODO: Someday, auto-download and unpack to themes/hugo-geekdoc like this:
    mkdir -p themes/hugo-geekdoc/
    curl -L https://github.com/thegeeklab/hugo-geekdoc/releases/latest/download/hugo-geekdoc.tar.gz | tar -xz -C  themes/hugo-geekdoc/ --strip-components=1
     */
    // Hugo / sourceDirectory := sourceDirectory.value / "hugo",
    // siteSubdirName / ScalaUnidoc := "api",
    // (mappings / (
    //   ScalaUnidoc, packageDoc), siteSubdirName in ScalaUnidoc
    // ),
    // publishSite
  )

val Riddlc = config("riddlc")
lazy val riddlc: Project = Program("riddlc", "riddlc")
  .configure(With.typical)
  .configure(With.coverage(50.0))
  .configure(With.publishing)
  .dependsOn(
    utils,
    language,
    passes,
    analyses,
    prettify,
    commands,
    testkit % "test->compile"
  )
  .settings(
    scalaVersion := "3.4.1",
    description := "The `riddlc` compiler and tests, the only executable in RIDDL",
    coverallsTokenFile := Some("/home/reid/.coveralls.yml"),
    maintainer := "reid@ossuminc.com",
    mainClass := Option("com.ossuminc.riddl.RIDDLC"),
    graalVMNativeImageOptions ++= Seq(
      "--verbose",
      "--no-fallback",
      "--native-image-info",
      "--enable-url-protocols=https,http",
      "-H:ResourceConfigurationFiles=../../src/native-image.resources"
    ),
    libraryDependencies ++= Seq(Dep.pureconfig) ++ Dep.testing
  )

lazy val plugin = Plugin("sbt-riddl")
  .configure(With.build_info)
  .configure(With.publishing)
  .settings(
    description := "An sbt plugin to embellish a project with riddlc usage",
    buildInfoObject := "SbtRiddlPluginBuildInfo",
    buildInfoPackage := "com.ossuminc.riddl.sbt",
    buildInfoUsePackageAsPath := true
  )
