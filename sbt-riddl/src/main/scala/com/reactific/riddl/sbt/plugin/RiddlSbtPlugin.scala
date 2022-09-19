/*
 * Copyright 2019 Reactific Software LLC
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

package com.reactific.riddl.sbt.plugin

import com.reactific.riddl.sbt.SbtRiddlPluginBuildInfo
import sbt.*
import sbt.Keys.*
import sbt.plugins.JvmPlugin

import scala.language.postfixOps
import scala.sys.process._

/** A plugin that endows sbt with knowledge of code generation via riddl */
object RiddlSbtPlugin extends AutoPlugin {
  override def requires: AutoPlugin = JvmPlugin

  object autoImport {

    lazy val riddlcPath = settingKey[File]("Path to `riddlc` compiler")

    lazy val riddlcOptions = {
      settingKey[Seq[String]]("Options for the riddlc compiler")
    }

    lazy val riddlcMinVersion = {
      settingKey[String]("Ensure the riddlc used is at least this version")
    }
  }

  import autoImport.*

  lazy val compileTask = taskKey[Unit]("A task to invoke riddlc compiler")

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

  override lazy val projectSettings: Seq[Setting[_]] = Seq(
    riddlcPath := file("riddlc"),
    riddlcOptions := Seq("from", "src/main/riddl/riddlc.conf"),
    riddlcMinVersion := SbtRiddlPluginBuildInfo.version,
    compileTask := {
      val execPath = riddlcPath.value
      val options = riddlcOptions.value
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

  def versionTriple(version: String): (Int, Int, Int) = {
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

  def versionSameOrLater(actualVersion: String, minVersion: String): Boolean = {
    if (actualVersion != minVersion) {
      val (aJ, aN, aP) = versionTriple(actualVersion)
      val (mJ, mN, mP) = versionTriple(minVersion)
      aJ > mJ || ((aJ == mJ) && ((aN > mN) || ((aN == mN) && (aP >= mP))))
    } else { true }
  }

  def checkVersion(
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
    }
  }

  def runRiddlc(
    riddlc: sbt.File,
    options: Seq[String],
    minimumVersion: String
  ): Unit = {
    checkVersion(riddlc, minimumVersion)
    val command = riddlc.toString + " " + options.mkString(" ")
    val logger = ProcessLogger(println(_))
    command.!(logger)
  }
}
