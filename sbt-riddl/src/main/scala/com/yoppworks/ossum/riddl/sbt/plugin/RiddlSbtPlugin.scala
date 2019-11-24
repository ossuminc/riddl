package com.yoppworks.ossum.riddl.sbt.plugin

import java.nio.file.Path

import sbt._
import Keys._
import com.yoppworks.ossum.riddl.language.Riddl
import com.yoppworks.ossum.riddl.language.TopLevelParser
import com.yoppworks.ossum.riddl.language.Validation
import com.yoppworks.ossum.riddl.translator.ParadoxTranslator

import sbt.plugins.JvmPlugin
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

    val riddl2Paradox = taskKey[Seq[File]](
      "Task to translate riddl source to Paradox source"
    )

  }

  import autoImport._

  override lazy val projectSettings: Seq[Setting[_]] = Seq(
    (compile in Compile) :=
      ((compile in Compile) dependsOn riddl2Paradox).value
  )

  case class SbtLogger(log: ManagedLogger) extends Riddl.Logger {
    override def severe(s: => String): Unit = log.error(s)
    override def error(s: => String): Unit = log.error(s)
    override def warn(s: => String): Unit = log.warn(s)
    override def info(s: => String): Unit = log.info(s)
  }

  def paradoxTranslation(
    sources: Seq[File],
    targetDir: Path,
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
      val fileList =
        trans.run(source.toPath, Some(targetDir), log, Some(configFile.toPath))
      fileList
    }
  }

  lazy val riddl2Paradox = Def.task[Seq[File]] {
    val log = SbtLogger(streams.value.log)
    val srcDir = (sourceDirectory in Compile).value / "riddl"
    val sourceFiles: Seq[File] = riddl2ParadoxSourceFiles.value.map { name =>
      srcDir / name
    }
    val targetDir = target.value.toPath
    val configFile: File = riddl2ParadoxConfigFile.value
    paradoxTranslation(sourceFiles, targetDir, configFile, log)
  }
}
