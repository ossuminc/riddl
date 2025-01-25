import org.scoverage.coveralls.Imports.CoverallsKeys.coverallsTokenFile
import com.ossuminc.sbt.OssumIncPlugin
import com.typesafe.tools.mima.core.{ProblemFilters, ReversedMissingMethodProblem}
import de.heikoseeberger.sbtheader.License.ALv2
import de.heikoseeberger.sbtheader.LicenseStyle.SpdxSyntax
import sbt.Append.{appendSeqImplicit, appendSet}
import sbt.Keys.{description, libraryDependencies, scalacOptions}
import sbtbuildinfo.BuildInfoPlugin.autoImport.buildInfoPackage
import sbtcrossproject.{CrossClasspathDependency, CrossProject}
import sbttastymima.TastyMiMaPlugin.autoImport.*
import tastymima.intf.{ProblemKind, ProblemMatcher}

import scala.collection.Seq

Global / onChangedBuildSource := ReloadOnSourceChanges
(Global / excludeLintKeys) ++= Set(mainClass, maintainer)

enablePlugins(OssumIncPlugin)

lazy val startYear: Int = 2019
lazy val license =
  ALv2(yyyy = "2019-2025", copyrightOwner = "Ossum Inc.", licenseStyle = SpdxSyntax)

def cpDep(cp: CrossProject): CrossClasspathDependency = cp % "compile->compile;test->test"
def pDep(p: Project): ClasspathDependency = p % "compile->compile;test->test"
def tkDep(cp: CrossProject): CrossClasspathDependency = cp % "compile->compile;test->test"

lazy val riddl: Project = Root("riddl", startYr = startYear /*, license = "Apache-2.0" */ )
  .configure(With.noPublishing, With.git, With.dynver, With.noMiMa)
  .settings(concurrentRestrictions += Tags.limit(NativeTags.Link, 1))
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
  .configure(With.GithubPublishing)
  .configure(With.typical, With.headerLicense("Apache-2.0"))
  .settings(
    scalacOptions += "-explain-cyclic",
    description := "Various utilities used throughout riddl libraries"
  )
  .jvmConfigure(With.coverage(70))
  .jvmConfigure(With.build_info)
  .jvmConfigure(With.MiMa("0.57.0", Seq("com.ossuminc.riddl.utils.RiddlBuildInfo")))
  .jvmSettings(
    buildInfoPackage := "com.ossuminc.riddl.utils",
    buildInfoObject := "RiddlBuildInfo",
    coverageExcludedFiles := """<empty>;$anon;.*RiddlBuildInfo.scala""",
    libraryDependencies ++= Seq(Dep.compress, Dep.lang3) ++ Dep.testing,
    tastyMiMaConfig ~= { prevConfig =>
      import java.util.Arrays.asList
      import tastymima.intf._
      prevConfig.withMoreProblemFilters(
        asList(
          ProblemMatcher.make(
            ProblemKind.IncompatibleTypeChange,
            "com.ossuminc.riddl.utils.RiddlBuildInfo.version"
          ),
          ProblemMatcher
            .make(
              ProblemKind.IncompatibleTypeChange,
              "com.ossuminc.riddl.utils.RiddlBuildInfo.builtAtString"
            ),
          ProblemMatcher
            .make(
              ProblemKind.IncompatibleTypeChange,
              "com.ossuminc.riddl.utils.RiddlBuildInfo.builtAtMillis"
            ),
          ProblemMatcher.make(
            ProblemKind.IncompatibleTypeChange,
            "com.ossuminc.riddl.utils.RiddlBuildInfo.isSnapshot"
          )
        )
      )
    }
  )
  .jsConfigure(With.js("RIDDL: utils", withCommonJSModule = true))
  .jsConfigure(With.noMiMa)
  .jsConfigure(
    With.build_info_plus_keys(
      "scalaJSVersion" -> org.scalajs.sbtplugin.ScalaJSPlugin.autoImport.scalaJSVersion
    )
  )
  .jsSettings(
    buildInfoPackage := "com.ossuminc.riddl.utils",
    buildInfoObject := "RiddlBuildInfo",
    libraryDependencies ++= Seq(
      Dep.dom.value,
      Dep.scala_java_time.value,
      Dep.scalatest_nojvm.value,
      Dep.scalactic_nojvm.value
    )
  )
  .nativeConfigure(
    With.native(
      mode = "fast",
      buildTarget = "static",
      linkOptions = Seq(
        "-I/usr/include",
        "-I/usr/local/opt/curl/include",
        "-I/opt/homebrew/opt/curl/include"
      )
    )
  )
  .nativeConfigure(
    With.build_info_plus_keys(
      "scalaNativeVersion" -> scalanative.sbtplugin.ScalaNativePlugin.autoImport.nativeVersion
    )
  )
  .nativeSettings(
    buildInfoPackage := "com.ossuminc.riddl.utils",
    buildInfoObject := "RiddlBuildInfo",
    libraryDependencies ++= Seq(
      Dep.sttp_nojvm.value,
      Dep.java_net_url_stubs.value,
      Dep.scala_java_time.value,
      Dep.scalactic_nojvm.value,
      Dep.scalatest_nojvm.value,
      Dep.scalactic_nojvm.value
    )
  )
