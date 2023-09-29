/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.sbt.plugin

import com.reactific.riddl.sbt.SbtRiddlPluginBuildInfo
import com.reactific.riddl.sbt.plugin.RiddlSbtPlugin.V
import sbt._
import sbt.Keys._
import sbt.plugins.JvmPlugin

import scala.language.postfixOps
import scala.sys.process.*

/** A plugin that endows sbt with knowledge of code generation via riddl */
object RiddlSbtPlugin extends AutoPlugin {
  override def requires: AutoPlugin = JvmPlugin

  object autoImport {

    lazy val riddlcPath = settingKey[Option[File]]("Optionally specify path to `riddlc` compiler")

    lazy val riddlcConf = settingKey[File]("Path to the config file")

    lazy val riddlcOptions = settingKey[Seq[String]]("Options to pass to riddlc")

    lazy val riddlcJvmHeapSize = settingKey[Int]("Number of megabytes of heap memory for running riddlc via jvm")

    lazy val riddlcMinVersion = {
      settingKey[String]("Ensure the riddlc used is at least this version")
    }

    lazy val runRiddlcTask = taskKey[Int]("Runs riddlc compiler")
    lazy val validateTask = taskKey[Int]("Runs riddlc validate command")
    lazy val infoTask = taskKey[Int]("Runs the riddlc info command")
  }

  import autoImport.*

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
    riddlcOptions := Seq("--show-times"),
    riddlcPath := None,
    riddlcConf := file("src/main/riddl/riddlc.conf"),
    riddlcJvmHeapSize := 512,
    riddlcMinVersion := SbtRiddlPluginBuildInfo.version,
    // commands ++= Seq(riddlcCommand, infoCommand),
    runRiddlcTask := {
      val s: TaskStreams = streams.value
      val classPath = (Runtime / fullClasspath).value
      val minimumVersion = riddlcMinVersion.value
      val options = riddlcOptions.value
      val heapSize = riddlcJvmHeapSize.value
      val args = Seq.empty[String]
      runRiddlc(s, classPath, heapSize, minimumVersion, options, args  )
    },
    validateTask := {
      val s: TaskStreams = streams.value
      val classPath = (Runtime / fullClasspath).value
      val conf = riddlcConf.value.getAbsoluteFile.toString
      val minimumVersion = riddlcMinVersion.value
      val options = riddlcOptions.value
      val heapSize = riddlcJvmHeapSize.value
      val args = Seq("from", conf, "validate")
      runRiddlc(s, classPath, heapSize, minimumVersion, options, args )
    },
    infoTask := {
      val s: TaskStreams = streams.value
      val classPath = (Runtime / fullClasspath).value
      val conf = riddlcConf.value.getAbsoluteFile.toString
      val minimumVersion = riddlcMinVersion.value
      val options = riddlcOptions.value
      val heapSize = riddlcJvmHeapSize.value
      val args = Seq("info")
      runRiddlc(s, classPath, heapSize, minimumVersion, options, args )
    },
    Compile / compile := Def.task {
      val c = (Compile / compile).value
      val s: TaskStreams = streams.value
      val classPath = (Runtime / fullClasspath).value
      val minimumVersion = riddlcMinVersion.value
      val conf = riddlcConf.value.getAbsoluteFile.toString
      val heapSize = riddlcJvmHeapSize.value
      val options = riddlcOptions.value
      val args = Seq("from", conf, "validate")
      runRiddlc(s, classPath, heapSize, minimumVersion, options, args)
      c
    }.value
  )

  /*
  // Allow riddlc to be run from inside an sbt shell
  private def riddlcCommand: Command = {
    Command.args(
      name = "riddlc",
      display = "<options> <command> <args...> ; `riddlc help` for details",
      help = Help.empty
    ) { (state, args) =>
      Def.task {
        val project = Project.extract(state)
        val s: TaskStreams = project.get(streams).value
        val classPath = project.get(Runtime / fullClasspath).value
        val minimumVersion = project.get(riddlcMinVersion)
        val conf: String = project.get(riddlcConf).getAbsolutePath
        val options = riddlcOptions.value
        val heapSize = riddlcJvmHeapSize.value
        val args = options ++ Seq("from", conf, "validate")
        runRiddlc(s, classPath, heapSize, minimumVersion, options, args)
      }
      state
    }
  }

  def infoCommand = Command.args(
    name = "info",
    display = "prints out riddlc info"
  ) { (state, _) =>
    import state._
    val s: TaskStreams = streams.value
    val extracted = Project.extract(state)
      import extracted._
    val classPath = currentRef.get(Runtime / fullClasspath).value
    val minimumVersion = project.get(riddlcMinVersion)
    val conf = riddlcConf.value.getAbsoluteFile.toString
    val options = riddlcOptions.value
    val heapSize = riddlcJvmHeapSize.value
    val args = Seq("info")
    runRiddlc(s, classPath, heapSize, minimumVersion, options, args)
    state
  }


*/
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
    commandPrefix: String,
    minimumVersion: String
  ): Unit = {
    import scala.sys.process.*
    val check = commandPrefix + " version"
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
    s: TaskStreams,
    fullClassPath: Classpath,
    heapSize: Int,
    minimumVersion: String,
    options: Seq[String],
    args: Seq[String]
  ): Int = {
    val invocation = makeRiddlcCommandPrefix(fullClassPath, heapSize, options)
    checkVersion(invocation, minimumVersion)
    val command = invocation + args.mkString(" ", " ", "")
    s.log.info(s"RIDDLC: $command")
    val logger = ProcessLogger(println(_))
    val rc: Int = command.!(logger)
    logger.out(s"RC=$rc")
    rc
  }

  private def makeRiddlcCommandPrefix(
    fullClassPath: Classpath,
    jvmHeapSize: Int,
    riddlc_options: Seq[String]
  ): String = {
    val classpath = fullClassPath.files.map(_.getAbsoluteFile.toString).mkString(";")
    val java_home = System.getProperty("java.home")
    val path_to_java = "/bin/java"
    val cp_option = s"-cp '$classpath'"
    val mem_option = s"-Xmx${jvmHeapSize}m"
    val jre_options = s"$mem_option $cp_option"
    val main = "com.reactific.riddl.RIDDLC$"
    s"$java_home$path_to_java $jre_options $main $riddlc_options"
  }
}
