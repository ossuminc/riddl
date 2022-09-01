package com.reactific.riddl
import com.reactific.riddl.utils.RiddlBuildInfo

/** A pluggable command for testing plugin commands! */
class TestCommand extends RiddlcCommandPlugin {
  override def run(options: RiddlOptions): Boolean = {
    println(options)
    true
  }
  override def pluginName: String = "test"
  override def pluginVersion: String = RiddlBuildInfo.version
}