lazy val utils = utils_cp.jvm
lazy val utilsJS = utils_cp.js
lazy val utilsNative = utils_cp.native

val Language = config("language")
lazy val language_cp: CrossProject = CrossModule("language", "riddl-language")(JVM, JS, Native)
  .dependsOn(cpDep(utils_cp))
  .configure(With.GithubPublishing)
  .configure(With.typical, With.headerLicense("Apache-2.0"))
  .settings(
    description := "Abstract Syntax Tree and basic RIDDL language parser",
    scalacOptions ++= Seq("-explain", "--explain-types", "--explain-cyclic", "--no-warnings"),
    Test / parallelExecution := false
  )
  .jvmConfigure(With.coverage(65))
  .jvmConfigure(With.MiMa("0.57.0"))
  .jvmSettings(
    tastyMiMaConfig ~= { prevConfig =>
      import java.util.Arrays.asList
      import tastymima.intf._
      prevConfig.withMoreProblemFilters(
        asList(
          ProblemMatcher.make(
            ProblemKind.NewAbstractMember,
            "com.ossuminc.riddl.language.AST.RiddlValue.loc"
          ),
          ProblemMatcher.make(
            ProblemKind.IncompatibleTypeChange,
            "com.ossuminc.riddl.language.AST.OccursInProcessor"
          )
        )
      )
    },
    coverageExcludedPackages := "<empty>;$anon",
    libraryDependencies ++= Dep.testing ++ Seq(
      Dep.fastparse,
      Dep.airframe_ulid,
      Dep.airframe_json,
      Dep.commons_io % Test
    )
  )
  .jsConfigure(With.js("RIDDL: language", withCommonJSModule = true))
  .jsConfigure(With.noMiMa)
  .jsSettings(
    libraryDependencies ++= Seq(Dep.fastparse_nojvm.value, Dep.airframe_ulid_nojvm.value)
  )
  .nativeConfigure(
    With.native(
      mode = "fast",
      buildTarget = "static",
      linkOptions = Seq(
        "-I/usr/include",
        "-I/usr/local/opt/curl/include",
        "-I/opt/homebrew/opt/curl/include"
      )
    )
  )
  .nativeConfigure(With.noMiMa)
  .nativeSettings(
    libraryDependencies ++= Seq(
      Dep.fastparse_nojvm.value,
      Dep.airframe_ulid_nojvm.value,
      Dep.scalatest_nojvm.value,
      Dep.scalactic_nojvm.value
    )
  )

