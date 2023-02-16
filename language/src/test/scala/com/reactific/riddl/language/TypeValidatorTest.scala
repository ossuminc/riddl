/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.language

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.Messages
import com.reactific.riddl.language.Messages.*

/** Unit Tests For TypeValidationTest */
class TypeValidatorTest extends ValidatingTest {

  "TypeValidator" should {
    "ensure type names start with capital letter" in {
      parseAndValidateDomain("""domain foo is {
                               |type bar is String
                               |}
                               |""".stripMargin) {
        case (_: Domain, _, msgs: Seq[Message]) =>
          if (msgs.isEmpty) fail("Type 'bar' should have generated warning")
          else if (
            msgs.map(_.message).exists(_.contains("should start with"))
          ) { succeed }
          else { fail("No such message") }
      }
    }
    "identify undefined type references" in {
      parseAndValidateDomain("""
                               |domain foo is {
                               |// type Foo is Number
                               |// type Bar is Integer
                               |type Rename is Bar
                               |type OneOrMore is many Bar
                               |type ZeroOrMore is many optional Bar
                               |type Optional is optional Bar
                               |type Aggregate is {a: Bar, b: Foo}
                               |type Alternation is one of { Bar or Foo }
                               |type Order is Id(Bar)
                               |}
                               |""".stripMargin) {
        case (_: Domain, _, msgsAndWarnings: Messages.Messages) =>
          val errors = msgsAndWarnings.filter(_.kind == Error)
          assert(errors.size == 9, "Should have 9 errors")
          assert(
            errors.forall(m => m.message.contains("not resolved")),
            "not resolved"
          )
          assert(
            errors.count(_.message.contains("refer to a Type")) == 8,
            "refer to a Type"
          )
          assert(
            errors.count(_.message.contains("refer to an Entity")) == 1,
            "refer to a Type"
          )
      }
    }
    "allow ??? in aggregate bodies without warning" in {
      parseAndValidateDomain("""domain foo {
                               |type Empty is { ??? } explained as "empty"
                               |} explained as "nothing"
                               |""".stripMargin) {
        case (_: Domain, _, msgs: Messages) =>
          msgs mustNot be(empty)
          msgs.size mustBe (3)
          msgs.filter(_.kind == Messages.Warning).head.format must include("is unused")
      }
    }

    "identify when pattern type does not refer to a valid pattern" in {
      parseAndValidateDomain("""
                               |domain foo is {
                               |type pat is Pattern("[")
                               |}
                               |""".stripMargin) {
        case (_: Domain, _, msgs: Messages) =>
          assertValidationMessage(msgs, Error, "Unclosed character class")
      }
    }

    "identify when unique ID types reference something other than an entity" in {
      parseAndValidateDomain("""
                               |domain foo is {
                               |context TypeTest is { ??? }
                               |type Order is Id(TypeTest)
                               |}
                               |""".stripMargin) {
        case (_: Domain, _, msgs: Messages) => assertValidationMessage(
            msgs,
            Error,
            "'TypeTest' was expected to be an Entity but is a Context."
          )
      }
    }
  }
}
