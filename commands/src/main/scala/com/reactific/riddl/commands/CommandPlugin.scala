package com.reactific.riddl.commands

import com.reactific.riddl.utils.{Logger, PluginInterface}
import pureconfig.ConfigReader
import scopt.OParser

/** The service interface for Riddlc command plugins */
abstract class CommandPlugin[OPT <: CommandOptions](
  val commandName: String
) extends PluginInterface {
  def getOptions: (OParser[Unit,OPT], OPT)
  def getConfigReader: ConfigReader[OPT]
  def run(options: OPT, log: Logger): Boolean
}

