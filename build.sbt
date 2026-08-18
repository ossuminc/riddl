import com.ossuminc.sbt.OssumIncPlugin
import com.typesafe.tools.mima.core.{ProblemFilters, ReversedMissingMethodProblem}
import sbt.Keys.{description, libraryDependencies, scalacOptions}
import sbtbuildinfo.BuildInfoPlugin.autoImport.buildInfoPackage
import com.typesafe.sbt.packager.universal.UniversalPlugin.autoImport.Universal

Global / onChangedBuildSource := ReloadOnSourceChanges
(Global / excludeLintKeys) ++= Set(mainClass, maintainer)

enablePlugins(OssumIncPlugin)

// NOTE: All modules override scalaVersion to 3.8.4 (from sbt-ossuminc's 3.3.7 LTS
// default). RIDDL originally pinned ahead of LTS to dodge a 3.3.x compiler
// infinite loop on opaque + intersection types; the override is kept while we
// ride ahead of LTS.

lazy val startYear: Int = 2019

// Ship the third-party license notices INSIDE the distribution, not just on the
// website: a user who downloads a tarball and never visits ossum.tech is exactly
// the person the attribution is for, and Apache-2.0 s4(d) asks that the NOTICE
// content travel WITH the redistribution. `riddlc info` prints a one-line-per-
// project summary and points at this file by name, so the name must not drift.
// A module that compiles ZERO sources publishes an EMPTY jar and reports success.
// riddl-testkit did exactly that in 2.0.0-rc.1 and rc.2: its five "main sources"
// were committed symlinks still pointing at the pre-restructure <mod>/shared/src
// layout, so every one dangled, sbt's source scan skipped them, and a 310-byte jar
// with nothing but a manifest went to GitHub Packages. Nothing failed -- not the
// tri-platform suite, not CI, not the registry check -- because zero sources is not
// an error. Synapify found it by trying to compile against it.
//
// Guarding on SOURCES rather than class files catches it earlier and states the
// cause; a module legitimately without sources (an aggregator) simply does not get
// this setting.
lazy val nonEmptySources = taskKey[Unit](
  "Fail the build if this module has no Compile sources, rather than publishing an empty jar"
)

lazy val guardEmptyModule: Seq[Setting[?]] = Seq(
  // Def.uncached, for the same reason the riddlc sbt tasks needed it: this returns
  // Unit, which sbt 2 CAN cache, and its real input is a directory scan. Without it
  // the guard passes once and is a no-op forever after -- caught by deleting the
  // sources and watching it still report success.
  nonEmptySources := Def.uncached {
    val srcs = (Compile / sources).value
    if (srcs.isEmpty) {
      sys.error(
        s"${moduleName.value}: ZERO Compile sources -- this would publish an empty jar. " +
          "Most likely a dangling symlink under src/main (check with: " +
          "find <mod>/src/main -name '*.scala' -exec test -e {} \\; -o -print)."
      )
    }
  },
  Compile / packageBin := (Compile / packageBin).dependsOn(nonEmptySources).value
)

lazy val thirdPartyNotices = Universal / mappings += {
  // sbt 2 mappings are (HashedVirtualFileRef, String), not (File, String) --
  // everything goes through the virtual FS, so a plain java.io.File will not
  // convert implicitly. `fileConverter` is the supported bridge.
  val conv = fileConverter.value
  val f = (ThisBuild / baseDirectory).value / "THIRD-PARTY-NOTICES.txt"
  conv.toVirtualFile(f.toPath) -> "THIRD-PARTY-NOTICES.txt"
}

