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
    P(location ~ "role" ~/ identifier).map(tpl => (RoleRef.apply _).tupled(tpl))
  }

  def roleOptions[_: P]: P[Seq[RoleOption]] = {
    options(StringIn("human", "device").!) {
      case (loc, "human")  => HumanOption(loc)
      case (loc, "device") => DeviceOption(loc)
      case _               => throw new RuntimeException("Impossible case")
    }
  }

  def role[_: P]: P[RoleDef] = {
    P(
      location ~ Keywords.role ~/ identifier ~ Punctuation.curlyOpen ~
        roleOptions ~
        Keywords.handles ~/ lines ~
        Keywords.requires ~ lines ~
        Punctuation.curlyClose ~ addendum
    ).map { tpl =>
      (RoleDef.apply _).tupled(tpl)
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
      location ~ identifier ~ Keywords.call ~ entityRef ~/ functionRef ~
        lines ~ addendum
    ).map(x => (Reaction.apply _).tupled(x))
  }

  def reactions[_: P]: P[Seq[Reaction]] = {
    P(
      Punctuation.curlyOpen ~ reaction
        .rep(0, Punctuation.comma) ~ Punctuation.curlyClose
    )
  }

  def causing[_: P]: P[Seq[Reaction]] = {
    P(Keywords.causing ~ reactions).?.map(_.getOrElse(Seq.empty[Reaction]))
  }

  def messageActionDef[_: P]: P[MessageActionDef] = {
    P(
      location ~
        "message" ~/ identifier ~ messageOptions ~ "from" ~/ entityRef ~ "to" ~/
        entityRef ~
        "as" ~ messageRef ~ causing ~ addendum
    ).map { tpl =>
      (MessageActionDef.apply _).tupled(tpl)
    }
  }

  def directiveActionDef[_: P]: P[DirectiveActionDef] = {
    P(
      location ~
        "directive" ~/ identifier ~ messageOptions ~ "from" ~ roleRef ~ "to" ~
        entityRef ~
        "as" ~ messageRef ~ causing ~ addendum
    ).map { tpl =>
      (DirectiveActionDef.apply _).tupled(tpl)
    }
  }

  def interactions[_: P]: P[Actions] = {
    P(
      messageActionDef | directiveActionDef
    ).rep(1)
  }

  def interactionDef[_: P]: P[InteractionDef] = {
    P(
      location ~ Keywords.interaction ~/ identifier ~ Punctuation.curlyOpen ~
        role.rep(1) ~ interactions ~
        Punctuation.curlyClose ~ addendum
    ).map { tpl =>
      (InteractionDef.apply _).tupled(tpl)
    }
  }
}
