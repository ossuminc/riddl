package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.AST.Domain
import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.passes.pc
import com.ossuminc.riddl.utils.CommonOptions 
import org.scalatest.TestData

/** Unit Tests For Repository */
class RepositoryTest extends AbstractValidatingTest {

  "RepositoryTest" should {
    "handle a basic definition" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain foo is {
          |  context bar is {
          |    source itin is { outlet hereyougo is bar.fubar.Reply }
          |    repository fubar is {
          |      query GetOne is { how: String }
          |      result Reply is { that: String }
          |      command AddThis is { what: String }
          |      handler Only is {
          |        on command AddThis {
          |          "add 'what' to the list"
          |        }
          |        on query GetOne {
          |          send result fubar.Reply to outlet hereyougo
          |        }
          |      }
          |     }
          |  }
          |}
          |""".stripMargin,td)
      val options = CommonOptions.noWarnings.copy(showMissingWarnings=false)
      pc.setOptions(options)
      parseAndValidateDomain(input)  {
        case (domain: Domain, _: RiddlParserInput, msgs: Messages.Messages) =>
          domain mustNot be(empty)
          domain.contexts.headOption match {
            case Some(context) =>
              context.repositories mustNot be(empty)
              // info(msgs.format)
              val errors = msgs.justErrors
              errors.size mustBe 0
              msgs.isOnlyWarnings
              succeed
            case _ =>
              fail("Did not parse a context!")
          }

      }
    }
  }
}
