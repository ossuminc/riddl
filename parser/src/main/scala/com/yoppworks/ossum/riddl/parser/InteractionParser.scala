package com.yoppworks.ossum.riddl.parser

import AST._
import CommonParser._
import fastparse._
import ScalaWhitespace._

/** Unit Tests For InteractionParser */
object InteractionParser {

  def roleRef[_: P]: P[RoleRef] = {
    P("role" ~ identifier).map(RoleRef)
  }

  def roleOption[_: P]: P[RoleOption] = {
    P(StringIn("human", "device")).!.map {
      case "human" ⇒ HumanOption
      case "device" ⇒ DeviceOption
    }
  }

  def role[_: P]: P[RoleDef] = {
    P(
      roleOption.rep(0) ~
        "role" ~/ Index ~ identifier ~
        ("handles" ~/ literalString.rep(1, ",")).?.map(
          _.getOrElse(Seq.empty[String]).toList
        ) ~
        ("requires" ~ literalString.rep(1, ",")).?.map(
          _.getOrElse(Seq.empty[String]).toList
        )
    ).map { tpl ⇒
      (RoleDef.apply _).tupled(tpl)
    }
  }

  def processingActionDef[_: P]: P[ProcessingActionDef] = {
    P(
      "processing" ~/ Index ~ identifier ~ "for" ~ entityRef ~ "as" ~
        literalString
    ).map(x ⇒ (ProcessingActionDef.apply _).tupled(x))
  }

  def messageOption[_: P]: P[MessageOption] = {
    P(
      StringIn("synch", "asynch", "reply")
    ).!.map {
      case "synch" ⇒ SynchOption
      case "asynch" ⇒ AsynchOption
      case "reply" ⇒ ReplyOption
    }
  }

  def messageActionDef[_: P]: P[MessageActionDef] = {
    P(
      messageOption.rep(0) ~
        "message" ~/ Index ~ identifier ~ "from" ~/ entityRef ~ "to" ~/
        entityRef ~
        "with" ~ messageRef
    ).map { tpl ⇒
      (MessageActionDef.apply _).tupled(tpl)
    }
  }

  def directiveActionDef[_: P]: P[DirectiveActionDef] = {
    P(
      messageOption.rep(0) ~
        "directive" ~/ Index ~ identifier ~ "from" ~ roleRef ~ "to" ~
        entityRef ~
        "with" ~ messageRef
    ).map { tpl ⇒
      (DirectiveActionDef.apply _).tupled(tpl)
    }
  }

  def interactions[_: P]: P[Actions] = {
    P(messageActionDef | directiveActionDef | processingActionDef).rep(1)
  }

  def interactionDef[_: P]: P[InteractionDef] = {
    P(
      "interaction" ~ Index ~/ pathIdentifier ~ "{" ~
        role.rep(1) ~ interactions ~
        "}"
    ).map {
      case (index, path, actors, interactions) ⇒
        InteractionDef(
          index,
          path.dropRight(1).toList,
          path.last,
          actors.toList,
          interactions.toList
        )
    }
  }

}
