import org.scoverage.coveralls.Imports.CoverallsKeys.*
import com.ossuminc.sbt.{OssumIncPlugin, Plugin}
import sbt.Keys.description
import sbtbuildinfo.BuildInfoPlugin.autoImport.buildInfoPackage
import sbtcrossproject.{CrossClasspathDependency, CrossProject}

Global / onChangedBuildSource := ReloadOnSourceChanges
(Global / excludeLintKeys) ++= Set(mainClass)

enablePlugins(OssumIncPlugin)

lazy val startYear: Int = 2019

def cpDep(cp: CrossProject): CrossClasspathDependency = cp % "compile->compile;test->test"
def pDep(p: Project): ClasspathDependency = p % "compile->compile;test->test"

lazy val riddl: Project = Root("riddl", startYr = startYear)
  .configure(With.noPublishing, With.git, With.dynver)
  .aggregate(
    utils,
    utilsJS,
    language,
    languageJS,
    passes,
    passesJS,
    analyses,
    analysesJS,
    diagrams,
    diagramsJS,
    command,
    prettify,
    hugo,
    commands,
    riddlc,
    docsite,
    plugin
  )

lazy val Utils = config("utils")
lazy val utils_cp: CrossProject = CrossModule("utils", "riddl-utils")(JVM, JS)
  .configure(With.typical)
  .configure(With.build_info)
  .settings(
    scalaVersion := "3.4.2",
    scalacOptions += "-explain-cyclic",
    buildInfoPackage := "com.ossuminc.riddl.utils",
    buildInfoObject := "RiddlBuildInfo",
    description := "Various utilities used throughout riddl libraries"
  )
  .jvmConfigure(With.coverage(70))
  .jvmConfigure(With.publishing)
  .jvmSettings(
    coverageExcludedFiles := """<empty>;$anon;.*RiddlBuildInfo.scala""",
    libraryDependencies ++= Seq(Dep.compress, Dep.lang3) ++ Dep.testing
  )
  .jsConfigure(With.js("RIDDL: utils", withCommonJSModule = true))
  .jsSettings(
    libraryDependencies ++= Seq(
      "org.scala-js" %%% "scalajs-dom" % "2.8.0",
      "io.github.cquiroz" %%% "scala-java-time" % "2.6.0",
      "org.scalactic" %%% "scalactic" % V.scalatest  % "test",
      "org.scalatest" %%% "scalatest" % V.scalatest  % "test",
      "org.scalacheck" %%% "scalacheck" % V.scalacheck  % "test"
    )
  )

lazy val utils = utils_cp.jvm
lazy val utilsJS = utils_cp.js

val Language = config("language")
lazy val language_cp: CrossProject = CrossModule("language", "riddl-language")(JVM, JS)
  .dependsOn(cpDep(utils_cp))
  .configure(With.typical)
  .settings(
    description := "Abstract Syntax Tree and basic RIDDL language parser",
    scalacOptions ++= Seq("-explain", "--explain-types", "--explain-cyclic", "--no-warnings")
  )
  .jvmConfigure(With.coverage(65))
  .jvmConfigure(With.publishing)
  .jvmSettings(
    coverageExcludedPackages := "<empty>;$anon",
    libraryDependencies ++= Dep.testing ++ Seq(Dep.fastparse),
    libraryDependencies += Dep.commons_io % Test
  )
  .jsConfigure(With.js("RIDDL: language", withCommonJSModule = true))
  .jsSettings(
    libraryDependencies += "com.lihaoyi" %%% "fastparse" % V.fastparse
  )
lazy val language = language_cp.jvm.dependsOn(utils)
lazy val languageJS = language_cp.js.dependsOn(utilsJS)

val Passes = config("passes")
lazy val passes_cp = CrossModule("passes", "riddl-passes")(JVM, JS)
  .dependsOn(cpDep(utils_cp), cpDep(language_cp))
  .configure(With.typical, With.publishing)
  .settings(
    scalacOptions ++= Seq("-explain", "--explain-types", "--explain-cyclic"),
    description := "AST Pass infrastructure and essential passes"
  )
  .jvmConfigure(With.coverage(30))
  .jvmSettings(
    coverageExcludedPackages := "<empty>;$anon",
  )
  .jsConfigure(With.js("RIDDL: passes", withCommonJSModule = true))
val passes = passes_cp.jvm
val passesJS = passes_cp.js

val Analyses = config("analyses")
lazy val analyses_cp: CrossProject = CrossModule("analyses", "riddl-analyses")(JVM, JS)
  .dependsOn(cpDep(utils_cp), cpDep(language_cp), cpDep(passes_cp))
  .configure(With.typical, With.publishing)
  .settings(
    description := "Implementation of various AST analyses passes other libraries may use"
  )
  .jvmConfigure(With.coverage(50))
  .jvmSettings(
    coverageExcludedFiles := """<empty>;$anon""",
  )
  .jsConfigure(With.js("RIDDL: analyses", withCommonJSModule = true))
val analyses = analyses_cp.jvm
val analysesJS = analyses_cp.js

val Diagrams = config("diagrams")
lazy val diagrams_cp: CrossProject = CrossModule("diagrams", "riddl-diagrams")(JVM, JS)
  .dependsOn(cpDep(utils_cp), cpDep(language_cp), cpDep(passes_cp), cpDep(analyses_cp))
  .configure(With.typical,With.publishing)
  .settings(
    description := "Implementation of various AST diagrams passes other libraries may use"
  )
  .jvmConfigure(With.coverage(50))
  .jvmSettings(
    coverageExcludedFiles := """<empty>;$anon""",
  )
  .jsConfigure(With.js("RIDDL: diagrams", withCommonJSModule = true))
val diagrams = diagrams_cp.jvm
val diagramsJS = diagrams_cp.js

val Command = config("command")
lazy val command = Module("command", "riddl-command")
  .configure(With.typical, With.coverage(30))
  .configure(With.publishing)
  .settings(
    coverageExcludedPackages := "<empty>;$anon",
    description := "Command infrastructure needed to define a command",
    libraryDependencies ++= Seq(Dep.scopt, Dep.pureconfig) ++ Dep.testing
  )
  .dependsOn(pDep(utils), pDep(language), passes)

def testDep(project: Project): ClasspathDependency = project % "compile->compile;compile->test;test->test"

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
  .dependsOn(utils, language, pDep(passes),command)

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
  .dependsOn(utils, pDep(language), pDep(passes), analyses, diagrams, pDep(command), prettify)

val Commands = config("commands")
lazy val commands: Project = Module("commands", "riddl-commands")
  .configure(With.typical)
  .configure(With.coverage(50))
  .configure(With.publishing)
  .settings(
    coverageExcludedFiles := """<empty>;$anon""",
    scalacOptions ++= Seq("-explain", "--explain-types", "--explain-cyclic"),
    description := "RIDDL Command Infrastructure and basic command definitions",
    libraryDependencies ++= Seq(Dep.scopt,Dep.pureconfig) ++ Dep.testing
  )
  .dependsOn(
    pDep(utils),
    pDep(language),
    pDep(passes),
    command,
    analyses,
    prettify,
    hugo
  )

val Riddlc = config("riddlc")
lazy val riddlc: Project = Program("riddlc", "riddlc")
  .configure(With.typical)
  .configure(With.coverage(50.0))
  .configure(With.publishing)
  .dependsOn(
    utils,
    language,
    testDep(passes),
    analyses,
    prettify,
    testDep(commands)
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
