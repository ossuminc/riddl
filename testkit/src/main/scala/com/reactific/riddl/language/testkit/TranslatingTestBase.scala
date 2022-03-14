package com.reactific.riddl.language.testkit

import com.reactific.riddl.language.*

import java.nio.file.Path

abstract class TranslatingTestBase[OPTS <: TranslatingOptions] extends
  ValidatingTest {

  val commonOptions: CommonOptions = CommonOptions(
    showTimes = true,
    showWarnings = false,
    showMissingWarnings = false,
    showStyleWarnings = false
  )
  val directory = "examples/src/riddl/"
  val output = "examples/target/translator/"
  val roots = Map("Reactive BBQ" -> "ReactiveBBQ/ReactiveBBQ.riddl", "DokN" -> "dokn/dokn.riddl")
  val logger: Logger = StringLogger()

  def  makeInputFile(partialFilePath: String): Path = {
    Path.of(directory).resolve(partialFilePath)
  }

  def makeTranslatorOptions(fileName: String): OPTS

  def getTranslator : Translator[OPTS]

  def runTests(testName: String): Unit = {
    testName should {
      for { (name, fileName) <- roots } {
        s"translate $name" in {
          val options = makeTranslatorOptions(fileName)
          val translator = getTranslator
          translator.parseValidateTranslate(
            logger,
            commonOptions,
            options
          )
          succeed
        }
      }
    }
  }
}
