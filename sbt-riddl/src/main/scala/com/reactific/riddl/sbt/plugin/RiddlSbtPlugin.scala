/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.sbt.plugin

import com.reactific.riddl.sbt.SbtRiddlPluginBuildInfo
import com.reactific.riddl.sbt.plugin.RiddlSbtPlugin.V
import sbt.{Def, *}
import sbt.Keys.*
import sbt.plugins.JvmPlugin

import scala.language.postfixOps
import scala.sys.process.*

/** A plugin that endows sbt with knowledge of code generation via riddl */
object RiddlSbtPlugin extends AutoPlugin {
  override def requires: AutoPlugin = JvmPlugin

  object autoImport {

    lazy val riddlcPath = settingKey[File]("Path to `riddlc` compiler")

    lazy val riddlcConf = settingKey[File]("Path to the config file")

    lazy val riddlcOptions = settingKey[Seq[String]]("Options to pass to riddlc")

    lazy val riddlcMinVersion = {
      settingKey[String]("Ensure the riddlc used is at least this version")
    }
  }

  import autoImport.*

  lazy val compileTask = taskKey[Unit]("A task to invoke riddlc compiler")
  lazy val infoTask = taskKey[Unit]("A task to invoke riddlc info command")

  // Allow riddlc to be run from inside an sbt shell
  def riddlcCommand = Command.args(
    name = "riddlc",
    display = "<options> <command> <args...> ; `riddlc help` for details"
  ) { (state, args) =>
    val project = Project.extract(state)
    val path = project.get(riddlcPath)
    val minVersion = project.get(riddlcMinVersion)
    runRiddlc(path, args, minVersion)
    state
  }

  def infoCommand = Command.args(
    name = "info",
    display = "prints out riddlc info"
  ) { (state, _) =>
    val project = Project.extract(state)
    val path = project.get(riddlcPath)
    val minVersion = project.get(riddlcMinVersion)
    val options = Seq("info")
    runRiddlc(path, options, minVersion)
    state

  }

  object V {
    val scala = "3.3.1" // NOTE: Synchronize with Helpers.C.withScala3
    val scalacheck = "1.17.0" // NOTE: Synchronize with Helpers.V.scalacheck
    val scalatest = "3.2.17" // NOTE: Synchronize with Helpers.V.scalatest
    val riddl: String = SbtRiddlPluginBuildInfo.version
  }

  override def projectSettings: Seq[Setting[_]] = Seq(
    scalaVersion := V.scala,
    libraryDependencies ++= Seq(
      "com.reactific" %% "riddlc" % V.riddl,
      "com.reactific" %% "riddl-testkit" % V.riddl % Test,
      "org.scalactic" %% "scalactic" % V.scalatest % Test,
      "org.scalatest" %% "scalatest" % V.scalatest % Test,
      "org.scalacheck" %% "scalacheck" % V.scalacheck % Test
    ),
    Test / classLoaderLayeringStrategy := ClassLoaderLayeringStrategy.ScalaLibrary,
    riddlcPath := file("riddlc"),
    riddlcConf := file("src/main/riddl/riddlc.conf"),
    riddlcOptions := Seq("--show-times", "--hide-warnings"),
    riddlcMinVersion := SbtRiddlPluginBuildInfo.version,
    compileTask := {
      val execPath = riddlcPath.value
      val conf = riddlcConf.value.getAbsoluteFile.toString
      val options = riddlcOptions.value
      val version = riddlcMinVersion.value
      val args = options ++ Seq("from", conf, "validate")
      runRiddlc(execPath, args, version)
    },
    infoTask := {
      val execPath = riddlcPath.value
      val options = Seq("info")
      val version = riddlcMinVersion.value
      runRiddlc(execPath, options, version)
    },
    commands ++= Seq(riddlcCommand),
    Compile / compile := Def.taskDyn {
      val c = (Compile / compile).value
      Def.task {
        val _ = (Compile / compileTask).value
        c
      }
    }.value
  )

  private def versionTriple(version: String): (Int, Int, Int) = {
    val trimmed = version.indexOf('-') match {
      case x: Int if x < 0 => version
      case y: Int          => version.take(y)
    }
    val parts = trimmed.split('.')
    if (parts.length < 3) {
      throw new IllegalArgumentException(
        s"riddlc version ($version) has insufficient semantic versioning parts."
      )
    } else { (parts(0).toInt, parts(1).toInt, parts(2).toInt) }
  }

  private def versionSameOrLater(actualVersion: String, minVersion: String): Boolean = {
    if (actualVersion != minVersion) {
      val (aJ, aN, aP) = versionTriple(actualVersion)
      val (mJ, mN, mP) = versionTriple(minVersion)
      aJ > mJ || ((aJ == mJ) && ((aN > mN) || ((aN == mN) && (aP >= mP))))
    } else { true }
  }

  private def checkVersion(
    riddlc: sbt.File,
    minimumVersion: String
  ): Unit = {
    import scala.sys.process.*
    val check = riddlc.toString + " version"
    val actualVersion = check.!!<.trim
    val minVersion = minimumVersion.trim
    if (!versionSameOrLater(actualVersion, minVersion)) {
      throw new IllegalArgumentException(
        s"riddlc version $actualVersion is below minimum required: $minVersion"
      )
    } else { println(s"riddlc version = $actualVersion") }
  }

  private def runRiddlc(
    riddlc: sbt.File,
    args: Seq[String],
    minimumVersion: String
  ): Unit = {
    checkVersion(riddlc, minimumVersion)
    val command = riddlc.toString + " " + args.mkString(" ")
    val logger = ProcessLogger(println(_))
    println(s"RIDDLC: $command")
    val rc = command.!(logger)
    logger.out(s"RC=$rc")
  }
}
