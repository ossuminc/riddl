package com.reactific.riddl.translator.hugo
import com.reactific.riddl.testkit.RunCommandSpecBase

class HugoCommandTest extends RunCommandSpecBase {

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
      // runHugo(path)
      // val root = Path.of(output).resolve(path)
      // val img = root.resolve("static/images/RBBQ.png")
      // Files.exists(img) mustBe true
    }
  }
}
