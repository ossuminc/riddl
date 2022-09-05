package com.reactific.riddl.language

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.Messages.*
import com.reactific.riddl.language.Validation.ValidationState
import com.reactific.riddl.language.ast.Location
import com.reactific.riddl.language.parsing.RiddlParserInput
import org.scalatest.matchers.must
import org.scalatest.wordspec.AnyWordSpec

class ValidationTest extends AnyWordSpec with must.Matchers {
  "ValidationMessage#format" should {
    "produce a correct string" in {
      val msg = Message(
        Location(1, 2, RiddlParserInput.empty),
        "the_message",
        Warning
      )
      msg.format mustBe s"Warning: empty(1:2): the_message"
    }
    "compare based on locations" in {
      val v1 =
        Message(Location(1, 2, "the_source"), "the_message", Warning)
      val v2 =
        Message(Location(2, 3, "the_source"), "the_message", Warning)
      v1 < v2 mustBe true
      v1 == v1 mustBe true
    }
  }

  "ValidationState" should {
    "parentOf" should {
      "find the parent of an existent child" in {
        val aType =
          Type(Location(), Identifier(Location(), "bar"), Strng(Location()))
        val domain = Domain(
          Location(),
          Identifier(Location(), "foo"),
          types = aType :: Nil
        )

        ValidationState(SymbolTable(domain)).parentOf(aType) mustBe domain
      }
      "not find the parent of a non-existent child" in {
        val aType =
          Type(Location(), Identifier(Location(), "bar"), Strng(Location()))
        val domain =
          Domain(Location(), Identifier(Location(), "foo"), types = Nil)

        ValidationState(SymbolTable(domain)).parentOf(aType) mustBe
          RootContainer.empty
      }
      "checkNonEmpty" in {
        ValidationState(SymbolTable(RootContainer.empty))
          .checkNonEmpty(Nil, "foo", RootContainer.empty).messages mustBe
          List(Message(
            Location(1, 1, RiddlParserInput.empty),
            "foo in Root should not be empty",
            Error
          ))
        ValidationState(SymbolTable(RootContainer.empty))
          .checkNonEmpty(List(1, 2, 3), "foo", RootContainer.empty)
          .messages mustBe Nil
      }
      "checkOptions" in {
        ValidationState(SymbolTable(RootContainer.empty)).checkOptions(
          List(
            EntityIsAggregate(Location()),
            EntityTransient(Location()),
            EntityIsAggregate(Location())
          ),
          Location()
        ).messages mustBe List(Message(
          Location(),
          "Options should not be repeated",
          Error
        ))
        case class IntOption(loc: Location, name: String) extends OptionValue
        ValidationState(SymbolTable(RootContainer.empty)).checkOptions(
          List(
            IntOption(1 -> 1, "One"),
            IntOption(2 -> 2, "Two"),
            IntOption(3 -> 3, "Three")
          ),
          Location()
        ).messages mustBe Nil
      }
    }
  }
}
