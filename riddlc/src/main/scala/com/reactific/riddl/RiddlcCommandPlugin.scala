package com.reactific.riddl

import com.reactific.riddl.utils.PluginInterface

/** The service interface for Riddlc command plugins */
trait RiddlcCommandPlugin extends PluginInterface {
  def validate(options: Map[String, String]): Seq[String]
  def run(options: RiddlOptions): Boolean
}
