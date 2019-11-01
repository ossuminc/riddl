package com.yoppworks.ossum.riddl.language

import com.yoppworks.ossum.riddl.language.AST.Domain
import com.yoppworks.ossum.riddl.language.AST.Entity
import com.yoppworks.ossum.riddl.language.Validation.ValidationMessage

/** Unit Tests For EntityValidatorTest */
class EntityValidatorTest extends ValidatingTest {

  "EntityValidator" should {
    "catch missing things" in {
      val input = "entity Hamburger is { state is {field:  SomeType } }"
      parseAndValidate[Entity](input) {
        case (model: Entity, _: Seq[ValidationMessage]) =>
          val msgs = Validation.validate(model, Validation.defaultOptions)
          msgs.size mustEqual 3
          msgs.exists(_.message.contains("is not defined")) mustBe true
          msgs.exists(_.message.contains("entity must consume a topic")) mustBe true
          msgs.exists(_.message.contains("should have explanations")) mustBe true
      }
    }
    "error on persistent entity with no event producer" in {
      val input =
        """
          |domain foo {
          |topic EntityChannel {
          |  commands { Foo is String yields event bar }
          |  events { bar is Number } queries {} results {}
          |}
          |context bar {
          |  entity Hamburger  is {
          |    options (aggregate persistent)
          |    state { field: SomeType }
          |    consumer foo for topic EntityChannel
          |  }
          |}
          |}
          |""".stripMargin
      parseAndValidate[Domain](input) {
        case (_: Domain, msgs: Seq[ValidationMessage]) =>
          val errors = msgs.filter(_.kind.isError)
          errors mustNot be(empty)
          msgs.exists { _.message.contains("has only empty topic") } mustBe true
      }
    }
  }
}
