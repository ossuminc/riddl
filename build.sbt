import com.jsuereth.sbtpgp.PgpKeys.pgpSigner
import sbt.Keys.scalaVersion
import sbtbuildinfo.BuildInfoOption.{ToJson, ToMap, BuildTime}

ThisBuild / maintainer := "reid@reactific.com"
ThisBuild / organization := "com.reactific"
ThisBuild / organizationHomepage := Some(new URL("https://reactific.com/"))
ThisBuild / organizationName := "Ossum Inc."
ThisBuild / startYear := Some(2019)
ThisBuild / licenses +=
  ("Apache-2.0", new URL("https://www.apache.org/licenses/LICENSE-2.0.txt"))

Global / onChangedBuildSource := ReloadOnSourceChanges
(Global / excludeLintKeys) ++=
  Set(buildInfoPackage, buildInfoKeys, buildInfoOptions, mainClass, maintainer)

ThisBuild / versionScheme := Option("semver-spec")
ThisBuild / dynverVTagPrefix := false

// NEVER  SET  THIS: version := "0.1"
// IT IS HANDLED BY: sbt-dynver
ThisBuild / dynverSeparator := "-"
ThisBuild / scalaVersion := "2.13.8"

lazy val scala2_13_Options = Seq(
  "-target:17",
  // "-Ypatmat-exhaust-depth 40", Zinc can't handle this :(
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

lazy val riddl = (project in file("."))
  .settings(
    publish := {}, publishLocal := {}, pgpSigner / skip := true,
    publishTo:= Some(Resolver.defaultLocal)
  ).aggregate(
    utils,
    language,
    testkit,
    `hugo-translator`,
    `hugo-git-check`,
    examples,
    doc,
    riddlc,
    `sbt-riddl`
  )

lazy val utils = project.in(file("utils")).configure(C.withCoverage())
  .configure(C.mavenPublish)
  .settings(
    name := "riddl-utils",
    coverageExcludedPackages := "<empty>",
    scalacOptions := scala2_13_Options

  )

lazy val language = project.in(file("language"))
  .configure(C.withCoverage()).configure(C.mavenPublish).settings(
    name := "riddl-language",
    coverageExcludedPackages :=
      "<empty>;.*AST;.*BuildInfo;.*PredefinedType;.*Terminals.*",
    scalacOptions := scala2_13_Options,
    libraryDependencies ++= Seq(Dep.scopt, Dep.fastparse, Dep.lang3) ++ Dep.testing
  )

lazy val testkit = project.in(file("testkit")).configure(C.mavenPublish)
  .settings(
    name := "riddl-language-testkit",
    scalacOptions := scala2_13_Options,
    libraryDependencies ++= Dep.testKitDeps
  ).dependsOn(language)

lazy val `hugo-translator`: Project = project.in(file("hugo-translator"))
  .configure(C.mavenPublish).settings(
    name := "riddl-hugo-translator",
    Compile / unmanagedResourceDirectories += {
      baseDirectory.value / "resources"
    },
    Test / parallelExecution := false,
    libraryDependencies ++= Seq(Dep.pureconfig) ++ Dep.testing
  ).dependsOn(language % "compile->compile", testkit % "test->compile")
  .dependsOn(utils)

lazy val `hugo-git-check`: Project = project.in(file("hugo-git-check"))
  .configure(C.mavenPublish).settings(
    name := "riddl-hugo-git-check-translator",
    Compile / unmanagedResourceDirectories += {
      baseDirectory.value / "resources"
    },
    Test / parallelExecution := false,
    libraryDependencies ++= Seq(Dep.pureconfig, Dep.jgit) ++ Dep.testing
  ).dependsOn(`hugo-translator` % "compile->compile;test->test")

lazy val examples = project.in(file("examples")).settings(
  name := "riddl-examples",
  Compile / packageBin / publishArtifact := false,
  Compile / packageDoc / publishArtifact := false,
  Compile / packageSrc / publishArtifact := false,
  publishTo := Option(Resolver.defaultLocal),
  libraryDependencies ++= Seq("org.scalatest" %% "scalatest" % "3.2.12" % "test")
).dependsOn(`hugo-translator` % "test->test", riddlc)

lazy val doc = project.in(file("doc")).enablePlugins(SitePlugin)
  .enablePlugins(SiteScaladocPlugin).configure(C.zipResource("hugo")).settings(
    name := "riddl-doc",
    publishTo := Option(Resolver.defaultLocal),
    // Hugo / sourceDirectory := sourceDirectory.value / "hugo",
    publishSite
  ).dependsOn(`hugo-translator` % "test->test", riddlc)

lazy val riddlc: Project = project.in(file("riddlc"))
  .enablePlugins(JavaAppPackaging,UniversalDeployPlugin,BuildInfoPlugin)
  .enablePlugins(MiniDependencyTreePlugin)
  .configure(C.mavenPublish)
  .dependsOn(
    language,
    `hugo-translator` % "compile->compile;test->test",
    `hugo-git-check` % "compile->compile;test->test"
  )
  .settings(
    name := "riddlc",
    mainClass := Option("com.reactific.riddl.RIDDLC"),
    scalacOptions := scala2_13_Options,
    libraryDependencies ++= Seq(Dep.pureconfig) ++ Dep.testing,
    maintainer := "reid@reactific.com",
    buildInfoObject := "BuildInfo",
    buildInfoPackage := "com.reactific.riddl",
    buildInfoOptions := Seq(ToMap, ToJson, BuildTime),
    buildInfoUsePackageAsPath := true,
    buildInfoKeys ++= Seq[BuildInfoKey](
      name,
      version,
      description,
      organization,
      organizationName,
      BuildInfoKey.map(organizationHomepage) {
        case (k, v) => k -> v.get.toString
      },
      BuildInfoKey.map(homepage) {
        case (k, v) => "projectHomepage" -> v.map(_.toString).getOrElse("http://riddl.tech")
      },
      BuildInfoKey.map(startYear) {
        case (k, v) => k -> v.get.toString
      },
      scalaVersion,
      sbtVersion,
      BuildInfoKey.map(licenses) {
        case (k,v) => k -> v.map(_._1).mkString(", ")
      }
    )
  )

lazy val `sbt-riddl` = (project in file("sbt-riddl")).enablePlugins(SbtPlugin)
  .configure(C.mavenPublish).settings(
    name := "sbt-riddl",
    sbtPlugin := true,
    scalaVersion := "2.12.15",
    scriptedLaunchOpts := {
      scriptedLaunchOpts.value ++
        Seq("-Xmx1024M", "-Dplugin.version=" + version.value)
    },
    scriptedBufferLog := false
  )