// Test sources may still call the deprecated throwing loaders. `fromURL`/`fromPath`
// are deprecated because THROWING is wrong for user-supplied input: riddlc used to
// print `java.io.FileNotFoundException` at people. A test loading a fixture that is
// guaranteed to exist has the opposite requirement — if it is missing, the test
// SHOULD blow up — so migrating ~60 test call sites to the Either-returning
// variants would only add ceremony. Silenced by message so every OTHER deprecation
// in test code still fails the build under -Werror.
// Stated ONCE, at ThisBuild. It used to have to be repeated on every project:
// `With.typical` defines a project-level `scalacOptions`, and sbt delegates the
// project axis last, so an undefined `<proj> / Test / scalacOptions` reached THAT
// before `ThisBuild / Test` and the ThisBuild setting was silently ignored.
// sbt-ossuminc 3.1.0 fixed the delegation, so the five per-project applications
// are gone.
ThisBuild / Test / scalacOptions += "-Wconf:msg=fromURL:s,msg=fromPath:s"

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
  .settings(guardEmptyModule)
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
    libraryDependencies ++= Seq(Dep.compress, Dep.lang3) ++ Dep.testing,
    // SysLoggerTest asserts on what SysLogger wrote to stdout, which it captures by
    // swapping the GLOBAL System.out. sbt runs a module's suites in parallel, so any
    // other suite that printed during that window landed in the capture and the
    // assertion failed on "random garbage" -- an intermittent CI red that has nothing
    // to do with the change under test, and which had already cost three of these
    // tests: they were commented out rather than fixed.
    //
    // Serialising utils' suites removes the only source of interference. The suite's
    // own SequentialNestedSuiteExecution does NOT cover this: it orders nested suites
    // and says nothing about siblings running concurrently. utils' tests take a few
    // seconds, so the throughput cost is negligible next to a flaky release gate.
    Test / parallelExecution := false
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
      Dep.scalatest_nojvm.value % Test,
      Dep.scalactic_nojvm.value % Test
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
      Dep.scalatest_nojvm.value % Test,
      Dep.scalactic_nojvm.value % Test
    )
  )
lazy val utils = utils_cp.jvm
lazy val utilsJS = utils_cp.js
lazy val utilsNative = utils_cp.native

val Language = config("language")
lazy val language_cp = CrossModule("language", "riddl-language", V.scala)(JVM, JS, Native)
  .settings(guardEmptyModule)
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
      Dep.scalatest_nojvm.value % Test,
      Dep.scalactic_nojvm.value % Test
    )
  )

lazy val language = language_cp.jvm.dependsOn(pDep(utils))
lazy val languageJS = language_cp.js.dependsOn(pDep(utilsJS))
lazy val languageNative = language_cp.native.dependsOn(pDep(utilsNative))

val Passes = config("passes")
lazy val passes_cp = CrossModule("passes", "riddl-passes", V.scala)(JVM, JS, Native)
  .settings(guardEmptyModule)
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
  .nativeConfigure(With.NoDocs)
  // Also on the JVM row: the release workflow used to blank docs with
  // `set every Compile/doc/sources := Seq.empty`, which sbt broadened to
  // Compile/sources ITSELF -- every module then looked source-less and the
  // empty-module guard fired on all of them. Expressed here instead.
  .jvmConfigure(With.NoDocs)
val passes = passes_cp.jvm.dependsOn(pDep(utils), pDep(language))
val passesJS = passes_cp.js.dependsOn(pDep(utilsJS), pDep(languageJS))
val passesNative = passes_cp.native.dependsOn(pDep(utilsNative), pDep(languageNative))

lazy val testkit_cp = CrossModule("testkit", "riddl-testkit", V.scala)(JVM, JS, Native)
  .settings(guardEmptyModule)
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
  .settings(guardEmptyModule)
  .configure(With.typical, With.GithubPublishing, With.Scala3.configure(version = Some(V.scala)))
  .settings(
    scalaVersion := V.scala, // Override 3.3.7 LTS - see top of file for reason
    description := "Bundling of essential RIDDL libraries"
  )
  // [1.3]: riddlLib was the ONE cross-platform module with no `scala-jvm-native` test wiring, so
  // every one of its suites was JVM-only by accident of the build rather than by any platform
  // constraint. The other four modules have had this since their own gap work.
  .jvmConfigure(jvmNativeSrc("riddlLib"))
  .nativeConfigure(jvmNativeSrc("riddlLib"))
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
    libraryDependencies += Dep.upickle,
    thirdPartyNotices
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
  .nativeConfigure(With.Native(mode = "fast", buildTarget = "static"))
  .nativeConfigure(With.noMiMa)
  // See note on passes_cp re: Scala 3.8.x scaladoc race condition.
  .nativeConfigure(With.NoDocs)
  // Also on the JVM row: the release workflow used to blank docs with
  // `set every Compile/doc/sources := Seq.empty`, which sbt broadened to
  // Compile/sources ITSELF -- every module then looked source-less and the
  // empty-module guard fired on all of them. Expressed here instead.
  .jvmConfigure(With.NoDocs)
  .nativeSettings(libraryDependencies += Dep.upickle_nojvm.value)
