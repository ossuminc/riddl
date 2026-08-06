/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.Messages.*
import com.ossuminc.riddl.utils.{CommonOptions, pc}

import org.scalatest.TestData

/** A57: `on other as x [: <envelope>]`.
  *
  * The ascription is an OPTIONAL restatement of `option message_envelope`, never an override, so
  * the rules are: a binding needs an envelope in scope, and an ascription must agree with the one
  * in scope. Every rule here is paired with the case that must STAY clean, because an error check
  * that fires on everything is as useless as one that never fires.
  */
class OnOtherEnvelopeBindingTest extends AbstractValidatingTest {

  /** `clause` goes in a handler; `ctxOpts` is appended to the context's `with` block. */
  private def model(clause: String, ctxOpts: String): String =
    s"""domain D is {
       |  context C is {
       |    command Ping is { note: String }
       |    entity E is {
       |      handler H is {
       |        on command D.C.Ping is { do "handle" }
       |        $clause
       |      }
       |    }
       |  }$ctxOpts
       |}
       |""".stripMargin

  private val withEnvelope = """ with { option message_envelope("Riddl.Envelope") }"""

  private def errorsOf(msgs: Messages): Seq[String] = msgs.filter(_.kind == Error).map(_.message)

  "A57 on-other envelope binding" should {

    "accept a bare binding when an envelope is in scope" in { (td: TestData) =>
      val src = model("""on other as env is { do "log it" }""", withEnvelope)
      parseAndValidate(src, td.name, shouldFailOnErrors = false) { case (_, _, msgs: Messages) =>
        errorsOf(msgs) mustBe empty
      }
    }

    "accept an ascription that agrees with the option" in { (td: TestData) =>
      val src = model("""on other as env: Riddl.Envelope is { do "log it" }""", withEnvelope)
      parseAndValidate(src, td.name, shouldFailOnErrors = false) { case (_, _, msgs: Messages) =>
        errorsOf(msgs) mustBe empty
      }
    }

    "leave a plain `on other` alone, with or without the option" in { (td: TestData) =>
      // A57 must not have changed what `on other` meant before it existed.
      val bare = model("""on other is { do "ignore" }""", "")
      parseAndValidate(bare, td.name, shouldFailOnErrors = false) { case (_, _, msgs: Messages) =>
        errorsOf(msgs) mustBe empty
      }
      val opted = model("""on other is { do "ignore" }""", withEnvelope)
      parseAndValidate(opted, td.name, shouldFailOnErrors = false) { case (_, _, msgs: Messages) =>
        errorsOf(msgs) mustBe empty
      }
    }

    "reject a binding when no envelope is in scope" in { (td: TestData) =>
      val src = model("""on other as env is { do "log it" }""", "")
      parseAndValidate(src, td.name, shouldFailOnErrors = false) { case (_, _, msgs: Messages) =>
        val errs = errorsOf(msgs)
        errs.size mustBe 1
        errs.head must include("has no envelope to bind")
        errs.head must include("'env'")
      }
    }

    "reject an ascription when no envelope is in scope" in { (td: TestData) =>
      val src = model("""on other as env: Riddl.Envelope is { do "log it" }""", "")
      parseAndValidate(src, td.name, shouldFailOnErrors = false) { case (_, _, msgs: Messages) =>
        val errs = errorsOf(msgs)
        errs.exists(_.contains("no 'option message_envelope' is in scope")) mustBe true
      }
    }

    "reject an ascription that contradicts the option" in { (td: TestData) =>
      val src =
        model("""on other as env: Riddl.GeneratorError is { do "log it" }""", withEnvelope)
      parseAndValidate(src, td.name, shouldFailOnErrors = false) { case (_, _, msgs: Messages) =>
        val errs = errorsOf(msgs)
        errs.size mustBe 1
        errs.head must include("Riddl.GeneratorError")
        errs.head must include("Riddl.Envelope")
        errs.head must include("does not override it")
      }
    }

    "inherit the option from an enclosing scope rather than requiring it on the handler" in {
      (td: TestData) =>
        // The whole point of scope inheritance: declared on the DOMAIN, it still reaches a clause
        // two levels down. If this ever fails, `envelopeInScope` has stopped walking the chain.
        val src =
          """domain D is {
            |  context C is {
            |    command Ping is { note: String }
            |    entity E is {
            |      handler H is {
            |        on command D.C.Ping is { do "handle" }
            |        on other as env is { do "log it" }
            |      }
            |    }
            |  }
            |} with { option message_envelope("Riddl.Envelope") }
            |""".stripMargin
        parseAndValidate(src, td.name, shouldFailOnErrors = false) { case (_, _, msgs: Messages) =>
          errorsOf(msgs) mustBe empty
        }
    }
  }
}
