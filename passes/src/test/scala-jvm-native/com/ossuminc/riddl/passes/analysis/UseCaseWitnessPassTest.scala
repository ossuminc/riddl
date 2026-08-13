/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.analysis

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.passes.{Pass, PassInput, PassesResult}
import com.ossuminc.riddl.passes.validate.AbstractValidatingTest
import com.ossuminc.riddl.utils.{pc, ec}

import org.scalatest.*

/** A36 Level 1 — use-case witnessing by parsed structure. */
class UseCaseWitnessPassTest extends AbstractValidatingTest {

  private def runWitnessPass(
    input: String,
    origin: String = "test"
  )(
    check: (UseCaseWitnessOutput, Messages.Messages) => Assertion
  ): Assertion =
    val rpi = RiddlParserInput(input, origin)
    parseValidateAndThen(rpi, shouldFailOnErrors = false) {
      (pr: PassesResult, root: Root, _, msgs: Messages.Messages) =>
        val passInput = PassInput(root)
        val outputs = pr.outputs
        // MessageFlowPass is a required predecessor; run it first so its output is available.
        Pass.runPass[MessageFlowOutput](passInput, outputs, MessageFlowPass(passInput, outputs))
        val pass = UseCaseWitnessPass(passInput, outputs)
        val uwo = Pass.runPass[UseCaseWitnessOutput](passInput, outputs, pass)
        check(uwo, msgs)
    }
  end runWitnessPass

  private def unwitnessed(uwo: UseCaseWitnessOutput): Seq[Messages.Message] =
    uwo.messages.filter(m =>
      m.kind == Messages.CompletenessWarning && m.message.contains("is not witnessed")
    )

