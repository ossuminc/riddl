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

/** A36 Level 2 — trace admissibility via FSM projection. */
class UseCaseTracePassTest extends AbstractValidatingTest {

  private def runTracePass(
    input: String,
    origin: String = "test"
  )(
    check: (UseCaseTraceOutput, Messages.Messages) => Assertion
  ): Assertion =
    val rpi = RiddlParserInput(input, origin)
    parseValidateAndThen(rpi, shouldFailOnErrors = false) {
      (pr: PassesResult, root: Root, _, msgs: Messages.Messages) =>
        val passInput = PassInput(root)
        val outputs = pr.outputs
        // EntityLifecyclePass is a required predecessor; run it first so its output is available.
        Pass.runPass[EntityLifecycleOutput](
          passInput,
          outputs,
          EntityLifecyclePass(passInput, outputs)
        )
        val pass = UseCaseTracePass(passInput, outputs)
        val uto = Pass.runPass[UseCaseTraceOutput](passInput, outputs, pass)
        check(uto, msgs)
    }
  end runTracePass

  private def inadmissible(uto: UseCaseTraceOutput): Seq[Messages.Message] =
    uto.messages.filter(_.kind == Messages.CompletenessWarning)

  // A two-state (plus terminal) entity: S0 --CmdA--> S1 --CmdB--> S2.
  private def linearEntity(guardCmdA: Boolean = false, initToS1: Boolean = false): String =
    val cmdABody =
      if guardCmdA then """when "ready" then
          |            morph entity D.C.E to state S1 with record D.C.E.F
          |          end"""
      else "morph entity D.C.E to state S1 with record D.C.E.F"
    val initHandler =
      if initToS1 then """      handler Init is {
          |        on init { set state S1 to "start" }
          |      }
          |""".stripMargin
      else ""
    s"""domain D is {
       |  user U is "a user"
       |  context C is {
       |    command CmdA is { ??? }
       |    command CmdB is { ??? }
       |    command Ping is { ??? }
       |    entity E is {
       |      record F is { x: String }
       |$initHandler      state S0 of record E.F is {
       |        handler S0H is {
       |          on command D.C.CmdA {
       |            $cmdABody
       |          }
       |          on command D.C.Ping { ??? }
       |        }
       |      }
       |      state S1 of record E.F is {
       |        handler S1H is {
       |          on command D.C.CmdB {
       |            morph entity D.C.E to state S2 with record D.C.E.F
       |          }
       |        }
       |      }
       |      state S2 of record E.F is {
       |        handler S2H is { ??? }
       |      }
       |    }
       |  }
       |""".stripMargin

  "UseCaseTracePass" should {

    "not warn when deliveries follow an admissible order (CmdA then CmdB)" in { (td: TestData) =>
      runTracePass(
        linearEntity() +
          """  epic Ep is {
            |    user U wants to "drive" so that "done"
            |    case Good is {
            |      user U wants to "drive" so that "done"
            |      step send command D.C.CmdA from user D.U to entity D.C.E
            |      step send command D.C.CmdB from user D.U to entity D.C.E
            |    }
            |  }
            |}
            |""".stripMargin
      ) { (uto, _) =>
        inadmissible(uto) mustBe empty
      }
    }

    "warn when the current state does not handle the delivered message (CmdB first, from S0)" in {
      (td: TestData) =>
        runTracePass(
          linearEntity() +
            """  epic Ep is {
              |    user U wants to "drive" so that "done"
              |    case Bad is {
              |      user U wants to "drive" so that "done"
              |      step send command D.C.CmdB from user D.U to entity D.C.E
              |    }
              |  }
              |}
              |""".stripMargin
        ) { (uto, _) =>
          val warns = inadmissible(uto)
          warns must not be empty
          warns.exists(m =>
            m.message.contains("does not handle it") && m.message.contains("state 'S0'")
          ) mustBe true
        }
    }

    "not warn a handled-but-non-transitioning delivery (self-loop) — Ping in S0" in {
      (td: TestData) =>
        runTracePass(
          linearEntity() +
            """  epic Ep is {
              |    user U wants to "drive" so that "done"
              |    case Loop is {
              |      user U wants to "drive" so that "done"
              |      step send command D.C.Ping from user D.U to entity D.C.E
              |    }
              |  }
              |}
              |""".stripMargin
        ) { (uto, _) =>
          inadmissible(uto) mustBe empty
        }
    }

    "warn (mentioning the guard) when only a guarded transition admits the message" in {
      (td: TestData) =>
        runTracePass(
          linearEntity(guardCmdA = true) +
            """  epic Ep is {
              |    user U wants to "drive" so that "done"
              |    case Guarded is {
              |      user U wants to "drive" so that "done"
              |      step send command D.C.CmdA from user D.U to entity D.C.E
              |    }
              |  }
              |}
              |""".stripMargin
        ) { (uto, _) =>
          val warns = inadmissible(uto)
          warns must not be empty
          warns.exists(m => m.message.contains("guarded")) mustBe true
        }
    }

    "not warn for an inadmissible step inside an Optional container (skippable)" in {
      (td: TestData) =>
        runTracePass(
          linearEntity() +
            """  epic Ep is {
              |    user U wants to "drive" so that "done"
              |    case Opt is {
              |      user U wants to "drive" so that "done"
              |      optional {
              |        step send command D.C.CmdB from user D.U to entity D.C.E
              |      }
              |    }
              |  }
              |}
              |""".stripMargin
        ) { (uto, _) =>
          inadmissible(uto) mustBe empty
        }
    }

    "never warn for a single-state entity" in { (td: TestData) =>
      runTracePass(
        """domain D is {
          |  user U is "a user"
          |  context C is {
          |    command CmdA is { ??? }
          |    entity E is {
          |      record F is { x: String }
          |      state Only of record E.F is {
          |        handler H is {
          |          on command D.C.CmdA { ??? }
          |        }
          |      }
          |    }
          |  }
          |  epic Ep is {
          |    user U wants to "drive" so that "done"
          |    case One is {
          |      user U wants to "drive" so that "done"
          |      step send command D.C.CmdA from user D.U to entity D.C.E
          |    }
          |  }
          |}
          |""".stripMargin
      ) { (uto, _) =>
        inadmissible(uto) mustBe empty
      }
    }

    "respect an initial state set in on-init (start in S1, so CmdB is admissible first)" in {
      (td: TestData) =>
        runTracePass(
          linearEntity(initToS1 = true) +
            """  epic Ep is {
              |    user U wants to "drive" so that "done"
              |    case Init is {
              |      user U wants to "drive" so that "done"
              |      step send command D.C.CmdB from user D.U to entity D.C.E
              |    }
              |  }
              |}
              |""".stripMargin
        ) { (uto, _) =>
          inadmissible(uto) mustBe empty
        }
    }
  }
}
