/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.sbt.plugin

import com.reactific.riddl.sbt.SbtRiddlPluginBuildInfo
import sbt.*
import sbt.Keys.*
import sbt.internal.util.ManagedLogger
import sbt.plugins.JvmPlugin

import java.io.File
import java.nio.file.FileSystem
import scala.language.postfixOps
import scala.sys.process.*

/** A plugin that endows sbt with knowledge of code generation via riddl */
object RiddlSbtPlugin extends AutoPlugin {
  override def requires: AutoPlugin = JvmPlugin

  object autoImport {

    lazy val riddlcPath = settingKey[Option[File]]("Optional path to riddlc").withRank(KeyRanks.Invisible)

    lazy val riddlcConf = settingKey[File]("Path to the config file")

    lazy val riddlcOptions = settingKey[Seq[String]]("Options to pass to riddlc")

    lazy val riddlcMinVersion = {
      settingKey[String]("Ensure the riddlc used is at least this version")
    }

    lazy val findRiddlcTask = taskKey[File]("Find the riddlc program locally")
  }

  import autoImport.*

  object V {
    val scala = "3.3.1" // NOTE: Synchronize with Helpers.C.withScala3
    val scalacheck = "1.17.0" // NOTE: Synchronize with Helpers.V.scalacheck
    val scalatest = "3.2.17" // NOTE: Synchronize with Helpers.V.scalatest
    val riddl: String = SbtRiddlPluginBuildInfo.version
  }

  /*private def getLogger(project: Extracted, state: State): ManagedLogger = {
    val (_, strms) = project.runTask(, state)
    project.structure.streams(state).log
    strms.log
  }*/

  override def projectSettings: Seq[Setting[_]] = Seq(
    // Global / excludeLintKeys ++= Seq(riddlcConf, riddlcOptions),
    scalaVersion := V.scala,
    libraryDependencies ++= Seq(
      "com.reactific" %% "riddlc" % V.riddl,
      "com.reactific" %% "riddl-testkit" % V.riddl % Test,
      "org.scalactic" %% "scalactic" % V.scalatest % Test,
      "org.scalatest" %% "scalatest" % V.scalatest % Test,
      "org.scalacheck" %% "scalacheck" % V.scalacheck % Test
    ),
    Test / classLoaderLayeringStrategy := ClassLoaderLayeringStrategy.ScalaLibrary,
    riddlcPath := None,
    riddlcOptions := Seq("--show-times"),
    riddlcConf := file("src/main/riddl/riddlc.conf"),
    riddlcMinVersion := SbtRiddlPluginBuildInfo.version,
    findRiddlcTask := {
      val found: File = riddlcPath.value match {
        case Some(path) =>
          if (path.getAbsolutePath.endsWith("riddlc")) path else {
            throw new IllegalStateException(s"Your riddlcPath setting is not the full path to the riddlc program ")
          }
        case None =>
          val riddlc_path = System.getenv("RIDDLC_PATH")
          if (riddlc_path.contains("riddlc")) {
            new File(riddlc_path)
          } else {
            val user_path = System.getenv("PATH")
            // FIXME: Won't work on Windoze, make it work
            val parts = user_path.split(":")
            val with_riddlc = parts.map { part: String =>
              if (part.contains("riddlc")) part else part + "/riddlc"
            }
            with_riddlc.find { (potential: String) =>
              Path(potential).exists
            } match {
              case Some(found) =>
                new File(found)
              case None =>
                throw new IllegalStateException(
                  "Can't find the 'riddlc' program in your path. Please install.\n" +
                    parts.mkString("\n")
                )
            }
          }
      }
      if (!found.exists) {
        throw new IllegalStateException(s"riddlc in PATH environment var, but executable not found: $found")
      }
      found
    },
    commands ++= Seq(riddlcCommand, infoCommand, hugoCommand, validateCommand, statsCommand)
  )

  private def runRiddlcAction(state: State, args: Seq[String]): (State, Int) = {
    val project = Project.extract(state)
    val riddlcPath = project.runTask(findRiddlcTask, state)
    val minimumVersion: String = project.get(riddlcMinVersion)
    val options: Seq[String] = project.get(riddlcOptions)
    val rc = runRiddlc(riddlcPath._2, minimumVersion, options, args)
    state -> rc
  }

  // Allow riddlc to be run from inside an sbt shell
  private def riddlcCommand: Command = {
    Command.args(
      name = "riddlc",
      display = "<options> <command> <args...> ; `riddlc help` for details"
    ) { (state: State, args: Seq[String]) =>
      runRiddlcAction(state, args)._1
    }
  }

  def infoCommand: Command = {
    Command.command("info") { (state: State) =>
      runRiddlcAction(state, Seq("info"))._1
    }
  }

  def hugoCommand: Command = {
    Command.command("hugo") { (state: State) =>
      val project = Project.extract(state)
      val conf = project.get(riddlcConf).getAbsoluteFile.toString
      runRiddlcAction(state, Seq("from", conf, "hugo"))._1
    }
  }

  def validateCommand: Command = {
    Command.command("validate") { (state: State) =>
      val project = Project.extract(state)
      val conf = project.get(riddlcConf).getAbsoluteFile.toString
      runRiddlcAction(state, Seq("from", conf, "validate"))._1
    }
  }

  def statsCommand: Command = {
    Command.command("stats") { (state: State) =>
      val project = Project.extract(state)
      val conf = project.get(riddlcConf).getAbsoluteFile.toString
      runRiddlcAction(state, Seq("from", conf, "stats"))._1
    }
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
    riddlcPath: File,
    minimumVersion: String
  ): Unit = {
    import scala.sys.process.*
    val check = riddlcPath.getAbsolutePath + " version"
    val actualVersion = check.!!<.trim
    val minVersion = minimumVersion.trim
    if (!versionSameOrLater(actualVersion, minVersion)) {
      throw new IllegalArgumentException(
        s"riddlc version $actualVersion is below minimum required: $minVersion"
      )
    } // else { println(s"riddlc version = $actualVersion") }
  }

  private def runRiddlc(
    // streams: TaskStreams,
    riddlcPath: File,
    minimumVersion: String,
    options: Seq[String],
    args: Seq[String]
  ): Int = {
    checkVersion(riddlcPath, minimumVersion)
    // streams.log.info(s"Running: riddlc ${args.mkString(" ")}\n")
    // println(s"Running: riddlc ${args.mkString(" ")}\n")
    // FIXME: use sbt I/O
    val logger = ProcessLogger(println(_))
    val process = Process(riddlcPath.getAbsolutePath, options ++ args)
    process.!(logger)
  }
}
