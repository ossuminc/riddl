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

/** The lexical scope a statement body carries is TWO parameters — `lets` and `elements` — and six
  * validators took the first and defaulted the second to empty: `validateComparand`,
  * `checkWhenValueRef`, `validateMatch`, `validatePut`, `validateReturn` and `validateCall`.
  *
  * The consequence had two halves, fixed by one change:
  *
  *   - **`on init`/`on term` parameters** (the instance-identity branch) resolved in the body's
  *     statements but not in any comparison, `when`, `match` or call argument within them, so
  *     `on init(seed: Integer) is { when seed > 5 then … end }` — guarding a lifecycle body on its
  *     own parameter, exactly what the design spec's `on init(custId: …, total: Currency)` example
  *     invites — was a false Error.
  *   - **`foreach` elements** had the SAME hole, and had had it since A25 shipped: `line.qty > 5`
  *     inside a loop was always a false Error. It is a real pre-existing bug that came along for
  *     free, so it is tested on its own terms rather than assumed covered.
  *
  * Every case here reads a name from a scope through a position that previously dropped it. The
  * counter-examples at the end are what keep the fix from being "accept everything": a name that is
  * genuinely not in scope must still Error in each of the same positions.
  *
  * NOTE which positions each scope can actually REACH. A `foreach` element reaches all six. An
  * `on init`/`on term` parameter cannot reach `return` (that statement is function-body-only, and
  * a function has no lifecycle clause) and reaching `put` would require an application-context
  * lifecycle clause; those two are covered by the `foreach` half, which shares the identical code
  * path.
  */
class LexicalScopeThreadingTest extends AbstractValidatingTest {

  private def diagnostics(src: String, origin: String): Messages =
    var captured: Messages = Messages.empty
    pc.withOptions(CommonOptions(showStyleWarnings = false, showWarnings = false)) { _ =>
      parseAndValidate(src, origin, shouldFailOnErrors = false) { (_, _, messages) =>
        captured = messages
        succeed
      }
    }
    // Half these cases assert the ABSENCE of an error, which a fixture that never parsed satisfies
    // for free. Refuse to report on one.
    captured.find(_.message.contains("Expected one of")) match
      case Some(m) => fail(s"fixture did not parse, so any absence proves nothing:\n${m.format}")
      case None    => captured
  end diagnostics

  private def clue(msgs: Messages): String = msgs.map(_.message).mkString("\n")

  /** An entity whose `on init` declares four parameters of distinct categories, with a sibling
    * function to call. NONE of the parameter names collides with a state field — a collision
    * resolves through the state even when the parameter scope does not exist, which is how the
    * original instance-identity fixture hid a declare-only feature.
    */
  private def initParams(body: String): String =
    s"""domain D is {
       |  context C is {
       |    type Amount is Integer
       |    record Cust is { tier: Integer, name: String } with { briefly "cust" }
       |    record Args is { a: Amount } with { briefly "args" }
       |    record Sum is { s: Integer } with { briefly "sum" }
       |    record St is { total: Integer, note: String } with { briefly "st" }
       |    function Score is {
       |      requires record Args
       |      returns record Sum
       |      return record Sum(s = "0")
       |    } with { briefly "f" }
       |    entity E is {
       |      state S of record St is {
       |        handler H is {
       |          on init(seed: Integer, ok: Boolean, tag: String, buyer: Cust) { $body }
       |        } with { briefly "h" }
       |      } with { briefly "s" }
       |    } with { briefly "e" }
       |  } with { briefly "c" }
       |} with { briefly "d" }
       |""".stripMargin

