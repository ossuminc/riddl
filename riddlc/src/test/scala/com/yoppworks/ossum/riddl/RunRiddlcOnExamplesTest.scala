package com.yoppworks.ossum.riddl

/** Unit Tests To Run Riddlc On Examples */
import com.yoppworks.ossum.riddl.translator.hugo.HugoTranslateExamplesBase
import org.scalatest.Assertion

import java.nio.file.{Files, Path}

class RunRiddlcOnExamplesTest extends HugoTranslateExamplesBase {

  val output = "riddlc/target/test/ReactiveBBQ.riddl"
  val name = "ReactiveBBQ"
  val path = "ReactiveBBQ/ReactiveBBQ.riddl"
  val conf = s"$directory/ReactiveBBQ/ReactiveBBQ.conf"

  def runRiddlcFromConfig: Assertion = {
    val args = Array("from", conf, "-o", output)
    RIDDLC.runMain(args) mustBe true
  }

  "riddlc" should {
    "handle the ReactiveBBQ example from config" in {
      runRiddlcFromConfig
      val outputDir = Path.of(output)
      runHugo(outputDir)
      val root = Path.of(output)
      val img = root.resolve("static/images/RBBQ.png")
      Files.exists(img) mustBe true
      // TODO: check themes dir
      // TODO: check config.toml setting values
      // TODO: check options
    }
  }
}
