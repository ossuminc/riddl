/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.utils.{CommonOptions, pc}
import org.scalatest.TestData

/** Statement scope: a statement that writes or reads state is accepted only where there is state to
  * write or read (Reid, 2026-08-12).
  *
  * Reported by ossum.tech while documenting the statement-availability table, and confirmed by
  * probing every (statement, container) pair: riddlc already enforced the analogous rules for
  * `put`, `return`, `morph`, `become` and the `on activate` bans, so these were the gaps in an
  * otherwise complete set.
  *
  * The NEGATIVE cases below are only half the suite. Each ban has a POSITIVE twin that pins where
  * the line falls, because a rule drawn one container too wide would still make every negative case
  * pass — and one of those twins (`set` in a projector fold) is load-bearing for A70, whose folds
  * are required to terminate in a `set`.
  */
class StatementScopeTest extends AbstractValidatingTest {

  private def diagnostics(src: String, origin: String): Messages =
    var captured: Messages = Messages.empty
    pc.withOptions(CommonOptions(showStyleWarnings = true, showWarnings = true)) { _ =>
      parseAndValidate(src, origin, shouldFailOnErrors = false) { (_, _, messages) =>
        captured = messages
        succeed
      }
    }
    captured

  private def errorsFor(src: String, origin: String): String =
    diagnostics(src, origin).justErrors.map(_.message).mkString("\n")

  "'set' scope" should {

    "reject a 'set' in a context handler" in { (td: TestData) =>
      // Computational Model §3.5: "Domain state lives in contained Entities, Repositories, and
      // Projectors — never in the Context itself." `Acct.balance` names a field of a TYPE, not any
      // instance's state, so there is nothing for the write to land in.
      val src =
        """domain D is {
          |  context C is {
          |    record Acct is { balance: Natural }
          |    command Plain is { why: String }
          |    handler CH is { on command Plain is { set field Acct.balance to "1" } }
          |  }
          |}
          |""".stripMargin
      val errors = errorsFor(src, "set-in-context")
      errors must include("'set' is not allowed in Context 'C'")
      errors must include("owns no state to write")
    }

    "reject a 'set' in a saga step" in { (td: TestData) =>
      // §9.5: "There is no domain-specific value in the saga state — it is simply housekeeping."
      // A step coordinates by sending commands; writing state directly breaks compensation, since
      // the undo has no way to reverse a write it never issued.
      val src =
        """domain D is {
          |  context C is {
          |    record Acct is { balance: Natural }
          |    saga Sg is {
          |      step One is { set field Acct.balance to "1" } reverted by { do "undo it" }
          |      step Two is { do "something" } reverted by { do "undo it" }
          |    }
          |  }
          |}
          |""".stripMargin
      errorsFor(src, "set-in-saga-step") must include("'set' is not allowed in Saga 'Sg'")
    }

    "reject a 'set' in a repository handler" in { (td: TestData) =>
      // Reid, 2026-08-12. reactive-bbq had 97 of these, added purely to silence the "contains only
      // prompt statements" warning — so they were evidence about that warning, which is now fixed
      // to exempt repositories, and not evidence that a repository writes state.
      val src =
        """domain D is {
          |  context C is {
          |    command PersistIt is { why: String }
          |    repository Store is {
          |      record Row is { status: String }
          |      handler S is { on command PersistIt is { set field Row.status to "1" } }
          |    }
          |  }
          |}
          |""".stripMargin
      errorsFor(src, "set-in-repository") must include("'set' is not allowed in Repository 'Store'")
    }

    "accept a 'set' in an entity handler" in { (td: TestData) =>
      // The positive twin: an entity owns its State, so this is where a write belongs.
      val src =
        """domain D is {
          |  context C is {
          |    record Acct is { balance: Natural }
          |    command Deposit is { amount: Natural }
          |    entity Own is {
          |      state PS of record Acct is {
          |        handler H is { on command Deposit is { set field Acct.balance to "increased" } }
          |      }
          |    }
          |  }
          |}
          |""".stripMargin
      errorsFor(src, "set-in-entity") must be("")
    }

    "accept a 'set' in a projector's correlation fold" in { (td: TestData) =>
      // LOAD-BEARING for A70: every fold must terminate in a `set`, so a ban drawn to include
      // projectors would make correlations unusable while every negative case above still passed.
      val src =
        """domain D is {
          |  context C is {
          |    command RecordJoin is { customerId: String, paidAmount: Number }
          |    event PaymentTaken is { customerId: String, amount: Number }
          |    repository Store is { ??? }
          |    projector V is {
          |      updates repository Store
          |      correlation J by customerId yields command RecordJoin is {
          |        handler Collect is {
          |          on e: event PaymentTaken is { set field paidAmount to e.amount }
          |        }
          |      } times out after "30 days" { do "escalate" }
          |    }
          |  }
          |}
          |""".stripMargin
      errorsFor(src, "set-in-correlation-fold") must be("")
    }
  }

