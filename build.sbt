import com.jsuereth.sbtpgp.PgpKeys.pgpSigner
import com.ossuminc.sbt.helpers.RootProjectInfo
import com.ossuminc.sbt.helpers.RootProjectInfo.Keys.{gitHubOrganization, gitHubRepository}
import org.scoverage.coveralls.Imports.CoverallsKeys.*
import sbtunidoc.BaseUnidocPlugin.autoImport.unidoc

Global / onChangedBuildSource := ReloadOnSourceChanges
(Global / excludeLintKeys) ++= Set(mainClass)

enablePlugins(OssumIncPlugin)

lazy val riddl: Project = Root("", "riddl", startYr = 2019)
  .configure(With.noPublishing, With.git, With.dynver)
  .settings(
    ThisBuild / gitHubRepository := "riddl",
    ThisBuild / gitHubOrganization := "ossuminc",
    ThisBuild /
      pgpSigner / skip := true
  )
  .aggregate(
    utils,
    language,
    passes,
    commands,
    diagrams,
    testkit,
    prettify,
    stats,
    hugo,
    riddlc,
    doc,
    plugin
  )

lazy val Utils = config("utils")
lazy val utils: Project = Module("utils", "riddl-utils")
  .enablePlugins(OssumIncPlugin)
  .configure(With.typical, With.build_info)
  .settings(
    buildInfoPackage := "com.ossuminc.riddl.utils",
    buildInfoObject := "RiddlBuildInfo",
    description := "Various utilities used throughout riddl libraries",
    libraryDependencies ++= Seq(Dep.compress, Dep.lang3) ++ Dep.testing
  )

val Language = config("language")
lazy val language: Project = Module("language", "riddl-language")
  .enablePlugins(OssumIncPlugin)
  .configure(With.typical)
  .configure(With.coverage(65))
  .settings(
    coverageExcludedPackages := "<empty>;.*BuildInfo;.*Terminals",
    description := "Abstract Syntax Tree and basic RIDDL language parser",
    libraryDependencies ++= Seq(Dep.fastparse, Dep.commons_io) ++ Dep.testing
  )
  .dependsOn(utils)

val Passes = config("passes")
lazy val passes = Module("passes", "riddl-passes")
  .enablePlugins(OssumIncPlugin)
  .configure(With.typical)
  .configure(With.coverage(30))
  .settings(
    coverageExcludedPackages := "<empty>;.*BuildInfo;.*Terminals",
    description := "AST Pass infrastructure and essential passes",
    libraryDependencies ++= Dep.testing
  )
  .dependsOn(language % "compile->compile;test->test")

val Commands = config("commands")
lazy val commands: Project = Module("commands", "riddl-commands")
  .enablePlugins(OssumIncPlugin)
  .configure(With.typical)
  .configure(With.coverage(50))
  .settings(
    description := "RIDDL Command Infrastructure and basic command definitions",
    libraryDependencies ++= Seq(Dep.scopt, Dep.pureconfig) ++ Dep.testing
  )
  .dependsOn(
    utils % "compile->compile;test->test",
    passes % "compile->compile;test->test"
  )

val TestKit = config("testkit")

lazy val testkit: Project = Module("testkit", "riddl-testkit")
  .enablePlugins(OssumIncPlugin)
  .configure(With.typical)
  .settings(
    description := "A Testkit for testing RIDDL code, and a suite of those tests",
    libraryDependencies ++= Dep.testKitDeps
  )
  .dependsOn(language % "compile->test;compile->compile;test->test")
  .dependsOn(commands % "compile->compile;test->test")

val Stats = config("stats")
lazy val stats: Project = Module("stats", "riddl-stats")
  .enablePlugins(OssumIncPlugin)
  .configure(With.typical)
  .configure(With.coverage(50))
  .settings(
    description := "Implementation of the Stats command which Hugo command depends upon",
    libraryDependencies ++= Seq(Dep.pureconfig) ++ Dep.testing
  )
  .dependsOn(commands % "compile->compile;test->test")
  .dependsOn(testkit % "test->compile")

val Diagrams = config("diagrams")
lazy val diagrams: Project = Module("diagrams", "riddl-diagrams")
  .in(file("diagrams"))
  .enablePlugins(OssumIncPlugin)
  .configure(With.typical)
  .configure(With.coverage(50))
  .settings(
    description := "A Library of passes and utilities for generating diagrams from RIDDL AST",
    libraryDependencies ++= Dep.testing
  )
  .dependsOn(language, passes, testkit % "compile->test")

