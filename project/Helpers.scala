import com.typesafe.sbt.packager.Keys.maintainer
import sbt.Keys.organizationName
import sbt.Keys._
import sbt._
import sbt.io.Path.allSubpaths
import sbtbuildinfo.BuildInfoKey
import sbtbuildinfo.BuildInfoKeys.{buildInfoKeys, buildInfoObject, buildInfoPackage, buildInfoUsePackageAsPath}
import sbtbuildinfo.BuildInfoOption.{BuildTime, ToJson, ToMap}
import sbtbuildinfo.BuildInfoPlugin.autoImport.buildInfoOptions
import scoverage.ScoverageKeys._
import sbtdynver.DynVerPlugin.autoImport.dynverSeparator
import sbtdynver.DynVerPlugin.autoImport.dynverSonatypeSnapshots
import wartremover.{Wart, WartRemover}
import wartremover.WartRemover.autoImport._

import java.net.URI
import java.util.Calendar
import scala.collection.Seq


object C {

  final val defaultPercentage: Int = 50

  def withCoverage(percent: Int = defaultPercentage)(p: Project): Project = {
    p.settings(
      coverageFailOnMinimum := true,
      coverageMinimumStmtTotal := percent,
      coverageMinimumBranchTotal := percent,
      coverageMinimumStmtPerPackage := percent,
      coverageMinimumBranchPerPackage := percent,
      coverageMinimumStmtPerFile := percent,
      coverageMinimumBranchPerFile := percent,
      coverageExcludedPackages := "<empty>"
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
      maintainer := "reid@ossum.biz",
      organization := "com.reactific",
      organizationName := "Ossum Inc.",
      organizationHomepage := Some(url("https://riddl.tech")),
      scmInfo := Some(
        ScmInfo(
          url("https://github.com/reactific/riddl"),
          "scm:git:git://github.com/reactific/riddl.git"
        )
      ),
      developers := List(
        Developer(
          id = "reid-spencer",
          name = "Reid Spencer",
          email = "reid@reactific.com",
          url = url("https://riddl.tech")
        )
      ),
      description :=
        """RIDDL is a language and toolset for specifying a system design using ideas from
          |DDD, reactive architecture, distributed systems patterns, and other software
          |architecture practices.""".stripMargin,
      licenses := List(
        "Apache License, Version 2.0" ->
          URI.create("https://www.apache.org/licenses/LICENSE-2.0").toURL
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
      publishMavenStyle := true
    )
  }
}
