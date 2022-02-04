package com.yoppworks.ossum.riddl.language.parsing

import com.yoppworks.ossum.riddl.language.AST.*
import com.yoppworks.ossum.riddl.language.Terminals.{Keywords, Options, Punctuation, Readability}
import fastparse.*
import fastparse.ScalaWhitespace.*

/** Parsing rules for context interactions */
trait InteractionParser extends ReferenceParser {

  def interactionOptions[u: P]: P[Seq[InteractionOption]] = {
    options(StringIn("gateway").!) {
      case (loc, "gateway", _) => GatewayInteraction(loc)
      case _ => throw new RuntimeException("Impossible case")
    }
  }

  def messageOptions[u: P]: P[Seq[MessageOption]] = {
    options(StringIn(Options.sync, Options.async, Options.reply).!) {
      case (loc, Options.sync, _) => SynchOption(loc)
      case (loc, Options.async, _) => AsynchOption(loc)
      case (loc, Options.reply, _) => ReplyOption(loc)
      case _ => throw new RuntimeException("invalid message option")
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
      location ~ Keywords.message ~/ identifier ~ messageOptions ~ Readability.from ~/ entityRef ~
        Readability.to ~/ entityRef ~ Readability.as ~ messageRef ~ causing ~ briefly ~ description
    ).map { tpl => (MessageAction.apply _).tupled(tpl) }
  }

  def interactionBody[u: P]: P[(Seq[InteractionOption], Actions)] = {
    P(interactionOptions ~ messageActionDef.rep(1))
  }

  def interaction[u: P]: P[Interaction] = {
    P(
      location ~ Keywords.interaction ~/ identifier ~ is ~ open ~
        (undefined((Seq.empty[InteractionOption], Seq.empty[ActionDefinition])) | interactionBody) ~
        close ~ briefly ~ description
    ).map { case (loc, id, (options, interactions), briefly, description) =>
      Interaction(loc, id, options, interactions, briefly, description)
    }
  }
}
