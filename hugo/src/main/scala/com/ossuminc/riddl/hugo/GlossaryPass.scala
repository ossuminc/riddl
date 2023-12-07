package com.ossuminc.riddl.hugo
import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Messages
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
  messages: Messages.Messages,
  entries: Seq[GlossaryEntry]
) extends CollectingPassOutput[GlossaryEntry]

case class GlossaryPass(
  input: PassInput,
  outputs: PassesOutput,
  options: HugoCommand.Options
) extends CollectingPass[GlossaryEntry](input, outputs)
    with PassUtilities {

  // Members declared in com.ossuminc.riddl.passes.CollectingPass
  protected def collect(
    definition: Definition,
    parents: mutable.Stack[Definition]
  ): Option[GlossaryEntry] = {
    definition match {
      case _: OnMessageClause | _: OnOtherClause | _: OnInitClause | _: OnTerminationClause | _: RootContainer |
          _: Include[Definition] @unchecked =>
        // None of these kinds of definitions contribute to the glossary
        None
      case d: Definition =>
        // everything else does
        val stack = parents.toSeq
        Some(makeGlossaryEntry(definition, stack))
    }
  }

  def makeGlossaryEntry(
    d: Definition,
    stack: Seq[Definition]
  ): GlossaryEntry = {
    val parents = makeStringParents(stack)
    val entry = GlossaryEntry(
      d.id.value,
      d.kind,
      d.brief.map(_.s).getOrElse("-- undefined --"),
      parents :+ d.id.value,
      makeDocLink(d, parents),
      makeSourceLink(d)
    )
    entry
  }

  def result: GlossaryOutput = {
    GlossaryOutput(messages.toMessages, collectedValues)
  }

  // Members declared in com.ossuminc.riddl.passes.Pass
  def name: String = GlossaryPass.name
  def postProcess(root: com.ossuminc.riddl.language.AST.RootContainer): Unit = ()
}

object GlossaryPass {
  val name: String = "Glossary"
}