lazy val language = language_cp.jvm.dependsOn(utils)
lazy val languageJS = language_cp.js.dependsOn(utilsJS)
lazy val languageNative = language_cp.native.dependsOn(utilsNative)

val Passes = config("passes")
lazy val passes_cp = CrossModule("passes", "riddl-passes")(JVM, JS, Native)
  .dependsOn(cpDep(utils_cp), cpDep(language_cp))
  .configure(With.GithubPublishing)
  .configure(With.typical, With.headerLicense("Apache-2.0"))
  .settings(
    Test / parallelExecution := false,
    scalacOptions ++= Seq("-explain", "--explain-types", "--explain-cyclic"),
    description := "AST Pass infrastructure and essential passes"
  )
  .jvmConfigure(With.coverage(30))
  .jvmConfigure(With.MiMa("0.57.0"))
  .jvmSettings(
    coverageExcludedPackages := "<empty>;$anon",
    mimaBinaryIssueFilters ++= Seq(
      ProblemFilters.exclude[ReversedMissingMethodProblem](
        "com.ossuminc.riddl.passes.PassVisitor.doRelationship"
      )
    ),
    tastyMiMaConfig ~= { prevConfig =>
      import java.util.Arrays.asList
      import tastymima.intf._
      prevConfig.withMoreProblemFilters(
        asList(
          ProblemMatcher.make(
            ProblemKind.NewAbstractMember,
            "com.ossuminc.riddl.passes.PassVisitor.doRelationship"
          )
        )
      )
    }
  )
  .jsConfigure(With.js("RIDDL: passes", withCommonJSModule = true))
  .jsConfigure(With.noMiMa)
  .nativeConfigure(With.native(mode = "fast"))
  .nativeConfigure(With.noMiMa)
val passes = passes_cp.jvm
val passesJS = passes_cp.js
val passesNative = passes_cp.native

lazy val testkit_cp = CrossModule("testkit", "riddl-testkit")(JVM, JS, Native)
  .configure(With.GithubPublishing)
  .configure(With.typical, With.headerLicense("Apache-2.0"))
  .settings(
    description := "Testing kit for RIDDL language and passes"
  )
  .dependsOn(tkDep(utils_cp), tkDep(language_cp), tkDep(passes_cp))
  .jvmSettings(
    libraryDependencies ++= Seq(
      Dep.scalatest_nojvm.value,
      Dep.scalactic_nojvm.value
    )
  )
  .jvmConfigure(With.MiMa("0.57.0"))
  .jsConfigure(With.js("RIDDL: language", withCommonJSModule = true))
  .jsConfigure(With.noMiMa)
  .jsSettings(
    // scalacOptions ++= Seq("-rewrite", "-source", "3.4-migration"),
    libraryDependencies ++= Seq(
      Dep.scalatest_nojvm.value,
      Dep.scalactic_nojvm.value
    )
  )
  .nativeConfigure(With.noMiMa)
  .nativeConfigure(With.native(mode = "fast"))
  .nativeSettings(
    evictionErrorLevel := sbt.util.Level.Warn,
    libraryDependencies ++= Seq(
      Dep.scalatest_nojvm.value,
      Dep.scalactic_nojvm.value
    )
  )
val testkit = testkit_cp.jvm
val testkitJS = testkit_cp.js
val testkitNative = testkit_cp.native

val Diagrams = config("diagrams")
lazy val diagrams_cp: CrossProject = CrossModule("diagrams", "riddl-diagrams")(JVM, JS, Native)
  .dependsOn(cpDep(utils_cp), cpDep(language_cp), cpDep(passes_cp))
  .configure(With.GithubPublishing)
  .configure(With.typical, With.headerLicense("Apache-2.0"))
  .settings(
    description := "Implementation of various AST diagrams passes other libraries may use"
  )
  .jvmConfigure(With.coverage(50))
  .jvmConfigure(With.MiMa("0.57.0"))
  .jvmSettings(coverageExcludedFiles := """<empty>;$anon""")
  .jsConfigure(With.js("RIDDL: diagrams", withCommonJSModule = true))
  .jsConfigure(With.noMiMa)
  .nativeConfigure(With.native(mode = "fast"))
  .nativeConfigure(With.noMiMa)
