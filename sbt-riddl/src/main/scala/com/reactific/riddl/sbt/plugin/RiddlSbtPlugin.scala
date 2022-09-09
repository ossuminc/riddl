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

import sbt.*
import sbt.Keys.*
import sbt.internal.util.ManagedLogger
import sbt.plugins.JvmPlugin

import scala.language.postfixOps

/** A plugin that endows sbt with knowledge of code generation via riddl */
object RiddlSbtPlugin extends AutoPlugin {
  override def requires: AutoPlugin = JvmPlugin

  object autoImport {

    lazy val riddlcPath = settingKey[File]("Path to `riddlc` compiler")

    lazy val riddlcOptions = {
      settingKey[Seq[String]]("Options for the riddlc compiler")
    }
  }

  import autoImport.*

  lazy val compileTask = taskKey[Unit]("A task to invoke riddlc compiler")

  override lazy val projectSettings: Seq[Setting[_]] = Seq(
    riddlcPath := file("riddlc"),
    riddlcOptions := Seq("from", "src/main/riddl/riddlc.conf"),
    compileTask := {
      val execPath = riddlcPath.value
      val options = riddlcOptions.value
      val log: ManagedLogger = streams.value.log
      translateToDoc(execPath, options, log)
    }
  )

  def translateToDoc(
    riddlc: sbt.File,
    options: Seq[String],
    log: ManagedLogger
  ): Unit = {
    import scala.sys.process.*
    val command = riddlc.toString + " " + options.mkString(" ")
    val logger = ProcessLogger(println(_))
    command.lineStream(logger)
  }
}
