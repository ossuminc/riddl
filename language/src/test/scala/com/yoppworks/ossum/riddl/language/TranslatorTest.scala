package com.yoppworks.ossum.riddl.language

import com.yoppworks.ossum.riddl.language.AST.{Container, Definition}
import pureconfig.generic.auto.*
import pureconfig.{ConfigReader, ConfigSource}

import java.io.File
import java.nio.file.Path

class TranslatorTest extends ValidatingTest {

  case class TestTranslatorConfig(
    showTimes: Boolean = true,
    showWarnings: Boolean = true,
    showMissingWarnings: Boolean = true,
    showStyleWarnings: Boolean = true,
    inputPath: Option[Path] = None
  ) extends TranslatorConfiguration

  case class TestTranslatorState(config: TestTranslatorConfig) extends TranslatorState {
    override def generatedFiles: Seq[File] = Seq.empty[File]

    override def addFile(file: File): TranslatorState = ???
  }

  class TestTranslator extends Translator[TestTranslatorConfig] {
    val defaultConfig: TestTranslatorConfig = TestTranslatorConfig()

    override def loadConfig(path: Path): ConfigReader.Result[TestTranslatorConfig] = {
      ConfigSource.file(path).load[TestTranslatorConfig]
    }

    override def translate(
      root: AST.RootContainer,
      outputRoot: Option[Path],
      logger: Riddl.Logger,
      config: TestTranslatorConfig
    ): Seq[File] = {
      val state = TestTranslatorState(config)
      val parents = scala.collection.mutable.Stack.empty[Container[Definition]]
      Folding.foldLeft(state, parents)(root) {
        case (state, definition, stack) =>
          logger.info(stack.reverse.mkString(".") + "." + definition.id.format)
          state
      }
      Seq.empty[File]
    }
  }

  val logger: Riddl.Logger = Riddl.StringLogger()
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
        val path = Path.of(directory).resolve(fileName)
        val outputRoot = Path.of(s"language/target/translator-test").resolve(fileName)
        tt.parseValidateTranslateFile(path, Some(outputRoot), logger, TestTranslatorConfig())
      }
    }
  }
}
