package com.ossuminc.riddl.hugo.writers

import com.ossuminc.riddl.hugo.HugoCommand
import com.ossuminc.riddl.hugo.themes.GeekDocTheme
import com.ossuminc.riddl.language.{CommonOptions, Messages}
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.passes.{Pass, PassesResult, Riddl}
import org.scalatest.Assertion
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.nio.file.Path

class WriterTest extends AnyWordSpec with Matchers{

  def makeMDW(filePath: Path, passesResult: PassesResult): MarkdownWriter = {
    val symbols = passesResult.symbols
    val refMap = passesResult.refMap
    val usages = passesResult.usage
    GeekDocTheme(filePath, passesResult.input, passesResult.outputs, HugoCommand.Options())
  }

  def runPasses(input: RiddlParserInput, options:CommonOptions = CommonOptions()): Either[Messages.Messages,PassesResult] = {
    Riddl.parseAndValidate(input, options, shouldFailOnError = true, Pass.standardPasses)
  }

  def validateRoot(input: RiddlParserInput, options:CommonOptions = CommonOptions())(
    validate: (root: PassesResult) => Assertion) = {
    runPasses(input, options) match {
      case Left(messages) =>
        fail(messages.justErrors.format)
      case Right(passesResult: PassesResult) =>
        validate(passesResult)
    }
  }

}
