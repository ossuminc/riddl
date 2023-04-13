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
  averageMaturity: Double = 0.0d,
  totalMaturity: Long = 0,
  percentComplete: Double = 0.0d,
  percentDocumented: Double = 0.0d) {
  override def toString: String = {
    f"count:$count%s($percentOfDefinitions%.2f%%), maturity:$totalMaturity%d($averageMaturity%.2f%%)," +
      f"complete: $percentComplete%.2f%%, documented: $percentDocumented%.2f%%"
  }
}


case class StatsOutput(
  messages: Messages = Messages.empty,
  count: Long = 0,
  term_count: Long = 0,
  maximum_depth: Int = 0,
  categories: Map[String, CategoryStats] = Map.empty
) extends PassOutput

/** Unit Tests For StatsPass */
case class StatsPass(input: PassInput) extends Pass(input) {

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
    if (parents.size >= maximum_depth) maximum_depth = parents.size + 1
    if (!definition.isEmpty) all_stats.completed += 1
    if (definition.brief.nonEmpty && definition.description.nonEmpty) all_stats.documented += 1

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
          case h: Handler => makeVitalStats(h, handlerStats)
          case s: Saga => makeVitalStats(s, sagaStats)
        }
      case t: Term =>
        term_count += 1
        other_stats.count += 1
        if (t.nonEmpty) other_stats.completed += 1
      case d: Definition =>
        other_stats.count += 1
        if (d.nonEmpty) other_stats.completed += 1
      case _ => // ignore
    }
  }

  private def makeVitalStats(
    v: VitalDefinition[?, ?],
    stats: => KindStats
  ): Unit = {
    stats.count += 1
    stats.maturitySum += v.maturity
    if (v.nonEmpty) stats.completed += 1
    if (v.brief.nonEmpty && v.description.nonEmpty) stats.documented += 1
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
    if (stats.count > 0) {
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
