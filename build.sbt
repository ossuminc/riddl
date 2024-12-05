import org.scoverage.coveralls.Imports.CoverallsKeys.coverallsTokenFile
import com.ossuminc.sbt.OssumIncPlugin
import com.typesafe.tools.mima.core.{ProblemFilters, ReversedMissingMethodProblem}
import de.heikoseeberger.sbtheader.License.ALv2
import de.heikoseeberger.sbtheader.LicenseStyle.SpdxSyntax
import sbt.Append.{appendSeqImplicit, appendSet}
import sbt.Keys.{description, libraryDependencies}
import sbtbuildinfo.BuildInfoPlugin.autoImport.buildInfoPackage
import sbtcrossproject.{CrossClasspathDependency, CrossProject}
import sbttastymima.TastyMiMaPlugin.autoImport.*

Global / onChangedBuildSource := ReloadOnSourceChanges
(Global / excludeLintKeys) ++= Set(mainClass, maintainer)

enablePlugins(OssumIncPlugin)

lazy val startYear: Int = 2019
lazy val license = ALv2(yyyy = "2019-2025", copyrightOwner = "Ossum Inc.", licenseStyle = SpdxSyntax)

def cpDep(cp: CrossProject): CrossClasspathDependency = cp % "compile->compile;test->test"
def pDep(p: Project): ClasspathDependency = p % "compile->compile;test->test"
def tkDep(cp: CrossProject): CrossClasspathDependency = cp % "compile->compile;test->test"

lazy val riddl: Project = Root("riddl", startYr = startYear /*, license = "Apache-2.0" */ )
  .configure(With.noPublishing, With.git, With.dynver, With.noMiMa)
  .aggregate(
    utils,
    utilsNative,
    utilsJS,
    language,
    languageNative,
    languageJS,
    passes,
    passesNative,
    passesJS,
    testkit,
    testkitNative,
    testkitJS,
    diagrams,
    diagramsNative,
    diagramsJS,
    riddlLib,
    riddlLibNative,
    riddlLibJS,
    commands,
    commandsNative,
    riddlc,
    riddlcNative,
    docsite,
    plugin
  )

lazy val Utils = config("utils")
lazy val utils_cp: CrossProject = CrossModule("utils", "riddl-utils")(JVM, JS, Native)
  .configure(With.typical, With.headerLicense("Apache-2.0"))
  .configure(With.build_info, With.publishing)
  .settings(
    scalacOptions += "-explain-cyclic",
    buildInfoPackage := "com.ossuminc.riddl.utils",
    buildInfoObject := "RiddlBuildInfo",
    description := "Various utilities used throughout riddl libraries"
  )
  .jvmConfigure(With.coverage(70))
  .jvmConfigure(With.MiMa("0.52.1", Seq("com.ossuminc.riddl.utils.RiddlBuildInfo")))
  .jvmSettings(
    coverageExcludedFiles := """<empty>;$anon;.*RiddlBuildInfo.scala""",
    libraryDependencies ++= Seq(Dep.compress, Dep.lang3) ++ Dep.testing,
    tastyMiMaConfig ~= { prevConfig =>
      import java.util.Arrays.asList
      import tastymima.intf._
      prevConfig.withMoreProblemFilters(
        asList(
          ProblemMatcher.make(ProblemKind.IncompatibleTypeChange, "com.ossuminc.riddl.utils.RiddlBuildInfo.version"),
          ProblemMatcher
            .make(ProblemKind.IncompatibleTypeChange, "com.ossuminc.riddl.utils.RiddlBuildInfo.builtAtString"),
          ProblemMatcher
            .make(ProblemKind.IncompatibleTypeChange, "com.ossuminc.riddl.utils.RiddlBuildInfo.builtAtMillis"),
          ProblemMatcher.make(ProblemKind.IncompatibleTypeChange, "com.ossuminc.riddl.utils.RiddlBuildInfo.isSnapshot")
        )
      )
    }
  )
  .jsConfigure(With.js("RIDDL: utils", withCommonJSModule = true))
  .jsConfigure(With.noMiMa)
  .jsSettings(
    libraryDependencies ++= Seq(
      "org.scala-js" %%% "scalajs-dom" % "2.8.0",
      "io.github.cquiroz" %%% "scala-java-time" % "2.6.0",
      "org.scalactic" %%% "scalactic" % V.scalatest % Test,
      "org.scalatest" %%% "scalatest" % V.scalatest % Test
    )
  )
  .nativeConfigure(
    With
      .native(
        lto = "none",
        targetTriple = "arm64-apple-darwin23.6.0",
        ld64Path = "/opt/homebrew/Cellar/lld/19.1.4/bin/ld64.lld",
        verbose = false
      )
  )
  .nativeSettings(
    libraryDependencies ++= Seq(
      "org.scala-native" %%% "java-net-url-stubs" % "1.0.0",
      "io.github.cquiroz" %%% "scala-java-time" % "2.6.0",
      "org.scalactic" %%% "scalactic" % V.scalatest % Test,
      "org.scalatest" %%% "scalatest" % V.scalatest % Test
    )
  )
