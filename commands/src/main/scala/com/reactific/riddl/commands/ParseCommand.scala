package com.reactific.riddl.commands

import com.reactific.riddl.language.Riddl
import com.reactific.riddl.utils.{Logger, RiddlBuildInfo}

/**
 * A Command for Parsing RIDDL input
 */
class ParseCommand extends InputFileCommandPlugin("parse") {
  def run(options: InputFileCommandPlugin.Options, log: Logger): Boolean = {
    options.inputFile match {
      case Some(path) => Riddl.parse(path, log, options.commonOptions)
          .nonEmpty
      case None =>
        log.error("No input file provided in options")
        false
    }
  }
  override def pluginName: String = "parse"
  override def pluginVersion: String = RiddlBuildInfo.version
}