val Prettify = config("prettify")
lazy val prettify = Module("prettify", "riddl-prettify")
  .enablePlugins(OssumIncPlugin)
  .configure(With.typical)
  .configure(With.coverage(65))
  .settings(
    description := "Implementation for the RIDDL prettify command, a code reformatter",
    libraryDependencies ++= Dep.testing
  )
  .dependsOn(commands, testkit % "test->compile", utils)

val Hugo = config("hugo")
lazy val hugo: Project = Module("hugo", "riddl-hugo")
  .enablePlugins(OssumIncPlugin)
  .configure(With.typical)
  .configure(With.coverage(50))
  .settings(
    description := "The hugo command turns a RIDDL AST into source input for hugo static site generator",
    Compile / unmanagedResourceDirectories += {
      baseDirectory.value / "resources"
    },
    Test / parallelExecution := false,
    libraryDependencies ++= Seq(Dep.pureconfig) ++ Dep.testing
  )
  .dependsOn(passes % "compile->compile;test->test")
  .dependsOn(commands, diagrams, stats)
  .dependsOn(testkit % "test->compile")

lazy val scaladocSiteProjects = List(
  (utils, Utils),
  (language, Language),
  (passes, Passes),
  (commands, Commands),
  (testkit, TestKit),
  (prettify, Prettify),
  (diagrams, Diagrams),
  (stats, Stats),
  (hugo, Hugo),
  (riddlc, Riddlc)
)

lazy val scaladocSiteSettings = scaladocSiteProjects
  .flatMap { case (project, conf) =>
    SiteScaladocPlugin.scaladocSettings(
      conf,
      project / Compile / packageDoc / mappings,
      scaladocDir = s"api/${project.id}"
    )
  }

lazy val doc = project
  .in(file("doc"))
  .enablePlugins(OssumIncPlugin)
  .configure(With.basic, With.scala3)
  .enablePlugins(ScalaUnidocPlugin, SitePlugin, SiteScaladocPlugin, HugoPlugin)
  .disablePlugins(ScoverageSbtPlugin)
  .settings(scaladocSiteSettings)
  .settings(
    name := "riddl-doc",
    description := "Generation of the documentation web site",
    publishTo := Option(Resolver.defaultLocal),
    // Hugo / baseURL := uri("https://riddl.tech"),
    SiteScaladoc / siteSubdirName := "api",
    ScalaUnidoc / unidoc / unidocProjectFilter :=
      inAnyProject -- inProjects(plugin),
    ScalaUnidoc / scalaVersion := (compile / scalaVersion).value,

    /* TODO: Someday, auto-download and unpack to themes/hugo-geekdoc like this:
    mkdir -p themes/hugo-geekdoc/
    curl -L https://github.com/thegeeklab/hugo-geekdoc/releases/latest/download/hugo-geekdoc.tar.gz | tar -xz -C  themes/hugo-geekdoc/ --strip-components=1
     */
    // Hugo / sourceDirectory := sourceDirectory.value / "hugo",
    // siteSubdirName / ScalaUnidoc := "api",
    // (mappings / (
    //   ScalaUnidoc, packageDoc), siteSubdirName in ScalaUnidoc
    // ),
    publishSite
  )
  .dependsOn(hugo % "test->test", riddlc)

val Riddlc = config("riddlc")
lazy val riddlc: Project = Module("riddlc", "riddlc")
  .enablePlugins(OssumIncPlugin)
  .configure(With.typical)
  .enablePlugins(JavaAppPackaging, UniversalDeployPlugin)
  .enablePlugins(MiniDependencyTreePlugin, GraalVMNativeImagePlugin)
  .configure(With.coverage(10.0))
  .dependsOn(
    utils % "compile->compile;test->test",
    commands,
    passes,
    hugo,
    testkit % "test->compile"
  )
  .settings(
    description := "The `riddlc` compiler and tests, the only executable in RIDDL",
    coverallsTokenFile := Some("/home/reid/.coveralls.yml"),
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

lazy val plugin = OssumIncPlugin.autoImport
  .Plugin("sbt-riddl")
  .configure(With.build_info)
  .settings(
    description := "An sbt plugin to embellish a project with riddlc usage",
    buildInfoObject := "SbtRiddlPluginBuildInfo",
    buildInfoPackage := "com.ossuminc.riddl.sbt",
    buildInfoUsePackageAsPath := true
  )