  /** An entity handler iterating a collection of `Line`, with a sibling function to call. */
  private def loop(body: String): String =
    s"""domain D is {
       |  context C is {
       |    type Amount is Integer
       |    record Line is { sku: String, qty: Integer, max: Integer, ok: Boolean }
       |      with { briefly "line" }
       |    record Args is { a: Amount } with { briefly "args" }
       |    record Sum is { s: Integer } with { briefly "sum" }
       |    record St is { lines: many Line, note: String, count: Integer } with { briefly "st" }
       |    command Cmd is { why: String } with { briefly "cmd" }
       |    function Score is {
       |      requires record Args
       |      returns record Sum
       |      return record Sum(s = "0")
       |    } with { briefly "f" }
       |    entity E is {
       |      state S of record St is {
       |        handler H is {
       |          on command Cmd { foreach line in field lines { $body } }
       |        } with { briefly "h" }
       |      } with { briefly "s" }
       |    } with { briefly "e" }
       |  } with { briefly "c" }
       |} with { briefly "d" }
       |""".stripMargin

  /** The `loop` fixture with the WHOLE on-clause body written by the caller, so a statement can sit
    * after the loop rather than inside it.
    */
  private def clauseBody(body: String): String =
    s"""domain D is {
       |  context C is {
       |    record Line is { sku: String, qty: Integer, ok: Boolean } with { briefly "line" }
       |    record St is { lines: many Line, note: String, count: Integer } with { briefly "st" }
       |    command Cmd is { why: String } with { briefly "cmd" }
       |    entity E is {
       |      state S of record St is {
       |        handler H is {
       |          on command Cmd { $body }
       |        } with { briefly "h" }
       |      } with { briefly "s" }
       |    } with { briefly "e" }
       |  } with { briefly "c" }
       |} with { briefly "d" }
       |""".stripMargin

  /** A function whose body iterates, so `return` is reachable with an element in scope. */
  private def loopInFunction(body: String): String =
    s"""domain D is {
       |  context C is {
       |    record Line is { sku: String, qty: Integer } with { briefly "line" }
       |    record Bag is { lines: many Line } with { briefly "bag" }
       |    record Sum is { s: Integer } with { briefly "sum" }
       |    function Total is {
       |      requires record Bag
       |      returns record Sum
       |      foreach line in field lines { $body }
       |      return record Sum(s = "0")
       |    } with { briefly "f" }
       |  } with { briefly "c" }
       |} with { briefly "d" }
       |""".stripMargin

  /** An application context iterating inside its handler, so `put` is reachable with an element in
    * scope. `put` is scope-gated to application-context handlers, which is why this fixture exists
    * separately from `loop`.
    */
  private def loopInApp(body: String): String =
    s"""domain D is {
       |  application context App is {
       |    type Greeting is String
       |    record Line is { sku: String, qty: Integer } with { briefly "line" }
       |    record Bag is { lines: many Line } with { briefly "bag" }
       |    command Refresh is { bag: Bag } with { briefly "cmd" }
       |    group Main is {
       |      output Panel presents type Greeting
       |    } with { briefly "g" }
       |    handler Screen is {
       |      on command Refresh { foreach line in field bag.lines { $body } }
       |    } with { briefly "h" }
       |  } with { briefly "c" }
       |} with { briefly "d" }
       |""".stripMargin

  "an `on init` parameter" should {

    "resolve as a COMPARISON operand" in { (td: TestData) =>
      // The exact shape the re-review reported: guarding a lifecycle body on its own parameter.
      diagnostics(initParams("""when seed > total then do "big" end"""), "param-comparison")
        .justErrors mustBe empty
    }

    "resolve as a bare boolean WHEN condition" in { (td: TestData) =>
      diagnostics(initParams("""when ok then do "yes" end"""), "param-when")
        .justErrors mustBe empty
    }

    "be TYPED in a bare WHEN condition, not merely tolerated" in { (td: TestData) =>
      // The case above passes even WITHOUT the scope: `checkWhenValueRef` is best-effort, so an
      // undetermined category is silently skipped. This is its load-bearing twin -- a `String`
      // parameter as a boolean condition can only be reported once the scope reaches the check.
      val errs = diagnostics(initParams("""when tag then do "no" end"""), "param-when-typed")
        .justErrors
      withClue(clue(errs)) { errs.exists(_.message.contains("must be a Boolean")) mustBe true }
    }

    "resolve as a MATCH subject" in { (td: TestData) =>
      diagnostics(
        initParams("""match tag { case "a" { do "a" } default { do "b" } }"""),
        "param-match"
      ).justErrors mustBe empty
    }

    "resolve as a MATCH comparison-pattern comparand" in { (td: TestData) =>
      diagnostics(
        initParams("""match seed { case > seed { do "a" } default { do "b" } }"""),
        "param-match-comparand"
      ).justErrors mustBe empty
    }

    "resolve as a CALL argument" in { (td: TestData) =>
      diagnostics(
        initParams("""let x = call function Score(a = seed)"""),
        "param-call"
      ).justErrors mustBe empty
    }

    "resolve through a FIELD WALK in a comparison" in { (td: TestData) =>
      diagnostics(initParams("""when buyer.tier > total then do "vip" end"""), "param-walk-comparison")
        .justErrors mustBe empty
    }
  }

