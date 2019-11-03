package com.yoppworks.ossum.riddl.language

import AST._
import fastparse._
import ScalaWhitespace._
import Terminals.Keywords
import Terminals.Options
import Terminals.Punctuation

/** Parsing rules for context interactions */
trait InteractionParser extends CommonParser {

  def roleRef[_: P]: P[RoleRef] = {
    P(location ~ "role" ~/ pathIdentifier)
      .map(tpl => (RoleRef.apply _).tupled(tpl))
  }

  def roleOptions[_: P]: P[Seq[RoleOption]] = {
    options(StringIn("human", "device").!) {
      case (loc, "human")  => HumanOption(loc)
      case (loc, "device") => DeviceOption(loc)
      case _               => throw new RuntimeException("Impossible case")
    }
  }

  def role[_: P]: P[Role] = {
    P(
      location ~ Keywords.role ~/ identifier ~ open ~
        roleOptions ~
        Keywords.handles ~/ docBlock ~
        Keywords.requires ~ docBlock ~
        close ~ description
    ).map { tpl =>
      (Role.apply _).tupled(tpl)
    }
  }

  def messageOptions[_: P]: P[Seq[MessageOption]] = {
    options(StringIn(Options.sync, Options.async, Options.reply).!) {
      case (loc, Options.sync)  => SynchOption(loc)
      case (loc, Options.async) => AsynchOption(loc)
      case (loc, Options.reply) => ReplyOption(loc)
      case _                    => throw new RuntimeException("invalid message option")
    }
  }

  def reaction[_: P]: P[Reaction] = {
    P(
      location ~ identifier ~ Keywords.call ~ entityRef ~/ actionRef ~
        docBlock ~ description
    ).map(x => (Reaction.apply _).tupled(x))
  }

  def reactions[_: P]: P[Seq[Reaction]] = {
    P(
      open ~ reaction.rep(0, Punctuation.comma) ~ close
    )
  }

  def causing[_: P]: P[Seq[Reaction]] = {
    P(Keywords.causing ~ reactions).?.map(_.getOrElse(Seq.empty[Reaction]))
  }

  def messageActionDef[_: P]: P[MessageAction] = {
    P(
      location ~
        "message" ~/ identifier ~ messageOptions ~ "from" ~/ entityRef ~ "to" ~/
        entityRef ~
        "as" ~ messageRef ~ causing ~ description
    ).map { tpl =>
      (MessageAction.apply _).tupled(tpl)
    }
  }

  def directiveActionDef[_: P]: P[DirectiveAction] = {
    P(
      location ~
        "directive" ~/ identifier ~ messageOptions ~ "from" ~ roleRef ~ "to" ~
        entityRef ~
        "as" ~ messageRef ~ causing ~ description
    ).map { tpl =>
      (DirectiveAction.apply _).tupled(tpl)
    }
  }

  def interactions[_: P]: P[Actions] = {
    P(
      messageActionDef | directiveActionDef
    ).rep(1)
  }

  def interactionDef[_: P]: P[Interaction] = {
    P(
      location ~ Keywords.interaction ~/ identifier ~ is ~
        open ~
        role.rep(1) ~ interactions ~
        close ~
        description
    ).map { tpl =>
      (Interaction.apply _).tupled(tpl)
    }
  }
}
