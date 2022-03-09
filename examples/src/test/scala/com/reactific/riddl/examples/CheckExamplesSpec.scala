package com.reactific.riddl.examples

import com.reactific.riddl.translator.hugo.HugoTranslateExamplesBase

import java.nio.file.Files

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
            val root = makeSrcDir(path)
            Files.isDirectory(root)
            val img = root.resolve("static/images/RBBQ.png")
            Files.exists(img) mustBe true
          case _ =>
            succeed
        }
      }
    }
  }
}
