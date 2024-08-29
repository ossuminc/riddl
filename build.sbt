import org.scoverage.coveralls.Imports.CoverallsKeys.coverallsTokenFile
import com.ossuminc.sbt.{CrossModule, DocSite, OssumIncPlugin, Plugin}
import com.typesafe.tools.mima.plugin.MimaPlugin.autoImport.mimaPreviousArtifacts
import sbt.Keys.{description, libraryDependencies}
import sbtbuildinfo.BuildInfoPlugin.autoImport.buildInfoPackage
import sbtcrossproject.{CrossClasspathDependency, CrossProject}

import scala.collection.immutable.Set

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
  .jvmConfigure(With.MiMa("0.50.0"))
  .jvmSettings(
    coverageExcludedFiles := """<empty>;$anon;.*RiddlBuildInfo.scala""",
    libraryDependencies ++= Seq(Dep.compress, Dep.lang3) ++ Dep.testing
  )
  .jsConfigure(With.js("RIDDL: utils", withCommonJSModule = true))
  .jsConfigure(With.noMiMa)
  .jsSettings(
    libraryDependencies ++= Seq(
      "org.scala-js" %%% "scalajs-dom" % "2.8.0",
      "io.github.cquiroz" %%% "scala-java-time" % "2.6.0",
      "org.scalactic" %%% "scalactic" % V.scalatest % "test",
      "org.scalatest" %%% "scalatest" % V.scalatest % "test",
      "org.scalacheck" %%% "scalacheck" % V.scalacheck % "test"
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
  .jvmConfigure(With.MiMa("0.50.0"))
  .jvmSettings(
    coverageExcludedPackages := "<empty>;$anon",
    libraryDependencies ++= Dep.testing ++ Seq(Dep.fastparse),
    libraryDependencies += "org.wvlet.airframe" %% "airframe-ulid" % "24.8.0",
    libraryDependencies += Dep.commons_io % Test
  )
  .jsConfigure(With.js("RIDDL: language", withCommonJSModule = true))
  .jsConfigure(With.noMiMa)
  .jsSettings(
    libraryDependencies += "com.lihaoyi" %%% "fastparse" % V.fastparse,
    libraryDependencies += "org.wvlet.airframe" %%% "airframe-ulid" % "24.8.0"
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
  .jvmConfigure(With.MiMa("0.50.0"))
  .jvmSettings(
    coverageExcludedPackages := "<empty>;$anon"
  )
  .jsConfigure(With.js("RIDDL: passes", withCommonJSModule = true))
  .jsConfigure(With.noMiMa)
val passes = passes_cp.jvm
val passesJS = passes_cp.js

val Diagrams = config("diagrams")
lazy val diagrams_cp: CrossProject = CrossModule("diagrams", "riddl-diagrams")(JVM, JS)
  .dependsOn(cpDep(utils_cp), cpDep(language_cp), cpDep(passes_cp))
  .configure(With.typical, With.publishing)
  .settings(
    description := "Implementation of various AST diagrams passes other libraries may use"
  )
  .jvmConfigure(With.coverage(50))
  .jvmConfigure(With.MiMa("0.50.0"))
  .jvmSettings(
    coverageExcludedFiles := """<empty>;$anon"""
  )
  .jsConfigure(With.js("RIDDL: diagrams", withCommonJSModule = true))
  .jsConfigure(With.noMiMa)
val diagrams = diagrams_cp.jvm
val diagramsJS = diagrams_cp.js

val Command = config("command")
lazy val command = Module("command", "riddl-command")
  .configure(With.typical, With.coverage(30), With.MiMa("0.50.0"))
  .configure(With.publishing)
  .settings(
    coverageExcludedPackages := "<empty>;$anon",
    description := "Command infrastructure needed to define a command",
    libraryDependencies ++= Seq(
      Dep.scopt,
      Dep.pureconfig,
      "org.scala-js" %% "scalajs-stubs" % "1.1.0" % "provided"
    ) ++ Dep.testing
  )
  .dependsOn(pDep(utils), pDep(language), passes)

def testDep(project: Project): ClasspathDependency = project % "compile->compile;compile->test;test->test"

val Prettify = config("prettify")
lazy val prettify = Module("prettify", "riddl-prettify")
  .configure(With.typical)
  .configure(With.coverage(65))
  .configure(With.MiMa("0.50.0"))
  .configure(With.publishing)
  .settings(
    coverageExcludedFiles := """<empty>;$anon""",
    scalaVersion := "3.4.2",
    description := "Implementation for the RIDDL prettify command, a code reformatter",
    libraryDependencies ++= Dep.testing ++ Seq(
      "org.scala-js" %% "scalajs-stubs" % "1.1.0" % "provided"
    )
  )
  .dependsOn(utils, language, pDep(passes), command)

val Hugo = config("hugo")
lazy val hugo = Module("hugo", "riddl-hugo")
  .configure(With.typical)
  .configure(With.coverage(65))
  .configure(With.publishing)
  .configure(With.MiMa("0.50.0"))
  .settings(
    coverageExcludedFiles := """<empty>;$anon""",
    scalaVersion := "3.4.2",
    scalacOptions += "-explain-cyclic",
    description := "Implementation for the RIDDL prettify command, a code reformatter",
    libraryDependencies ++= Dep.testing ++ Seq(
      "org.scala-js" %% "scalajs-stubs" % "1.1.0" % "provided"
    )
  )
  .dependsOn(utils, pDep(language), pDep(passes), diagrams, pDep(command), prettify)

val Commands = config("commands")
lazy val commands: Project = Module("commands", "riddl-commands")
  .configure(With.typical)
  .configure(With.coverage(50))
  .configure(With.MiMa("0.50.0"))
  .configure(With.publishing)
  .settings(
    coverageExcludedFiles := """<empty>;$anon""",
    scalacOptions ++= Seq("-explain", "--explain-types", "--explain-cyclic"),
    description := "RIDDL Command Infrastructure and basic command definitions",
    libraryDependencies ++= Seq(Dep.scopt, Dep.pureconfig) ++ Dep.testing ++ Seq(
      "org.scala-js" %% "scalajs-stubs" % "1.1.0" % "provided"
    )
  )
  .dependsOn(
    pDep(utils),
    pDep(language),
    pDep(passes),
    command,
    prettify,
    hugo
  )

val Riddlc = config("riddlc")
lazy val riddlc: Project = Program("riddlc", "riddlc")
  .configure(With.typical)
  .configure(With.coverage(50.0))
  .configure(With.publishing)
  .configure(With.MiMa("0.50.0"))
  .dependsOn(
    utils,
    language,
    testDep(passes),
    prettify,
    testDep(commands)
  )
  .settings(
    coverageExcludedFiles := """<empty>;$anon""",
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
  (diagrams, Diagrams),
  (command, Command),
  (prettify, Prettify),
  (hugo, Hugo),
  (commands, Commands),
  (riddlc, Riddlc)
)

lazy val docOutput: File = file("doc") / "src" / "main" / "hugo" / "static" / "apidoc"

//def akkaMappings: Map[(String, String), URL] = Map(
//  ("com.typesafe.akka", "akka-actor") -> url(s"http://doc.akka.io/api/akka/"),
//  ("com.typesafe", "config") -> url("http://typesafehub.github.io/config/latest/api/")
//)

lazy val docsite = DocSite(
  dirName = "doc",
  apiOutput = file("src") / "main" / "hugo" / "static" / "apidoc",
  baseURL = Some("https://riddl.tech/apidoc"),
  inclusions = Seq(utils, language, passes, diagrams, command, prettify, hugo, commands),
  logoPath = Some("doc/src/main/hugo/static/images/RIDDL-Logo-128x128.png")
)
  .settings(
    name := "riddl-doc",
    scalaVersion := "3.4.2",
    description := "Generation of the documentation web site",
    libraryDependencies ++= Dep.testing
  )
  .configure(With.noMiMa)
  .dependsOn(utils, language, passes, diagrams, command, prettify, hugo, commands)

lazy val plugin = Plugin("sbt-riddl")
  .configure(With.build_info, With.scala2, With.noMiMa, With.publishing)
  .settings(
    description := "An sbt plugin to embellish a project with riddlc usage",
    buildInfoObject := "SbtRiddlPluginBuildInfo",
    buildInfoPackage := "com.ossuminc.riddl.sbt",
    buildInfoUsePackageAsPath := true,
    scalaVersion := "2.12.19"
  )
