import com.ossuminc.sbt.OssumIncPlugin
import com.typesafe.tools.mima.core.{ProblemFilters, ReversedMissingMethodProblem}
import sbt.Keys.{description, libraryDependencies, scalacOptions}
import sbtbuildinfo.BuildInfoPlugin.autoImport.buildInfoPackage

Global / onChangedBuildSource := ReloadOnSourceChanges
(Global / excludeLintKeys) ++= Set(mainClass, maintainer)

enablePlugins(OssumIncPlugin)

// NOTE: All modules override scalaVersion to 3.8.4 (from sbt-ossuminc's 3.3.7 LTS
// default). RIDDL originally pinned ahead of LTS to dodge a 3.3.x compiler
// infinite loop on opaque + intersection types; the override is kept while we
// ride ahead of LTS.

lazy val startYear: Int = 2019

// Test sources may still call the deprecated throwing loaders. `fromURL`/`fromPath`
// are deprecated because THROWING is wrong for user-supplied input: riddlc used to
// print `java.io.FileNotFoundException` at people. A test loading a fixture that is
// guaranteed to exist has the opposite requirement — if it is missing, the test
// SHOULD blow up — so migrating ~60 test call sites to the Either-returning
// variants would only add ceremony. Silenced by message so every OTHER deprecation
// in test code still fails the build under -Werror.
// Must be applied per project: `With.typical` defines a project-level
// `scalacOptions`, and an undefined `<proj> / Test / scalacOptions` delegates to
// THAT before it ever reaches `ThisBuild / Test`, so a ThisBuild setting here is
// silently ignored.
lazy val deprecatedLoadersOkInTests = Seq[Setting[?]](
  Test / scalacOptions += "-Wconf:msg=fromURL:s,msg=fromPath:s"
)

// The full git commit SHA of the source tree, captured at build-definition load.
// Exposed via RiddlBuildInfo.gitCommit so `riddlc info` can report the exact source
// commit a binary was built from (lets downstream model repos locate the changes).
// Falls back to "unknown" outside a git checkout (e.g. a source tarball).
lazy val gitHeadCommit: String =
  try scala.sys.process.Process(Seq("git", "rev-parse", "HEAD")).!!.trim
  catch { case _: Throwable => "unknown" }

// Per-platform dependency: a row Project depends on another row's compile AND
// test code (the sbt 1.x CrossProject `cpDep` helper carried both).
def pDep(p: Project): ClasspathDependency = p % "compile->compile;test->test"

// projectMatrix has no partial-shared source dir, so the JVM+Native shared code
// (formerly `<mod>/jvm-native/src`) lives in a custom `scala-jvm-native` dir that
// must be added to BOTH the JVM and Native rows. Missing dirs are ignored by sbt,
// so applying this uniformly (main + test) is safe even where only main exists.
// Anchored with file(dir) at the build root — a row's baseDirectory is not the
// place to compute this from.
def jvmNativeSrc(dir: String): Project => Project = _.settings(
  Compile / unmanagedSourceDirectories += file(dir) / "src" / "main" / "scala-jvm-native",
  Test / unmanagedSourceDirectories += file(dir) / "src" / "test" / "scala-jvm-native"
)

lazy val riddl: Project = Root("riddl", startYr = startYear, spdx = "Apache-2.0")
  .configure(With.Scala3, With.noPublishing, With.Git, With.DynVer, With.noMiMa)
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
    riddlLib,
    riddlLibNative,
    riddlLibJS,
    commands,
    commandsNative,
    riddlc,
    riddlcNative,
    plugin
  )

lazy val Utils = config("utils")
/** The coverage instrumenter reports any value initializer or method body over 3000 tree nodes
  * as "skipped", and -Werror turns that into a build failure whenever coverage is enabled. A
  * large METHOD is worth splitting and has been (see JsonifierPass.buildContainer), but upickle's
  * macro-generated picklers are over the threshold purely because of how many fields their DTOs
  * have — there is nothing to split and nothing to act on. Silence that one message so -Werror
  * keeps its teeth everywhere else.
  */
lazy val quietCoverageSkips = Seq(
  Compile / scalacOptions += "-Wconf:msg=Skipping coverage instrumentation:s",
  Test / scalacOptions += "-Wconf:msg=Skipping coverage instrumentation:s"
)

