package com.reactific.riddl.passes.validate

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.CommonOptions
import com.reactific.riddl.language.Messages.Messages
import com.reactific.riddl.language.parsing.RiddlParserInput

class ActionValidationTest extends ValidatingTest {

  "Morph" should {
    "not error on valid action" in {
      val input = """domain Ignore is {
                    |context Ignore2 is {
                    |entity OfInterest is {
                    |  command MorphIt is {}
                    |  record Data is { field: Integer }
                    |  state First of OfInterest.Data is { ??? }
                    |  state Second of OfInterest.Data is {
                    |    handler only is {
                    |      on command MorphIt {
                    |        then morph entity Ignore.Ignore2.OfInterest to state OfInterest.First
                    |        with !OfInterest.Data(field=3)
                    |      }
                    |    }
                    |  }
                    |}}}
                    |""".stripMargin
      parseAndValidate(input, "Morph Test", CommonOptions.noMinorWarnings) {
        case (_: RootContainer, _: RiddlParserInput, messages: Messages) =>
          messages.hasErrors mustBe false
      }
    }
    "error when value expression does not match state type " in {
      val input = """domain Ignore is {
          |context Ignore2 is {
          |entity OfInterest is {
          |  command MorphIt is {}
          |  record Data is { field: Integer }
          |  state First of OfInterest.Data is { ??? }
          |  state Second of OfInterest.Data is {
          |    handler only is {
          |      on command MorphIt {
          |        then morph entity OfInterest to state First with 3
          |
          |      }
          |    }
          |  }
          |}}}
          |""".stripMargin
      parseAndValidate(input, "Morph Test", CommonOptions.noMinorWarnings, shouldFailOnErrors=false) {
        case (_: RootContainer, _: RiddlParserInput, messages: Messages @unchecked) =>
          messages.hasErrors mustBe true
          val errors = messages.justErrors
          errors.size mustBe 1
          errors.head.format must include(
            "Morph value of type Integer cannot be assigned to State 'First' " +
              "value of type Record 'Data'"
          )
      }
    }

    "ensure we catch states not part of the entity " in {
      val input = """domain Ignore is {
                    |context Ignore2 is {
                    |record Data is { field: Integer }
                    |entity Confusion  is {
                    |  state WrongOne of record Ignore2.Data is {
                    |   handler foo { ??? }
                    | }
                    |}
                    |entity OfInterest is {
                    |  command MorphIt is {}
                    |  state First of Ignore.Ignore2.Data is { ??? }
                    |  state Second of Ignore.Ignore2.Data is {
                    |    handler only is {
                    |      on command MorphIt {
                    |        then morph entity Ignore.Ignore2.Confusion to state Ignore.Ignore2.OfInterest.First
                    |        with !Ignore.Ignore2.Data(field=3)
                    |
                    |      }
                    |    }
                    |  }
                    |}}}
                    |""".stripMargin
      parseAndValidate(input, "Morph Test", CommonOptions.noMinorWarnings, shouldFailOnErrors = false) {
        case (_: RootContainer, _: RiddlParserInput, messages: Messages) =>
          messages.hasErrors mustBe true
          val errors = messages.justErrors
          errors.size mustBe 1
          errors.head.format must include(
            "Entity 'Confusion' does not contain State 'First'"
          )
      }
    }
  }
}
