package com.yoppworks.ossum.riddl.language

import com.yoppworks.ossum.riddl.language.AST._
import fastparse._
import ScalaWhitespace._
import Terminals.Keywords
import Terminals.Options

/** Parsing rules for Context definitions */
trait ContextParser
    extends AdaptorParser
    with EntityParser
    with InteractionParser
    with TypeParser {

  def contextOptions[X: P]: P[Seq[ContextOption]] = {
    options[X, ContextOption](
      StringIn(Options.wrapper, Options.function, Options.gateway).!
    ) {
      case (loc, Options.wrapper)  => WrapperOption(loc)
      case (loc, Options.function) => FunctionOption(loc)
      case (loc, Options.gateway)  => GatewayOption(loc)
      case (_, _)                  => throw new RuntimeException("Impossible case")
    }
  }

  def contextInclude[_: P]: P[Seq[ContextDefinition]] = {
    P(
      Keywords.include ~/ literalString
    ).map { str =>
      doInclude(str, Seq.empty[ContextDefinition])(contextDefinitions(_))
    }
  }

  def contextDefinitions[_: P]: P[Seq[ContextDefinition]] = {
    P(
      typeDef.map(Seq(_)) |
        entityDef.map(Seq(_)) |
        adaptorDef.map(Seq(_)) |
        interactionDef.map(Seq(_)) |
        contextInclude
    ).rep(0).map { seq =>
      seq.flatten
    }
  }

  def contextDef[_: P]: P[Context] = {
    P(
      location ~ Keywords.context ~/ identifier ~ is ~
        open ~
        contextOptions ~ contextDefinitions ~
        close ~ description
    ).map {
      case (
          loc,
          id,
          options,
          definitions,
          addendum
          ) =>
        val groups = definitions.groupBy(_.getClass)
        val types = mapTo[Type](groups.get(classOf[Type]))
        val entities = mapTo[Entity](groups.get(classOf[Entity]))
        val adaptors = mapTo[Adaptor](groups.get(classOf[Adaptor]))
        val interactions =
          mapTo[Interaction](groups.get(classOf[Interaction]))
        Context(
          loc,
          id,
          options,
          types,
          entities,
          adaptors,
          interactions,
          addendum
        )
    }
  }
}
