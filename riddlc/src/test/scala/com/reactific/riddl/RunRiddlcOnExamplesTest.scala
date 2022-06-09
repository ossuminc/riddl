package com.reactific.riddl

/** Unit Tests To Run Riddlc On Examples */

import com.reactific.riddl.translator.hugo.HugoTranslateExamplesBase

import java.nio.file.{Files, Path}

class RunRiddlcOnExamplesTest extends HugoTranslateExamplesBase {

  val output = "riddlc/target/test/"
  val name = "ReactiveBBQ"
  val path = "ReactiveBBQ/ReactiveBBQ.riddl"
  val conf = s"$directory/ReactiveBBQ/ReactiveBBQ.conf"
  val validateConf = s"$directory/ReactiveBBQ/validate.conf"
  val inputFile = s"$directory/$path"
  val outputDir = s"$output/$path"

  "riddlc" should {
    "handle parse" in {
      val args = Array("parse", "-i", inputFile)
      RIDDLC.runMain(args) mustBe 0
    }
    "handle validate" in {
      val args = Array("validate", "-i", inputFile)
      RIDDLC.runMain(args) mustBe 0
    }
    "handle hugo" in {
      val args = Array("hugo", "-i", inputFile.toString, "-o", outputDir )
      RIDDLC.runMain(args) mustBe 0
    }
    "handle hugo from config" in {
      val args = Array("from", conf, "-o", makeSrcDir(path).toString, "-H", "bluk")
      RIDDLC.runMain(args) mustBe 0
      runHugo(path)
      val root = Path.of(output).resolve(path)
      val img = root.resolve("static/images/RBBQ.png")
      Files.exists(img) mustBe true
      // TODO: check themes dir
      // TODO: check config.toml setting values
      // TODO: check options
    }
    "repeat validation of the ReactiveBBQ example" in {
      val args = Array("repeat", validateConf, "1s", "2", "-v", "-n")
      RIDDLC.runMain(args) mustBe 0
    }
  }
}
