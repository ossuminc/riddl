/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.passes.validate

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.Messages
import com.reactific.riddl.language.Messages.*
import com.reactific.riddl.language.parsing.RiddlParserInput

/** Unit Tests For TypeValidationTest */
class TypeValidatorTest extends ValidatingTest {

  "TypeValidator" should {
    "ensure type names start with capital letter" in {
      parseAndValidateDomain("""domain foo is {
                               |type bar is String
                               |}
                               |""".stripMargin) { case (_: Domain, _, msgs: Seq[Message]) =>
        if msgs.isEmpty then fail("Type 'bar' should have generated warning")
        else if msgs.map(_.message).exists(_.contains("should start with"))
        then { succeed }
        else { fail("No such message") }
      }
    }
    "identify undefined type references" in {
      val input = RiddlParserInput(
        """
          |domain foo is {
          |  type Foo is Number
          |  type Bar is Integer
          |  type Rename is Bar
          |  type OneOrMore is many Bar
          |  type ZeroOrMore is many optional Bar
          |  type Optional is optional Bar
          |  type Aggregate is {a: Bar, b: Foo}
          |  type Alternation is one of { Bar or Foo }
          |  type Order is Id(Bar)
          |}
          |""".stripMargin
      )
      parseAndValidateDomain(input, shouldFailOnErrors = false) {
        case (_: Domain, _, msgsAndWarnings: Messages.Messages) =>
          val errors = msgsAndWarnings.justErrors
          errors.size mustBe (1)
          errors.head.message must include("but an Entity was expected")
      }
    }
    "allow ??? in aggregate bodies without warning" in {
      val input = RiddlParserInput(
        """domain foo {
          |type Empty is { ??? } explained as "empty"
          |} explained as "nothing"
          |""".stripMargin
      )
      parseAndValidateDomain(input, shouldFailOnErrors = false) { case (_: Domain, _, msgs: Messages) =>
        msgs mustNot be(empty)
        msgs.size mustBe (3)
        msgs.filter(_.kind == Messages.UsageWarning).head.format must include("is unused")
      }
    }

    "identify when pattern type does not refer to a valid pattern" in {
      val input = RiddlParserInput(
        """
          |domain foo is {
          |type pat is Pattern("[")
          |}
          |""".stripMargin
      )
      parseAndValidateDomain(input, shouldFailOnErrors = false) { case (_: Domain, _, msgs: Messages) =>
        assertValidationMessage(msgs, Error, "Unclosed character class")
      }
    }

    "identify when unique ID types reference something other than an entity" in {
      val input = RiddlParserInput(
        """
          |domain foo is {
          |context TypeTest is { ??? }
          |type Order is Id(TypeTest)
          |}
          |""".stripMargin
      )
      parseAndValidateDomain(input, shouldFailOnErrors = false) { case (_: Domain, _, msgs: Messages) =>
        assertValidationMessage(
          msgs,
          Error,
          "Path 'TypeTest' resolved to Context 'TypeTest' at empty(3:1), in Type 'Order', but an Entity"
        )
      }
    }
  }
}