  /** A parameter whose name COLLIDES with a state field, and whose type DIFFERS from it.
    *
    * The collision case is the one to watch, for two opposite reasons. Before the instance-identity
    * fix wave, a colliding name resolved through the STATE FIELD in the refMap -- accidentally, but
    * it meant a comparison on it validated. The lexical guard added to `ResolutionPass` removes
    * that refMap entry (deliberately: the coincidence is what made a declare-only feature look
    * complete), so without the threading below the same source now ERRORS.
    *
    * The types differ so the case can tell WHICH binding won. `count` is `String` on the state and
    * `Integer` as the parameter; an ordering comparison against an Integer is legal for the
    * parameter and an Error for the state field.
    */
  private def collide(body: String): String =
    s"""domain D is {
       |  context C is {
       |    record St is { count: String, limit: Integer } with { briefly "st" }
       |    entity E is {
       |      state S of record St is {
       |        handler H is {
       |          on init(count: Integer) { $body }
       |        } with { briefly "h" }
       |      } with { briefly "s" }
       |    } with { briefly "e" }
       |  } with { briefly "c" }
       |} with { briefly "d" }
       |""".stripMargin

  "a parameter that COLLIDES with a state field" should {

    "still resolve in a comparison" in { (td: TestData) =>
      diagnostics(collide("""when count > limit then do "big" end"""), "collide-resolves")
        .justErrors mustBe empty
    }

    "resolve to the PARAMETER, not to the state field" in { (td: TestData) =>
      // If the state field (String) won, this ordering comparison would be
      // "Ordering operator '>' requires a numeric operand but got a string value".
      val errs = diagnostics(collide("""when count > limit then do "big" end"""), "collide-which")
        .justErrors
      withClue(clue(errs)) { errs.exists(_.message.contains("numeric operand")) mustBe false }
    }
  }