lazy val utils = utils_cp.jvm
lazy val utilsJS = utils_cp.js
lazy val utilsNative = utils_cp.native

val Language = config("language")
lazy val language_cp: CrossProject = CrossModule("language", "riddl-language")(JVM, JS, Native)
  .dependsOn(cpDep(utils_cp))
  .configure(With.typical, With.headerLicense("Apache-2.0"))
  .settings(
    description := "Abstract Syntax Tree and basic RIDDL language parser",
    scalacOptions ++= Seq("-explain", "--explain-types", "--explain-cyclic", "--no-warnings"),
    Test / parallelExecution := false
  )
  .jvmConfigure(With.coverage(65))
  .jvmConfigure(With.publishing)
  .jvmConfigure(With.MiMa("0.52.1"))
  .jvmSettings(
    tastyMiMaConfig ~= { prevConfig =>
      import java.util.Arrays.asList
      import tastymima.intf._
      prevConfig.withMoreProblemFilters(
        asList(
          ProblemMatcher.make(ProblemKind.NewAbstractMember, "com.ossuminc.riddl.language.AST.RiddlValue.loc"),
          ProblemMatcher.make(ProblemKind.IncompatibleTypeChange, "com.ossuminc.riddl.language.AST.OccursInProcessor")
        )
      )
    },
    coverageExcludedPackages := "<empty>;$anon",
    libraryDependencies ++= Dep.testing ++ Seq(Dep.fastparse),
    libraryDependencies += "org.wvlet.airframe" %% "airframe-ulid" % V.airframe_ulid,
    libraryDependencies += "org.wvlet.airframe" %% "airframe-json" % V.airframe_json,
    libraryDependencies += Dep.commons_io % Test
  )
  .jsConfigure(With.js("RIDDL: language", withCommonJSModule = true))
  .jsConfigure(With.publishing)
  .jsConfigure(With.noMiMa)
  .jsSettings(
    libraryDependencies += "com.lihaoyi" %%% "fastparse" % V.fastparse,
    libraryDependencies += "org.wvlet.airframe" %%% "airframe-ulid" % V.airframe_ulid
  )
  .nativeConfigure(
    With
      .native(
        lto = "none",
        targetTriple = "arm64-apple-darwin23.6.0",
        ld64Path = "/opt/homebrew/bin/ld64.lld",
        verbose = false
      )
  )
  .nativeConfigure(With.noMiMa)
  .nativeSettings(
    libraryDependencies ++= Seq(
      "com.lihaoyi" %%% "fastparse" % V.fastparse,
      "org.wvlet.airframe" %%% "airframe-ulid" % V.airframe_ulid,
      "org.scalactic" %%% "scalactic" % V.scalatest % Test,
      "org.scalatest" %%% "scalatest" % V.scalatest % Test
    )
  )

lazy val language = language_cp.jvm.dependsOn(utils)
lazy val languageJS = language_cp.js.dependsOn(utilsJS)
lazy val languageNative = language_cp.native.dependsOn(utilsNative)