  "UseCaseWitnessPass" should {

    "not warn a SendMessage step whose receiver handles the message and is wired" in {
      (td: TestData) =>
        runWitnessPass(
          """domain D is {
            |  user U is "a user"
            |  context Gateway is {
            |    command DoIt is { ??? }
            |    handler GH is {
            |      on command D.Gateway.DoIt is { ??? }
            |    }
            |  }
            |  context App is {
            |    command Start is { ??? }
            |    handler AH is {
            |      on command D.App.Start is {
            |        tell command D.Gateway.DoIt to context D.Gateway
            |      }
            |    }
            |  }
            |  epic E is {
            |    user U wants to "do" so that "done"
            |    case C is {
            |      user U wants to "do" so that "done"
            |      step send command D.Gateway.DoIt from context D.App to context D.Gateway
            |    }
            |  }
            |}
            |""".stripMargin
        ) { (uwo, _) =>
          unwitnessed(uwo) mustBe empty
        }
    }

    "warn a SendMessage step whose receiver has no on-clause for the message" in { (td: TestData) =>
      runWitnessPass(
        """domain D is {
            |  user U is "a user"
            |  command DoIt is { ??? }
            |  context App is { ??? }
            |  context Gateway is { ??? }
            |  epic E is {
            |    user U wants to "do" so that "done"
            |    case C is {
            |      user U wants to "do" so that "done"
            |      step send command D.DoIt from context D.App to context D.Gateway
            |    }
            |  }
            |}
            |""".stripMargin
      ) { (uwo, _) =>
        val warns = unwitnessed(uwo)
        warns must not be empty
        warns.exists(_.message.contains("no 'on")) mustBe true
      }
    }

    "not warn a ShowOutput step when a put statement produces the output" in { (td: TestData) =>
      runWitnessPass(
        """domain D is {
          |  user U is "a user"
          |  application context UI is {
          |    record Greeting is { text: String }
          |    command Refresh is { ??? }
          |    group Main is {
          |      form Entry acquires type Greeting
          |      output Panel presents type Greeting
          |    }
          |    handler Screen is {
          |      on command D.UI.Refresh is {
          |        put get from input D.UI.Main.Entry to output D.UI.Main.Panel
          |      }
          |    }
          |  }
          |  epic E is {
          |    user U wants to "see" so that "known"
          |    case C is {
          |      user U wants to "see" so that "known"
          |      step show output D.UI.Main.Panel to user D.U
          |    }
          |  }
          |}
          |""".stripMargin
      ) { (uwo, _) =>
        unwitnessed(uwo) mustBe empty
      }
    }

    "warn a ShowOutput step when no put statement produces the output" in { (td: TestData) =>
      runWitnessPass(
        """domain D is {
          |  user U is "a user"
          |  application context UI is {
          |    record Greeting is { text: String }
          |    group Main is {
          |      output Panel presents type Greeting
          |    }
          |  }
          |  epic E is {
          |    user U wants to "see" so that "known"
          |    case C is {
          |      user U wants to "see" so that "known"
          |      step show output D.UI.Main.Panel to user D.U
          |    }
          |  }
          |}
          |""".stripMargin
      ) { (uwo, _) =>
        val warns = unwitnessed(uwo)
        warns must not be empty
        warns.exists(_.message.contains("Panel")) mustBe true
      }
    }

    "not warn a TakeInput step whose input is read by a get-from-input" in { (td: TestData) =>
      runWitnessPass(
        """domain D is {
          |  user U is "a user"
          |  application context UI is {
          |    record Greeting is { text: String }
          |    command Refresh is { ??? }
          |    group Main is {
          |      form Entry acquires type Greeting
          |      output Panel presents type Greeting
          |    }
          |    handler Screen is {
          |      on command D.UI.Refresh is {
          |        put get from input D.UI.Main.Entry to output D.UI.Main.Panel
          |      }
          |    }
          |  }
          |  epic E is {
          |    user U wants to "give" so that "taken"
          |    case C is {
          |      user U wants to "give" so that "taken"
          |      step take input D.UI.Main.Entry from user D.U
          |    }
          |  }
          |}
          |""".stripMargin
      ) { (uwo, _) =>
        unwitnessed(uwo) mustBe empty
      }
    }

    // Regression for a total-dispatch gap found reviewing Task 5 (processor-instance identity):
    // `collectGetInputRefs` enumerates statement kinds whose VALUES it recurses into looking for
    // `get from input` refs, and had no `TerminateStatement` arm even though `terminate`'s `args`
    // is the same `ConstructorArg` shape `Constructor.args` already walks. Without the arm, an
    // input read only through a `terminate` argument was invisible here and its `TakeInput` step
    // was reported as an unwitnessed input -- a false positive.
    "not warn a TakeInput step whose input is read via a 'terminate' argument" in { (td: TestData) =>
      runWitnessPass(
        """domain D is {
          |  user U is "a user"
          |  application context UI is {
          |    record Greeting is { text: String }
          |    command Refresh is { ??? }
          |    group Main is {
          |      form Entry acquires type Greeting
          |    }
          |  }
          |  context Ctx is {
          |    entity Order is {
          |      handler OH is {
          |        on term(oid: Id(entity Order)) is { do "end" }
          |      }
          |    }
          |    handler Screen is {
          |      on command D.UI.Refresh is {
          |        terminate entity D.Ctx.Order(get from input D.UI.Main.Entry)
          |      }
          |    }
          |  }
          |  epic E is {
          |    user U wants to "give" so that "taken"
          |    case C is {
          |      user U wants to "give" so that "taken"
          |      step take input D.UI.Main.Entry from user D.U
          |    }
          |  }
          |}
          |""".stripMargin
      ) { (uwo, _) =>
        unwitnessed(uwo) mustBe empty
      }
    }

    "warn a TakeInput step whose input is neither consumed nor read" in { (td: TestData) =>
      runWitnessPass(
        """domain D is {
          |  user U is "a user"
          |  application context UI is {
          |    record Greeting is { text: String }
          |    group Main is {
          |      form Entry acquires type Greeting
          |    }
          |  }
          |  epic E is {
          |    user U wants to "give" so that "taken"
          |    case C is {
          |      user U wants to "give" so that "taken"
          |      step take input D.UI.Main.Entry from user D.U
          |    }
          |  }
          |}
          |""".stripMargin
      ) { (uwo, _) =>
        unwitnessed(uwo) must not be empty
      }
    }

    "never warn structural steps (focus)" in { (td: TestData) =>
      runWitnessPass(
        """domain D is {
          |  user U is "a user"
          |  application context UI is {
          |    record Greeting is { text: String }
          |    group Main is {
          |      output Panel presents type Greeting
          |    }
          |  }
          |  epic E is {
          |    user U wants to "focus" so that "focused"
          |    case C is {
          |      user U wants to "focus" so that "focused"
          |      step focus user D.U on group D.UI.Main
          |    }
          |  }
          |}
          |""".stripMargin
      ) { (uwo, _) =>
        unwitnessed(uwo) mustBe empty
      }
    }
  }
}
