package com.reactific.riddl.stats

import com.reactific.riddl.language.{AST, Messages}
import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.Messages.Messages
import com.reactific.riddl.passes.{Pass, PassInfo, PassInput, PassOutput}
import com.reactific.riddl.passes.resolve.ResolutionPass
import com.reactific.riddl.passes.symbols.SymbolsPass

import scala.collection.mutable

object StatsPass extends PassInfo {
  val name: String = "stats"
}

case class CategoryStats(
  count: Long = 0,
  percentOfDefinitions: Double = 0.0d,
  averageCompleteness: Double = 0.0d,
  averageComplexity: Double = 0.0d,
  averageMaturity: Double = 0.0d,
  totalMaturity: Long = 0,
  percentComplete: Double = 0.0d,
  percentDocumented: Double = 0.0d) {
  override def toString: String = {
    f"count:$count%s($percentOfDefinitions%.2f%%), maturity:$totalMaturity%d($averageMaturity%.2f%%)," +
      f"complete: $percentComplete%.2f%%, documented: $percentDocumented%.2f%%"
  }
}

/**
 *
 * @param specifications
 * The number of kinds of specifications that can be completed from the node's specifications method
 * @param completed
 */
case class DefinitionStats(
  numCompletions: Long = 0;
  completion: Double = 0.0d
  contained: Long = 0, // the number of contained definitions
)

case class StatsOutput(
  messages: Messages = Messages.empty,
  count: Long = 0,
  term_count: Long = 0,
  maximum_depth: Int = 0,
  categories: Map[String, CategoryStats] = Map.empty
) extends FoldingPassOutput[]

