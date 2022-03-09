package com.reactific.riddl.language.parsing

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.Terminals.{Keywords, Options}
import fastparse.*
import fastparse.ScalaWhitespace.*

/** Parsing rules for Context definitions */
trait ContextParser
    extends GherkinParser
    with AdaptorParser
    with EntityParser
    with SagaParser
    with TypeParser {

  def contextOptions[X: P]: P[Seq[ContextOption]] = {
    options[X, ContextOption](
      StringIn(Options.wrapper, Options.function, Options.gateway, Options.service).!
    ) {
      case (loc, Options.wrapper, _)  => WrapperOption(loc)
      case (loc, Options.function, _) => FunctionOption(loc)
      case (loc, Options.gateway, _)  => GatewayOption(loc)
      case (loc, Options.service, _)  => ServiceOption(loc)
      case (_, _, _)                  => throw new RuntimeException("Impossible case")
    }
  }

  def contextInclude[X: P]: P[Include] = {
    include[ContextDefinition, X](contextDefinitions(_))
  }

  def contextDefinitions[u: P]: P[Seq[ContextDefinition]] = {
    P( undefined(Seq.empty[ContextDefinition]) |
      (typeDef | entity | adaptor  | function | saga | term | contextInclude
      ).rep(0)
    )
  }

  def context[u: P]: P[Context] = {
    P(
      location ~ Keywords.context ~/ identifier ~ is ~ open ~
        (undefined(Seq.empty[ContextOption] -> Seq.empty[ContextDefinition]) |
          (contextOptions ~ contextDefinitions)) ~ close ~ briefly ~ description
    ).map { case (loc, id, (options, definitions), briefly, description) =>
      val groups = definitions.groupBy(_.getClass)
      val types = mapTo[Type](groups.get(classOf[Type]))
      val functions = mapTo[Function](groups.get(classOf[Function]))
      val entities = mapTo[Entity](groups.get(classOf[Entity]))
      val adaptors = mapTo[Adaptor](groups.get(classOf[Adaptor]))
      val terms = mapTo[Term](groups.get(classOf[Term]))
      val includes = mapTo[Include](groups.get(classOf[Include]))
      val sagas = mapTo[Saga](groups.get(classOf[Saga]))
      Context(
        loc,
        id,
        options,
        types,
        entities,
        adaptors,
        sagas,
        functions,
        terms,
        includes,
        briefly,
        description
      )
    }
  }
}
