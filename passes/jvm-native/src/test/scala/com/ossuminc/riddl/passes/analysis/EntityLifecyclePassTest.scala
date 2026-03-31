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

class EntityLifecyclePassTest extends AbstractValidatingTest {

  private def runLifecyclePass(
    input: String,
    origin: String = "test"
  )(
    check: (EntityLifecycleOutput, Messages.Messages) => Assertion
  ): Assertion =
    val rpi = RiddlParserInput(input, origin)
    parseValidateAndThen(rpi, shouldFailOnErrors = false) {
      (pr: PassesResult, root: Root, _, msgs: Messages.Messages) =>
        val passInput = PassInput(root)
        val outputs = pr.outputs
        val pass = EntityLifecyclePass(passInput, outputs)
        val elo = Pass.runPass[EntityLifecycleOutput](
          passInput, outputs, pass
        )
        check(elo, msgs)
    }
  end runLifecyclePass

  "EntityLifecyclePass" should {
    "detect transitions from entity-level handlers" in {
      (td: TestData) =>
        runLifecyclePass(
          """domain D is {
            |  context C is {
            |    command GoBar is { ??? }
            |    command GoFoo is { ??? }
            |    type FooData is { x: String }
            |    type BarData is { y: String }
            |    entity E is {
            |      record FooFields is { f: String }
            |      state Foo of E.FooFields
            |      record BarFields is { b: String }
            |      state Bar of E.BarFields
            |      handler H is {
            |        on command D.C.GoBar {
            |          morph entity D.C.E to state Bar
            |            with command D.C.GoBar
            |        }
            |        on command D.C.GoFoo {
            |          morph entity D.C.E to state Foo
            |            with command D.C.GoFoo
            |        }
            |      }
            |    }
            |  }
            |}
            |""".stripMargin
        ) { (elo, _) =>
          elo.lifecycles must not be empty
          val lc = elo.lifecycles.values.head
          lc.states.size mustBe 2
          // Entity-level handler transitions expand to all states
          // GoBar: Foo->Bar and Bar->Bar; GoFoo: Foo->Foo and Bar->Foo
          lc.transitions.size mustBe 4
          // Every transition must have a fromState
          lc.transitions.forall(_.fromState.isDefined) mustBe true
        }
    }

    "detect transitions from state-level handlers" in {
      (td: TestData) =>
        runLifecyclePass(
          """domain D is {
            |  context C is {
            |    command Activate is { ??? }
            |    command Deactivate is { ??? }
            |    entity E is {
            |      record ActiveFields is { a: String }
            |      state Active of E.ActiveFields is {
            |        handler ActiveHandler is {
            |          on command D.C.Deactivate {
            |            morph entity D.C.E to state Inactive
            |              with command D.C.Deactivate
            |          }
            |        }
            |      }
            |      record InactiveFields is { i: String }
            |      state Inactive of E.InactiveFields is {
            |        handler InactiveHandler is {
            |          on command D.C.Activate {
            |            morph entity D.C.E to state Active
            |              with command D.C.Activate
            |          }
            |        }
            |      }
            |    }
            |  }
            |}
            |""".stripMargin
        ) { (elo, _) =>
          elo.lifecycles must not be empty
          val lc = elo.lifecycles.values.head
          lc.states.size mustBe 2
          lc.transitions.size mustBe 2
          // Active -> Inactive
          val t1 = lc.transitions.find(t =>
            t.fromState.exists(_.id.value == "Active") &&
              t.toState.id.value == "Inactive"
          )
          t1 mustBe defined
          // Inactive -> Active
          val t2 = lc.transitions.find(t =>
            t.fromState.exists(_.id.value == "Inactive") &&
              t.toState.id.value == "Active"
          )
          t2 mustBe defined
          // No terminal states — both have outgoing
          lc.terminalStates mustBe empty
        }
    }

    "detect initial state from on init with set state" in {
      (td: TestData) =>
        runLifecyclePass(
          """domain D is {
            |  context C is {
            |    command GoBar is { ??? }
            |    entity E is {
            |      record FooFields is { f: String }
            |      state Foo of E.FooFields
            |      record BarFields is { b: String }
            |      state Bar of E.BarFields
            |      handler H is {
            |        on init {
            |          set state Foo to "initial"
            |        }
            |        on command D.C.GoBar {
            |          morph entity D.C.E to state Bar
            |            with command D.C.GoBar
            |        }
            |      }
            |    }
            |  }
            |}
            |""".stripMargin
        ) { (elo, _) =>
          elo.lifecycles must not be empty
          val lc = elo.lifecycles.values.head
          lc.states.size mustBe 2
          // Initial state should be Foo (from on init + set state)
          lc.initialState mustBe defined
          lc.initialState.get.id.value mustBe "Foo"
        }
    }

    "identify terminal states" in {
      (td: TestData) =>
        runLifecyclePass(
          """domain D is {
            |  context C is {
            |    command GoMiddle is { ??? }
            |    command GoEnd is { ??? }
            |    entity E is {
            |      record StartFields is { s: String }
            |      state Start of E.StartFields is {
            |        handler StartH is {
            |          on command D.C.GoMiddle {
            |            morph entity D.C.E to state Middle
            |              with command D.C.GoMiddle
            |          }
            |        }
            |      }
            |      record MiddleFields is { m: String }
            |      state Middle of E.MiddleFields is {
            |        handler MiddleH is {
            |          on command D.C.GoEnd {
            |            morph entity D.C.E to state End
            |              with command D.C.GoEnd
            |          }
            |        }
            |      }
            |      record EndFields is { e: String }
            |      state End of E.EndFields
            |    }
            |  }
            |}
            |""".stripMargin
        ) { (elo, _) =>
          elo.lifecycles must not be empty
          val lc = elo.lifecycles.values.head
          lc.states.size mustBe 3
          lc.transitions.size mustBe 2
          // End is terminal (no outgoing transitions)
          lc.terminalStates.size mustBe 1
          lc.terminalStates.head.id.value mustBe "End"
        }
    }

    "skip entities with fewer than 2 states" in {
      (td: TestData) =>
        runLifecyclePass(
          """domain D is {
            |  context C is {
            |    entity E is {
            |      record Fields is { f: String }
            |      state Only of E.Fields
            |      handler H is { ??? }
            |    }
            |  }
            |}
            |""".stripMargin
        ) { (elo, _) =>
          elo.lifecycles mustBe empty
        }
    }

    "combine entity-level and state-level transitions" in {
      (td: TestData) =>
        runLifecyclePass(
          """domain D is {
            |  context C is {
            |    command Reset is { ??? }
            |    command Advance is { ??? }
            |    entity E is {
            |      record IdleFields is { i: String }
            |      state Idle of E.IdleFields is {
            |        handler IdleH is {
            |          on command D.C.Advance {
            |            morph entity D.C.E to state Running
            |              with command D.C.Advance
            |          }
            |        }
            |      }
            |      record RunningFields is { r: String }
            |      state Running of E.RunningFields
            |      handler GlobalH is {
            |        on command D.C.Reset {
            |          morph entity D.C.E to state Idle
            |            with command D.C.Reset
            |        }
            |      }
            |    }
            |  }
            |}
            |""".stripMargin
        ) { (elo, _) =>
          elo.lifecycles must not be empty
          val lc = elo.lifecycles.values.head
          lc.states.size mustBe 2
          // State-level: Idle -> Running (1 transition)
          // Entity-level Reset: expanded to Idle->Idle, Running->Idle (2)
          lc.transitions.size mustBe 3
          // Running has outgoing (Reset -> Idle from entity-level)
          // Idle has outgoing (Advance -> Running from state-level,
          //   plus Reset -> Idle from entity-level)
          lc.terminalStates mustBe empty
        }
    }
  }
}
