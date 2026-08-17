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

/** A `tell`/`send` whose message operand is a `let`-local produces a flow edge.
  *
  * Reported by riddl-models 2026-08-15: finishing their migration to `let` + typed hole produced
  * **90** `MessageFlowPass: could not resolve message type` warnings, every one naming a
  * `let`-local's BINDING NAME as if it were a message type. The type is stated on the line above,
  * so nothing was actually unresolvable.
  *
  * The warning is the visible half. The invisible half is the one that matters: the pass takes its
  * `case _ =>` arm and **no edge is added**, so every flow whose operand is a `let`-local vanishes
  * from the graph the simulator and generator consume, while the model reports zero errors. 557
  * `let`-locals landed in that corpus in a single day.
  *
  * The cause is that `let`-locals are deliberately LEXICAL — a `let` is not a Definition and is
  * statement-ordered, which the symbol table does not model (see `ValidationPass.letIndexOf`), so
  * they are absent from the refMap by design and `refMap.definitionOf[Type]` could never find one.
  */
class LetLocalMessageFlowTest extends AbstractValidatingTest {

  private def runMessageFlowPass(
    input: String,
    origin: String = "test"
  )(
    check: (MessageFlowOutput, Messages.Messages) => Assertion
  ): Assertion =
    val rpi = RiddlParserInput(input, origin)
    parseValidateAndThen(rpi, shouldFailOnErrors = false) {
      (pr: PassesResult, root: Root, _, msgs: Messages.Messages) =>
        val passInput = PassInput(root)
        val outputs = pr.outputs
        val pass = MessageFlowPass(passInput, outputs)
        val mfo = Pass.runPass[MessageFlowOutput](passInput, outputs, pass)
        check(mfo, msgs)
    }
  end runMessageFlowPass

  private def unresolved(mfo: MessageFlowOutput): Messages.Messages =
    mfo.messages.filter(_.message.contains("MessageFlowPass: could not resolve"))

