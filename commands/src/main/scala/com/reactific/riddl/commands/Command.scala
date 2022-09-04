package com.reactific.riddl.commands

/** Definitions of the types of commands we have */
sealed trait Command

case object Unspecified extends Command
case object HugoGitCheck extends Command
case object Help extends Command
case object Repeat extends Command
case object Info extends Command
case class PluginCommand(name: String) extends Command
