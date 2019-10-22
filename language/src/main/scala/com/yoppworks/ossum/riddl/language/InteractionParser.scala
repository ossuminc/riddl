package com.yoppworks.ossum.riddl.language

import AST._
import fastparse._
import ScalaWhitespace._

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
      location ~ "role" ~/ identifier ~ open ~
        roleOptions ~
        "handles" ~/ literalStrings() ~
        "requires" ~ literalStrings() ~
        close ~ addendum
    ).map { tpl =>
      (RoleDef.apply _).tupled(tpl)
    }
  }

  def messageOptions[_: P]: P[Seq[MessageOption]] = {
    options(StringIn("synch", "asynch", "reply").!) {
      case (loc, "synch")  => SynchOption(loc)
      case (loc, "asynch") => AsynchOption(loc)
      case (loc, "reply")  => ReplyOption(loc)
      case _               => throw new RuntimeException("invalid message option")
    }
  }

  def reaction[_: P]: P[Reaction] = {
    P(
      location ~ identifier ~ "call" ~ entityRef ~/ functionRef ~
        literalStrings() ~ addendum
    ).map(x => (Reaction.apply _).tupled(x))
  }

  def reactions[_: P]: P[Seq[Reaction]] = {
    P(open ~ reaction.rep(0, ",") ~ close)
  }

  def causing[_: P]: P[Seq[Reaction]] = {
    P("causing" ~ reactions).?.map(_.getOrElse(Seq.empty[Reaction]))
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
      location ~ "interaction" ~/ identifier ~ "{" ~
        role.rep(1) ~ interactions ~
        "}" ~ addendum
    ).map { tpl =>
      (InteractionDef.apply _).tupled(tpl)
    }
  }
}
