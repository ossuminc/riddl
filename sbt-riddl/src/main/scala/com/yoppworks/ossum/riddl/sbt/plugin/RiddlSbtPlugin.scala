package com.yoppworks.ossum.riddl.sbt.plugin

import sbt._
import Keys._
import com.yoppworks.ossum.riddl.language.AST.RootContainer
import com.yoppworks.ossum.riddl.language.RiddlParserInput
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

  def paradoxTranslation(
    sources: Seq[File],
    target: File,
    configFile: File,
    log: ManagedLogger
  ): Seq[File] = {
    sources.flatMap { source =>
      log.info(s"Translating RIDDL ${source.getName}")
      val input = RiddlParserInput(source)
      ParadoxTranslator.parseValidateTranslate(input, log.err, configFile)
    }
  }

  private def paradoxTask: Def.Initialize[Task[Seq[File]]] = Def.task {
    val log: ManagedLogger = streams.value.log
    val srcDir = (sourceDirectory in Compile).value / "main" / "riddl"
    val sourceFiles: Seq[File] = riddl2ParadoxSourceFiles.value.map { name =>
      srcDir / name
    }
    val targetDir: File = (sourceManaged in Compile).value / "main" / "paradox"
    val configFile: File = riddl2ParadoxConfigFile.value
    paradoxTranslation(sourceFiles, targetDir, configFile, log)
  }

}
