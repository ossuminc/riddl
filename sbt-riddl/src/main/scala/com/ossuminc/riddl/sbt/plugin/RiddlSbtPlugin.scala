/*
 * Copyright 2019-2024 Ossum, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ossuminc.riddl.sbt.plugin

import com.ossuminc.riddl.sbt.SbtRiddlPluginBuildInfo
import sbt.*
import sbt.Keys.*
import sbt.plugins.JvmPlugin

import java.io.File
import java.nio.file.{Files, Path}
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

    lazy val findRiddlcTask = taskKey[Option[File]]("Find the riddlc program locally")
  }

  import autoImport.*

  private object V {
    val scala = "3.4.1" // NOTE: Synchronize with Helpers.C.withScala3
    val scalacheck = "1.17.0" // NOTE: Synchronize with Dependencies.V.scalacheck
    val scalatest = "3.2.18" // NOTE: Synchronize with Depenendencies.V.scalatest
    val riddl: String = SbtRiddlPluginBuildInfo.version
  }

  /*private def getLogger(project: Extracted, state: State): ManagedLogger = {
    val (_, strms) = project.runTask(, state)
    project.structure.streams(state).log
    strms.log
  }*/

  private val riddlc_partial_path: String =
    Path.of("riddl", "riddlc", "target", "universal", "stage", "bin", "riddlc").toString

  private def findRiddlcPathInPATH: Option[Path] = {
    val user_path = sys.env("PATH")
    val parts = user_path.split(File.pathSeparatorChar)
    val viable_paths = parts.filter {
      part: String =>
        part.endsWith("bin") && Files.isExecutable(java.nio.file.Path.of(part, "/riddlc"))
    }
    viable_paths.headOption match {
      case Some(path) =>
        Some(Path.of(path))
      case None =>
        None
    }
  }

  override def projectSettings: Seq[Setting[_]] = Seq(
    // Global / excludeLintKeys ++= Seq(riddlcConf, riddlcOptions),
    scalaVersion := V.scala,
    libraryDependencies ++= Seq(
      "com.ossuminc" %% "riddlc" % V.riddl,
      "com.ossuminc" %% "riddl-testkit" % V.riddl % Test,
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
      val found: Option[Path] = riddlcPath.value match {
        case Some(path) =>
          if (path.getAbsolutePath.endsWith("riddlc")) {
            Some(path.toPath)
          } else {
            throw new IllegalStateException(
              s"Your riddlcPath setting is not the full path to the riddlc program: $path"
            )
          }
        case None =>
          Option(System.getenv("RIDDLC_PATH")) match {
            case None => findRiddlcPathInPATH
            case Some(path) =>
              if (path.endsWith(riddlc_partial_path)) {
                Some(Path.of(path))
              } else {
                findRiddlcPathInPATH
              }
          }
      }
      found match {
        case Some(path) =>
          if (!Files.isExecutable(path)) {
            sLog.value.log(Level.Warn, s"The RIDDLC found at $path is not executable")
          }
          Some(path.toFile)
        case None =>
          sLog.value.log(Level.Error, s"Could not find riddlc in RIDDLC_PATH(${sys.env("RIDDLC_PATH")}) or PATH ")
          Option.empty[File]
      }
    },
    commands ++= Seq(riddlcCommand, infoCommand, hugoCommand, validateCommand, statsCommand)
  )

  private def runRiddlcAction(state: State, args: Seq[String]): State = {
    val project = Project.extract(state)
    val riddlcPath = project.runTask(findRiddlcTask, state)
    val minimumVersion: String = project.get(riddlcMinVersion)
    val options: Seq[String] = project.get(riddlcOptions)
    Def.task[Int] {
      val streams: TaskStreams = project.get(Keys.streams).value
      runRiddlc(streams, riddlcPath._2, minimumVersion, options, args)
    }
    state
  }

  // Allow riddlc to be run from inside an sbt shell
  private def riddlcCommand: Command = {
    Command.args(
      name = "riddlc",
      display = "<options> <command> <args...> ; `riddlc help` for details"
    ) { (state: State, args: Seq[String]) =>
      runRiddlcAction(state, args)
    }
  }

  private def infoCommand: Command = {
    Command.command("info") { (state: State) =>
      runRiddlcAction(state, Seq("info"))
    }
  }

  private def hugoCommand: Command = {
    Command.command("hugo") { (state: State) =>
      val project = Project.extract(state)
      val conf = project.get(riddlcConf).getAbsoluteFile.toString
      runRiddlcAction(state, Seq("from", conf, "hugo"))
    }
  }

  private def validateCommand: Command = {
    Command.command("validate") { (state: State) =>
      val project = Project.extract(state)
      val conf = project.get(riddlcConf).getAbsoluteFile.toString
      runRiddlcAction(state, Seq("from", conf, "validate"))
    }
  }

  private def statsCommand: Command = {
    Command.command("stats") { (state: State) =>
      val project = Project.extract(state)
      val conf = project.get(riddlcConf).getAbsoluteFile.toString
      runRiddlcAction(state, Seq("from", conf, "stats"))
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
    streams: TaskStreams,
    riddlcPath: Option[File],
    minimumVersion: String,
    options: Seq[String],
    args: Seq[String]
  ): Int = {
    riddlcPath match {
      case None =>
        streams.log.info("riddlc not found")
        -1
      case Some(riddlc_path) =>
        checkVersion(riddlc_path, minimumVersion)
        streams.log.info(s"Running: riddlc ${args.mkString(" ")}\n")
        val logger = ProcessLogger { str =>
          println(str); streams.log(str); ()
        }
        val process = Process(riddlc_path.getAbsolutePath, options ++ args)
        process.!(logger)
    }
  }
}
