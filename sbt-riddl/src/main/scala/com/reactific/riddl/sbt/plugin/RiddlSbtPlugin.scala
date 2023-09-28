/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.sbt.plugin

import com.reactific.riddl.sbt.SbtRiddlPluginBuildInfo
import sbt.*
import sbt.Keys.*
import sbt.plugins.JvmPlugin

import scala.language.postfixOps
import scala.sys.process.*

/** A plugin that endows sbt with knowledge of code generation via riddl */
object RiddlSbtPlugin extends AutoPlugin {
  override def requires: AutoPlugin = JvmPlugin

  object autoImport {

    lazy val riddlcPath = settingKey[Option[File]]("Optionally specify path to `riddlc` compiler")

    lazy val riddlcConf = settingKey[File]("Path to the config file")

    lazy val riddlcMem = settingKey[Int]("Number of megabytes for running riddlc jvm")

    lazy val riddlcCommandPrefix = taskKey[String]("")
    lazy val riddlcMinVersion = {
      settingKey[String]("Ensure the riddlc used is at least this version")
    }
  }

  import autoImport.*

  private def makeInvocation(state: State): Def.Initialize[sbt.Task[String]] = Def.taskDyn[String] {
    val project = Project.extract(state)
    Def.task[String] {
      val fullClassPath = project.get(Runtime / fullClasspath).value.files
      val classpath = fullClassPath.map(_.getAbsoluteFile.toString).mkString(";")
      val java_home = System.getProperty("java.home")
      val path_to_java = "/bin/java"
      val cp_option = s"-cp '$classpath'"
      val mem_option = s"-Xmx${riddlcMem.value}m"
      val jre_options = s"$mem_option $cp_option"
      val main = "com.reactific.riddl.RIDDLC$"
      s"$java_home$path_to_java $jre_options $main "
    }
  }

  // Allow riddlc to be run from inside an sbt shell
  private def riddlcCommand = Command.args(
    name = "riddlc",
    display = "<options> <command> <args...> ; `riddlc help` for details"
  ) { (state, args) =>
    val project = Project.extract(state)
    val minVersion = project.get(riddlcMinVersion)
    val invocation = Def.task {
      makeInvocation(state).value
    }.value
    runRiddlc(invocation, args, minVersion)
    state
  }

  private val riddl_version = SbtRiddlPluginBuildInfo.version

  override val projectSettings: Seq[Setting[_]] = Seq(
    riddlcPath := None,
    riddlcConf := file("src/main/riddl/riddlc.conf"),
    riddlcMem := 512,
    riddlcMinVersion := SbtRiddlPluginBuildInfo.version,
    libraryDependencies := Seq(
      "com.reactific" % "riddlc_3" % riddl_version,
      "com.reactific" % "riddl-testkit_3" % riddl_version % Test
    ),
    commands ++= Seq(riddlcCommand),
    Compile / compile := Def.taskDyn {
      val c = (Compile / compile).value
      Def.task {
        val command = s"riddlc from ${riddlcConf.value} validate"
        val _ = runCommandAndRemaining(command)
        c
      }
    }.value
  )

  /**
   * Convert the given command string to a release step action, preserving and invoking remaining commands
   * Note: This was copied from https://github.com/sbt/sbt-release/blob/663cfd426361484228a21a1244b2e6b0f7656bdf/src/main/scala/ReleasePlugin.scala#L99-L115
   */
  def runCommandAndRemaining(command: String): State => State = { st: State =>
    import sbt.complete.Parser
    @annotation.tailrec
    def runCommand(command: String, state: State): State = {
      val nextState = Parser.parse(command, state.combinedParser) match {
        case Right(cmd) => cmd()
        case Left(msg) => throw sys.error(s"Invalid programmatic input:\n$msg")
      }
      nextState.remainingCommands.toList match {
        case Nil => nextState
        case head :: tail => runCommand(head.commandLine, nextState.copy(remainingCommands = tail))
      }
    }
    runCommand(command, st.copy(remainingCommands = Nil)).copy(remainingCommands = st.remainingCommands)
  }

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
    invocation: String,
    minimumVersion: String
  ): Unit = {
    import scala.sys.process.*
    val check = invocation + " version"
    println(s"Running: $check")
    val actualVersion = check.!!<.trim
    val minVersion = minimumVersion.trim
    if (!versionSameOrLater(actualVersion, minVersion)) {
      throw new IllegalArgumentException(
        s"riddlc version $actualVersion is below minimum required: $minVersion"
      )
    } else { println(s"riddlc version = $actualVersion") }
  }

  private def runRiddlc(
    invocation: String,
    options: Seq[String],
    minimumVersion: String
  ): Unit = {
    checkVersion(invocation, minimumVersion)
    val command = invocation + " " + options.mkString(" ")
    println(s"Running: $command")
    val logger = ProcessLogger(println(_))
    val rc = command.!(logger)
    logger.out(s"RC=$rc")
  }
}
