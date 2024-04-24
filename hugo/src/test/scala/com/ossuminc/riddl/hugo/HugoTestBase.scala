package com.ossuminc.riddl.hugo

import com.ossuminc.riddl.hugo.themes.GeekDocWriter
import com.ossuminc.riddl.hugo.writers.MarkdownWriter
import com.ossuminc.riddl.language.{CommonOptions, Messages}
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.language.AST.Root
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, StringParserInput, TopLevelParser}
import com.ossuminc.riddl.passes.{Pass, PassInput, PassesOutput, PassesResult}
import com.ossuminc.riddl.passes.validate.ValidatingTest
import org.scalatest.Assertion

import java.nio.file.Path

abstract class HugoTestBase extends ValidatingTest {

  def runHugoOn(input: String): Either[Messages, (PassesResult, Root, RiddlParserInput)] = {
    val rpi = RiddlParserInput(input, "hugo Test")
    val commonOptions = CommonOptions.noMinorWarnings
    val options = HugoPass.Options(Some(Path.of(".")), Some(Path.of("target/hugo-test")))
    val passes = HugoPass.getPasses(options)

    TopLevelParser.parseString(input) match {
      case Left(errors) =>
        fail(errors.format)
      case Right(root) =>
        val passInput = PassInput(root, commonOptions)
        val result = Pass.runThesePasses(passInput, passes)
        if result.messages.hasErrors then Left(result.messages)
        else Right((result, root, rpi))
    }
  }

  def runHugoAndAssert(input: String)(
    checker: (PassesResult, Root, RiddlParserInput) => Assertion
  ): Assertion = {
    runHugoOn(input) match {
      case Left(messages) =>
        fail(messages.format)
      case Right((passesResult, root, rpi)) =>
        checker(passesResult, root, rpi)
    }
  }

  def makeMDW(filePath: Path, passesResult: PassesResult): MarkdownWriter = {
    GeekDocWriter(filePath, passesResult.input, passesResult.outputs, HugoPass.Options(), CommonOptions())
  }

  def makeMDWFor(input: String): (PassesResult, Root, MarkdownWriter) = {
    runHugoOn(input) match {
      case Left(messages) =>
        fail(messages.format)
      case Right((passesResult: PassesResult, root: Root, rpi: RiddlParserInput)) =>
        val filePath = rpi.root.toPath
        passesResult.outputOf[HugoOutput](HugoPass.name) match
          case None => fail("No output from hugo pass")
          case Some(_) =>
            val mdw = makeMDW(filePath, passesResult)
            (passesResult, root, mdw)
    }
  }
}