val Passes = config("passes")
lazy val passes_cp = CrossModule("passes", "riddl-passes")(JVM, JS, Native)
  .dependsOn(cpDep(utils_cp), cpDep(language_cp))
  .configure(With.typical, With.headerLicense("Apache-2.0"))
  .settings(
    Test / parallelExecution := false,
    scalacOptions ++= Seq("-explain", "--explain-types", "--explain-cyclic"),
    description := "AST Pass infrastructure and essential passes"
  )
  .jvmConfigure(With.coverage(30))
  .jvmConfigure(With.MiMa("0.52.1"))
  .jvmSettings(
    coverageExcludedPackages := "<empty>;$anon",
    mimaBinaryIssueFilters ++= Seq(
      ProblemFilters.exclude[ReversedMissingMethodProblem]("com.ossuminc.riddl.passes.PassVisitor.doRelationship")
    ),
    tastyMiMaConfig ~= { prevConfig =>
      import java.util.Arrays.asList
      import tastymima.intf._
      prevConfig.withMoreProblemFilters(
        asList(
          ProblemMatcher.make(ProblemKind.NewAbstractMember, "com.ossuminc.riddl.passes.PassVisitor.doRelationship")
        )
      )
    }
  )
  .jsConfigure(With.js("RIDDL: passes", withCommonJSModule = true))
  .jsConfigure(With.publishing)
  .jsConfigure(With.noMiMa)
  .nativeConfigure(
    With
      .native(
        lto = "none",
        targetTriple = "arm64-apple-darwin23.6.0",
        ld64Path = "/opt/homebrew/bin/ld64.lld"
      )
  )
  .nativeConfigure(With.noMiMa)
val passes = passes_cp.jvm
val passesJS = passes_cp.js
val passesNative = passes_cp.native

lazy val testkit_cp = CrossModule("testkit", "riddl-testkit")(JVM, JS, Native)
  .configure(With.typical, With.publishing, With.headerLicense("Apache-2.0"))
  .settings(
    description := "Testing kit for RIDDL language and passes"
  )
  .dependsOn(tkDep(utils_cp), tkDep(language_cp), tkDep(passes_cp))
  .jvmSettings(
    libraryDependencies ++= Seq(
      "org.scalactic" %% "scalactic" % V.scalatest,
      "org.scalatest" %% "scalatest" % V.scalatest
    )
  )
  .jvmConfigure(With.MiMa("0.52.1"))
  .jsConfigure(With.js("RIDDL: language", withCommonJSModule = true))
  .jsConfigure(With.publishing)
  .jsConfigure(With.noMiMa)
  .jsSettings(
    // scalacOptions ++= Seq("-rewrite", "-source", "3.4-migration"),
    libraryDependencies ++= Seq(
      "org.scalactic" %%% "scalactic" % V.scalatest,
      "org.scalatest" %%% "scalatest" % V.scalatest
    )
  )
  .nativeConfigure(With.noMiMa)
  .nativeConfigure(
    With
      .native(
        lto = "none",
        targetTriple = "arm64-apple-darwin23.6.0",
        ld64Path = "/opt/homebrew/bin/ld64.lld"
      )
  )
  .nativeSettings(
    evictionErrorLevel := sbt.util.Level.Warn,
    libraryDependencies ++= Seq(
      "org.scalactic" %%% "scalactic" % V.scalatest,
      "org.scalatest" %%% "scalatest" % V.scalatest
    )
  )
val testkit = testkit_cp.jvm
val testkitJS = testkit_cp.js
val testkitNative = testkit_cp.native

val Diagrams = config("diagrams")
lazy val diagrams_cp: CrossProject = CrossModule("diagrams", "riddl-diagrams")(JVM, JS, Native)
  .dependsOn(cpDep(utils_cp), cpDep(language_cp), cpDep(passes_cp))
  .configure(With.typical, With.publishing, With.headerLicense("Apache-2.0"))
  .settings(
    description := "Implementation of various AST diagrams passes other libraries may use"
  )
  .jvmConfigure(With.coverage(50))
  .jvmConfigure(With.MiMa("0.52.1"))
  .jvmSettings(
    coverageExcludedFiles := """<empty>;$anon"""
  )
  .jsConfigure(With.js("RIDDL: diagrams", withCommonJSModule = true))
  .jsConfigure(With.noMiMa)
  .nativeConfigure(
    With
      .native(
        lto = "none",
        targetTriple = "arm64-apple-darwin23.6.0",
        ld64Path = "/opt/homebrew/bin/ld64.lld"
      )
  )
  .nativeConfigure(With.noMiMa)
val diagrams = diagrams_cp.jvm
val diagramsJS = diagrams_cp.js
val diagramsNative = diagrams_cp.native

lazy val riddlLib_cp: CrossProject = CrossModule("riddlLib", "riddl-lib")(JS, JVM, Native)
  .dependsOn(cpDep(utils_cp), cpDep(language_cp), cpDep(passes_cp), cpDep(diagrams_cp))
  .configure(With.scala3, With.publishing)
  .settings(
    description := "Bundling of essential RIDDL libraries"
  )
  .jvmConfigure(With.coverage(50))
  .jvmConfigure(With.MiMa("0.52.1"))
  .jvmSettings(
    coverageExcludedFiles := """<empty>;$anon"""
  )
  .jsConfigure(With.js("RIDDL: diagrams", withCommonJSModule = true))
  .jsConfigure(With.noMiMa)
  .nativeConfigure(
    With
      .native(
        lto = "none",
        targetTriple = "arm64-apple-darwin23.6.0",
        ld64Path = "/opt/homebrew/bin/ld64.lld"
      )
  )
  .nativeConfigure(With.noMiMa)
