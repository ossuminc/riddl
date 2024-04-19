package com.ossuminc.riddl.hugo

import com.ossuminc.riddl.testkit.RunCommandSpecBase

class HugoCommandTest extends RunCommandSpecBase  {

  val inputFile = "hugo/src/test/input/rbbq.riddl"
  val hugoConfig = "hugo/src/test/input/hugo.conf"
  val validateConfig = "hugo/src/test/input/validate.conf"
  val outputDir: String => String = (name: String) => s"hugo/target/test/$name"

  "HugoCommand" should {
    "handle hugo" in {
      val args = Seq(
        "--quiet",
        "--hide-missing-warnings",
        "--hide-style-warnings",
        "hugo",
        inputFile,
        "-o",
        outputDir("hugo"),
        "--hugo-theme-name", "GeekDoc"
      )
      runWith(args)
    }
    "handle hugo from config" in {
      val args = Seq(
        "--verbose",
        "--hide-missing-warnings",
        "--hide-style-warnings",
        "from",
        hugoConfig,
        "hugo"
      )
      runWith(args)
    }
  }
}
