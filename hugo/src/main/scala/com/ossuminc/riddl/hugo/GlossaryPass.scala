/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.hugo
import com.ossuminc.riddl.hugo.themes.ThemeGenerator
import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.passes.symbols.Symbols
import com.ossuminc.riddl.passes.{CollectingPass, CollectingPassOutput, PassInput, PassesOutput, PassesResult}
import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.utils.PlatformContext

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
)(using PlatformContext) extends CollectingPass[GlossaryEntry](input, outputs) {

  private val generator = ThemeGenerator(options, input, outputs, messages)

  // Members declared in com.ossuminc.riddl.passes.CollectingPass
  protected def collect(
    definition: RiddlValue,
    parents: ParentStack
  ): Seq[GlossaryEntry] = {
    definition match {
      case ad: Definition if ad.isAnonymous => Seq.empty[GlossaryEntry]
      // Implicit definitions don't have a name so there's no word to define in the glossary
      case d: Definition =>
        // everything else does have a name
        val parentsSeq = parents.toParents
        Seq(makeGlossaryEntry(d, parentsSeq))
      case _: RiddlValue =>
        // None of these kinds of definitions contribute to the glossary
        Seq.empty[GlossaryEntry]
    }
  }

  private def makeGlossaryEntry(
    d: Definition,
    stack: Parents
  ): GlossaryEntry = {
    val parents = generator.makeStringParents(stack)
    val brief: Option[String] =
      d match
        case dwb: WithMetaData =>
          val content = dwb.briefString
          if content.isEmpty then None else Some(content)
        case _ => None
    val entry = GlossaryEntry(
      d.id.value,
      d.kind,
      brief.getOrElse("-- undefined --"),
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