val riddlLib = riddlLib_cp.jvm
val riddlLibJS = riddlLib_cp.js
val riddlLibNative = riddlLib_cp.native

val Commands = config("commands")
lazy val commands_cp: CrossProject = CrossModule("commands", "riddl-commands")(JVM, Native)
  .dependsOn(cpDep(utils_cp), cpDep(language_cp), cpDep(passes_cp), cpDep(diagrams_cp))
  .configure(With.typical, With.publishing, With.headerLicense("Apache-2.0"))
  .settings(
    scalacOptions ++= Seq("-explain", "--explain-types", "--explain-cyclic"),
    description := "RIDDL Command Infrastructure and command definitions",
    libraryDependencies ++= Seq(Dep.scopt, Dep.pureconfig) ++ Dep.testing
  )
  .jvmConfigure(With.coverage(50))
  .jvmConfigure(With.MiMa("0.52.1"))
  .jvmSettings(
    coverageExcludedFiles := """<empty>;$anon"""
  )
  .nativeConfigure(
    With
      .native(
        mode = "full",
        buildTarget = "library",
        gc = "boehm",
        lto = "none",
        targetTriple = "arm64-apple-darwin23.6.0",
        ld64Path = "/opt/homebrew/bin/ld64.lld"
      )
  )
  .nativeConfigure(With.noMiMa)
val commands: Project = commands_cp.jvm
val commandsNative = riddlLib_cp.native

val Riddlc = config("riddlc")
lazy val riddlc_cp: CrossProject = CrossModule("riddlc", "riddlc")(JVM, Native)
  .configure(With.typical, With.publishing, With.headerLicense("Apache-2.0"))
  .configure(With.coverage(50.0))
  .configure(With.noMiMa)
  .dependsOn(cpDep(utils_cp), cpDep(language_cp), cpDep(passes_cp), cpDep(commands_cp))
  .settings(
    coverageExcludedFiles := """<empty>;$anon""",
    description := "The `riddlc` compiler and tests, the only executable in RIDDL",
    coverallsTokenFile := Some("/home/reid/.coveralls.yml"),
    maintainer := "reid@ossuminc.com",
    mainClass := Option("com.ossuminc.riddl.RIDDLC"),
    // graalVMNativeImageOptions ++= Seq(
    //   "--verbose",
    //   "--no-fallback",
    //   "--native-image-info",
    //   "--enable-url-protocols=https,http",
    //   "-H:ResourceConfigurationFiles=../../src/native-image.resources"
    // ),
    libraryDependencies ++= Seq(Dep.pureconfig) ++ Dep.testing
  )
  .jvmConfigure(With.coverage(50))
  .jvmSettings(
    coverageExcludedFiles := """<empty>;$anon"""
  )
  .nativeConfigure(
    With.native(
      mode = "full",
      buildTarget = "application",
      lto = "none"
    )
  )
val riddlc = riddlc_cp.jvm
val riddlcNative = riddlc_cp.native

lazy val docProjects = List(
  (utils, Utils),
  (language, Language),
  (passes, Passes),
  (diagrams, Diagrams),
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
  inclusions = Seq(utils, language, passes, diagrams, commands),
  logoPath = Some("doc/src/main/hugo/static/images/RIDDL-Logo-128x128.png")
)
  .settings(
    name := "riddl-doc",
    description := "Generation of the documentation web site",
    libraryDependencies ++= Dep.testing
  )
  .configure(With.noMiMa)
  .dependsOn(utils, language, passes, diagrams, commands)

lazy val plugin = Plugin("sbt-riddl")
  .configure(With.build_info, With.scala2, With.noMiMa, With.publishing)
  .configure(With.headerLicense("Apache-2.0"))
  .settings(
    description := "An sbt plugin to embellish a project with riddlc usage",
    buildInfoObject := "SbtRiddlPluginBuildInfo",
    buildInfoPackage := "com.ossuminc.riddl.sbt",
    buildInfoUsePackageAsPath := true,
    scalaVersion := "2.12.20"
  )