lazy val utils_cp = CrossModule("utils", "riddl-utils", V.scala)(JVM, JS, Native)
  .configure(With.typical, With.GithubPublishing, With.Scala3.configure(version = Some(V.scala)))
  .settings(
    scalaVersion := V.scala, // Override 3.3.7 LTS - see top of file for reason
    scalacOptions += "-explain-cyclic",
    description := "Various utilities used throughout riddl libraries"
  )
  .jvmConfigure(jvmNativeSrc("utils"))
  .jvmConfigure(With.coverage(70))
  .jvmConfigure(With.BuildInfo.withKeys("gitCommit" -> gitHeadCommit))
  .jvmConfigure(With.MiMa(V.previous, Seq("com.ossuminc.riddl.utils.RiddlBuildInfo")))
  .jvmSettings(
    buildInfoPackage := "com.ossuminc.riddl.utils",
    buildInfoObject := "RiddlBuildInfo",
    coverageExcludedFiles := """<empty>;$anon;.*RiddlBuildInfo.scala""",
    libraryDependencies ++= Seq(Dep.compress, Dep.lang3) ++ Dep.testing
  )
  .jsConfigure(With.ScalaJS("RIDDL: utils", withCommonJSModule = true))
  .jsConfigure(With.noMiMa)
  .jsConfigure(
    With.BuildInfo.withKeys(
      "scalaJSVersion" -> org.scalajs.sbtplugin.ScalaJSPlugin.autoImport.scalaJSVersion,
      "gitCommit" -> gitHeadCommit
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
  .nativeConfigure(jvmNativeSrc("utils"))
  .nativeConfigure(
    With.Native(
      linkOptions = Seq(
        "-I/usr/include",
        "-I/usr/local/opt/curl/include",
        "-I/opt/homebrew/opt/curl/include"
      )
    )
  )
  .nativeConfigure(
    With.BuildInfo.withKeys(
      "scalaNativeVersion" -> scalanative.sbtplugin.ScalaNativePlugin.autoImport.nativeVersion,
      "gitCommit" -> gitHeadCommit
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
lazy val language_cp = CrossModule("language", "riddl-language", V.scala)(JVM, JS, Native)
  .settings(deprecatedLoadersOkInTests)
  .configure(With.typical, With.GithubPublishing, With.Scala3.configure(version = Some(V.scala)))
  .settings(
    scalaVersion := V.scala, // Override 3.3.7 LTS - see top of file for reason
    description := "Abstract Syntax Tree and basic RIDDL language parser",
    scalacOptions ++= Seq("-explain", "--explain-types", "--explain-cyclic", "--no-warnings"),
    Test / parallelExecution := false
  )
  .jvmConfigure(jvmNativeSrc("language"))
  .jvmConfigure(With.coverage(65))
  .jvmConfigure(With.MiMa(V.previous))
  .jvmSettings(
    coverageExcludedPackages := "<empty>;$anon",
    libraryDependencies ++= Dep.testing ++ Seq(
      Dep.fastparse,
      Dep.airframe_ulid,
      Dep.airframe_json,
      Dep.commons_io % Test
    )
  )
  .jsConfigure(With.ScalaJS("RIDDL: language", withCommonJSModule = true))
  .jsConfigure(With.noMiMa)
  .jsSettings(
    libraryDependencies ++= Seq(Dep.fastparse_nojvm.value, Dep.airframe_ulid_nojvm.value)
  )
  .nativeConfigure(jvmNativeSrc("language"))
  .nativeConfigure(
    With.Native(
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

lazy val language = language_cp.jvm.dependsOn(pDep(utils))
lazy val languageJS = language_cp.js.dependsOn(pDep(utilsJS))
lazy val languageNative = language_cp.native.dependsOn(pDep(utilsNative))

val Passes = config("passes")
lazy val passes_cp = CrossModule("passes", "riddl-passes", V.scala)(JVM, JS, Native)
  .settings(deprecatedLoadersOkInTests)
  .configure(With.typical, With.GithubPublishing, With.Scala3.configure(version = Some(V.scala)))
  .settings(
    scalaVersion := V.scala, // Override 3.3.7 LTS - see top of file for reason
    Test / parallelExecution := false,
    scalacOptions ++= Seq("-explain", "--explain-types", "--explain-cyclic"),
    description := "AST Pass infrastructure and essential passes"
  )
  .jvmConfigure(jvmNativeSrc("passes"))
  .jvmConfigure(With.coverage(30))
  .jvmConfigure(With.MiMa(V.previous))
  .jvmSettings(
    coverageExcludedPackages := "<empty>;$anon",
    mimaBinaryIssueFilters ++= Seq(
      ProblemFilters.exclude[ReversedMissingMethodProblem](
        "com.ossuminc.riddl.passes.PassVisitor.doRelationship"
      )
    )
  )
  .jsConfigure(With.ScalaJS("RIDDL: passes", withCommonJSModule = true))
  .jsConfigure(With.noMiMa)
  .nativeConfigure(jvmNativeSrc("passes"))
  .nativeConfigure(With.Native(mode = "fast"))
  .nativeConfigure(With.noMiMa)
  // Scala 3.8.x scaladoc has a race condition in Resources.allResources that
  // crashes intermittently when multiple `doc` tasks run concurrently under
  // `publish`. Disabling Native scaladoc avoids the race; Native consumers
  // rarely consult the docs jar.
  .nativeSettings(Compile / doc / sources := Seq.empty)
val passes = passes_cp.jvm.dependsOn(pDep(utils), pDep(language))
val passesJS = passes_cp.js.dependsOn(pDep(utilsJS), pDep(languageJS))
val passesNative = passes_cp.native.dependsOn(pDep(utilsNative), pDep(languageNative))

lazy val testkit_cp = CrossModule("testkit", "riddl-testkit", V.scala)(JVM, JS, Native)
  .settings(deprecatedLoadersOkInTests)
  .configure(With.typical, With.GithubPublishing, With.Scala3.configure(version = Some(V.scala)))
  .settings(
    scalaVersion := V.scala, // Override 3.3.7 LTS - see top of file for reason
    description := "Testing kit for RIDDL language and passes"
  )
  .jvmSettings(
    libraryDependencies ++= Seq(
      Dep.scalatest_nojvm.value,
      Dep.scalactic_nojvm.value
    )
  )
  .jvmConfigure(With.MiMa(V.previous))
  .jsConfigure(With.ScalaJS("RIDDL: language", withCommonJSModule = true))
  .jsConfigure(With.noMiMa)
  .jsSettings(
    libraryDependencies ++= Seq(
      Dep.scalatest_nojvm.value,
      Dep.scalactic_nojvm.value
    )
  )
  .nativeConfigure(With.noMiMa)
  .nativeConfigure(With.Native(mode = "fast"))
  .nativeSettings(
    evictionErrorLevel := sbt.util.Level.Warn,
    libraryDependencies ++= Seq(
      Dep.scalatest_nojvm.value,
      Dep.scalactic_nojvm.value
    )
  )
val testkit = testkit_cp.jvm.dependsOn(pDep(utils), pDep(language), pDep(passes))
val testkitJS = testkit_cp.js.dependsOn(pDep(utilsJS), pDep(languageJS), pDep(passesJS))
val testkitNative =
  testkit_cp.native.dependsOn(pDep(utilsNative), pDep(languageNative), pDep(passesNative))

lazy val riddlLib_cp = CrossModule("riddlLib", "riddl-lib", V.scala)(JS, JVM, Native)
  .settings(deprecatedLoadersOkInTests)
  .configure(With.typical, With.GithubPublishing, With.Scala3.configure(version = Some(V.scala)))
  .settings(
    scalaVersion := V.scala, // Override 3.3.7 LTS - see top of file for reason
    description := "Bundling of essential RIDDL libraries"
  )
  .jvmConfigure(With.coverage(50))
  .jvmConfigure(With.MiMa(V.previous))
  .jvmConfigure(
    With.Packaging.universal(
      maintainerEmail = "reid@ossuminc.com",
      pkgName = "riddlLib",
      pkgSummary = "Library for RIDDL language, Universal packaging",
      pkgDescription = ""
    )
  )
  .jvmSettings(
    coverageExcludedFiles := """<empty>;$anon""",
    libraryDependencies += Dep.upickle
  )
  .settings(quietCoverageSkips)
  .jsConfigure(With.ScalaJS("RIDDL: riddl-lib"))
  .jsConfigure(With.noMiMa)
  .jsSettings(
    Test / scalaJSLinkerConfig ~= {
      _.withModuleKind(ModuleKind.CommonJSModule)
    },
    libraryDependencies += Dep.upickle_nojvm.value
  )
  .jsConfigure(
    With.Packaging.npm(
      scope = "@ossuminc",
      pkgName = "riddl-lib",
      pkgDescription = "RIDDL Language Library - JavaScript/TypeScript bindings",
      keywords = Seq("riddl", "ddd", "domain-driven-design", "parser", "ast", "typescript"),
      esModule = true
    )
  )
  .jsConfigure(
    With.Publishing.npm(
      registries = Seq("github")
    )
  )
  // MUST come after `With.Packaging.npm` above, which sets this key itself — applied before it,
  // this override is silently discarded.
  //
  // The plugin looks for TypeScript definitions at `<baseDirectory>/types`, a convention from the
  // sbt 1 CrossProject layout where the JS row was based at `riddlLib/js`. Under sbt 2's
  // projectMatrix the row's baseDirectory is the SYNTHETIC `.sbt/matrix/riddlLibJS`, so the lookup
  // found nothing and said nothing: `index.d.ts` was never copied into the package, and the
  // generated package.json omitted `types`, `exports` and `files` — leaving every TypeScript
  // consumer of @ossuminc/riddl-lib with no types at all. Anchor it at the real source tree.
  .jsSettings(
    com.ossuminc.sbt.helpers.NpmPackaging.Keys.npmTypesDir :=
      Some((ThisBuild / baseDirectory).value / "riddlLib" / "js" / "types")
  )
  .nativeConfigure(With.Native(mode = "fast", buildTarget = "static"))
  .nativeConfigure(With.noMiMa)
  // See note on passes_cp re: Scala 3.8.x scaladoc race condition.
  .nativeSettings(
    Compile / doc / sources := Seq.empty,
    libraryDependencies += Dep.upickle_nojvm.value
  )
val riddlLib = riddlLib_cp.jvm.dependsOn(pDep(utils), pDep(language), pDep(passes))
val riddlLibJS = riddlLib_cp.js.dependsOn(pDep(utilsJS), pDep(languageJS), pDep(passesJS))
val riddlLibNative =
  riddlLib_cp.native.dependsOn(pDep(utilsNative), pDep(languageNative), pDep(passesNative))

val Commands = config("commands")
lazy val commands_cp = CrossModule("commands", "riddl-commands", V.scala)(JVM, Native)
  .configure(With.typical, With.GithubPublishing, With.Scala3.configure(version = Some(V.scala)))
  .settings(
    scalaVersion := V.scala, // Override 3.3.7 LTS - see top of file for reason
    scalacOptions ++= Seq("-explain", "--explain-types", "--explain-cyclic", "--no-warnings"),
    description := "RIDDL Command Infrastructure and command definitions"
  )
  .jvmConfigure(With.coverage(50))
  .jvmConfigure(With.MiMa(V.previous))
  .jvmSettings(
    libraryDependencies ++= Seq(Dep.scopt, Dep.sconfig, Dep.scalajs_stubs),
    coverageExcludedFiles := """<empty>;$anon"""
  )
  // NOTE: A JS variant is not supported because executing commands from
  // JavaScript is not easy.
  .nativeConfigure(With.Native(mode = "fast"))
  .nativeConfigure(With.noMiMa)
  .nativeSettings(
    libraryDependencies ++= Seq(Dep.scopt_nojvm.value, Dep.sconfig_nojvm.value)
  )
val commands: Project = commands_cp.jvm.dependsOn(pDep(utils), pDep(language), pDep(passes))
val commandsNative =
  commands_cp.native.dependsOn(pDep(utilsNative), pDep(languageNative), pDep(passesNative))

val Riddlc = config("riddlc")
lazy val riddlc_cp = CrossModule("riddlc", "riddlc", V.scala)(JVM, Native)
  .settings(deprecatedLoadersOkInTests)
  .configure(With.typical, With.GithubPublishing, With.Scala3.configure(version = Some(V.scala)))
  .configure(With.noMiMa)
  .settings(
    scalaVersion := V.scala, // Override 3.3.7 LTS - see top of file for reason
    description := "The `riddlc` compiler and tests, the only executable in RIDDL",
    maintainer := "reid@ossuminc.com",
    mainClass := Option("com.ossuminc.riddl.RIDDLC")
  )
  .jvmConfigure(With.coverage(50))
  .jvmConfigure(
    With.Packaging.universal(
      maintainerEmail = "reid@ossuminc.com",
      pkgName = "riddlc",
      pkgSummary = "Compiler for RIDDL language, Universal packaging",
      pkgDescription = "Compiler for the Reactive Interface to Domain Definition Language"
    )
  )
  .jvmConfigure(
    With.Packaging.docker(
      maintainerEmail = "reid@ossuminc.com",
      pkgName = "riddlc",
      pkgSummary = "RIDDL Language Compiler",
      pkgDescription = "Compiler for the Reactive Interface to Domain Definition Language"
    )
  )
  .jvmSettings(
    coverageExcludedFiles := """<empty>;$anon""",
    libraryDependencies += Dep.sconfig
  )
  .nativeConfigure(With.Native(mode = "fast", buildTarget = "application"))
  .nativeConfigure(With.noMiMa)
  .nativeSettings(
    libraryDependencies += Dep.sconfig_nojvm.value
  )
val riddlc = riddlc_cp.jvm.dependsOn(pDep(utils), pDep(language), pDep(passes), pDep(commands))
val riddlcNative =
  riddlc_cp.native.dependsOn(
    pDep(utilsNative),
    pDep(languageNative),
    pDep(passesNative),
    pDep(commandsNative)
  )

lazy val docProjects = List(
  (utils, Utils),
  (language, Language),
  (passes, Passes),
  (commands, Commands),
  (riddlc, Riddlc)
)

lazy val docOutput: File = file("doc") / "src" / "main" / "hugo" / "static" / "apidoc"

lazy val docsite = DocSite(
  dirName = "doc",
  apiOutput = file("src") / "main" / "hugo" / "static" / "apidoc",
  baseURL = Some("https://riddl.tech/apidoc"),
  inclusions = Seq(utils, language, passes, commands),
  logoPath = Some("doc/src/main/hugo/static/images/RIDDL-Logo-128x128.png")
)
  .dependsOn(utils, language, passes, commands)
  .configure(With.noMiMa)
  .configure(With.GithubPublishing)
  .settings(
    name := "riddl-doc",
    description := "Generation of the documentation web site",
    libraryDependencies ++= Dep.testing
  )

// Plugin(...) auto-configures GitHub Packages publishing and scripted testing.
lazy val plugin = OssumIncPlugin.autoImport
  .Plugin("sbt-riddl")
  .configure(With.BuildInfo, With.noMiMa)
  .settings(
    description := "An sbt plugin to embellish a project with riddlc usage",
    buildInfoObject := "SbtRiddlPluginBuildInfo",
    buildInfoPackage := "com.ossuminc.riddl.sbt",
    buildInfoUsePackageAsPath := true
  )

addCommandAlias(
  "cJVM",
  "; utils/Test/compile ; language/Test/compile ; passes/Test/compile; testkit/Test/compile ; " +
    "commands/Test/compile ; riddlLib/Test/compile ; riddlc/Test/compile"
)
addCommandAlias(
  "cNative",
  "; utilsNative/Test/compile ; languageNative/Test/compile ;  passesNative/Test/compile ; " +
    "testkitNative/Test/compile ; commandsNative/Test/compile ; " +
    "riddlLibNative/Test/compile ;  riddlcNative/Test/compile"
)

addCommandAlias(
  "cJS",
  "; utilsJS/Test/compile ; languageJS/Test/compile ; passesJS/Test/compile ; " +
    "testkitJS/Test/compile ; riddlLibJS/Test/compile"
)
addCommandAlias(
  "tJVM",
  "; utils/test ; language/test ; passes/test ; testkit/test ; commands/test ; " +
    "riddlLib/test ; riddlc/test"
)
addCommandAlias(
  "tNative",
  "; utils/test ; language/test ; passesNative/test ; testkit/test ; " +
    "commands/test ; riddlLib/test ; riddlcNative/test ; riddlcNative/nativeLink"
)
addCommandAlias(
  "tJS",
  "; utilsJS/test ; languageJS/test ; passesJS/test ; testkitJS/test ; " +
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
