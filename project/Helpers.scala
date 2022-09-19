import com.typesafe.sbt.packager.Keys.maintainer

import sbt.Keys._
import sbt._
import sbt.io.Path.allSubpaths
import scoverage.ScoverageKeys.coverageEnabled
import scoverage.ScoverageKeys.coverageFailOnMinimum
import scoverage.ScoverageKeys.coverageMinimumBranchTotal
import scoverage.ScoverageKeys.coverageMinimumStmtTotal
import sbtdynver.DynVerPlugin.autoImport.dynverSeparator
import sbtdynver.DynVerPlugin.autoImport.dynverSonatypeSnapshots
import sbtdynver.DynVerPlugin.autoImport.dynverVTagPrefix

/** V - Dependency Versions object */
object V {
  val commons_io = "2.11.0"
  val compress = "1.21"
  val config = "1.4.2"
  val fastparse = "2.3.3"
  val jgit = "6.3.0.202209071007-r"
  val lang3 = "3.12.0"
  val pureconfig = "0.17.1"
  val scalacheck = "1.17.0"
  val scalatest = "3.2.12"
  val scopt = "4.1.0"
}

object Dep {
  val compress = "org.apache.commons" % "commons-compress" % V.compress
  val commons_io = "commons-io" % "commons-io" % V.commons_io
  val config = "com.typesafe" % "config" % V.config
  val fastparse = "com.lihaoyi" %% "fastparse" % V.fastparse
  val jgit = "org.eclipse.jgit" % "org.eclipse.jgit" % V.jgit
  val lang3 = "org.apache.commons" % "commons-lang3" % V.lang3
  val pureconfig = "com.github.pureconfig" %% "pureconfig" % V.pureconfig
  val scalactic = "org.scalactic" %% "scalactic" % V.scalatest
  val scalatest = "org.scalatest" %% "scalatest" % V.scalatest
  val scalacheck = "org.scalacheck" %% "scalacheck" % V.scalacheck
  val scopt = "com.github.scopt" %% "scopt" % V.scopt
  val testing: Seq[ModuleID] =
    Seq(scalactic % "test", scalatest % "test", scalacheck % "test")
  val testKitDeps: Seq[ModuleID] = Seq(scalactic, scalatest, scalacheck)

}

object C {
  def withInfo(p: Project): Project = {
    p.settings(
      ThisBuild / maintainer := "reid@reactific.com",
      ThisBuild / organization := "com.reactific",
      ThisBuild / organizationHomepage :=
        Some(new URL("https://reactific.com/")),
      ThisBuild / organizationName := "Ossum Inc.",
      ThisBuild / startYear := Some(2019),
      ThisBuild / licenses +=
        (
          "Apache-2.0",
          new URL("https://www.apache.org/licenses/LICENSE-2.0.txt")
        ),
      ThisBuild / versionScheme := Option("semver-spec"),
      ThisBuild / dynverVTagPrefix := false,
      // NEVER  SET  THIS: version := "0.1"
      // IT IS HANDLED BY: sbt-dynver
      ThisBuild / dynverSeparator := "-"
    )
  }

  lazy val scala3_2_Options: Seq[String] =
    Seq("-deprecation", "-feature", "-Werror")
  lazy val scala2_13_Options: Seq[String] = Seq(
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

  def withScalaCompile(p: Project): Project = {
    p.configure(withInfo).settings(
      scalaVersion := "2.13.8",
      scalacOptions := {
        if (scalaVersion.value.startsWith("3.2")) scala3_2_Options
        else if (scalaVersion.value.startsWith("2.13")) { scala2_13_Options }
        else Seq.empty[String]
      }
    )
  }

  def withCoverage(enabled: Boolean = false)(p: Project): Project = {
    p.settings(
      coverageEnabled := enabled,
      coverageFailOnMinimum := true,
      coverageMinimumStmtTotal := 80,
      coverageMinimumBranchTotal := 80
    )
  }

  private def makeThemeResource(
    name: String,
    from: File,
    targetDir: File
  ): Seq[File] = {
    val zip = name + ".zip"
    val distTarget = targetDir / zip
    IO.copyDirectory(from, targetDir)
    val dirToZip = targetDir / name
    IO.zip(allSubpaths(dirToZip), distTarget, None)
    Seq(distTarget)
  }

  def zipResource(srcDir: String)(p: Project): Project = {
    p.settings(
      Compile / resourceGenerators += Def.task {
        val projectName = name.value
        val from = sourceDirectory.value / srcDir
        val targetDir = target.value / "dist"
        makeThemeResource(projectName, from, targetDir)
      }.taskValue,
      Compile / packageDoc / publishArtifact := false
    )
  }

  def mavenPublish(p: Project): Project = {
    p.configure(withScalaCompile).settings(
      ThisBuild / dynverSonatypeSnapshots := true,
      ThisBuild / dynverSeparator := "-",
      maintainer := "reid@ossum.biz",
      organization := "com.reactific",
      organizationName := "Ossum Inc.",
      organizationHomepage := Some(url("https://riddl.tech")),
      scmInfo := Some(ScmInfo(
        url("https://github.com/reactific/riddl"),
        "scm:git:git://github.com/reactific/riddl.git"
      )),
      developers := List(Developer(
        id = "reid-spencer",
        name = "Reid Spencer",
        email = "reid@reactific.com",
        url = url("https://riddl.tech")
      )),
      description :=
        """RIDDL is a language and toolset for specifying a system design using ideas from
          |DDD, reactive architecture, distributed systems patterns, and other software
          |architecture practices.""".stripMargin,
      licenses := List(
        "Apache License, Version 2.0" ->
          new URL("https://www.apache.org/licenses/LICENSE-2.0")
      ),
      homepage := Some(url("https://riddl.tech")),

      // Remove all additional repository other than Maven Central from POM
      pomIncludeRepository := { _ => false },
      publishTo := {
        val nexus = "https://oss.sonatype.org/"
        if (isSnapshot.value) {
          Some("snapshots" at nexus + "content/repositories/snapshots")
        } else {
          Some("releases" at nexus + "service/local/staging/deploy/maven2")
        }
      },
      publishMavenStyle := true,
      versionScheme := Some("early-semver")
    )
  }
}