val riddlLib = riddlLib_cp.jvm.dependsOn(pDep(utils), pDep(language), pDep(passes))
val riddlLibJS = riddlLib_cp.js.dependsOn(pDep(utilsJS), pDep(languageJS), pDep(passesJS))
val riddlLibNative =
  riddlLib_cp.native.dependsOn(pDep(utilsNative), pDep(languageNative), pDep(passesNative))

val Commands = config("commands")
lazy val commands_cp = CrossModule("commands", "riddl-commands", V.scala)(JVM, Native)
  .settings(guardEmptyModule)
  .configure(With.typical, With.GithubPublishing, With.Scala3.configure(version = Some(V.scala)))
  .settings(
    scalaVersion := V.scala, // Override 3.3.7 LTS - see top of file for reason
    scalacOptions ++= Seq("-explain", "--explain-types", "--explain-cyclic", "--no-warnings"),
    description := "RIDDL Command Infrastructure and command definitions"
  )
  .jvmConfigure(With.coverage(50))
  .jvmConfigure(With.MiMa(V.previous))
  .jvmConfigure(jvmNativeSrc("commands"))
  .jvmSettings(
    libraryDependencies ++= Seq(Dep.scopt, Dep.sconfig, Dep.scalajs_stubs),
    coverageExcludedFiles := """<empty>;$anon"""
  )
  // NOTE: A JS variant is not supported because executing commands from
  // JavaScript is not easy.
  .nativeConfigure(With.Native(mode = "fast"))
  .nativeConfigure(jvmNativeSrc("commands"))
  .nativeConfigure(With.noMiMa)
  .nativeSettings(
    libraryDependencies ++= Seq(Dep.scopt_nojvm.value, Dep.sconfig_nojvm.value)
  )
val commands: Project = commands_cp.jvm.dependsOn(pDep(utils), pDep(language), pDep(passes))
val commandsNative =
  commands_cp.native.dependsOn(pDep(utilsNative), pDep(languageNative), pDep(passesNative))

val Riddlc = config("riddlc")
lazy val riddlc_cp = CrossModule("riddlc", "riddlc", V.scala)(JVM, Native)
  .settings(guardEmptyModule)
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
    libraryDependencies += Dep.sconfig,
    thirdPartyNotices
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
  // `testOnly *`, NOT `test`. In sbt 2 `test` resolves to `testQuick`, which skips
  // suites it judges unaffected -- and the judgement survives `clean`, because the
  // action cache does. CI restores target/out from actions/cache on top of that, so
  // the JS row of a GREEN run was executing 109 of 567 tests: languageJS, passesJS
  // and testkitJS never ran at all. A release gate that skips three modules and
  // reports success is worse than no gate. `testOnly *` ignores incremental state.
  "; utils/testOnly * ; language/testOnly * ; passes/testOnly * ; testkit/testOnly * ; " +
    "commands/testOnly * ; riddlLib/testOnly * ; riddlc/testOnly *"
)
addCommandAlias(
  "tNative",
  // See tJVM above for why this is `testOnly *` and not `test`.
  //
  // Every row here MUST be a `*Native` project. Until 2026-08-05 five of them
  // named the `.jvm` rows (`utils`, `language`, `testkit`, `commands`,
  // `riddlLib`), so this alias -- and CI's Native matrix leg, which runs
  // `cNative; tNative` -- reported Native green while re-running JVM tests the
  // JVM leg had already run. Native runtime behaviour in those five modules had
  // never been executed by any gate. It was clean when finally run (176 suites /
  // 1339 tests), but that was luck, not coverage. `cNative` always named all
  // seven, so only test EXECUTION was affected, never compilation.
  "; utilsNative/testOnly * ; languageNative/testOnly * ; passesNative/testOnly * ; " +
    "testkitNative/testOnly * ; commandsNative/testOnly * ; riddlLibNative/testOnly * ; " +
    "riddlcNative/testOnly * ; riddlcNative/nativeLink"
)
addCommandAlias(
  "tJS",
  // See tJVM above. This row is the one the skipping actually bit.
  "; utilsJS/testOnly * ; languageJS/testOnly * ; passesJS/testOnly * ; " +
    "testkitJS/testOnly * ; riddlLibJS/testOnly *"
)
addCommandAlias(
  "packageArtifacts",
  "; riddlc/Universal/packageBin " +
    "; riddlcNative/nativeLink " +
    "; riddlLibJS/fullLinkJS" +
    "; riddlLibNative/nativeLink" +
    "; riddlLib/Universal/packageBin"
)
