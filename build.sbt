import sbt.Keys.scalaVersion
import sbtbuildinfo.BuildInfoOption.{BuildTime, ToMap}

maintainer := "reid@reactific.com"
Global / onChangedBuildSource := ReloadOnSourceChanges
ThisBuild / versionScheme := Some("semver-spec")
ThisBuild / dynverVTagPrefix := false

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
  "-Wunused:imports", // Warn if an import selector is not referenced.
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
  .aggregate(language, `hugo-generator`, riddlc, `sbt-riddl`, doc)

val themeTask = taskKey[File]("Task to generate theme artifact")
lazy val `riddl-hugo-theme` = project.in(file("riddl-hugo-theme")).settings(
  Compile / packageBin / publishArtifact := false,
  Compile / packageDoc / publishArtifact := false,
  Compile / packageSrc / publishArtifact := false,
  themeTask := {
    import scala.sys.process._
    val artifact = Artifact("riddl-hugo-theme", "zip", "zip")
    val output: File = target.value.toPath.toAbsolutePath.toFile /
      (artifact.name + "." + artifact.extension)
    val inputDir: File = sourceDirectory.value / "main"
    val command = s"zip -rv9o ${output.toString} riddl-hugo-theme"
    val pb = Process(command, inputDir)
    println(command)
    pb.run
    output
  },
  addArtifact(Artifact("riddl-hugo-theme", "zip", "zip"), themeTask)
)

lazy val doc = project.in(file("doc")).enablePlugins(SitePlugin).enablePlugins(HugoPlugin)
  .enablePlugins(SiteScaladocPlugin).settings(
    name := "riddl-doc",
    publishTo := Some(Resolver.defaultLocal),
    Hugo / sourceDirectory := sourceDirectory.value / "hugo",
    // minimumHugoVersion := "0.89.4",
    publishSite
  ).dependsOn(language % "test->compile;test->test")

lazy val riddlc = project.in(file("riddlc")).enablePlugins(BuildInfoPlugin)
  .enablePlugins(JavaAppPackaging).settings(
    name := "riddlc",
    mainClass := Some("com.yoppworks.ossum.riddl.RIDDLC"),
    scalacOptions := scala2_13_Options,
    libraryDependencies ++= Seq(Dep.cats_core, Dep.scopt, Dep.config),
    buildInfoObject := "BuildInfo",
    buildInfoPackage := "com.yoppworks.ossum.riddl",
    buildInfoUsePackageAsPath := true
  ).dependsOn(language, `hugo-generator`)

lazy val language = project.in(file("language")).enablePlugins(BuildInfoPlugin)
  .configure(C.withCoverage).settings(
  name := "riddl-language",
  buildInfoObject := "BuildInfo",
  buildInfoPackage := "com.yoppworks.ossum.riddl.language",
  buildInfoUsePackageAsPath := true,
  scalacOptions := scala2_13_Options,
  libraryDependencies ++= Dep.parsing ++ Dep.testing,
  Compile / compileCheck := {
    Def.sequential(Compile / compile, (Compile / scalastyle).toTask("")).value
  }
)

lazy val `hugo-generator` = (project in file("hugo-generator")).enablePlugins(BuildInfoPlugin)
  .settings(
    name := "riddl-hugo-generator",
    buildInfoPackage := "com.yoppworks.ossum.riddl.generation.hugo",
    Compile / unmanagedResourceDirectories += { baseDirectory.value / "resources" },
    Test / parallelExecution := false,
    libraryDependencies ++= Seq(Dep.pureconfig, Dep.cats_core) ++ Dep.testing
  ).dependsOn(language, `riddl-hugo-theme`)

// lazy val exampleTask = taskKey[File]("Task to generate example hugo docs")
// lazy val runRiddlc = taskKey[Unit]("Run the riddlc compiler")

lazy val `example` = project.in(file("example")).settings(
  name := "riddl-example",
  Compile / packageBin / publishArtifact := false,
  Compile / packageDoc / publishArtifact := false,
  Compile / packageSrc / publishArtifact := false,
  publishTo := Some(Resolver.defaultLocal),
  libraryDependencies ++= Seq("org.scalatest" %% "scalatest" % "3.2.9" % "test")
  /*
    runRiddlc := {
      val outDir: File = target.value / "riddlc" / "ReactiveBBQ"
      val inputDir: File = sourceDirectory.value / "riddl" / "ReactiveBBQ"
      riddlc/Compile/run.inputTaskValue.fullInput(
        s"translate hugo -i ${inputDir}/rbbq.riddl -c rbbq.conf -o $outDir"
      ).parsed
    },
    exampleTask := {
      import scala.sys.process._
      val outDir: File = target.value / "riddlc" / "ReactiveBBQ"
      // val inputDir: File = sourceDirectory.value / "riddl" / "ReactiveBBQ"
      // val riddlcPath: File = target.value.getParentFile.getParentFile.getAbsoluteFile / "riddlc" /
        "target" / "universal" / "scripts" / "bin" / "riddlc"
      // val cp: Seq[File] = (Runtime/fullClasspath).value.files
      // val foo = riddlc/Compile/run.value.fullInput("translate hugo -i rbbq.riddl -c rbbq.conf -o $outDir")
      runRiddlc.value
      // val pb = Process(command, inputDir)
      // println(command)
      // val stdout = pb.!!
      // println(stdout)
      val artifact = Artifact("riddl-example", "zip", "zip")
      val output: File = target.value / (artifact.name + "." + artifact.extension)
      val zipCommand = s"zip -rv9o ${output.toString} ReactiveBBQ"
      val zpb = Process(zipCommand, outDir.getParentFile)
      println(zipCommand)
      zpb.run
      output
    },
    addArtifact(Artifact("riddl-example", "zip", "zip"), exampleTask)
   */
).dependsOn(language % "test->compile;test->test", riddlc)

lazy val `sbt-riddl` = (project in file("sbt-riddl")).enablePlugins(SbtPlugin)
  .enablePlugins(BuildInfoPlugin).settings(
    name := "sbt-riddl",
    sbtPlugin := true,
    scalaVersion := "2.12.15",
    buildInfoPackage := "com.yoppworks.ossum.riddl.sbt.plugin"
  )

(Global / excludeLintKeys) ++=
  Set(buildInfoPackage, buildInfoKeys, buildInfoOptions, mainClass, maintainer)