  "a tell whose operand is a let-local" should {

    "resolve through the let's DECLARED type" in { (td: TestData) =>
      runMessageFlowPass(
        """domain D is {
          |  context C is {
          |    command Cmd is { id: String }
          |    event Evt is { id: String }
          |    entity Sender is {
          |      handler H is {
          |        on command D.C.Cmd is {
          |          let evt: D.C.Evt = prompt("the event resulting from the command")
          |          tell evt to entity D.C.Receiver
          |        }
          |      }
          |    }
          |    entity Receiver is {
          |      handler H is { on event D.C.Evt is { ??? } }
          |    }
          |  }
          |}
          |""".stripMargin
      ) { (mfo, _) =>
        withClue(s"warnings were:\n${unresolved(mfo).format}\n") {
          unresolved(mfo) mustBe empty
        }
        val tells = mfo.edges.filter(_.mechanism == FlowMechanism.Tell)
        tells must not be empty
        tells.head.producer.id.value mustBe "Sender"
        tells.head.consumer.id.value mustBe "Receiver"
        tells.head.messageType.map(_.id.value) mustBe Some("Evt")
      }
    }

    "resolve through the let's INFERRED type when its expression is a constructor" in {
      (td: TestData) =>
        runMessageFlowPass(
          """domain D is {
            |  context C is {
            |    command Cmd is { id: String }
            |    event Evt is { id: String }
            |    entity Sender is {
            |      handler H is {
            |        on command D.C.Cmd is {
            |          let evt = event D.C.Evt(id = "an id")
            |          tell evt to entity D.C.Receiver
            |        }
            |      }
            |    }
            |    entity Receiver is {
            |      handler H is { on event D.C.Evt is { ??? } }
            |    }
            |  }
            |}
            |""".stripMargin
        ) { (mfo, _) =>
          withClue(s"warnings were:\n${unresolved(mfo).format}\n") {
            unresolved(mfo) mustBe empty
          }
          val tells = mfo.edges.filter(_.mechanism == FlowMechanism.Tell)
          tells.map(_.messageType.map(_.id.value)) mustBe Seq(Some("Evt"))
        }
    }

    "resolve a let declared INSIDE a conditional, told from inside it" in { (td: TestData) =>
      runMessageFlowPass(
        """domain D is {
          |  context C is {
          |    command Cmd is { id: String }
          |    event Evt is { id: String }
          |    entity Sender is {
          |      handler H is {
          |        on command D.C.Cmd is {
          |          when "the command is acceptable" then
          |            let evt: D.C.Evt = prompt("the event resulting from the command")
          |            tell evt to entity D.C.Receiver
          |          end
          |        }
          |      }
          |    }
          |    entity Receiver is {
          |      handler H is { on event D.C.Evt is { ??? } }
          |    }
          |  }
          |}
          |""".stripMargin
      ) { (mfo, _) =>
        withClue(s"warnings were:\n${unresolved(mfo).format}\n") {
          unresolved(mfo) mustBe empty
        }
        val tells = mfo.edges.filter(_.mechanism == FlowMechanism.Tell)
        tells.map(_.messageType.map(_.id.value)) mustBe Seq(Some("Evt"))
      }
    }

    /* The conditional case above would pass even with a nested `let`'s type reference left
     * unresolved: `ValidationPass.letType` falls back to the refMap overload keyed on the PATH
     * alone, which finds any entry with that path — and `D.C.Evt` is also named by the receiver's
     * own on-clause. Here the ascribed type is named NOWHERE else in the model, so nothing but the
     * nested `let`'s own TypeRef can supply it. */
    "resolve a let inside a conditional whose type is named nowhere else" in { (td: TestData) =>
      runMessageFlowPass(
        """domain D is {
          |  context C is {
          |    command Cmd is { id: String }
          |    event Evt is { id: String }
          |    entity Sender is {
          |      handler H is {
          |        on command D.C.Cmd is {
          |          when "the command is acceptable" then
          |            let evt: D.C.Evt = prompt("the event resulting from the command")
          |            tell evt to entity D.C.Receiver
          |          end
          |        }
          |      }
          |    }
          |    entity Receiver is {
          |      handler H is { on command D.C.Cmd is { ??? } }
          |    }
          |  }
          |}
          |""".stripMargin
      ) { (mfo, _) =>
        withClue(s"warnings were:\n${unresolved(mfo).format}\n") {
          unresolved(mfo) mustBe empty
        }
        val tells = mfo.edges.filter(_.mechanism == FlowMechanism.Tell)
        tells.map(_.messageType.map(_.id.value)) mustBe Seq(Some("Evt"))
      }
    }

    "resolve the on-clause BINDING, which names the handled message itself" in { (td: TestData) =>
      runMessageFlowPass(
        """domain D is {
          |  context C is {
          |    command Cmd is { id: String }
          |    entity Sender is {
          |      handler H is {
          |        on cmd: command D.C.Cmd is {
          |          tell cmd to entity D.C.Receiver
          |        }
          |      }
          |    }
          |    entity Receiver is {
          |      handler H is { on command D.C.Cmd is { ??? } }
          |    }
          |  }
          |}
          |""".stripMargin
      ) { (mfo, _) =>
        withClue(s"warnings were:\n${unresolved(mfo).format}\n") {
          unresolved(mfo) mustBe empty
        }
        val tells = mfo.edges.filter(_.mechanism == FlowMechanism.Tell)
        tells.map(_.messageType.map(_.id.value)) mustBe Seq(Some("Cmd"))
      }
    }
  }

  "a send whose operand is a let-local" should {
    "resolve through the let's declared type" in { (td: TestData) =>
      runMessageFlowPass(
        """domain D is {
          |  context C is {
          |    command Cmd is { id: String }
          |    event Evt is { id: String }
          |    entity Sender is {
          |      outlet Out is type D.C.Evt
          |      handler H is {
          |        on command D.C.Cmd is {
          |          let evt: D.C.Evt = prompt("the event resulting from the command")
          |          send evt to outlet D.C.Sender.Out
          |        }
          |      }
          |    }
          |  }
          |}
          |""".stripMargin
      ) { (mfo, _) =>
        withClue(s"warnings were:\n${unresolved(mfo).format}\n") {
          unresolved(mfo) mustBe empty
        }
        val sends = mfo.edges.filter(_.mechanism == FlowMechanism.Send)
        sends.map(_.messageType.map(_.id.value)) mustBe Seq(Some("Evt"))
      }
    }
  }

  "an operand that genuinely names nothing" should {
    "still be reported — the widening must not silence real defects" in { (td: TestData) =>
      runMessageFlowPass(
        """domain D is {
          |  context C is {
          |    command Cmd is { id: String }
          |    entity Sender is {
          |      handler H is {
          |        on command D.C.Cmd is {
          |          tell nonesuch to entity D.C.Receiver
          |        }
          |      }
          |    }
          |    entity Receiver is {
          |      handler H is { on command D.C.Cmd is { ??? } }
          |    }
          |  }
          |}
          |""".stripMargin
      ) { (mfo, _) =>
        unresolved(mfo) must not be empty
      }
    }
  }
}
