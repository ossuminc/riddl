package com.yoppworks.ossum.riddl.validator

import com.yoppworks.ossum.riddl.parser.AST.EntityDef
import com.yoppworks.ossum.riddl.parser.ParsingTest
import com.yoppworks.ossum.riddl.validator.Validation.ValidationMessage

/** Unit Tests For EntityValidatorTest */
class EntityValidatorTest extends ValidatingTest {

  "EntityValidator" should {
    "catch missing things" in {
      val input = "entity Hamburger is SomeType {}"
      parseAndValidate[EntityDef](input) {
        case (model: EntityDef, msgs: Seq[ValidationMessage]) =>
          val msgs = Validation.validateEntity(model, Validation.defaultOptions)
          msgs.size mustEqual 3
          msgs.exists(_.message.contains("is not defined")) mustBe true
          msgs.exists(_.message.contains("entity must consume a channel")) mustBe true
          msgs.exists(_.message.contains("should have explanations")) mustBe true
      }
    }
    "error on persistent entity with no event producer" in {
      val input =
        """
          |domain foo {
          |channel entityChannel { commands: Foo
          |      location ~ "channel" ~/ identifier ~ "{" ~
          |        commandRefs ~ eventRefs ~ queryRefs ~ resultRefs ~
          |        "}" ~/ addendum
          |
          |entity Hamburger is SomeType {
          |  options { persistent aggregate }
          |  consumes channel EntityChannel
          |  produces channel EntityChannel
          |  feature AnAspect {
          |    DESCRIPTION { "This is some aspect of the entity" }
          |    BACKGROUND {
          |      Given "Nobody loves me"
          |    }
          |    EXAMPLE foo {
          |      "My Fate"
          |      GIVEN "everybody hates me"
          |      AND "I'm depressed"
          |      WHEN "I go fishing"
          |      THEN "I'll just eat worms"
          |    }
          |  }
          |}
          |""".stripMargin
      pending
    }
  }
}
