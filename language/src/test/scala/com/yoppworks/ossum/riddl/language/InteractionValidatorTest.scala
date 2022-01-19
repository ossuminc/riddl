package com.yoppworks.ossum.riddl.language

import com.yoppworks.ossum.riddl.language.AST.*

class InteractionValidatorTest extends ValidatingTest {

  "Interaction" should {
    "give error for interactions without actions" in {
      val input = """domain dodo is { context foo is {
                    |  interaction dosomething is {
                    |   ???
                    |  }
                    |} }
                    |""".stripMargin
      parseAndValidate[Domain](input) { (domain, msgs) =>
        assertValidationMessage(
          msgs,
          Validation.Error,
          "Actions in Interaction 'dosomething' should not be empty"
        )
        val content = domain.contexts.head
        content.interactions.length mustBe 1
      }
    }
    "allow interaction definitions" in {
      val input = """domain bar is { context foo is {
                    |  entity myLittlePony is {
                    |   ???
                    |  }
                    |  interaction dosomething is {
                    |    message 'perform a command' option is async
                    |      from entity Unicorn
                    |      to entity myLittlePony as command DoAThing
                    |
                    |    message 'handle a thing' option is async
                    |      from entity myLittlePony
                    |      to entity Unicorn as command HandleAThing
                    |  }
                    |}}
                    |""".stripMargin
      parseAndValidate[Domain](input) { (domain, msgs) =>
        msgs.filter(m => m.kind.isError && m.message.startsWith("Interaction 'dosomething'")) mustBe
          empty
        val content = domain.contexts.head
        content.interactions.length mustBe 1
        val interaction = content.interactions.head

        interaction.id.value mustBe "dosomething"
        interaction.actions.length mustBe 2
        assert(interaction.actions.exists {
          case m: MessageAction => m.id.value == "perform a command" &&
              (m.sender match {
                case EntityRef(_, PathIdentifier(_, collection.Seq("Unicorn"))) => true
                case _                                                          => false
              }) &&
              (m.receiver match {
                case EntityRef(_, PathIdentifier(_, collection.Seq("myLittlePony"))) => true
                case _                                                               => false
              }) &&
              (m.message match {
                case CommandRef(_, PathIdentifier(_, collection.Seq("DoAThing"))) => true
                case _                                                            => false
              }) && m.reactions.isEmpty && m.description.isEmpty
          case _ => false
        })
      }
    }
  }
}
