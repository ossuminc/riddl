package com.ossuminc.riddl.hugo.writers

import com.ossuminc.riddl.hugo.HugoPass
import com.ossuminc.riddl.hugo.themes.GeekDocWriter
import com.ossuminc.riddl.language.{CommonOptions, Messages}
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.passes.{Pass, PassesResult, Riddl}
import com.ossuminc.riddl.utils.TestingBasis
import org.scalatest.Assertion
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.nio.file.Path

class WriterTest extends TestingBasis {

  val base = Path.of("hugo", "src", "test", "input")
  val output = Path.of("hugo", "target", "test", "adaptors")

  def makeMDW(filePath: Path, passesResult: PassesResult): MarkdownWriter = {
    GeekDocWriter(filePath, passesResult.input, passesResult.outputs, HugoPass.Options(), CommonOptions())
  }
  
  def validateRoot(input: RiddlParserInput, options: CommonOptions = CommonOptions())(
    validate: (root: PassesResult) => Assertion
  ) = {
    Riddl.parseAndValidate(input, options) match {
      case Left(messages) => fail(messages.justErrors.format)
      case Right(passesResult: PassesResult) => validate(passesResult)
    }
  }
}
