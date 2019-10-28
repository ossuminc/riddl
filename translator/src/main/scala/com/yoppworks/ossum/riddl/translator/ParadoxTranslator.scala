package com.yoppworks.ossum.riddl.translator

import java.io.File

import com.yoppworks.ossum.riddl.language.AST._
import com.yoppworks.ossum.riddl.language.Folding
import pureconfig.ConfigSource
import pureconfig.generic.auto._

/** A Translator that generates Paradox documentation */
object ParadoxTranslator extends Translator {

  case class ParadoxConfig() extends Configuration

  case class ParadoxState(
    config: ParadoxConfig,
    generatedFiles: Seq[File] = Seq.empty[File]
  ) extends Folding.State[ParadoxState]
      with State {
    def step(f: ParadoxState => ParadoxState): ParadoxState = f(this)

    def addFile(f: File): ParadoxState = {
      this.copy(generatedFiles = this.generatedFiles :+ f)
    }
  }

  def translate(root: RootContainer, confFile: File): Seq[File] = {
    val readResult = ConfigSource.file(confFile).load[ParadoxConfig]
    handleConfigLoad[ParadoxConfig](readResult) match {
      case Some(c) => translate(root, c)
      case None    => Seq.empty[File]
    }
  }

  def translate[C <: ParadoxConfig](
    root: RootContainer,
    config: C
  ): Seq[File] = {
    val state: ParadoxState = ParadoxState(config, Seq.empty[File])
    val dispatcher = ParadoxFolding(root, root, state)
    dispatcher.foldLeft(root, root, state).generatedFiles
  }

  case class ParadoxFolding(
    container: Container,
    definition: Definition,
    state: ParadoxState
  ) extends Folding.Folding[ParadoxState] {}
}
