package com.yoppworks.ossum.riddl.language

import com.yoppworks.ossum.riddl.language.AST.{Container, Definition, RootContainer}

import java.io.File
import java.nio.file.Path

class TranslatorTest extends ValidatingTest {

  val validatingOptions: ValidatingOptions = ValidatingOptions(
    showWarnings = false,
    showMissingWarnings = false,
    showStyleWarnings = false
  )

  case class TestTranslatingOptions(
    projectName: Option[String] = None,
    outputPath: Option[Path] = None,
    inputPath: Option[Path] = None,
  ) extends TranslatingOptions

  case class TestTranslatorState(options: TestTranslatingOptions) extends TranslatorState {
    override def generatedFiles: Seq[File] = Seq.empty[File]

    override def addFile(file: File): TranslatorState = this
  }

  class TestTranslator extends Translator[TestTranslatingOptions] {
    val defaultOptions: TestTranslatingOptions = TestTranslatingOptions()

    override def translateImpl(
      root: RootContainer,
      log: Logger,
      commonOptions: CommonOptions,
      options: TestTranslatingOptions,
    ): Seq[File] = {
      val state = TestTranslatorState(options)

      val parents = scala.collection.mutable.Stack.empty[Container[Definition]]
      Folding.foldLeft(state, parents)(root) { case (state, definition, stack) =>
        log.info(stack.reverse.mkString(".") + "." + definition.id.format)
        state
      }
      Seq.empty[File]
    }
  }

  val directory = "examples/src/riddl/"
  val output = "examples/target/translator/"
  val roots = Map("Reactive BBQ" -> "ReactiveBBQ/ReactiveBBQ.riddl", "DokN" -> "dokn/dokn.riddl")

  "Translator" should {
    for { (name, fileName) <- roots } {
      s"translate $name" in {
        val tt = new TestTranslator
        val logger = StringLogger()
        val inputPath = Path.of(directory).resolve(fileName)
        val options = TestTranslatingOptions(
          outputPath = Some(Path.of(s"language/target/translator-test").resolve(fileName)),
        )
        val files = tt.parseValidateTranslate(
          inputPath,
          logger,
          CommonOptions(),
          validatingOptions,
          options)
        files mustBe empty
      }
    }
  }
}
