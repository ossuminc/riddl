package com.yoppworks.ossum.riddl.translator

import java.io.File

import com.yoppworks.ossum.riddl.language.AST._

trait Translator {
  def translate(root: RootContainer, configFile: File): Seq[File]
}

object Translator {}
