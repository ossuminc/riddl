package com.yoppworks.ossum.riddl.language.parsing

import com.yoppworks.ossum.riddl.language.AST.*
import com.yoppworks.ossum.riddl.language.Terminals.Keywords
import com.yoppworks.ossum.riddl.language.Terminals.Options
import com.yoppworks.ossum.riddl.language.Terminals.Punctuation
import fastparse.*
import fastparse.ScalaWhitespace.*

/** Parsing rules for context interactions */
trait InteractionParser extends ReferenceParser {

  def roleOptions[u: P]: P[Seq[RoleOption]] = {
    options(StringIn("human", "device").!) {
      case (loc, "human")  => HumanOption(loc)
      case (loc, "device") => DeviceOption(loc)
      case _               => throw new RuntimeException("Impossible case")
    }
  }

  def messageOptions[u: P]: P[Seq[MessageOption]] = {
    options(StringIn(Options.sync, Options.async, Options.reply).!) {
      case (loc, Options.sync)  => SynchOption(loc)
      case (loc, Options.async) => AsynchOption(loc)
      case (loc, Options.reply) => ReplyOption(loc)
      case _                    => throw new RuntimeException("invalid message option")
    }
  }

  def reaction[u: P]: P[Reaction] = {
    P(location ~ identifier ~ Keywords.call ~ entityRef ~/ actionRef ~ docBlock ~ description)
      .map(x => (Reaction.apply _).tupled(x))
  }

  def reactions[u: P]: P[Seq[Reaction]] = { P(open ~ reaction.rep(0, Punctuation.comma) ~ close) }

  def causing[u: P]: P[Seq[Reaction]] = {
    P(Keywords.causing ~ reactions).?.map(_.getOrElse(Seq.empty[Reaction]))
  }

  def messageActionDef[u: P]: P[MessageAction] = {
    P(
      location ~ "message" ~/ identifier ~ messageOptions ~ "from" ~/ entityRef ~ "to" ~/
        entityRef ~ "as" ~ messageRef ~ causing ~ description
    ).map { tpl => (MessageAction.apply _).tupled(tpl) }
  }

  def interactions[u: P]: P[Actions] = { P(messageActionDef).rep(1) }

  def interaction[u: P]: P[Interaction] = {
    P(
      location ~ Keywords.interaction ~/ identifier ~ is ~ open ~
        (undefined(Seq.empty[ActionDefinition]) | interactions) ~ close ~ description
    ).map { case (loc, id, interactions, description) =>
      Interaction(loc, id, interactions, description)
    }
  }
}
