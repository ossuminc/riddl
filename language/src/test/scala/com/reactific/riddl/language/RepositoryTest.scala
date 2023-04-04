package com.reactific.riddl.language

/** Unit Tests For Repository */
import com.reactific.riddl.language.AST.Domain
import com.reactific.riddl.language.parsing.RiddlParserInput
import com.reactific.riddl.language.passes.validation.ValidatingTest

/** Tests For Repository */
class RepositoryTest extends ValidatingTest {

  "RepositoryTest" should {
    "handl a basic definition" in {
      val input = RiddlParserInput(
        """domain foo is {
          |  context bar is {
          |    outlet hereyougo is bar.fubar.Reply
          |    repository fubar is {
          |      query GetOne is { how: String }
          |      result Reply is { that: String }
          |      command AddThis is { what: String }
          |      handler Only is {
          |        on command AddThis {
          |          then { "add 'what' to the list" }
          |        }
          |        on query GetOne {
          |          then {
          |            send result fubar.Reply(that = "some value") to outlet
          |             hereyougo
          |          }
          |        }
          |        }
          |     }
          |  }
          |}
          |""".stripMargin
      )
      parseAndValidateDomain(input) {
        case (domain: Domain, _: RiddlParserInput, msgs: Messages.Messages) =>
          domain mustNot be(empty)
          domain.contexts.headOption match {
            case Some(context) =>
              context.repositories mustNot be(empty)
              if (msgs.nonEmpty) { info(msgs.format) }
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