  "a `foreach` element" should {

    // Pre-existing A25 hole, fixed by the same change. `foreach` IS used in real models, unlike
    // lifecycle parameters, so this half is the one that could move the corpus.

    "resolve as a COMPARISON operand" in { (td: TestData) =>
      // BOTH operands are element-scoped on purpose. A statement nested inside a `foreach`
      // body is never visited by `ResolutionPass` (see its ForeachStatement arm: "the pass
      // framework does not descend into nested statement bodies"), so a reference to an ordinary
      // STATE field from in here does not resolve either -- a separate, pre-existing gap that has
      // nothing to do with this threading and would silently make this case pass for the wrong
      // reason. Comparing `line.qty` to `line.max` keeps the case about what it claims to be.
      diagnostics(loop("""when line.qty > line.max then do "big" end"""), "foreach-comparison")
        .justErrors mustBe empty
    }

    "resolve as a bare boolean WHEN condition" in { (td: TestData) =>
      diagnostics(loop("""when line.ok then do "yes" end"""), "foreach-when")
        .justErrors mustBe empty
    }

    "be TYPED in a bare WHEN condition, not merely tolerated" in { (td: TestData) =>
      // Load-bearing twin, as above: `line.sku` is a String, reportable only once the element
      // scope reaches `checkWhenValueRef`.
      val errs = diagnostics(loop("""when line.sku then do "no" end"""), "foreach-when-typed")
        .justErrors
      withClue(clue(errs)) { errs.exists(_.message.contains("must be a Boolean")) mustBe true }
    }

    "resolve as a MATCH subject" in { (td: TestData) =>
      diagnostics(
        loop("""match line.sku { case "a" { do "a" } default { do "b" } }"""),
        "foreach-match"
      ).justErrors mustBe empty
    }

    "resolve as a CALL argument" in { (td: TestData) =>
      diagnostics(loop("""let x = call function Score(a = line.qty)"""), "foreach-call")
        .justErrors mustBe empty
    }

    "resolve in a RETURN value" in { (td: TestData) =>
      diagnostics(loopInFunction("""do "seen"""" + "\n      return record Sum(s = line.qty)"), "foreach-return")
        .justErrors mustBe empty
    }

    "resolve in a PUT value" in { (td: TestData) =>
      diagnostics(loopInApp("""put line.sku to output Panel"""), "foreach-put")
        .justErrors mustBe empty
    }
  }

  "the widened scope" should {

    // Counter-examples. A scope that accepted every name would satisfy every case above while
    // being no scope at all, so each newly-reached position keeps a negative twin.

    "still REJECT an unknown name in a comparison" in { (td: TestData) =>
      val errs = diagnostics(initParams("""when nosuchname > total then do "x" end"""), "neg-comparison")
        .justErrors
      withClue(clue(errs)) { errs.exists(_.message.contains("nosuchname")) mustBe true }
    }

    // NO negative twin for the bare-boolean `when` position: `checkWhenValueRef` is deliberately
    // best-effort (an UNDETERMINED category is skipped, mirroring `checkBooleanOperand`), so an
    // unresolvable bare condition has always been silent and this change does not alter that. The
    // comparison twin immediately above covers the same threading through the same function.

    "still REJECT an unknown name as a MATCH subject" in { (td: TestData) =>
      val errs = diagnostics(
        initParams("""match nosuchname { case "a" { do "a" } default { do "b" } }"""),
        "neg-match"
      ).justErrors
      withClue(clue(errs)) { errs.exists(_.message.contains("nosuchname")) mustBe true }
    }

    "still REJECT an unknown name as a CALL argument" in { (td: TestData) =>
      val errs =
        diagnostics(initParams("""let x = call function Score(a = nosuchname)"""), "neg-call")
          .justErrors
      withClue(clue(errs)) { errs.exists(_.message.contains("nosuchname")) mustBe true }
    }

    "still REJECT a field the parameter's type does not have, in a comparison" in {
      (td: TestData) =>
        val errs =
          diagnostics(initParams("""when buyer.nosuch > total then do "x" end"""), "neg-walk")
            .justErrors
        withClue(clue(errs)) { errs.exists(_.message.contains("buyer.nosuch")) mustBe true }
    }

    "still REJECT the element after the loop body has closed" in { (td: TestData) =>
      // The scope is still SCOPED. `line` leaves at the closing brace, in a comparison as
      // everywhere else. Written as a whole clause body, since the reference must sit AFTER the
      // loop rather than inside it.
      val errs = diagnostics(
        clauseBody(
          """foreach line in field lines { do "nothing" }
            |            when line.qty > count then do "x" end""".stripMargin
        ),
        "neg-leak"
      ).justErrors
      withClue(clue(errs)) { errs.exists(_.message.contains("line.qty")) mustBe true }
    }

    "name the newly legal sources in its diagnostic" in { (td: TestData) =>
      // The message enumerated lets, message/state fields, function inputs and constants. Once
      // parameters and foreach elements are in scope, a message that omits them is telling the
      // author to look in the wrong places.
      val errs =
        diagnostics(initParams("""when nosuchname > total then do "x" end"""), "msg-text")
          .justErrors
      val text = errs.map(_.message).mkString("\n")
      withClue(text) {
        text must include("on init")
        text must include("foreach")
      }
    }
  }
}
