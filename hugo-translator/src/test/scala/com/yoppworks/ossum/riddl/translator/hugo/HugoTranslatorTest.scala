package com.yoppworks.ossum.riddl.translator.hugo

class HugoTranslatorTest extends HugoTranslateExamplesBase {

  val output: String = "hugo-translator/target/translator/"
  val roots = Map("Reactive BBQ" -> "ReactiveBBQ/ReactiveBBQ.riddl", "DokN" -> "dokn/dokn.riddl")

  "HugoTranslator" should {
    for { (name, fileName) <- roots } {
      s"parse, validate, and translate $name" in { checkExamples(name, fileName) }
    }
  }
}
