package com.ossuminc.riddl.codify

import com.ossuminc.riddl.language.CommonOptions
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.language.Messages.*
import com.ossuminc.riddl.testkit.ValidatingTest

import java.nio.file.Path

class CodifyPassTest extends ValidatingTest {

  def input(path: String): RiddlParserInput = {
    RiddlParserInput(Path.of("codify/src/test/input/", path))
  }

  "CodifyPass" must {

    val commonOptions = CommonOptions()

    "run" in {
      val rpi = input("VendingMachine.riddl")
      parseTopLevelDomains(rpi) match {
        case Left(messages) =>
          val errors = messages.justErrors
          if errors.nonEmpty then fail(errors.format)
          info(messages.justWarnings.format)
          succeed
        case Right(root) =>
          runStandardPasses(root, commonOptions) match
            case Left(messages) =>
              val errors = messages.justErrors
              if errors.nonEmpty then fail(errors.format)
              info(messages.justWarnings.format)
              succeed
            case Right(passesResult) =>
      }
    }
  }

}