/** Unit Tests For StatsPass */
case class StatsPass(input: PassInput) extends FoldingPass(input) {

  def name: String = StatsPass.name

  requires(SymbolsPass)
  requires(ResolutionPass)

  case class KindStats(
    var count: Long = 0,
    var maturitySum: Long = 0,
    var completed: Long = 0,
    var documented: Long = 0,
  )

  var term_count: Long = 0
  var maximum_depth: Int = 0

  var all_stats: KindStats = KindStats()
  var adaptorStats: KindStats = KindStats()
  var applicationStats: KindStats = KindStats()
  var contextStats: KindStats = KindStats()
  var domainStats: KindStats = KindStats()
  var entityStats: KindStats = KindStats()
  var functionStats: KindStats = KindStats()
  var handlerStats: KindStats = KindStats()
  var plantStats: KindStats = KindStats()
  var streamletStats: KindStats = KindStats()
  var projectionStats: KindStats = KindStats()
  var repositoryStats: KindStats = KindStats()
  var sagaStats: KindStats = KindStats()
  var storyStats: KindStats = KindStats()
  var other_stats: KindStats = KindStats()

  var categories: Map[String, CategoryStats] = Map.empty


  override protected def process(definition: AST.Definition, parents: mutable.Stack[AST.Definition]): Unit = {
    all_stats.count += 1
    if parents.size >= maximum_depth then maximum_depth = parents.size + 1
    if !definition.isEmpty then all_stats.completed += 1
    if definition.brief.nonEmpty && definition.description.nonEmpty then all_stats.documented += 1

    definition match {
      case vd: VitalDefinition[?, ?] =>
        all_stats.maturitySum += vd.maturity
        vd match {
          case p: Processor[_,_] => p match {
            case a: Adaptor => makeVitalStats(a, adaptorStats)
            case a: Application => makeVitalStats(a, applicationStats)
            case c: Context => makeVitalStats(c, contextStats)
            case e: Entity => makeVitalStats(e, entityStats)
            case p: Streamlet => makeVitalStats(p, streamletStats)
            case p: Projector => makeVitalStats(p, projectionStats)
            case r: Repository => makeVitalStats(r, repositoryStats)
          }
          case e: Epic => makeVitalStats(e, storyStats)
          case f: Function => makeVitalStats(f, functionStats)
          case d: Domain => makeVitalStats(d, domainStats)
          case s: Saga => makeVitalStats(s, sagaStats)
        }
      case t: Term =>
        term_count += 1
        other_stats.count += 1
        if t.nonEmpty then other_stats.completed += 1
      case d: Definition =>
        other_stats.count += 1
        if d.nonEmpty then other_stats.completed += 1
    }
  }

  /**
   * Calculates the number of types of specifications that can be added to
   * the vital definition provided.
   * @param v
   *   The vital definition for whom specifications number is computed
   * @return
   *   The number of types of specifications for v
   */
  private def specificationsFor(v: VitalDefinition[?, ?]): Int = {
    val specsForDefinition: Int = 0
      + 1 // Brief Description
      + 1 // Description
      + 1 // Options
      + 1 // Authors
      + 1 // Terms

    v match {
      case p: Processor[?,?] =>
        val specsForProcessor = specsForDefinition
          + 1 // Types
          + 1 // Constants
          + 1 // Functions
          + 1 // Handlers
          + 1 // Inlets
          + 1 // Outlets
        p match {
          case _: Application => specsForProcessor
            + 1  // Groups
          case _: Adaptor => specsForProcessor
            + 1 // direction
            + 1 // contextRef
          case _: Context => specsForProcessor
            + 1 // entities
            + 1 // adaptors
            + 1 // sagas
            + 1 // streamlets
            + 1 // projectors
            + 1 // repositories
            + 1 // connections
            + 1 // replicas
          case _: Entity => specsForProcessor
            + 1 // states
            + 1 // invariants
          case _: Projector => specsForProcessor
            + 1 // invariants
          case _: Repository => specsForProcessor
          case _: Streamlet => specsForProcessor
             +1 // shape
        }
      case _: Domain      => specsForDefinition
        + 1 // authorDefinitions
        + 1 // contexts
        + 1 // users
        + 1 // epics
        + 1 // sagas
        + 1 // applications
        + 0 // recursive domains not included
      case _: Epic        => specsForDefinition
        + 1 // userStory
        + 1 // shownBy
        + 1 // cases
      case _: Function    => specsForDefinition
        + 1 // input
        + 1 // output
        + 1 // statements
        + 0 // recursive functions not included
      case _: Saga        => specsForDefinition
        + 1 // input
        + 1 // output
        + 1 // steps
    }

  }

  private def definitionCount(d: Definition): Int =
    d.hasTypes.toInt + d.hasAuthors.toInt + d.hasOptions.toInt +
      d.hasDescription.toInt + d.hasBriefDescription.toInt


  private def processorCount(p: Processor[?, ?]): Int =
    definitionCount(p) + handlers.nonEmpty + functions.nonEmpty +
      constants.nonEmpty + inlets.nonEmpty + outlets.nonEmpty

  private def completenessOf(v: VitalDefinition[?, ?]): Double = {
    val divisor = specificationsFor(v)
    val numerator = v match {
      case p: Processor[?, ?] =>
        val countForProcessor = processorCount(p)
          + p.types.nonEmpty + p.constants.nonEmpty + p.functions.nonEmpty
          + p.handlers.nonEmpty + p.inlets.nonEmpty + p.outlets.nonEmpty
        p match {
          case a: Application => countForPrrocessor + a.groups.nonEmpty
          case a: Adaptor =>
            specsForProcessor +
              +1 // direction (required)
              + 1 // contextRef (required)
          case c: Context =>
            specsForProcessor +
              +c.entities.nonEmpty
              + c.adaptors.nonEmpty
              + c.sagas.nonEmpty
              + c.streamlets.nonEmpty
              + c.projectors.nonEmpty
              + c.repositories.nonEmpty
              + c.connections.nonEmpty
              + c.replicas.nonEmpty
          case e: Entity =>
            specsForProcessor
              + e.states.nonEmpty
              + e.invariants.nonEmpty
          case p: Projector =>
            specsForProcessor
              + p.invariants.nonEmpty
          case _: Repository => specsForProcessor
          case s: Streamlet =>
            specsForProcessor
              + 1 // shape required
        }
      case d: Domain =>
        processorCount(d)
          + d.authorDefs.nonEmpty
          + d.contexts.nonEmpty
          + d.users.nonEmpty
          + d.epics.nonEmpty
          + d.sagas.nonEmpty
          + d.applications.nonEmpty
          + 0 // recursive domains not included
      case e: Epic =>
        processorCount(d)
          + e.userStory.nonEmpty
          + e.shownBy.nonEmpty
          + e.cases.nonEmpty
      case f: Function =>
        processorCount(d)
          + f.input.nonEmpty
          + f.output.nonEmpty
          + f.statements.nonEmpty
          + 0 // recursive functions not included
      case s: Saga =>
        processorCount(d)
          + s.input.nonEmpty
          + s.output.nonEmpty
          + s.sagaSteps.nonEmpty
    }
    require(divisor > 0, "Completeness requires positive specification divisor" )
    numerator.toDouble / divisor
  }

  private def complexityOf(v: VitalDefinition[?, ?]): Double = {
    v match {
      case a: Adaptor     => 0.0d
      case a: Applicaiton => 0.0d
      case c: Context     => 0.0d
      case d: Domain      => 0.0d
      case e: Entity      => 0.0d
      case e: Epic        => 0.0d
      case f: Function    => 0.0d
      case p: Projector   => 0.0d
      case r: Repository  => 0.0d
      case s: Saga        => 0.0d
      case s: Streamlet   => 0.0d
    }
  }

  private def maturityOf(v: VitalDefinion[?,?]): Double = {

  }

  private def makeVitalStats(
    v: VitalDefinition[?, ?],
    stats: => KindStats
  ): Unit = {
    stats.count += 1
    stats.maturitySum += v.maturity
    if v.nonEmpty then stats.completed += 1
    if v.brief.nonEmpty && v.description.nonEmpty then stats.documented += 1
  }

  def postProcess(root: RootContainer): Unit = ()

  /**
   * Generate the output of this Pass. This will only be called after all the calls
   * to process have completed.
   *
   * @return an instance of the output type
   */
  override def result: PassOutput = {
    val count = all_stats.count
    val percent_complete = (all_stats.completed.toDouble / count) * 100.0d
    val averageMaturity = (all_stats.maturitySum.toDouble / count) * 100.0d
    val percent_documented = (all_stats.documented.toDouble / count) * 100.0d
    val all = CategoryStats(
      count,
      100.0d,
      averageMaturity,
      all_stats.maturitySum,
      percent_complete,
      percent_documented
    )
    val adaptor = makeCategoryStats(adaptorStats, all_stats)
    val context = makeCategoryStats(contextStats, all_stats)
    val domain = makeCategoryStats(domainStats, all_stats)
    val entity = makeCategoryStats(entityStats, all_stats)
    val function = makeCategoryStats(functionStats, all_stats)
    val handler = makeCategoryStats(handlerStats, all_stats)
    val plant = makeCategoryStats(plantStats, all_stats)
    val processor = makeCategoryStats(streamletStats, all_stats)
    val projection = makeCategoryStats(projectionStats, all_stats)
    val saga = makeCategoryStats(sagaStats, all_stats)
    val story = makeCategoryStats(storyStats, all_stats)
    val other = makeCategoryStats(other_stats, all_stats)
    StatsOutput(
      Messages.empty,
      all_stats.count,
      term_count,
      maximum_depth,
      categories = Map(
        "All" -> all,
        "Adaptor" -> adaptor,
        "Context" -> context,
        "Domain" -> domain,
        "Entity" -> entity,
        "Function" -> function,
        "Handler" -> handler,
        "Plant" -> plant,
        "Processor" -> processor,
        "Projector" -> projection,
        "Saga" -> saga,
        "Story" -> story,
        "Other" -> other
      )
    )

  }

  def makeCategoryStats(
    stats: KindStats,
    all_stats: KindStats
  ): CategoryStats = {
    if stats.count > 0 then {
      val average_maturity = (stats.maturitySum.toFloat / stats.count)
      val percent_of_all = (stats.count.toDouble / all_stats.count) * 100.0d
      val percent_completed = (stats.completed.toDouble / stats.count) * 100.0d
      val percent_documented =
        (stats.documented.toDouble / stats.count) * 100.0d
      CategoryStats(
        stats.count,
        percent_of_all,
        average_maturity,
        stats.maturitySum,
        percent_completed,
        percent_documented
      )
    } else CategoryStats()
  }
}