val diagrams = diagrams_cp.jvm
val diagramsJS = diagrams_cp.js
val diagramsNative = diagrams_cp.native

lazy val riddlLib_cp: CrossProject = CrossModule("riddlLib", "riddl-lib")(JS, JVM, Native)
  .dependsOn(
    cpDep(utils_cp),
    cpDep(language_cp),
    cpDep(passes_cp),
    cpDep(diagrams_cp)
  )
  .configure(With.GithubPublishing)
  .configure(With.typical)
  .settings(
    description := "Bundling of essential RIDDL libraries"
  )
  .jvmConfigure(With.coverage(50))
  .jvmConfigure(With.MiMa("0.57.0"))
  .jvmConfigure(
    With.packagingUniversal(
      maintainerEmail = "reid@ossuminc.com",
      pkgName = "riddlLib",
      pkgSummary = "Library for RIDDL language, Universal packaging",
      pkgDescription = ""
    )
  )
  .jvmSettings(
    coverageExcludedFiles := """<empty>;$anon"""
  )
  .jsConfigure(With.js("RIDDL: diagrams", withCommonJSModule = true))
  .jsConfigure(With.noMiMa)
  .nativeConfigure(With.native(mode = "fast", buildTarget = "static"))
  .nativeConfigure(With.noMiMa)
val riddlLib = riddlLib_cp.jvm
val riddlLibJS = riddlLib_cp.js
val riddlLibNative = riddlLib_cp.native

val Commands = config("commands")
lazy val commands_cp: CrossProject = CrossModule("commands", "riddl-commands")(JVM, Native)
  .dependsOn(cpDep(utils_cp), cpDep(language_cp), cpDep(passes_cp), cpDep(diagrams_cp))
  .configure(With.typical, With.headerLicense("Apache-2.0"))
  .configure(With.GithubPublishing)
  .settings(
    scalacOptions ++= Seq("-explain", "--explain-types", "--explain-cyclic", "--no-warnings"),
    description := "RIDDL Command Infrastructure and command definitions"
  )
  .jvmConfigure(With.coverage(50))
  .jvmConfigure(With.MiMa("0.57.0"))
  .jvmSettings(
    libraryDependencies ++= Seq(Dep.scopt, Dep.sconfig, Dep.scalajs_stubs),
    coverageExcludedFiles := """<empty>;$anon"""
  )
  // NOTE: This configuration is not supported because executing commands
  // NOTE: from javascript is not easy
  // .jsConfigure(With.js("RIDDL: diagrams", withCommonJSModule = true))
  // .jsConfigure(With.noMiMa)
  // .jsSettings(
  //   libraryDependencies ++= Seq(Dep.scopt_njvm.value, Dep.sconfig_nojvm.value)
  // )
  .nativeConfigure(With.native(mode = "fast"))
  .nativeConfigure(With.noMiMa)
  .nativeSettings(
    libraryDependencies ++= Seq(Dep.scopt_nojvm.value, Dep.sconfig_nojvm.value)
  )
val commands: Project = commands_cp.jvm
val commandsNative = riddlLib_cp.native

