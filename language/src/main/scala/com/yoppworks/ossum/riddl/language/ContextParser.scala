package com.yoppworks.ossum.riddl.language

import com.yoppworks.ossum.riddl.language.AST._
import fastparse._
import ScalaWhitespace._

/** Parsing rules for Context definitions */
trait ContextParser
    extends AdaptorParser
    with ChannelParser
    with EntityParser
    with InteractionParser
    with MessageParser
    with TypeParser {

  def contextOptions[X: P]: P[Seq[ContextOption]] = {
    options[X, ContextOption](StringIn("wrapper", "function", "gateway").!) {
      case (loc, "wrapper")  => WrapperOption(loc)
      case (loc, "function") => FunctionOption(loc)
      case (loc, "gateway")  => GatewayOption(loc)
      case (_, _)            => throw new RuntimeException("Impossible case")
    }
  }

  type ContextDefinitions = (
    Seq[TypeDef],
    Seq[CommandDef],
    Seq[EventDef],
    Seq[QueryDef],
    Seq[ResultDef],
    Seq[ChannelDef],
    Seq[EntityDef],
    Seq[AdaptorDef],
    Seq[InteractionDef]
  )

  def mapTo[T <: Definition](seq: Option[Seq[Definition]]): Seq[T] = {
    seq.map(_.map(_.asInstanceOf[T])).getOrElse(Seq.empty[T])
  }

  def contextDefinitions[_: P]: P[ContextDefinitions] = {
    P(
      typeDef |
        commandDef |
        eventDef |
        queryDef |
        resultDef |
        channelDef |
        entityDef |
        adaptorDef |
        interactionDef
    ).rep(0).map { seq =>
      val groups = seq.groupBy(_.getClass)
      (
        mapTo[TypeDef](groups.get(classOf[TypeDef])),
        mapTo[CommandDef](groups.get(classOf[CommandDef])),
        mapTo[EventDef](groups.get(classOf[EventDef])),
        mapTo[QueryDef](groups.get(classOf[QueryDef])),
        mapTo[ResultDef](groups.get(classOf[ResultDef])),
        mapTo[ChannelDef](groups.get(classOf[ChannelDef])),
        mapTo[EntityDef](groups.get(classOf[EntityDef])),
        mapTo[AdaptorDef](groups.get(classOf[AdaptorDef])),
        mapTo[InteractionDef](groups.get(classOf[InteractionDef]))
      )
    }
  }

  def contextDef[_: P]: P[ContextDef] = {
    P(
      location ~ "context" ~/ identifier ~ open ~
        contextOptions ~ contextDefinitions ~
        close ~ addendum
    ).map {
      case (loc, id, options, defs, addendum) =>
        ContextDef(
          loc,
          id,
          options,
          defs._1,
          defs._2,
          defs._3,
          defs._4,
          defs._5,
          defs._6,
          defs._7,
          defs._8,
          defs._9,
          addendum
        )
    }
  }
}
