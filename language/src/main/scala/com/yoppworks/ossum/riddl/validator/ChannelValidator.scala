package com.yoppworks.ossum.riddl.validator

import com.yoppworks.ossum.riddl.parser.AST._
import com.yoppworks.ossum.riddl.parser.Traversal
import Validation._

/** Validator For Channels*/
case class ChannelValidator(
  channel: ChannelDef,
  payload: ValidationState
) extends ValidatorBase[ChannelDef](channel)
    with Traversal.ChannelTraveler[ValidationState] {

  def visitCommands(commands: Seq[CommandRef]): Unit = {
    commands.foreach { ref =>
      checkRef[CommandDef](ref.id)
    }
  }

  def visitEvents(events: Seq[EventRef]): Unit = {
    events.foreach { ref =>
      checkRef[CommandDef](ref.id)
    }
  }

  def visitQueries(queries: Seq[QueryRef]): Unit = {
    queries.foreach { ref =>
      checkRef[CommandDef](ref.id)
    }
  }

  def visitResults(results: Seq[ResultRef]): Unit = {
    results.foreach { ref =>
      checkRef[CommandDef](ref.id)
    }

  }
}
