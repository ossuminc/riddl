package com.reactific.riddl
import com.reactific.riddl.utils.RiddlBuildInfo

/** A pluggable command for testing plugin commands! */
class TestCommand extends RiddlcCommandPlugin {
  def validate(options: Map[String,String]): Seq[String] = Seq.empty[String]
  override def run(options: RiddlOptions): Boolean = {
    options.commandArgs.foreach {
      case (name,value) =>
        println(s"$name: $value")
    }
    true
  }
  override def pluginName: String = "test"
  override def pluginVersion: String = RiddlBuildInfo.version
}
