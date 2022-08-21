package com.reactific.riddl.translator.hugo

class HugoTranslatorTest extends HugoTranslateExamplesBase {

  val output: String = "hugo-translator/target/translator/"
  val roots = Map("Reactive BBQ" -> s"ReactiveBBQ/ReactiveBBQ.riddl")

  "HugoTranslator" should {
    for { (name, fileName) <- roots } {
      s"parse, validate, and translate $name" in {
        checkExamples(name, fileName)
      }
    }
  }
}
