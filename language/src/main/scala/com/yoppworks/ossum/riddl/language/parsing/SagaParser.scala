package com.yoppworks.ossum.riddl.language.parsing

import com.yoppworks.ossum.riddl.language.AST.*
import com.yoppworks.ossum.riddl.language.Terminals.{Keywords, Options, Readability}
import fastparse.*
import fastparse.ScalaWhitespace.*

/** SagaParser Implements the parsing of saga definitions in context definitions.
  */
trait SagaParser extends CommonParser with FeatureParser with TypeParser {

  def sagaAction[u: P]: P[SagaAction] = {
    P(
      location ~ Keywords.action ~/ identifier ~ Readability.for_ ~ entityRef ~ is ~ open ~
        commandRef ~ Keywords.reverted ~ Readability.by.? ~ commandRef ~ as ~ open ~ examples ~
        close ~ close ~ description
    ).map(x => (SagaAction.apply _).tupled(x))
  }

  def sagaOptions[u: P]: P[Seq[SagaOption]] = {
    options[u, SagaOption](Options.parallel.! | Options.sequential.!) {
      case (loc, option) if option == Options.parallel => ParallelOption(loc)
      case (loc, option) if option == Options.sequential => SequentialOption(loc)
      case (loc, option) => throw new IllegalStateException(s"Unknown saga option $option at $loc")
    }
  }

  def sagaInput[u: P]: P[Aggregation] = {
    P(location ~ Keywords.input ~ aggregationWithoutDescription.?).map {
      case (_, Some(a)) => a
      case (loc, None) => Aggregation(loc)
    }
  }

  def saga[u: P]: P[Saga] = {
    P(
      location ~ Keywords.saga ~ identifier ~ is ~ open ~ sagaOptions ~ sagaInput ~
        sagaAction.rep(2) ~ close ~ description
    ).map(x => (Saga.apply _).tupled(x))
  }
}
