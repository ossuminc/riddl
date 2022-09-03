package com.reactific.riddl.commands

import com.reactific.riddl.language.CommonOptions
import com.reactific.riddl.utils.{Logger, RiddlBuildInfo}
import pureconfig.{ConfigCursor, ConfigReader}
import scopt.OParser

case class TestOptions(
  arg1: String = "",
  commonOptions: CommonOptions = CommonOptions()
) extends CommandOptions

/** A pluggable command for testing plugin commands! */
class TestCommand extends CommandPlugin[TestOptions]("test") {
  override def getOptions: (OParser[Unit, TestOptions], TestOptions) = {
    val builder = OParser.builder[TestOptions]
    import builder.*
    OParser.sequence(
      cmd("test")
        .children(
          arg[String]("arg1").action( (s,to)=>
            to.copy(arg1 = s))
            .validate { a1 =>
              if (a1.nonEmpty) { Right(()) }
              else { Left("All argument keys must be nonempty") }
            }
        )
    ) -> TestOptions()
  }

  override def getConfigReader: ConfigReader[TestOptions] = {
    (cur: ConfigCursor) =>
      for {
        objCur <- cur.asObjectCursor
        contentCur <- objCur.atKey("test")
        contentObjCur <- contentCur.asObjectCursor
        arg1Res <- contentObjCur.atKey("arg1")
        str <- arg1Res.asString
      } yield {
        TestOptions(arg1 = str)
      }
  }

  override def run(options: TestOptions, log: Logger): Boolean = {
    println(s"arg1: '${options.arg1}''")
    true
  }

  override def pluginName: String = "test"
  override def pluginVersion: String = RiddlBuildInfo.version
}