  "'get from state' scope" should {

    "reject 'get from state' in a saga step" in { (td: TestData) =>
      // The rule the `ask` ban already states (§9.5), which reading state directly would otherwise
      // bypass by spelling it differently — arguably more so, since a `get` reads without even the
      // correlation an `ask` implies.
      val src =
        """domain D is {
          |  context C is {
          |    record Acct is { balance: Natural }
          |    entity Peer is {
          |      state PS of record Acct is { handler PH is { on init is { do "x" } } }
          |    }
          |    saga Sg is {
          |      step One is { let v = get from state Peer.PS } reverted by { do "undo it" }
          |      step Two is { do "something" } reverted by { do "undo it" }
          |    }
          |  }
          |}
          |""".stripMargin
      errorsFor(src, "get-state-in-saga") must include(
        "state may be read only inside the entity that owns it"
      )
    }

    "reject 'get from state' in a context handler" in { (td: TestData) =>
      val src =
        """domain D is {
          |  context C is {
          |    record Acct is { balance: Natural }
          |    command Plain is { why: String }
          |    entity Peer is {
          |      state PS of record Acct is { handler PH is { on init is { do "x" } } }
          |    }
          |    handler CH is { on command Plain is { let v = get from state Peer.PS } }
          |  }
          |}
          |""".stripMargin
      errorsFor(src, "get-state-in-context") must include(
        "state may be read only inside the entity that owns it"
      )
    }

    "reject one entity reading ANOTHER entity's state" in { (td: TestData) =>
      // §4.6: an entity's data "is 100% encapsulated by the entity and acted upon only by the
      // entity's handlers", so only a message may cross that boundary. This is the half that could
      // not live in the parser — it needs the resolved State and its owner.
      val src =
        """domain D is {
          |  context C is {
          |    record Acct is { balance: Natural }
          |    command Poke is { why: String }
          |    entity Peer is {
          |      state PS of record Acct is { handler PH is { on init is { do "x" } } }
          |    }
          |    entity Nosy is {
          |      state NS of record Acct is {
          |        handler NH is { on command Poke is { let v = get from state Peer.PS } }
          |      }
          |    }
          |  }
          |}
          |""".stripMargin
      val errors = errorsFor(src, "get-state-cross-entity")
      errors must include("does not own")
      errors must include("encapsulated")
    }

    "accept an entity reading its OWN state" in { (td: TestData) =>
      val src =
        """domain D is {
          |  context C is {
          |    record Acct is { balance: Natural }
          |    command Poke is { why: String }
          |    entity Own is {
          |      state PS of record Acct is {
          |        handler H is { on command Poke is { let v = get from state Own.PS } }
          |      }
          |    }
          |  }
          |}
          |""".stripMargin
      errorsFor(src, "get-own-state") must be("")
    }
  }

  "handler behaviour warning" should {

    "say 'do', not 'prompt'" in { (td: TestData) =>
      // `do` is canonical and `prompt` is the deprecated synonym, so naming `prompt` sent authors
      // looking for something their model does not contain.
      val src =
        """domain D is {
          |  context C is {
          |    command Poke is { why: String }
          |    entity E is { handler H is { on command Poke is { do "think about it" } } }
          |  }
          |}
          |""".stripMargin
      val all = diagnostics(src, "do-only-entity").map(_.message).mkString("\n")
      all must include("contains only 'do' statements")
      all must not(include("only prompt statements"))
    }

    "exempt a repository" in { (td: TestData) =>
      // Most repository on-clauses legitimately hold a single `do` standing in for the SQL that
      // implements them: naming the persistence step IS the modelling. Nagging them is what
      // produced reactive-bbq's 97 workaround `set` statements.
      val src =
        """domain D is {
          |  context C is {
          |    command PersistIt is { why: String }
          |    repository Store is {
          |      handler S is { on command PersistIt is { do "insert row into table" } }
          |    }
          |  }
          |}
          |""".stripMargin
      diagnostics(src, "do-only-repository").map(_.message).mkString("\n") must not(
        include("contains only 'do' statements")
      )
    }
  }
}
