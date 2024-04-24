/*
 * Copyright 2023 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.ossuminc.riddl.analyses

import com.ossuminc.riddl.language.{AST, Messages}
import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.passes.*
import com.ossuminc.riddl.passes.resolve.ResolutionPass
import com.ossuminc.riddl.passes.symbols.SymbolsPass

import scala.collection.mutable

object StatsPass extends PassInfo[PassOptions] {
  val name: String = "stats"
  def creator(options: PassOptions = PassOptions.empty): PassCreator = { (in: PassInput, out: PassesOutput) =>
    StatsPass(in, out)
  }
}

/** @param isEmpty
  *   An indication if the definition is completely empty
  * @param descriptionLines
  *   The number of lines of documentation between the description and brief fields
  * @param numSpecifications
  *   The number of kinds of specifications that can be completed from the node's specifications method
  * @param numCompleted
  *   The number of specifications that hve been completed
  * @param numContained
  *   The number of contained definitions
  * @param numAuthors
  *   The number of defining authors
  * @param numTerms
  *   The number of term definitions
  * @param numOptions
  *   The number of options declared
  * @param numIncludes
  *   The number of include statements
  * @param numStatements
  *   The number of statements used
  */
case class DefinitionStats(
  kind: String = "",
  isEmpty: Boolean = true, // if Definition.isEmpty returns true
  descriptionLines: Int = 0, // number of lines of doc in briefly and description
  numSpecifications: Int = 0, // how many kinds of specifications there are
  numCompleted: Long = 0, // the actual number of specfications completed
  numContained: Long = 0, // the number of contained definitions
  numAuthors: Long = 0, // the number of defining authors
  numTerms: Long = 0, // the number of term definitions
  numOptions: Long = 0, // number of options declared
  numIncludes: Long = 0,
  numStatements: Long = 0
)

case class KindStats(
  var count: Long = 0,
  var numEmpty: Long = 0,
  var descriptionLines: Long = 0,
  var numSpecifications: Long = 0,
  var numCompleted: Long = 0,
  var numContained: Long = 0,
  var numAuthors: Long = 0, // the number of defining authors
  var numTerms: Long = 0, // the number of term definitions
  var numOptions: Long = 0,
  var numIncludes: Long = 0,
  var numStatements: Long = 0
) {
  def completeness: Double = (numCompleted.toDouble / numSpecifications) * 100.0d
  def complexity: Double =
    ((numCompleted + numContained + numTerms + descriptionLines + numAuthors + numTerms + numOptions + numIncludes) /
      count.toDouble) * 1000.0d
  def averageContainment: Double = numContained.toDouble / count
  def percent_of_all(total_count: Long): Double = (count.toDouble / total_count) * 100d
  def percent_documented: Double = Math.min(descriptionLines / 5d, 100d)
}

case class StatsOutput(
  messages: Messages = Messages.empty,
  maximum_depth: Int = 0,
  categories: Map[String, KindStats] = Map.empty
) extends CollectingPassOutput[DefinitionStats]

