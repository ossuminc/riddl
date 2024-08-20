/*
 * Copyright 2023 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.hugo
import com.ossuminc.riddl.hugo.themes.ThemeGenerator
import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.passes.symbols.Symbols
import com.ossuminc.riddl.passes.{CollectingPass, CollectingPassOutput, PassInput, PassesOutput, PassesResult}

import java.nio.file.Path
import scala.collection.mutable

case class GlossaryEntry(
  term: String,
  kind: String,
  brief: String,
  path: Seq[String],
  link: String = "",
  sourceLink: String = ""
)

case class GlossaryOutput(
  root: Root,
  messages: Messages.Messages,
  entries: Seq[GlossaryEntry]
) extends CollectingPassOutput[GlossaryEntry]

case class GlossaryPass(
  input: PassInput,
  outputs: PassesOutput,
  options: HugoPass.Options
) extends CollectingPass[GlossaryEntry](input, outputs) {

  private val generator = ThemeGenerator(options, input, outputs, messages)
  
  // Members declared in com.ossuminc.riddl.passes.CollectingPass
  protected def collect(
    definition: RiddlValue,
    parents: ParentStack
  ): Seq[GlossaryEntry] = {
    definition match {
      case ad: Definition if ad.isAnonymous => Seq.empty[GlossaryEntry]
      // Implicit definitions don't have a name so there's no word to define in the glossary
      case d: LeafDefinition =>
        // everything else does
        val parentsSeq = parents.toParentsSeq
        Seq(makeGlossaryEntry(d, parentsSeq))
      case _: RiddlValue =>
        // None of these kinds of definitions contribute to the glossary
        Seq.empty[GlossaryEntry]
    }
  }

  private def makeGlossaryEntry(
    d: LeafDefinition,
    stack: Parents
  ): GlossaryEntry = {
    val parents = generator.makeStringParents(stack)
    val entry = GlossaryEntry(
      d.id.value,
      d.kind,
      d.brief.map(_.brief.s).getOrElse("-- undefined --"),
      parents :+ d.id.value,
      generator.makeDocLink(d, parents),
      generator.makeSourceLink(d)
    )
    entry
  }

  def result(root: Root): GlossaryOutput = {
    GlossaryOutput(root, messages.toMessages, collectedValues.toSeq)
  }

  // Members declared in com.ossuminc.riddl.passes.Pass
  def name: String = GlossaryPass.name
  override def postProcess(root: Root): Unit = ()
}

object GlossaryPass {
  val name: String = "Glossary"
}
