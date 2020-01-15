package com.yoppworks.ossum.riddl.language

import com.yoppworks.ossum.riddl.language.AST.Domain
import com.yoppworks.ossum.riddl.language.AST.Identifier
import com.yoppworks.ossum.riddl.language.AST.Location
import com.yoppworks.ossum.riddl.language.AST.RootContainer
import com.yoppworks.ossum.riddl.language.AST.Topic
import com.yoppworks.ossum.riddl.language.Validation.ValidationMessage
import com.yoppworks.ossum.riddl.language.Validation.ValidationState
import com.yoppworks.ossum.riddl.language.Validation.Warning
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.must

class ValidationTest extends AnyWordSpec with must.Matchers {
  "ValidationMessage#format" should {
    "produce a correct string" in {
      ValidationMessage(Location(1, 2), "the_message", Warning)
        .format("the_source") mustBe
        s"Warning: the_source(1:2): the_message"
    }

    "ValidationState" should {
      "parentOf" should {
        "find the parent of an existant child" in {
          val topic = Topic(Location(), Identifier(Location(), "bar"))
          val domain = Domain(
            Location(),
            Identifier(Location(), "foo"),
            topics = topic :: Nil
          )

          ValidationState(SymbolTable(domain)).parentOf(topic) mustBe domain
        }
        "not find the parent of a non-existant child" in {
          val topic = Topic(Location(), Identifier(Location(), "bar"))
          val domain =
            Domain(Location(), Identifier(Location(), "foo"), topics = Nil)

          ValidationState(SymbolTable(domain))
            .parentOf(topic) mustBe RootContainer.empty
        }
      }
    }
  }

}
