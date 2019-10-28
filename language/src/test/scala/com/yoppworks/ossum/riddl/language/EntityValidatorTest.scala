package com.yoppworks.ossum.riddl.language

import com.yoppworks.ossum.riddl.language.AST.DomainDef
import com.yoppworks.ossum.riddl.language.AST.EntityDef
import com.yoppworks.ossum.riddl.language.Validation.ValidationMessage

/** Unit Tests For EntityValidatorTest */
class EntityValidatorTest extends ValidatingTest {

  "EntityValidator" should {
    "catch missing things" in {
      val input = "entity Hamburger as SomeType is {}"
      parseAndValidate[EntityDef](input) {
        case (model: EntityDef, _: Seq[ValidationMessage]) =>
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
          |  entity Hamburger as SomeType is {
          |    options (aggregate persistent)
          |    consumes topic EntityChannel
          |  }
          |}
          |}
          |""".stripMargin
      parseAndValidate[DomainDef](input) {
        case (_: DomainDef, msgs: Seq[ValidationMessage]) =>
          val errors = msgs.filter(_.kind.isError)
          errors mustNot be(empty)
          errors.exists(
            _.message.contains(
              "no events on a topic"
            )
          ) mustBe true
      }
    }
  }
}
