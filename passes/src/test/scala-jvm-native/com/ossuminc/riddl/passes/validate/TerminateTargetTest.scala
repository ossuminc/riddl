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

/** A70/instance-identity: `terminate <target>` names an INSTANCE, and its target must be a value
  * typed `Id(entity E)` (Reid, 2026-08-15).
  *
  * Two Errors are checked here and BOTH have to be written by hand, which is the whole reason this
  * suite exists. It is tempting to assume the type system already enforces the second one -- it
  * does not, and deliberately so: Reid ruled that `Id(P)` stays valid for ALL six processor kinds
  * because a singleton's `Id` is how you SEND IT MESSAGES, denoting its singular deployment rather
  * than a shard. So `Id(context C)` is a perfectly good value that is simply not a legal thing to
  * end, and only an explicit check says so.
  *
  * Every negative case is paired with a POSITIVE one. Without the positive half a check wrongly
  * applied to everything would still look green -- the standing lesson from A70's timeout block.
  */
class TerminateTargetTest extends AbstractValidatingTest {

  private def diagnostics(src: String, origin: String): Messages =
    var captured: Messages = Messages.empty
    pc.withOptions(CommonOptions(showStyleWarnings = true, showWarnings = true)) { _ =>
      parseAndValidate(src, origin, shouldFailOnErrors = false) { (_, _, messages) =>
        captured = messages
        succeed
      }
    }
    captured

  private def errorsIn(src: String, origin: String): String =
    diagnostics(src, origin).justErrors.map(_.message).mkString("\n")

  /** One model shape throughout; only the statement under test varies. `Order` is an entity with an
    * `on term`, `Ordering` is the enclosing context (a singleton), and `OrderId` is an ALIAS of
    * `Id(entity Order)` -- riddl-models' documented house style, and the spelling a check matching
    * a bare `UniqueId` alone would miss.
    */
  private def wrap(decls: String, stmts: String): String =
    s"""domain Dom is {
       |  context Ordering is {
       |    record R is { total: Integer } with { briefly "r" }
       |    type OrderId is Id(entity Order) with { briefly "alias" }
       |    type CtxId is Id(context Ordering) with { briefly "singleton id" }
       |    $decls
       |    entity Order is {
       |      state OS of record R is {
       |        handler OH is {
       |          on init { do "start" }
       |          on term { do "end" }
       |        } with { briefly "oh" }
       |      } with { briefly "os" }
       |    } with { briefly "e" }
       |    entity Caller is {
       |      state CS of record R is {
       |        handler CH is {
       |          on init {
       |            $stmts
       |          }
       |        } with { briefly "ch" }
       |      } with { briefly "cs" }
       |    } with { briefly "ce" }
       |  } with { briefly "c" }
       |} with { briefly "d" }
       |""".stripMargin

