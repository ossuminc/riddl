import sbt.Keys._

import sbt._
import sbt.io.Path.allSubpaths
import scoverage.ScoverageKeys.coverageEnabled
import scoverage.ScoverageKeys.coverageFailOnMinimum
import scoverage.ScoverageKeys.coverageMinimumBranchTotal
import scoverage.ScoverageKeys.coverageMinimumStmtTotal
import sbtdynver.DynVerPlugin.autoImport.dynverSonatypeSnapshots
import sbtdynver.DynVerPlugin.autoImport.dynverSeparator

/** V - Dependency Versions object */
object V {
  val cats = "2.7.0"
  val config = "1.4.1"
  val fastparse = "2.3.3"
  val jgit = "6.0.0.202111291000-r"
  val pureconfig = "0.17.1"
  val scalacheck = "1.15.4"
  val scalatest = "3.2.9"
  val scopt = "4.0.1"
  val ujson = "1.5.0"
}

object Dep {
  val cats_core = "org.typelevel" %% "cats-core" % V.cats
  val config = "com.typesafe" % "config" % V.config
  val fastparse = "com.lihaoyi" %% "fastparse" % V.fastparse
  val jgit = "org.eclipse.jgit" % "org.eclipse.jgit" % V.jgit
  val pureconfig = "com.github.pureconfig" %% "pureconfig" % V.pureconfig
  val scalactic = "org.scalactic" %% "scalactic" % V.scalatest
  val scalatest = "org.scalatest" %% "scalatest" % V.scalatest
  val scalacheck = "org.scalacheck" %% "scalacheck" % V.scalacheck
  val scopt = "com.github.scopt" %% "scopt" % V.scopt
  val ujson = "com.lihaoyi" %% "ujson" % V.ujson

  val testing = Seq(scalactic % "test", scalatest % "test", scalacheck % "test")
  val testKitDeps = Seq(scalactic, scalatest, scalacheck)

}

object C {
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
    p.settings(
      ThisBuild / dynverSonatypeSnapshots := true,
      ThisBuild / dynverSeparator := "-",
      organization := "com.reactific",
      organizationName := "Reactific Software LLC",
      organizationHomepage := Some(url("https://riddl.tech")),
      scmInfo := Some(ScmInfo(
        url("https://github.com/reactific/riddl"),
        "scm:git@github.reactific/riddl.git"
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
