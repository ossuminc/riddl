package com.reactific.riddl.language

/** Unit Tests For Repository */
import com.reactific.riddl.language.AST.Domain
import com.reactific.riddl.language.parsing.RiddlParserInput

/** Tests For Repository */
class RepositoryTest extends ValidatingTest {

  "RepositoryTest" should {
    "perform some tests" in {
      val input = RiddlParserInput(
        """domain foo is {
          |  context bar is {
          |    repository fubar is {
          |      query GetOne is { how: String }
          |      result Reply is { that: String }
          |      command AddThis is { what: String }
          |      handler Only is {
          |        on command AddThis {
          |          then { "add 'what' to the list" }
          |        }
          |        on query GetOne {
          |          then { reply result fubar.Reply(that = "some value") } }
          |        }
          |     }
          |  }
          |}
          |""".stripMargin
      )
      parseAndValidateDomain (input) {
        case (domain: Domain, _: RiddlParserInput, msgs: Messages.Messages) =>
          domain mustNot be(empty)
          domain.contexts.headOption match {
            case Some(context) =>
              context.repositories mustNot be(empty)
              if (msgs.nonEmpty) { info(msgs.format) }
              msgs.isOnlyIgnorable mustBe true
              succeed
            case _ =>
              fail("Did not parse a context!")
          }

      }
    }
  }
}
