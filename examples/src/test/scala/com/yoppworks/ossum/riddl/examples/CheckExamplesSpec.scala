package com.yoppworks.ossum.riddl.examples

import com.yoppworks.ossum.riddl.translator.hugo.HugoTranslateExamplesBase

import java.nio.file.{Files, Path}

/** Unit Tests To Check Documentation Examples */
class CheckExamplesSpec extends HugoTranslateExamplesBase {

  val output = "examples/target/translator/"
  val roots = Map("Reactive BBQ" -> "ReactiveBBQ/ReactiveBBQ.riddl", "DokN" -> "dokn/dokn.riddl")

  "Examples" should {
    for { (name, path) <- roots } {
      s"parse, validate, and generate $name" in {
        checkExamples(name, path)
        name match {
          case "Reactive BBQ" =>
            val topDirName = Path.of(path).getFileName
            val root = Path.of(output).resolve(topDirName)
            val img = root.resolve("static/images/RBBQ.png")
            Files.exists(img) mustBe true
          case _ =>
        }
      }
    }
  }
}
