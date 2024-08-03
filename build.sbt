import org.scoverage.coveralls.Imports.CoverallsKeys.*
import com.ossuminc.sbt.{OssumIncPlugin, Plugin}
import sbt.Keys.description
import sbtbuildinfo.BuildInfoPlugin.autoImport.buildInfoPackage
import sbtcrossproject.CrossProject
import org.scalajs.linker.interface.OutputPatterns

Global / onChangedBuildSource := ReloadOnSourceChanges
(Global / excludeLintKeys) ++= Set(mainClass)

enablePlugins(OssumIncPlugin)

lazy val startYear: Int = 2019

def compTest(p: Project): ClasspathDependency = p % "compile->compile;test->test"

lazy val riddl: Project = Root("riddl", startYr = startYear)
  .configure(With.noPublishing, With.git, With.dynver)
  .aggregate(
    utils,
    language,
    passes,
    command,
    analyses,
    prettify,
    hugo,
    testkit,
    commands,
    riddlc,
    docsite,
    plugin
  )

lazy val Utils = config("utils")
lazy val utils_cp: CrossProject = CrossModule("utils", "riddl-utils")(JVM, JS)
  .configure(With.typical)
  .configure(With.build_info)
  .jvmConfigure(With.coverage(70))
  .jvmConfigure(With.publishing)
  .jsConfigure(With.js(hasMain = false, forProd = true, withCommonJSModule = false))
  .settings(
    scalaVersion := "3.4.2",
    scalacOptions += "-explain-cyclic",
    buildInfoPackage := "com.ossuminc.riddl.utils",
    buildInfoObject := "RiddlBuildInfo",
    description := "Various utilities used throughout riddl libraries"
  )
  .jvmSettings(
    coverageExcludedFiles := """<empty>;$anon;.*RiddlBuildInfo.scala""",
    libraryDependencies ++= Seq(Dep.compress, Dep.lang3) ++ Dep.testing
  )
  .jsSettings(

    scalaJSLinkerConfig ~= {
      // Enable ECMAScript module output.
      _.withModuleKind(ModuleKind.ESModule)
        // Use .mjs extension.
        .withOutputPatterns(OutputPatterns.fromJSFile("%s.mjs"))
    },
    libraryDependencies ++= Seq(
      "org.scalactic" %%% "scalactic" % V.scalatest,
      "org.scalatest" %%% "scalatest" % V.scalatest,
      "org.scala-js" %%% "scalajs-dom" % "2.8.0",
      "io.github.cquiroz" %%% "scala-java-time" % "2.6.0"
    )
  )

lazy val utils = utils_cp.jvm
lazy val utilsJS = utils_cp.js

val Language = config("language")
lazy val language_cp: CrossProject = CrossModule("language", "riddl-language")(JVM, JS)
  .configure(With.typical)
  .settings(
    description := "Abstract Syntax Tree and basic RIDDL language parser",
    scalaVersion := "3.4.2",
    scalacOptions ++= Seq("-explain", "--explain-types", "--explain-cyclic", "--no-warnings")
  )
  .jvmConfigure(With.coverage(65))
  .jvmConfigure(With.publishing)
  .jsConfigure(With.js(hasMain = false, forProd = true, withCommonJSModule = false))
  .jvmSettings(
    coverageExcludedPackages := "<empty>;$anon",
    libraryDependencies ++= Dep.testing ++ Seq(Dep.fastparse),
    libraryDependencies += Dep.commons_io % Test
  )
  .jsSettings(
    libraryDependencies += "com.lihaoyi" %%% "fastparse" % V.fastparse
  )
lazy val language = language_cp.jvm.dependsOn(utils)
lazy val language_js = language_cp.js.dependsOn(utilsJS)

val Passes = config("passes")
lazy val passes_cp = CrossModule("passes", "riddl-passes")(JVM, JS)
  .configure(With.typical)
  .jvmConfigure(With.coverage(30))
  .jvmConfigure(With.publishing)
  .jsConfigure(With.js(hasMain = false, forProd = true, withCommonJSModule = false))
  .settings(
    scalaVersion := "3.4.2",
    scalacOptions ++= Seq("-explain", "--explain-types", "--explain-cyclic"),
    description := "AST Pass infrastructure and essential passes"
  )
  .jvmSettings(
    coverageExcludedPackages := "<empty>;$anon",
    libraryDependencies ++= Dep.testing
  )
  .dependsOn(utils_cp, language_cp % "compile->compile;test->test")
val passes = passes_cp.jvm
val passesJS = passes_cp.js

