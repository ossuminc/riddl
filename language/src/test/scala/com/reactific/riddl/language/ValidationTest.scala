package com.reactific.riddl.language

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.Validation.*
import org.scalatest.matchers.must
import org.scalatest.wordspec.AnyWordSpec

class ValidationTest extends AnyWordSpec with must.Matchers {
  "ValidationMessage#format" should {
    "produce a correct string" in {
      ValidationMessage(Location(1, 2, "the_source"), "the_message", Warning)
        .format mustBe s"Warning: the_source(1:2): the_message"
    }
    "compare based on locations" in {
      val v1 =
        ValidationMessage(Location(1, 2, "the_source"), "the_message", Warning)
      val v2 =
        ValidationMessage(Location(2, 3, "the_source"), "the_message", Warning)
      v1 < v2 mustBe (true)
      v1 == v1 mustBe (true)
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
          List(ValidationMessage(
            Location(0, 0, "Root"),
            "foo in Root should not be empty",
            Validation.Error
          ))
        ValidationState(SymbolTable(RootContainer.empty))
          .checkNonEmpty(List(1, 2, 3), "foo", RootContainer.empty)
          .messages mustBe Nil
      }
      "checkOptions" in {
        ValidationState(SymbolTable(RootContainer.empty)).checkOptions(
          List(
            EntityAggregate(Location()),
            EntityTransient(Location()),
            EntityAggregate(Location())
          ),
          Location()
        ).messages mustBe List(ValidationMessage(
          Location(),
          "Options should not be repeated",
          Validation.Error
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

  "ValidationMessageKind" should {
    "have correct field values" in {
      MissingWarning.isWarning mustBe true
      MissingWarning.isError mustBe false
      MissingWarning.isSevereError mustBe false
      MissingWarning.toString mustBe "Missing"

      StyleWarning.isWarning mustBe true
      StyleWarning.isError mustBe false
      StyleWarning.isSevereError mustBe false
      StyleWarning.toString mustBe "Style"

      Warning.isWarning mustBe true
      Warning.isError mustBe false
      Warning.isSevereError mustBe false
      Warning.toString mustBe "Warning"

      Validation.Error.isWarning mustBe false
      Validation.Error.isError mustBe true
      Validation.Error.isSevereError mustBe false
      Validation.Error.toString mustBe "Error"

      SevereError.isWarning mustBe false
      SevereError.isError mustBe true
      SevereError.isSevereError mustBe true
      SevereError.toString mustBe "Severe"
    }
  }

}