val Riddlc = config("riddlc")
lazy val riddlc_cp: CrossProject = CrossModule("riddlc", "riddlc")(JVM, Native)
  .configure(With.GithubPublishing)
  .configure(With.typical, With.headerLicense("Apache-2.0"))
  .configure(With.coverage(50.0))
  .configure(With.noMiMa)
  .dependsOn(cpDep(utils_cp), cpDep(language_cp), cpDep(passes_cp), cpDep(commands_cp))
  .settings(
    description := "The `riddlc` compiler and tests, the only executable in RIDDL",
    maintainer := "reid@ossuminc.com",
    mainClass := Option("com.ossuminc.riddl.RIDDLC")
  )
  .jvmConfigure(With.coverage(50))
  .jvmConfigure(
    With.packagingUniversal(
      maintainerEmail = "reid@ossuminc.com",
      pkgName = "riddlc",
      pkgSummary = "Compiler for RIDDL language, Universal packaging",
      pkgDescription = ""
    )
  )
  .jvmSettings(
    coverageExcludedFiles := """<empty>;$anon""",
    coverallsTokenFile := Some("/home/reid/.coveralls.yml"),
    libraryDependencies += Dep.sconfig
  )
  .nativeConfigure(With.native(mode = "fast", buildTarget = "application"))
  .nativeConfigure(With.noMiMa)
  .nativeSettings(
    libraryDependencies += Dep.sconfig_nojvm.value
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

lazy val docsite = DocSite(
  dirName = "doc",
  apiOutput = file("src") / "main" / "hugo" / "static" / "apidoc",
  baseURL = Some("https://riddl.tech/apidoc"),
  inclusions = Seq(utils, language, passes, diagrams, commands),
  logoPath = Some("doc/src/main/hugo/static/images/RIDDL-Logo-128x128.png")
)
  .dependsOn(utils, language, passes, diagrams, commands)
  .configure(With.noMiMa)
  .configure(With.GithubPublishing)
  .settings(
    name := "riddl-doc",
    description := "Generation of the documentation web site",
    libraryDependencies ++= Dep.testing
  )

lazy val plugin = Plugin("sbt-riddl")
  .configure(With.GithubPublishing)
  .configure(With.build_info, With.scala2, With.noMiMa)
  .configure(With.headerLicense("Apache-2.0"))
  .settings(
    description := "An sbt plugin to embellish a project with riddlc usage",
    buildInfoObject := "SbtRiddlPluginBuildInfo",
    buildInfoPackage := "com.ossuminc.riddl.sbt",
    buildInfoUsePackageAsPath := true,
    scalaVersion := "2.12.20"
  )

addCommandAlias(
  "cJVM",
  "; utils/Test/compile ; language/Test/compile ; passes/Test/compile; testkit/Test/compile ; " +
    "diagrams/Test/compile ; commands/Test/compile ; riddlLib/Test/compile ; riddlc/Test/compile"
)
addCommandAlias(
  "cNative",
  "; utilsNative/Test/compile ; languageNative/Test/compile ;  passesNative/Test/compile ; " +
    "testkitNative/Test/compile ; diagramsNative/Test/compile ; commandsNative/Test/compile ; " +
    "riddlLibNative/Test/compile ;  riddlcNative/Test/compile"
)

addCommandAlias(
  "cJS",
  "; utilsJS/Test/compile ; languageJS/Test/compile ; passesJS/Test/compile ; " +
    "testkitJS/Test/compile ; diagramsJS/Test/compile ; riddlLibJS/Test/compile"
)
addCommandAlias(
  "tJVM",
  "; utils/test ; language/test ; passes/test ; testkit/test ; diagrams/test ; commands/test ; " +
    "riddlLib/test ; riddlc/test"
)
addCommandAlias(
  "tNative",
  "; utils/test ; language/test ; passesNative/test ; testkit/test ; diagrams/test ; " +
    "commands/test ; riddlLib/test ; riddlcNative/test ; riddlcNative/nativeLink"
)
addCommandAlias(
  "tJS",
  "; utilsJS/test ; languageJS/test ; passesJS/test ; testkitJS/test ; diagramsJS/test ; " +
    "riddlLibJS/test"
)
addCommandAlias(
  "packageArtifacts",
  "; riddlc/Universal/packageBin " +
    "; riddlcNative/nativeLink " +
    "; riddlLibJS/fullLinkJS" +
    "; riddlLibNative/nativeLink" +
    "; riddlLib/Universal/packageBin"
)
