package com.reactific.riddl.language

import com.reactific.riddl.language.AST.*
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
                    |  state First of ^Data is { ??? }
                    |  state Second of ^Data is {
                    |    handler only is {
                    |      on command MorphIt {
                    |        then morph entity OfInterest to state First
                    |        with !^^^Data(field=3)
                    |      }
                    |    }
                    |  }
                    |}}}
                    |""".stripMargin
      parseAndValidate(input, "Morph Test") {
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
          |  state First of ^Data is { ??? }
          |  state Second of ^Data is {
          |    handler only is {
          |      on command MorphIt {
          |        then morph entity OfInterest to state First with 3
          |
          |      }
          |    }
          |  }
          |}}}
          |""".stripMargin
      parseAndValidate(input, "Morph Test") {
        case (_: RootContainer, _: RiddlParserInput, messages: Messages) =>
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
                    |  state WrongOne of record ^^Data is {
                    |   handler foo { ??? }
                    | }
                    |}
                    |entity OfInterest is {
                    |  command MorphIt is {}
                    |  state First of ^^Data is { ??? }
                    |  state Second of ^^Data is {
                    |    handler only is {
                    |      on command MorphIt {
                    |        then morph entity Confusion to state First
                    |        with !^^^^Data(field=3)
                    |
                    |      }
                    |    }
                    |  }
                    |}}}
                    |""".stripMargin
      parseAndValidate(input, "Morph Test") {
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
