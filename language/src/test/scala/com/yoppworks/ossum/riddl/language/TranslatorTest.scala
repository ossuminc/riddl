package com.yoppworks.ossum.riddl.language

import com.yoppworks.ossum.riddl.language.AST.{Container, Definition}
import com.yoppworks.ossum.riddl.language.Validation.ValidatingOptions
import pureconfig.generic.auto.*
import pureconfig.{ConfigReader, ConfigSource}

import java.io.File
import java.nio.file.Path

class TranslatorTest extends ValidatingTest {

  case class TestTranslatingOptions(
    validatingOptions: ValidatingOptions = ValidatingOptions(
      parsingOptions = ParsingOptions(showTimes = true),
      showWarnings = false,
      showMissingWarnings = false,
      showStyleWarnings = false
    ),
    projectName: Option[String] = None,
    inputPath: Option[Path] = None,
    outputPath: Option[Path] = None,
    configPath: Option[Path] = None,
    logger: Option[Logger] = None,
  ) extends TranslatingOptions

  case class TestTranslatorConfig() extends TranslatorConfiguration

  case class TestTranslatorState(config: TestTranslatorConfig) extends TranslatorState {
    override def generatedFiles: Seq[File] = Seq.empty[File]

    override def addFile(file: File): TranslatorState = this
  }

  class TestTranslator extends Translator[TestTranslatingOptions, TestTranslatorConfig] {
    val defaultConfig: TestTranslatorConfig = TestTranslatorConfig()
    val defaultOptions: TestTranslatingOptions = TestTranslatingOptions()

    override def loadConfig(path: Path): ConfigReader.Result[TestTranslatorConfig] = {
      ConfigSource.file(path).load[TestTranslatorConfig]
    }

    override def translate(
      root: AST.RootContainer,
      options: TestTranslatingOptions,
      config: TestTranslatorConfig
    ): Seq[File] = {
      val state = TestTranslatorState(config)
      val parents = scala.collection.mutable.Stack.empty[Container[Definition]]
      Folding.foldLeft(state, parents)(root) {
        case (state, definition, stack) =>
          options.log.info(stack.reverse.mkString(".") + "." + definition.id.format)
          state
      }
      Seq.empty[File]
    }
  }

  val directory = "examples/src/riddl/"
  val output = "examples/target/translator/"
  val roots = Map(
    "Reactive BBQ" -> "ReactiveBBQ/ReactiveBBQ.riddl",
    "DokN" -> "dokn/dokn.riddl"
  )

  "Translator" should {
    for {(name, fileName) <- roots} {
      s"translate $name" in {
        val tt = new TestTranslator
        val logger = StringLogger()
        val options = TestTranslatingOptions(
          inputPath = Some(Path.of(directory).resolve(fileName)),
          outputPath = Some(Path.of(s"language/target/translator-test").resolve(fileName)),
          logger = Some(logger)
        )
        val files = tt.parseValidateTranslate(options)
        files mustBe empty
      }
    }
  }
}