val Analyses = config("analyses")
lazy val analyses_cp: CrossProject = CrossModule("analyses", "riddl-analyses")(JVM, JS)
  .configure(With.typical)
  .jvmConfigure(With.coverage(50))
  .jvmConfigure(With.publishing)
  .jsConfigure(With.js(hasMain = false, forProd = true, withCommonJSModule = false))
  .settings(
    scalaVersion := "3.4.2",
    description := "Implementation of various AST analyses passes other libraries may use"
  )
  .jvmSettings(
    coverageExcludedFiles := """<empty>;$anon""",
    libraryDependencies ++= Seq(Dep.pureconfig) ++ Dep.testing
  )
  .dependsOn(utils_cp, language_cp, passes_cp % "compile->compile;test->test")
val analyses = analyses_cp.jvm
val analysesJS = analyses_cp.js

val Command = config("command")
lazy val command = Module("command", "riddl-command")
  .configure(With.typical, With.coverage(30))
  .configure(With.publishing)
  .settings(
    scalaVersion := "3.4.2",
    scalacOptions ++= Seq("-explain", "--explain-types", "--explain-cyclic"),
    coverageExcludedPackages := "<empty>;$anon",
    description := "Command infrastructure needed to define a command",
    libraryDependencies ++= Seq(Dep.scopt, Dep.pureconfig) ++ Dep.testing
  )
  .dependsOn(compTest(utils), compTest(language), passes)

def testDep(project: Project): ClasspathDependency = project % "compile->compile;compile->test;test->test"

val TestKit = config("testkit")
lazy val testkit: Project = Module("testkit", "riddl-testkit")
  .configure(With.typical)
  .configure(With.publishing)
  .settings(
    coverageExcludedFiles := """<empty>;$anon""",
    scalaVersion := "3.4.2",
    scalacOptions += "--no-warnings",
    description := "A Testkit for testing RIDDL code, and a suite of those tests",
    libraryDependencies ++= Dep.testKitDeps
  )
  .dependsOn(
    testDep(language),
    testDep(passes),
    testDep(command)
  )

def testKitDep: ClasspathDependency = testkit % "test->compile;test->test"

val Prettify = config("prettify")
lazy val prettify = Module("prettify", "riddl-prettify")
  .configure(With.typical)
  .configure(With.coverage(65))
  .configure(With.publishing)
  .settings(
    coverageExcludedFiles := """<empty>;$anon""",
    scalaVersion := "3.4.2",
    description := "Implementation for the RIDDL prettify command, a code reformatter",
    libraryDependencies ++= Dep.testing
  )
  .dependsOn(utils, language, compTest(passes), command, testKitDep)

val Hugo = config("hugo")
lazy val hugo = Module("hugo", "riddl-hugo")
  .configure(With.typical)
  .configure(With.coverage(65))
  .configure(With.publishing)
  .settings(
    coverageExcludedFiles := """<empty>;$anon""",
    scalaVersion := "3.4.2",
    scalacOptions += "-explain-cyclic",
    description := "Implementation for the RIDDL prettify command, a code reformatter",
    libraryDependencies ++= Dep.testing
  )
  .dependsOn(utils, compTest(language), compTest(passes), compTest(command), analyses, prettify, testKitDep)

val Commands = config("commands")
lazy val commands: Project = Module("commands", "riddl-commands")
  .configure(With.typical)
  .configure(With.coverage(50))
  .configure(With.publishing)
  .settings(
    coverageExcludedFiles := """<empty>;$anon""",
    scalaVersion := "3.4.2",
    scalacOptions ++= Seq("-explain", "--explain-types", "--explain-cyclic"),
    description := "RIDDL Command Infrastructure and basic command definitions",
    libraryDependencies ++= Dep.testing
  )
  .dependsOn(
    compTest(utils),
    compTest(language),
    compTest(passes),
    command,
    analyses,
    prettify,
    hugo,
    testKitDep
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
    coverageExcludedFiles := """<empty>;$anon""",
    scalaVersion := "3.4.2",
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

lazy val docProjects = List(
  (utils, Utils),
  (language, Language),
  (passes, Passes),
  (analyses, Analyses),
  (command, Command),
  (testkit, TestKit),
  (prettify, Prettify),
  (hugo, Hugo),
  (commands, Commands),
  (riddlc, Riddlc)
)

lazy val docOutput: File = file("doc") / "src" / "main" / "hugo" / "static" / "apidoc"

lazy val docsite = DocSite("doc", docOutput, docProjects)
  .settings(
    name := "riddl-doc",
    scalaVersion := "3.4.2",
    description := "Generation of the documentation web site",
    libraryDependencies ++= Dep.testing
  )

lazy val plugin = Plugin("sbt-riddl")
  .configure(With.build_info)
  .configure(With.publishing)
  .settings(
    description := "An sbt plugin to embellish a project with riddlc usage",
    buildInfoObject := "SbtRiddlPluginBuildInfo",
    buildInfoPackage := "com.ossuminc.riddl.sbt",
    buildInfoUsePackageAsPath := true,
    scalaVersion := "2.12.19"
  )
