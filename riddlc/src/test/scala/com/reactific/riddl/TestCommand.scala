package com.reactific.riddl
import com.reactific.riddl.RiddlOptions.Plugin
import com.reactific.riddl.commands.CommandPlugin
import com.reactific.riddl.language.CommonOptions
import com.reactific.riddl.utils.RiddlBuildInfo
import scopt.OParser

case class TestOptions(
  command: Plugin = Plugin("<none>"),
  args: Map[String,String] = Map.empty[String,String],
  commonOptions: CommonOptions = CommonOptions(),
) extends RiddlcCommandOptions

/** A pluggable command for testing plugin commands! */
class TestCommand extends CommandPlugin[TestOptions] {
  override def getOptions: OParser[Unit, TestOptions] = {
    val builder = OParser.builder[TestOptions]
    import builder._
    OParser.sequence(
      cmd("test").action( (_,to) => to.copy(command = Plugin("test")))
        .children(
          arg[Map[String,String]]("args").action( (m,to) =>
            to.copy(args = m))
            .validate { map =>
              if (map.keys.forall(_.nonEmpty)) { Right(()) }
              else { Left("All argument keys must be nonempty") }
            }
        )
    )
  }
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
