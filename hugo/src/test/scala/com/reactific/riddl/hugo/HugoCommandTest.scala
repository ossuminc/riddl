package com.reactific.riddl.hugo

import com.reactific.riddl.testkit.RunCommandSpecBase
import org.scalatest.wordspec.AnyWordSpec

class HugoCommandTest extends RunCommandSpecBase  {

  val inputFile = "testkit/src/test/input/rbbq.riddl"
  val hugoConfig = "testkit/src/test/input/hugo.conf"
  val validateConfig = "testkit/src/test/input/validate.conf"
  val outputDir: String => String =
    (name: String) => s"riddlc/target/test/$name"

  "HugoCommand" should {
    "handle hugo" in {
      val args = Seq(
        "--quiet",
        "--suppress-missing-warnings",
        "--suppress-style-warnings",
        "hugo",
        inputFile,
        "-o",
        outputDir("hugo")
      )
      runWith(args)
    }
    "handle hugo from config" in {
      val args = Seq(
        "--verbose",
        "--suppress-missing-warnings",
        "--suppress-style-warnings",
        "from",
        hugoConfig,
        "hugo"
      )
      runWith(args)
    }
  }
}
