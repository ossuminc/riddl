package com.reactific.riddl.language

import com.reactific.riddl.language.AST.Definition
import com.reactific.riddl.language.Messages.Messages
import com.reactific.riddl.language.Validation.Result
import com.reactific.riddl.utils.Logger
import com.reactific.riddl.utils.OutputFile
import com.reactific.riddl.utils.StringLogger

import java.nio.file.Path

class TranslatorTest extends ValidatingTest {

  val validatingOptions: CommonOptions = CommonOptions(
    showWarnings = false,
    showMissingWarnings = false,
    showStyleWarnings = false
  )

  case class TestTranslatingOptions(
    projectName: Option[String] = None,
    outputDir: Option[Path] = None,
    inputFile: Option[Path] = None)
      extends TranslatingOptions

  case class TestTranslatorState(options: TestTranslatingOptions)
      extends TranslatingState[OutputFile] with TranslationResult {
    override def generatedFiles: Seq[Path] = Seq.empty[Path]
  }

  class TestTranslator extends Translator[TestTranslatingOptions] {
    val defaultOptions: TestTranslatingOptions = TestTranslatingOptions()

    override def translate(
      result: Result,
      log: Logger,
      commonOptions: CommonOptions,
      options: TestTranslatingOptions
    ): Either[Messages, TestTranslatorState] = {
      val state = TestTranslatorState(options)
      val parents = scala.collection.mutable.Stack.empty[Definition]
      Right(Folding.foldLeftWithStack(state, parents)(result.root) {
        case (state, _ /*definition*/, _ /*stack*/ ) =>
          // log.info(stack.reverse.mkString(".") + "." + definition.id.format)
          state
      })
    }
  }

  val directory = "examples/src/riddl/"
  val output = "examples/target/translator/"
  val roots = Map("Reactive BBQ" -> "ReactiveBBQ/ReactiveBBQ.riddl")

  "Translator" should {
    pending // this needs to move to riddl-examples repository
    for { (name, fileName) <- roots } {
      s"translate $name" in {
        val tt = new TestTranslator
        val logger = StringLogger()
        val inputPath = Path.of(directory).resolve(fileName)
        val options = TestTranslatingOptions(outputDir =
          Some(Path.of(s"language/target/translator-test").resolve(fileName))
        )
        tt.parseValidateTranslate(
          inputPath,
          logger,
          CommonOptions(showStyleWarnings = false, showMissingWarnings = false),
          options
        ) match {
          case Right(_)       => succeed
          case Left(messages) => fail(messages.format)
        }
      }
    }
  }
}
