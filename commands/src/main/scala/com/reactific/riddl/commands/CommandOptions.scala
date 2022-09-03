package com.reactific.riddl.commands
import com.reactific.riddl.language.CommonOptions

/**
 * Base class for command options. Every command should extend this to a case
 * class
 */
trait CommandOptions  {
  def commonOptions: CommonOptions
}
object CommandOptions {
  val empty: CommandOptions = new CommandOptions {
    val commonOptions: CommonOptions = CommonOptions()
  }
}