/** Unit Tests For StatsPass */
case class StatsPass(input: PassInput, outputs: PassesOutput) extends CollectingPass[DefinitionStats](input, outputs) {

  def name: String = StatsPass.name

  requires(SymbolsPass)
  requires(ResolutionPass)

  private var maximum_depth: Int = 0
  private val kind_stats: mutable.HashMap[String, KindStats] = mutable.HashMap.empty
  private var total_stats: Option[KindStats] = None

  private def computeNumStatements(definition: Definition): Long = {
    def handlerStatements(handlers: Seq[Handler]): Long = {
      val sizes: Seq[Long] = for {
        handler <- handlers
        clause <- handler.clauses
      } yield {
        clause.statements.size.toLong
      }
      sizes.foldLeft(0L)((a, b) => a + b)
    }

    definition.contents.vitals
      .map { (vd: Definition) =>
        vd match {
          case a: Adaptor     => handlerStatements(a.handlers)
          case a: Application => handlerStatements(a.handlers)
          case c: Context     => handlerStatements(c.handlers)
          case e: Entity      => handlerStatements(e.handlers)
          case p: Projector   => handlerStatements(p.handlers)
          case r: Repository  => handlerStatements(r.handlers)
          case s: Streamlet   => handlerStatements(s.handlers)
          case s: Saga =>
            s.sagaSteps.map(step => step.doStatements.size.toLong + step.undoStatements.size).sum[Long]
          case f: Function   => f.statements.size.toLong
          case _: Epic       => 0L
          case _: Domain     => 0L
          case _: Definition => 0L // Non Vital, ignore
        }
      }
      .sum[Long]
  }

  protected def collect(definition: RiddlValue, parents: mutable.Stack[AST.Definition]): Seq[DefinitionStats] = {
    if parents.size >= maximum_depth then maximum_depth = parents.size + 1

    val (options: Int, authors: Int, terms: Int, includes: Int) = definition match {
      case vd: VitalDefinition[?] =>
        (vd.options.size, vd.authorRefs.size, vd.terms.size, vd.includes.size)
      case _ => (0, 0, 0, 0)
    }
    val specs: Int = specificationsFor(definition)
    val completes: Int = completedCount(definition)
    definition match {
      case definition: Definition =>
        Seq(
          DefinitionStats(
            kind = definition.kind,
            isEmpty = definition.isEmpty,
            descriptionLines = {
              definition.description.getOrElse(Description.empty).lines.size + {
                if definition.brief.nonEmpty then 1 else 0
              }
            },
            numSpecifications = specs,
            numCompleted = completes,
            numContained = definition.contents.size,
            numOptions = options,
            numAuthors = authors,
            numTerms = terms,
            numIncludes = includes,
            numStatements = computeNumStatements(definition)
          )
        )
      case value: RiddlValue => Seq.empty[DefinitionStats]
    }
  }

  def postProcess(root: Root): Unit = {
    for { defStats <- collectedValues } {
      def remapping(existing: Option[KindStats]): Option[KindStats] = {
        Some(
          existing.fold(
            KindStats(
              count = 1L,
              numEmpty = { if defStats.isEmpty then 1L else 0L },
              descriptionLines = defStats.descriptionLines,
              numSpecifications = defStats.numSpecifications,
              numCompleted = defStats.numCompleted,
              numContained = defStats.numCompleted,
              numAuthors = defStats.numAuthors,
              numTerms = defStats.numTerms,
              numOptions = defStats.numOptions,
              numIncludes = defStats.numIncludes,
              numStatements = defStats.numStatements
            )
          ) { (ks: KindStats) =>
            ks.count += 1
            ks.numEmpty += { if defStats.isEmpty then 1 else 0 }
            ks.numSpecifications += defStats.numSpecifications
            ks.numCompleted += defStats.numCompleted
            ks.numContained += defStats.numContained
            ks.numAuthors += defStats.numAuthors
            ks.numTerms += defStats.numTerms
            ks.numOptions += defStats.numOptions
            ks.numIncludes += defStats.numIncludes
            ks.numStatements += defStats.numStatements
            ks
          }
        )
      }
      kind_stats.updateWith(defStats.kind)(remapping)
    }
    val totals = kind_stats.values.foldLeft(KindStats()) { (total: KindStats, next: KindStats) =>
      total.count += 1
      total.descriptionLines += next.descriptionLines
      total.numSpecifications += next.numSpecifications
      total.numCompleted += next.numCompleted
      total.numContained += next.numContained
      total.numIncludes += next.numIncludes
      total.numTerms += next.numTerms
      total.numEmpty += next.numEmpty
      total.numAuthors += next.numAuthors
      total.numStatements += next.numStatements
      total
    }
    total_stats = Some(totals)
  }

  /** Generate the output of this Pass. This will only be called after all the calls to process have completed.
    *
    * @return
    *   an instance of the output type
    */
  override def result: StatsOutput = {
    val totals = total_stats.getOrElse(KindStats())
    StatsOutput(
      Messages.empty,
      maximum_depth,
      categories = (kind_stats.toSeq :+ ("All" -> totals)).toMap[String, KindStats]
    )
  }

  /** Calculates the number of types of specifications that can be added to the vital definition provided.
    * @param v
    *   The vital definition for whom specifications number is computed
    * @return
    *   The number of types of specifications for v
    */
  private def specificationsFor(v: RiddlValue): Int = {
    val specsForDefinition: Int = 0
      + 1 // Brief Description
      + 1 // Description
      + 1 // RiddlOptions
      + 1 // Authors
      + 1 // Terms

    v match {
      case p: Processor[?] =>
        val specsForProcessor = specsForDefinition
          + 1 // Types
          + 1 // Constants
          + 1 // Functions
          + 1 // Handlers
          + 1 // Inlets
          + 1 // Outlets
        p match {
          case _: Application =>
            specsForProcessor
              + 1 // Groups
          case _: Adaptor =>
            specsForProcessor
              + 1 // direction
              + 1 // contextRef
          case _: Context =>
            specsForProcessor
              + 1 // entities
              + 1 // adaptors
              + 1 // sagas
              + 1 // streamlets
              + 1 // projectors
              + 1 // repositories
              + 1 // connections
              + 1 // replicas
          case _: Entity =>
            specsForProcessor
              + 1 // states
              + 1 // invariants
          case _: Projector =>
            specsForProcessor
              + 1 // invariants
          case _: Repository => specsForProcessor
          case _: Streamlet =>
            specsForProcessor
              + 1 // shape
        }
      case _: Domain =>
        specsForDefinition
          + 1 // authorDefinitions
          + 1 // contexts
          + 1 // users
          + 1 // epics
          + 1 // sagas
          + 1 // applications
          + 0 // recursive domains not included
      case _: Epic =>
        specsForDefinition
          + 1 // userStory
          + 1 // shownBy
          + 1 // cases
      case _: Function =>
        specsForDefinition
          + 1 // input
          + 1 // output
          + 1 // statements
          + 0 // recursive functions not included
      case _: Saga =>
        specsForDefinition
          + 1 // input
          + 1 // output
          + 1 // steps
      case _: RiddlValue => // shouldn't happen
        specsForDefinition
    }
  }

  private def definitionCount(d: RiddlValue): Int =
    var result = 0
    if d.hasTypes then result += 1
    if d.hasAuthors then result += 1
    if d.hasOptions then result += 1
    if d.hasDescription then result += 1
    if d.hasBriefDescription then result += 1
    result

  private def processorCount(p: Processor[?]): Int =
    var result: Int = definitionCount(p)
    if p.handlers.nonEmpty then result += 1
    if p.functions.nonEmpty then result += 1
    if p.constants.nonEmpty then result += 1
    if p.inlets.nonEmpty then result += 1
    if p.outlets.nonEmpty then result += 1
    result

  private def completedCount(v: RiddlValue): Int = {
    v match {
      case p: Processor[?] =>
        val countForProcessor = processorCount(p)
        p match {
          case a: Application =>
            countForProcessor
              + { if a.groups.nonEmpty then 1 else 0 }
          case a: Adaptor =>
            countForProcessor
              + 1 // direction (required)
              + 1 // contextRef (required)
          case c: Context =>
            countForProcessor
              + { if c.entities.nonEmpty then 1 else 0 }
              + { if c.adaptors.nonEmpty then 1 else 0 }
              + { if c.sagas.nonEmpty then 1 else 0 }
              + { if c.streamlets.nonEmpty then 1 else 0 }
              + { if c.projectors.nonEmpty then 1 else 0 }
              + { if c.repositories.nonEmpty then 1 else 0 }
              + { if c.connectors.nonEmpty then 1 else 0 }
          case e: Entity =>
            countForProcessor
              + { if e.states.nonEmpty then 1 else 0 }
              + { if e.invariants.nonEmpty then 1 else 0 }
          case p: Projector =>
            countForProcessor
              + { if p.invariants.nonEmpty then 1 else 0 }
          case _: Repository => countForProcessor
          case s: Streamlet =>
            countForProcessor
              + 1 // shape required
        }
      case d: Domain =>
        definitionCount(d)
          + { if d.authors.nonEmpty then 1 else 0 }
          + { if d.contexts.nonEmpty then 1 else 0 }
          + { if d.users.nonEmpty then 1 else 0 }
          + { if d.epics.nonEmpty then 1 else 0 }
          + { if d.sagas.nonEmpty then 1 else 0 }
          + { if d.applications.nonEmpty then 1 else 0 }
          + 0 // recursive domains not included
      case e: Epic =>
        definitionCount(e)
          + { if e.userStory.nonEmpty then 1 else 0 }
          + { if e.shownBy.nonEmpty then 1 else 0 }
          + { if e.cases.nonEmpty then 1 else 0 }
      case f: Function =>
        definitionCount(f)
          + { if f.input.nonEmpty then 1 else 0 }
          + { if f.output.nonEmpty then 1 else 0 }
          + { if f.statements.nonEmpty then 1 else 0 }
          + 0 // recursive functions not included
      case s: Saga =>
        definitionCount(s)
          + { if s.input.nonEmpty then 1 else 0 }
          + { if s.output.nonEmpty then 1 else 0 }
          + { if s.sagaSteps.nonEmpty then 1 else 0 }
      case x: RiddlValue => // shouldn't happen
        definitionCount(x)
    }
  }
}
