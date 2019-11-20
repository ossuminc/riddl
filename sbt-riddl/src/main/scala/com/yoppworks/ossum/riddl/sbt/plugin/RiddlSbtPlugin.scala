package com.yoppworks.ossum.riddl.sbt.plugin

import sbt._
import Keys._
import com.yoppworks.ossum.riddl.language.Riddl
import com.yoppworks.ossum.riddl.language.TopLevelParser
import com.yoppworks.ossum.riddl.language.Validation
import sbt.plugins.JvmPlugin
import com.yoppworks.ossum.riddl.translator.ParadoxTranslator
import sbt.internal.util.ManagedLogger

/** A plugin that endows sbt with knowledge of code generation via riddl */
object RiddlSbtPlugin extends AutoPlugin {
  override def requires: AutoPlugin = JvmPlugin

  object autoImport {

    val riddl2ParadoxSourceFiles = settingKey[Seq[String]](
      "names of the .riddl files to translate"
    )

    val riddl2ParadoxConfigFile = settingKey[File](
      "Path location of the Paradox translator's config file"
    )
  }

  import autoImport._

  override lazy val projectSettings: Seq[Setting[_]] = Seq(
    sourceGenerators in Compile += paradoxTask.taskValue
  )

  case class SbtLogger(log: ManagedLogger) extends Riddl.Logger {
    override def severe(s: => String): Unit = log.error(s)
    override def error(s: => String): Unit = log.error(s)
    override def warn(s: => String): Unit = log.warn(s)
    override def info(s: => String): Unit = log.info(s)
  }

  def paradoxTranslation(
    sources: Seq[File],
    target: File,
    configFile: File,
    log: SbtLogger
  ): Seq[File] = {
    sources.flatMap { source =>
      log.info(s"Translating RIDDL to Paradox for ${source.getName}")
      TopLevelParser.parse(source) match {
        case Left(errors) =>
        case Right(root) =>
          val msgs = Validation.validate(root)
      }
      val trans = new ParadoxTranslator
      trans.run(source.toPath, log, Some(configFile.toPath))
    }
  }

  private def paradoxTask: Def.Initialize[Task[Seq[File]]] = Def.task {
    val log = SbtLogger(streams.value.log)
    val srcDir = (sourceDirectory in Compile).value / "riddl"
    val sourceFiles: Seq[File] = riddl2ParadoxSourceFiles.value.map { name =>
      srcDir / name
    }
    val targetDir: File = (sourceManaged in Compile).value / "paradox"
    val configFile: File = riddl2ParadoxConfigFile.value
    paradoxTranslation(sourceFiles, targetDir, configFile, log)
  }

}