  "terminate's target" should {

    "ACCEPT an Id produced by initiate" in { (td: TestData) =>
      val errs = errorsIn(
        wrap(
          "",
          """let oid = initiate entity Order
                   |            terminate oid""".stripMargin
        ),
        td.name
      )
      errs mustBe ""
    }

    // The alias case is the one that matters in practice: all 227 `Id(...)` uses in riddl-models
    // are written through a named alias, so a check that only matched a bare `UniqueId` would
    // fire on essentially every real model.
    "ACCEPT a value whose type is an ALIAS of Id(entity E)" in { (td: TestData) =>
      val errs = errorsIn(
        wrap(
          """command Close is { oid: OrderId } with { briefly "close" }""",
          """let x: OrderId = initiate entity Order
               |            terminate x""".stripMargin
        ),
        td.name
      )
      errs mustBe ""
    }

    "ACCEPT self.id" in { (td: TestData) =>
      val src =
        """domain Dom is {
          |  context Ordering is {
          |    record R is { total: Integer } with { briefly "r" }
          |    entity Order is {
          |      state OS of record R is {
          |        handler OH is {
          |          on init { do "start" }
          |          on term { terminate self.id }
          |        } with { briefly "oh" }
          |      } with { briefly "os" }
          |    } with { briefly "e" }
          |  } with { briefly "c" }
          |} with { briefly "d" }
          |""".stripMargin
      errorsIn(src, td.name) mustBe ""
    }

    // `self` is the whole instance record, `self.id` is its identity -- writing the former is the
    // realistic version of this mistake, and its type IS determinable (the synthesized
    // Aggregation), so the check can speak.
    "REJECT a target whose type is determinable and not an Id" in { (td: TestData) =>
      val src =
        """domain Dom is {
          |  context Ordering is {
          |    record R is { total: Integer } with { briefly "r" }
          |    entity Order is {
          |      state OS of record R is {
          |        handler OH is {
          |          on init { do "start" }
          |          on term { terminate self }
          |        } with { briefly "oh" }
          |      } with { briefly "os" }
          |    } with { briefly "e" }
          |  } with { briefly "c" }
          |} with { briefly "d" }
          |""".stripMargin
      errorsIn(src, td.name) must include("requires a value of type 'Id(entity ...)'")
    }

    // The boundary between "wrong" and "unknown" is explicit on purpose. `let n = 5` infers no
    // type, so `valueTypeExpr` yields None and reporting anything would be reasoning from
    // absence -- the same conservative rule A20's unascribed-hole warning follows.
    "stay SILENT when the target's type cannot be determined" in { (td: TestData) =>
      val errs = errorsIn(
        wrap(
          "",
          """let n = 5
                   |            terminate n""".stripMargin
        ),
        td.name
      )
      errs mustBe ""
    }

    // The two halves of the `valueTypeExpr` widening. Both of these used to fall into the SILENT
    // case above -- not because the type was genuinely unknown, but because `valueTypeExpr` could
    // not see a type the author had WRITTEN OUT. "Not determinable" and "we did not look" are
    // different facts, and only the first deserves silence.
    "REJECT a let whose DECLARED PREDEFINED type is not an Id" in { (td: TestData) =>
      // Contrast the SILENT case: there the type is inferred and absent; here `Integer` is
      // declared. Predefined keywords are deliberately never in the symbol table, so this needs
      // `PredefTypes.typeExpressionFor` rather than a refMap lookup.
      val errs = errorsIn(
        wrap(
          "",
          """let n: Integer = 5
                   |            terminate n""".stripMargin
        ),
        td.name
      )
      errs must include("requires a value of type 'Id(entity ...)'")
    }

    "REJECT an ASCRIBED typed hole whose type is not an Id" in { (td: TestData) =>
      // A20 gives a hole an ascription, so `prompt(...) as Integer` states its type as plainly as
      // any literal does. The UNASCRIBED form stays silent -- that is the case below.
      val errs = errorsIn(
        wrap("", """terminate prompt("the order to end") as Integer"""),
        td.name
      )
      errs must include("requires a value of type 'Id(entity ...)'")
    }

    "ACCEPT an ASCRIBED typed hole whose type IS an Id alias" in { (td: TestData) =>
      // The positive half: reading the ascription must admit the legal spelling, not merely reject
      // the illegal one. Without this a check wired to reject everything would still look green.
      errorsIn(wrap("", """terminate prompt("the order to end") as OrderId"""), td.name) mustBe ""
    }

    "stay SILENT for an UNASCRIBED typed hole" in { (td: TestData) =>
      // A20's conservative rule is unchanged: an unwired position stays quiet rather than guessing.
      errorsIn(wrap("", """terminate prompt("the order to end")"""), td.name) mustBe ""
    }

    // The check that is NOT free. `Id(context Ordering)` is a legal, meaningful value -- it names
    // the context's singular deployment so messages can be sent to it -- and nothing about its
    // TYPE says it cannot be terminated. Only this check does.
    "REJECT an Id of a SINGLETON processor, though the Id itself is legal" in { (td: TestData) =>
      val errs = errorsIn(
        wrap(
          """command Ping is { cid: CtxId } with { briefly "ping" }""",
          """let c: CtxId = prompt("the ordering deployment") as CtxId
               |            terminate c""".stripMargin
        ),
        td.name
      )
      errs must include("only an entity has instances to create or destroy")
    }

    "REJECT initiate on a SINGLETON processor" in { (td: TestData) =>
      val errs = errorsIn(
        wrap(
          "",
          """let c = initiate context Ordering
                   |            terminate c""".stripMargin
        ),
        td.name
      )
      errs must include("only an entity has instances to create or destroy")
    }

    // `on term`'s parameters are pure PAYLOAD now -- the leading-Id addressing convention is gone
    // -- so arity is counted against the declared payload alone.
    "count arguments against `on term`'s payload parameters" in { (td: TestData) =>
      val src =
        """domain Dom is {
          |  context Ordering is {
          |    record R is { total: Integer } with { briefly "r" }
          |    entity Order is {
          |      state OS of record R is {
          |        handler OH is {
          |          on init { do "start" }
          |          on term(why: String) { do "end" }
          |        } with { briefly "oh" }
          |      } with { briefly "os" }
          |    } with { briefly "e" }
          |    entity Caller is {
          |      state CS of record R is {
          |        handler CH is {
          |          on init {
          |            let oid = initiate entity Order
          |            terminate oid
          |          }
          |        } with { briefly "ch" }
          |      } with { briefly "cs" }
          |    } with { briefly "ce" }
          |  } with { briefly "c" }
          |} with { briefly "d" }
          |""".stripMargin
      errorsIn(src, td.name) must include("with 1 parameter, but 0 arguments supplied")
    }

    "ACCEPT the matching argument count behind `with`" in { (td: TestData) =>
      val src =
        """domain Dom is {
          |  context Ordering is {
          |    record R is { total: Integer } with { briefly "r" }
          |    entity Order is {
          |      state OS of record R is {
          |        handler OH is {
          |          on init { do "start" }
          |          on term(why: String) { do "end" }
          |        } with { briefly "oh" }
          |      } with { briefly "os" }
          |    } with { briefly "e" }
          |    entity Caller is {
          |      state CS of record R is {
          |        handler CH is {
          |          on init {
          |            let oid = initiate entity Order
          |            terminate oid with ("done")
          |          }
          |        } with { briefly "ch" }
          |      } with { briefly "cs" }
          |    } with { briefly "ce" }
          |  } with { briefly "c" }
          |} with { briefly "d" }
          |""".stripMargin
      errorsIn(src, td.name) mustBe ""
    }
  }
}
