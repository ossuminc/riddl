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

  def open(): Unit = {}

  def visitCommands(commands: Seq[CommandRef]): Unit = {}

  def visitEvents(events: Seq[EventRef]): Unit = {}

  def visitQueries(queries: Seq[QueryRef]): Unit = {}

  def visitResults(results: Seq[ResultRef]): Unit = {}
}
