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

  type ContextDefinitions = (
    Seq[TypeDef],
    Seq[EntityDef],
    Seq[AdaptorDef],
    Seq[InteractionDef]
  )

  def contextDefinitions[_: P]: P[ContextDefinitions] = {
    P(
      typeDef |
        entityDef |
        adaptorDef |
        interactionDef
    ).rep(0).map { seq =>
      val groups = seq.groupBy(_.getClass)
      (
        mapTo[TypeDef](groups.get(classOf[TypeDef])),
        mapTo[EntityDef](groups.get(classOf[EntityDef])),
        mapTo[AdaptorDef](groups.get(classOf[AdaptorDef])),
        mapTo[InteractionDef](groups.get(classOf[InteractionDef]))
      )
    }
  }

  def contextDef[_: P]: P[ContextDef] = {
    P(
      location ~ Keywords.context ~/ identifier ~ is ~
        open ~
        contextOptions ~ contextDefinitions ~
        close ~ addendum
    ).map {
      case (
          loc,
          id,
          options,
          (types, entities, adaptors, interactions),
          addendum
          ) =>
        ContextDef(
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
