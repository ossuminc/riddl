package com.yoppworks.ossum.riddl.language

import com.yoppworks.ossum.riddl.language.AST.*
import fastparse.*
import ScalaWhitespace.*
import Terminals.Keywords
import Terminals.Options

/** Parsing rules for Context definitions */
trait ContextParser extends AdaptorParser with EntityParser with InteractionParser with TypeParser {

  def contextOptions[X: P]: P[Seq[ContextOption]] = {
    options[X, ContextOption](StringIn(Options.wrapper, Options.function, Options.gateway).!) {
      case (loc, Options.wrapper)  => WrapperOption(loc)
      case (loc, Options.function) => FunctionOption(loc)
      case (loc, Options.gateway)  => GatewayOption(loc)
      case (_, _)                  => throw new RuntimeException("Impossible case")
    }
  }

  def contextInclude[X: P]: P[Seq[ContextDefinition]] = {
    include[ContextDefinition, X](contextDefinitions(_))
  }

  def contextDefinitions[u: P]: P[Seq[ContextDefinition]] = {
    P(
      typeDef.map(Seq(_)) | entity.map(Seq(_)) | adaptor.map(Seq(_)) | interaction.map(Seq(_)) |
        contextInclude
    ).rep(0).map { seq => seq.flatten }
  }

  def context[u: P]: P[Context] = {
    P(
      location ~ Keywords.context ~/ identifier ~ is ~ open ~
        (undefined(Seq.empty[ContextOption] -> Seq.empty[ContextDefinition]) |
          (contextOptions ~ contextDefinitions)) ~ close ~ description
    ).map { case (loc, id, (options, definitions), addendum) =>
      val groups = definitions.groupBy(_.getClass)
      val types = mapTo[Type](groups.get(classOf[Type]))
      val entities = mapTo[Entity](groups.get(classOf[Entity]))
      val adaptors = mapTo[Adaptor](groups.get(classOf[Adaptor]))
      val interactions = mapTo[Interaction](groups.get(classOf[Interaction]))
      Context(loc, id, options, types, entities, adaptors, interactions, addendum)
    }
  }
}
