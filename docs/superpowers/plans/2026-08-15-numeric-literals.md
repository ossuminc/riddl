# Numeric Literals Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let RIDDL authors write numbers — `record R(1)`, `count > 5`, `constant Max is Integer = 5` — instead of quoting them or naming them.

**Architecture:** One AST node, `NumericLiteral(loc, text)`, storing the literal exactly as written so prettify is byte-exact without a mapping table. It joins the `Value` and `Comparand` unions, and a new `ConstantValue` union widens what a `constant` may hold. Validation infers `Integer`/`Real` from the text and rides the existing assignment-compatibility path, plus three Errors that exploit a literal's statically-known value.

**Tech Stack:** Scala 3.9.0-RC4, sbt 2 (sbt-ossuminc 3.0.3), fastparse, uPickle (JSON), ScalaTest.

**Design doc:** `docs/superpowers/specs/2026-08-14-numeric-literals-design.md`

## Global Constraints

- **Scala 3 syntax only** — `while … do … end while`, `if … then`, no `null` (use `Option`). Build files are Scala 3 too.
- **Every parser change needs a matching EBNF change** in `language/src/main/resources/riddl/grammar/ebnf-grammar.ebnf`, then a GBNF regeneration. CI fails otherwise.
- **RIDDL is fully reflective**: parse ⇒ prettify ⇒ BAST ⇒ JSON. A new AST node touches all four.
- **BAST `FORMAT_REVISION` goes 17 → 18 in this work, exactly once** (Task 6). BACKLOG § 2 reserves one bump shared with A20 and A38; this lands first and spends it. Say so in the Task 6 commit message.
- **Do NOT run `scalafmt` / `scalafmtCheckAll`** — formatting is deliberately deferred to one pass at 2.0 release.
- **Use `<module>/testOnly *`, never bare `test`** — `test`/`tJVM` resolve to `testQuick`, which silently skips suites and reports a false green.
- **Run each module's tests in ONE sbt invocation with `;` separators**, e.g. `sbt -batch "; language/testOnly * ; passes/testOnly *"`. Multiple quoted arguments run only the FIRST and exit 0.
- **Count `Suites: completed N` lines** against the number of modules you asked for. A short count means a red or a skip.
- **The corpus is RED on this branch for three pre-existing reasons.** Take a baseline before attributing any corpus failure to this work.
- Commit messages end with:
  `Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>`

---

## File Structure

| File | Responsibility | Task |
|---|---|---|
| `language/src/main/scala/com/ossuminc/riddl/language/AST.scala` | `NumericLiteral`, `Value`/`Comparand`/`ConstantValue` unions, `Constant`, `Natural`/`Whole` scaladoc | 1, 3, 4, 5 |
| `language/src/main/scala/com/ossuminc/riddl/language/parsing/StatementParser.scala` | `numericLiteral` rule; `value` and `comparand` wiring | 1, 3 |
| `language/src/main/scala/com/ossuminc/riddl/language/parsing/TypeParser.scala` | `constant` rule widening | 4 |
| `language/src/main/resources/riddl/grammar/ebnf-grammar.ebnf` | grammar of record | 1, 3, 4 |
| `passes/src/main/scala/com/ossuminc/riddl/passes/validate/ValidationPass.scala` | dispatch arms, StyleWarning, three Errors | 1, 3, 5 |
| `passes/src/main/scala/com/ossuminc/riddl/passes/resolve/ResolutionPass.scala` | value dispatch arm | 1 |
| `passes/src/main/scala/com/ossuminc/riddl/passes/prettify/RiddlFileEmitter.scala` | constant emission | 4 |
| `language/src/main/scala/com/ossuminc/riddl/language/bast/{package,BASTWriter,BASTReader}.scala` | wire format + revision | 6 |
| `riddlLib/src/main/scala/com/ossuminc/riddl/json/{JsonModel,JsonAstBuilder}.scala` | JSON surface | 7 |

**Value tags already taken** (`BASTWriter.writeValue`, `:1366`): 0 LiteralString, 1 Constructor, 2 ValueRef, 3 GetValue, 4 PromptValue, 5 BooleanExpression (+ sub-tag), 6 Call, 7 Ask, 8 Initiate, 9 SelfValue. **NumericLiteral = 10.**
**Comparand tags taken** (`writeComparand`, `:1466`): 0 ValueRef, 1 GetValue, 2 ConstantRef. **NumericLiteral = 3.**

---

### Task 1: The `NumericLiteral` node, parser rule, and dispatch arms

Delivers `let x = 5`, `record R(1.5)`, `initiate entity Order(1)`.

**Files:**
- Modify: `language/src/main/scala/com/ossuminc/riddl/language/AST.scala` (add node near `PromptValue` `:3218`; `Value` union `:2981`)
- Modify: `language/src/main/scala/com/ossuminc/riddl/language/parsing/StatementParser.scala` (new rule; `value` `:457`)
- Modify: `language/src/main/resources/riddl/grammar/ebnf-grammar.ebnf` (`value` `:319`, new `numeric_literal`)
- Modify: `passes/src/main/scala/com/ossuminc/riddl/passes/validate/ValidationPass.scala` (`:5175`, `:5281`, `:5317`, `:5365`, `:6412`)
- Modify: `passes/src/main/scala/com/ossuminc/riddl/passes/resolve/ResolutionPass.scala` (`:501`)
- Test: `language/src/test/scala/com/ossuminc/riddl/language/parsing/NumericLiteralTest.scala` (abstract)
- Test: `language/src/test/scala-jvm-native/com/ossuminc/riddl/language/parsing/JVMNativeTests.scala` (register concrete subclass)
- Test: `language/src/test/scalajs/com/ossuminc/riddl/language/parsing/JSTests.scala` (register concrete subclass)

**Suites in the shared `src/test/scala` tree are ABSTRACT and are registered by a concrete subclass in each platform aggregator.** A suite that skips the registration is never instantiated, so its cases never appear in the log at all — zero mentions, not even as skipped. This is trap #2 of the three ways a suite passes without running.

**Interfaces:**
- Produces: `AST.NumericLiteral(loc: At, text: String)` with `isInteger: Boolean`, `asLong: Long`, `asBigDecimal: BigDecimal`, `format: String`. Parser rule `StatementParser.numericLiteral[u: P]: P[NumericLiteral]`.

- [ ] **Step 1: Write the failing test**

Create `language/src/test/scala/com/ossuminc/riddl/language/parsing/NumericLiteralTest.scala`:

```scala
/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Finder
import com.ossuminc.riddl.utils.PlatformContext
import org.scalatest.TestData

/** Numeric literals store their text AS WRITTEN so prettify is byte-exact:
  * `1.50`, `007` and `+3` are not recoverable from a parsed number.
  */
// ABSTRACT with `(using PlatformContext)`, matching every sibling in this directory. ScalaTest
// cannot instantiate a suite that takes parameters, so the concrete subclasses live in the two
// platform aggregators; without them this suite silently never runs.
abstract class NumericLiteralTest(using PlatformContext) extends AbstractParsingTest {

  private def firstLetValue(src: String, td: TestData): Value =
    val input = RiddlParserInput(src, td)
    TopLevelParser.parseInput(input, true) match
      case Left(msgs) => fail(s"parse failed:\n${msgs.format}")
      case Right(root) =>
        // `Finder` takes the CONTAINER, not its contents.
        val lets = Finder(root).recursiveFindByType[LetStatement]
        lets.headOption.map(_.expression).getOrElse(fail("no let statement found"))

  private def wrap(expr: String): String =
    s"""domain D is {
       |  context C is {
       |    function F is {
       |      body { let x = $expr }
       |    }
       |  }
       |}
       |""".stripMargin

  "NumericLiteral" should {
    "store an integer as written" in { (td: TestData) =>
      firstLetValue(wrap("5"), td) match
        case nl: NumericLiteral =>
          nl.text mustBe "5"
          nl.isInteger mustBe true
          nl.asLong mustBe 5L
        case other => fail(s"expected NumericLiteral, got $other")
    }

    "preserve trailing zeros in a decimal" in { (td: TestData) =>
      firstLetValue(wrap("1.50"), td) match
        case nl: NumericLiteral =>
          nl.text mustBe "1.50"
          nl.isInteger mustBe false
          nl.format mustBe "1.50"
        case other => fail(s"expected NumericLiteral, got $other")
    }

    "preserve leading zeros" in { (td: TestData) =>
      firstLetValue(wrap("007"), td) match
        case nl: NumericLiteral => nl.text mustBe "007"
        case other              => fail(s"expected NumericLiteral, got $other")
    }

    "preserve an explicit plus sign" in { (td: TestData) =>
      firstLetValue(wrap("+3"), td) match
        case nl: NumericLiteral => nl.text mustBe "+3"
        case other              => fail(s"expected NumericLiteral, got $other")
    }

    "accept a negative decimal" in { (td: TestData) =>
      firstLetValue(wrap("-0.25"), td) match
        case nl: NumericLiteral =>
          nl.text mustBe "-0.25"
          nl.asBigDecimal mustBe BigDecimal("-0.25")
        case other => fail(s"expected NumericLiteral, got $other")
    }

    "accept scientific notation" in { (td: TestData) =>
      firstLetValue(wrap("2E+8"), td) match
        case nl: NumericLiteral =>
          nl.text mustBe "2E+8"
          nl.isInteger mustBe false
        case other => fail(s"expected NumericLiteral, got $other")
    }

    "accept a negative exponent" in { (td: TestData) =>
      firstLetValue(wrap("1.5e-3"), td) match
        case nl: NumericLiteral => nl.text mustBe "1.5e-3"
        case other              => fail(s"expected NumericLiteral, got $other")
    }
  }
}
```

**Note the `(td: TestData)` lambda:** it is meaningful ONLY because `AbstractParsingTest` derives from the `…WithTestData` fixture base. On a plain spec that form constructs a `Function1` and never evaluates the body — a silently-passing test. Do not copy this shape onto a plain `AnyWordSpec`.

- [ ] **Step 2: Register the concrete subclasses**

Without these the suite never runs and says nothing. In
`language/src/test/scala-jvm-native/com/ossuminc/riddl/language/parsing/JVMNativeTests.scala`, beside `class JVMNativeHandlerTest extends HandlerTest`:

```scala
class JVMNativeNumericLiteralTest extends NumericLiteralTest
```

In `language/src/test/scalajs/com/ossuminc/riddl/language/parsing/JSTests.scala`, beside `class JSHandlerTest extends HandlerTest`:

```scala
class JSNumericLiteralTest extends NumericLiteralTest
```

Both aggregators already `import com.ossuminc.riddl.utils.{pc, ec}`, which supplies the `PlatformContext` the abstract suite needs.

- [ ] **Step 3: Run the test to verify it fails**

```bash
sbt -batch "language/testOnly *NumericLiteralTest"
```

Expected: FAIL — `NumericLiteral` does not exist (compile error).

**Confirm the suite actually runs** once it compiles: the log must name `JVMNativeNumericLiteralTest`. If no suite by that name appears, the registration is missing and a green result means nothing.

- [ ] **Step 4: Add the AST node**

In `AST.scala`, immediately after `end PromptValue` (`:3224`):

```scala
  /** A numeric literal — an integer or a real number, written directly rather than quoted or
    * named.
    *
    * **The text is stored AS WRITTEN, and that is the point.** `1.50`, `007` and `+3` are not
    * recoverable from a parsed `Long`/`BigDecimal`, so a parsed payload would make prettify
    * diverge from the source on its first use. Storing text makes the round trip byte-exact by
    * construction, needs one BAST tag rather than two, and keeps `BigDecimal` off the Native and
    * JS paths entirely. Same reasoning as `UniqueId.kindKeyword` and correlation keys.
    *
    * `isEmpty` is deliberately NOT overridden: a literal is a non-container, so the inherited
    * `true` is correct. Emptiness asks whether a node HAS CONTENTS, never whether the author
    * supplied it.
    *
    * @param loc
    *   The location of the literal in the source
    * @param text
    *   The literal exactly as the author wrote it
    */
  // `loc` required (not defaulted): @JSExportTopLevel forbids a non-trailing default and `text`
  // has no empty default — matching PromptValue and the other sibling value nodes.
  @JSExportTopLevel("NumericLiteral")
  case class NumericLiteral(loc: At, text: String) extends RiddlValue:
    override def kind: String = "Numeric Literal"
    def format: String = text

    /** True when the literal has neither a fractional part nor an exponent. `1e3` is therefore
      * NOT an integer literal: it denotes a real, and the type inference in ValidationPass
      * depends on that reading.
      */
    def isInteger: Boolean = !text.exists(c => c == '.' || c == 'e' || c == 'E')
    def asLong: Long = text.toLong
    def asBigDecimal: BigDecimal = BigDecimal(text)
  end NumericLiteral
```

Then widen the `Value` union (`:2981`):

```scala
  type Value =
    LiteralString | PromptValue | Constructor | ValueRef | GetValue | BooleanExpression | Call |
      Ask | SelfValue | Initiate | NumericLiteral
```

- [ ] **Step 5: Add the parser rule**

In `StatementParser.scala`, immediately before `def value` (`:457`):

```scala
  /** A numeric literal — `[+-]? digits [ . digits ] [ (e|E) [+-] digits ]`.
    *
    * Captured as raw text, not converted: the AST stores what the author wrote. No digit
    * separators and no radix prefixes — declined deliberately (Reid, 2026-08-14); both are pure
    * additions later if wanted.
    *
    * There is no lexical ambiguity with identifiers or paths: an identifier must begin with a
    * letter (`simpleIdentifier`), so nothing beginning with a digit or a sign can be one.
    */
  private def numericLiteral[u: P]: P[NumericLiteral] = {
    P(
      Index ~~ (CharIn("+\\-").? ~~ CharIn("0-9").rep(1) ~~
        ("." ~~ CharIn("0-9").rep(1)).? ~~
        (CharIn("eE") ~~ CharIn("+\\-").? ~~ CharIn("0-9").rep(1)).?).! ~~ Index
    ).map { case (start, text, end) => NumericLiteral(at(start, end), text) }
  }
```

Use `~~` (no-whitespace sequencing) throughout — the file imports `MultiLineWhitespace`, so a plain `~` would let `1 . 5` parse as `1.5`.

- [ ] **Step 6: Wire it into `value` — ORDER MATTERS**

Change `value` (`:457`) so `numericLiteral` comes **last, after `booleanExpr`**:

```scala
  def value[u: P]: P[Value] = {
    P(
      literalString.map(ls => ls: Value) |
        promptValue.map(pv => pv: Value) |
        callValue.map(c => c: Value) | // A24: `call function F(args)` (keyword-led)
        askValue.map(a => a: Value) | // `ask query Q of <processor>` (keyword-led)
        initiateValue.map(i => i: Value) | // `initiate <processor>[(args)]` (keyword-led)
        constructor.map(c => c: Value) |
        getValue.map(gv => gv: Value) |
        booleanExpr |
        // LAST, and deliberately: `booleanExpr` must get first refusal so `5 > 3` parses as a
        // comparison. Tried earlier, `numericLiteral` would match `5`, return it as the whole
        // value, and leave `> 3` dangling. `comparison` cuts only AFTER its operator, so a bare
        // `5` backtracks cleanly out of `booleanExpr` and lands here.
        numericLiteral.map(nl => nl: Value)
    )
  }
```

- [ ] **Step 7: Add the dispatch arms**

Five sites, each currently listing `_: LiteralString | _: PromptValue | _: ValueRef | _: BooleanLiteral`. Each ends in a `throw` that names the function, so a missed arm fails loudly at runtime — but `-Werror` will NOT tell you, because a wildcard/throw arm makes the match exhaustive. Add `_: NumericLiteral` to each:

- `ValidationPass.scala:5175` (`countValueFailPoints`) → `0`. A literal cannot fail.
- `ValidationPass.scala:5281` (`stateReadsIn`) → `Seq.empty`. Holds no nested value.
- `ValidationPass.scala:5317` (`initiatesIn`) → `Seq.empty`.
- `ValidationPass.scala:5365` (`asksIn`) → `Seq.empty`.
- `ValidationPass.scala:6412` (`validateValue`) → add `case _: NumericLiteral => ()` beside `case _: BooleanLiteral => ()`. Type conformance is Task 5's job; there is nothing to resolve.
- `ResolutionPass.scala:501` → add to the existing "atoms resolve" arm beside `_: BooleanLiteral`. A literal holds no references.

- [ ] **Step 8: Update the EBNF**

In `ebnf-grammar.ebnf`, add after `prompt_value` (`:321`):

```ebnf
(* Numeric literals - integers and reals, written directly. No digit separators and no radix   *)
(* prefixes: declined deliberately, and pure additions later if wanted.                        *)
numeric_literal = [ "+" | "-" ] digit { digit } [ "." digit { digit } ]
                  [ ( "e" | "E" ) [ "+" | "-" ] digit { digit } ] ;
```

If the grammar has no `digit` rule already, use `/[0-9]/` inline in TatSu style consistent with `natural = /[0-9]+/` (`:31`). Then add `numeric_literal` to the `value` rule (`:319`).

- [ ] **Step 9: Run the tests to verify they pass**

```bash
sbt -batch "; language/testOnly *NumericLiteralTest ; passes/testOnly *"
```

Expected: PASS. Confirm TWO `Suites: completed` summaries.

- [ ] **Step 10: Regenerate and validate the grammars**

```bash
cd language/src/test/scalajvm/python
.venv/bin/python ebnf_tatsu_validator.py
.venv/bin/python ebnf_to_gbnf.py
.venv/bin/python gbnf_validator.py
```

Use `.venv/bin/python` — TatSu is installed there, and Homebrew's `python3` is externally managed.

- [ ] **Step 11: Commit**

```bash
git add language/src/main/scala/com/ossuminc/riddl/language/AST.scala \
        language/src/main/scala/com/ossuminc/riddl/language/parsing/StatementParser.scala \
        language/src/main/resources/riddl/grammar/ \
        language/src/test/scala/com/ossuminc/riddl/language/parsing/NumericLiteralTest.scala \
        passes/src/main/scala/com/ossuminc/riddl/passes/validate/ValidationPass.scala \
        passes/src/main/scala/com/ossuminc/riddl/passes/resolve/ResolutionPass.scala
git commit -m "Let RIDDL authors write a number

Stores the literal AS WRITTEN, so 1.50, 007 and +3 survive prettify
byte-exact; a parsed Long/BigDecimal payload could not have. Parsed LAST
in the value rule so booleanExpr gets first refusal and 5 > 3 stays a
comparison rather than a literal with a dangling tail.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 2: Prettify round trip

Proves the byte-exactness claim end to end.

**Files:**
- Test: `passes/src/test/scala-jvm-native/com/ossuminc/riddl/passes/prettify/NumericLiteralRoundTripTest.scala`

**Interfaces:**
- Consumes: `AST.NumericLiteral` and the parser rule from Task 1.

Prettify emits values through `.format` (`RiddlFileEmitter.scala:256`, `:470`), so `NumericLiteral.format` from Task 1 is already the whole implementation. This task is the proof, and it is a real one: the claim "byte-exact by construction" is worthless untested.

- [ ] **Step 1: Write the failing test**

Model it on `passes/src/test/scala-jvm-native/com/ossuminc/riddl/passes/prettify/InfixAlternationRoundTripTest.scala` — read that file first for the exact prettify invocation and base class in use, and mirror it.

The test must, for each of `5`, `-1`, `+3`, `007`, `1.50`, `-0.25`, `1e3`, `1.5e-3`, `2E+8`:

1. parse a model containing `let x = <literal>`
2. run `PrettifyPass` with `flatten=true`
3. assert the emitted text contains the literal **exactly as written**
4. re-parse the emitted text and assert the `NumericLiteral.text` is unchanged

Assert on the literal's exact text, not merely that re-parsing succeeds — `1.5` re-parses fine after `1.50` is mangled to it, and that is precisely the bug this guards.

- [ ] **Step 2: Run to verify it fails**

```bash
sbt -batch "passes/testOnly *NumericLiteralRoundTripTest"
```

Expected: FAIL — the suite does not exist yet, then passes once written if Task 1 is correct. **If it passes on first run, verify the test actually executes** by dropping `fail("canary")` into a case body and confirming the suite goes red. A green suite is not proof the assertions ran.

- [ ] **Step 3: Fix any divergence found**

If a form does not survive, the bug is in `NumericLiteral.format` or the parser capture, not in the emitter. Fix it there.

- [ ] **Step 4: Run to verify it passes**

```bash
sbt -batch "passes/testOnly *NumericLiteralRoundTripTest"
```

- [ ] **Step 5: Commit**

```bash
git add passes/src/test/scala-jvm-native/com/ossuminc/riddl/passes/prettify/NumericLiteralRoundTripTest.scala
git commit -m "Pin that every numeric literal form survives prettify byte-exact

Asserts the exact text, not that re-parsing succeeds: 1.5 re-parses
perfectly well after 1.50 has been mangled into it, which is the whole
failure this guards.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 3: Widen `Comparand`, with a StyleWarning

Delivers `count > 5`. **This reverses a deliberate A28 decision** — the AST currently says comparands are ref-only "so magic-constant comparisons cannot be constructed at all" (`:3262`) and the parser repeats it (`StatementParser.scala:529-533`). Reid reversed it 2026-08-14 with that reasoning in front of him. **Rewrite both comments; do not leave them contradicting the code.**

**Files:**
- Modify: `AST.scala` (`Comparand` `:3255` and the doc at `:3262`)
- Modify: `StatementParser.scala` (`comparand` `:534`, comment `:529-533`)
- Modify: `ValidationPass.scala` (`validateComparand`, near `:6414`)
- Modify: `ebnf-grammar.ebnf` (the comparand rule)
- Test: `passes/src/test/scala-jvm-native/com/ossuminc/riddl/passes/validate/NumericComparandTest.scala`

**Interfaces:**
- Consumes: `AST.NumericLiteral` (Task 1).
- Produces: widened `type Comparand = ValueRef | GetValue | ConstantRef | NumericLiteral`.

- [ ] **Step 1: Write the failing test**

Create `NumericComparandTest.scala`. Read an existing suite in that directory first for the base class and the message-assertion helpers, then write cases asserting:

1. `when count > 5 then …` parses and validates without an Error.
2. It produces a `StyleWarning` whose message suggests a named constant.
3. `when count > MaxCount` (a `constant`) produces **no** such warning — the negative control.
4. `when count > true` is still a parse error (booleans are atoms, not comparands).

Assert the warning **count is exactly 1** in case 2, not merely `nonEmpty`. Over-firing is the plausible failure here and `nonEmpty` cannot see it.

- [ ] **Step 2: Run to verify it fails**

```bash
sbt -batch "passes/testOnly *NumericComparandTest"
```

Expected: FAIL — `count > 5` does not parse.

- [ ] **Step 3: Widen the union and rewrite the stale doc**

In `AST.scala:3255`:

```scala
  type Comparand = ValueRef | GetValue | ConstantRef | NumericLiteral
```

Replace the sentence at `:3262` ("Comparison operands, by contrast, are narrowed to `Comparand` (ref-only) so magic-constant comparisons cannot be constructed at all") with:

```scala
    * Comparison operands are [[Comparand]] — the refs plus a [[NumericLiteral]]. A28 originally
    * narrowed this to refs ALONE so that "magic-constant comparisons cannot be constructed at
    * all", forcing `count > MaxCount`. Reid reversed that 2026-08-14: in the whole riddl-models
    * corpus there was exactly ONE constant, so the rule had no uptake to protect — plausibly
    * because the only way to name a number was to put it in a string. The intent survives as
    * advice, not structure: a literal comparand draws a StyleWarning suggesting a named constant.
    * Booleans remain excluded — `true`/`false` are boolean ATOMS, so `count > true` is still a
    * parse error.
```

- [ ] **Step 4: Widen the parser**

In `StatementParser.scala:534`, and rewrite the comment above it to match Step 3:

```scala
  private def comparand[u: P]: P[Comparand] = {
    P(
      getValue.map(gv => gv: Comparand) |
        constantRef.map(cr => cr: Comparand) |
        numericLiteral.map(nl => nl: Comparand) |
        (!booleanLiteral ~ valueRef).map(vr => vr: Comparand)
    )
  }
```

`numericLiteral` goes before the `valueRef` arm; `valueRef` is the permissive bare-path fallback and stays last.

- [ ] **Step 5: Add the StyleWarning**

In `ValidationPass.validateComparand`, add:

```scala
      case nl: NumericLiteral =>
        // A28's original rule made this unconstructible; Reid reversed that 2026-08-14 and the
        // intent survives as advice. The population starts at ZERO -- `count > 5` is a parse
        // error before this change, so no existing model can contain one.
        messages.addStyle(
          nl.loc,
          s"Comparison against the literal ${nl.text} would read better as a named constant",
          suggestion = s"Declare `constant SomeName is <type> = ${nl.text}` and compare against it."
        )
```

Match the surrounding call style — check whether siblings use `messages.addStyle` or a `check(...)` helper with a `suggestion` parameter, and follow that.

- [ ] **Step 6: Update the EBNF comparand rule**

Add `numeric_literal` as an alternative in the comparand rule, with a comment recording the reversal.

- [ ] **Step 7: Run tests**

```bash
sbt -batch "; language/testOnly * ; passes/testOnly *"
```

Expected: PASS, two suite summaries. Existing A28 tests asserting `count > 5` is a parse error will now fail — **that is this task's point.** Update them to assert the StyleWarning instead, and note the change in the commit.

- [ ] **Step 8: Regenerate grammars**

```bash
cd language/src/test/scalajvm/python
.venv/bin/python ebnf_tatsu_validator.py && .venv/bin/python ebnf_to_gbnf.py && .venv/bin/python gbnf_validator.py
```

- [ ] **Step 9: Commit**

```bash
git add -A language/ passes/
git commit -m "Let a comparison name a number, and reverse A28's ban on saying so

A28 narrowed comparands to refs so that magic-constant comparisons could
not be constructed at all. The corpus shows why that failed: ONE constant
in 189 models, so the rule had nothing to protect -- plausibly because
naming a number meant quoting it. Reversed by Reid 2026-08-14; the intent
survives as a StyleWarning whose population starts at zero, since the form
is a parse error until this commit.

Both the AST doc and the parser comment argued the opposite case and are
rewritten, not left to contradict the code.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 4: Widen `Constant`, and fix its two-copies-of-one-dispatch defect

Delivers `constant Max: Integer = 5`, `constant Enabled: Boolean = true`, `constant Gravity: Real = prompt("…")`.

**Files:**
- Modify: `AST.scala` (`Constant` `:2952`, `format` `:2962`; new `ConstantValue` type)
- Modify: `TypeParser.scala` (`constant` `:825-832`)
- Modify: `RiddlFileEmitter.scala` (`emitConstant` `:248-258`)
- Modify: `ebnf-grammar.ebnf` (constant rule)
- Test: `language/src/test/scala/com/ossuminc/riddl/language/parsing/ConstantValueTest.scala`

**Interfaces:**
- Consumes: `AST.NumericLiteral` (Task 1).
- Produces: `type ConstantValue = LiteralString | NumericLiteral | BooleanLiteral | PromptValue`; `Constant.value: ConstantValue`.

**No parser work for the separator.** `CommonParser.is` (`:38`) is already `Keywords.keywords(StringIn("is", "are", ":", "=")).?` — the colon has always parsed, as has omitting the separator. All spellings are legal and **none warns**. The only change is prettify emitting `: ` instead of `is `.

- [ ] **Step 1: Write the failing test**

Create `ConstantValueTest.scala` asserting:

1. `constant Max is Integer = 5` parses; `value` is a `NumericLiteral` with `text == "5"`.
2. `constant Max: Integer = 5` parses identically (the colon already worked).
3. `constant Enabled is Boolean = true` parses; `value` is a `BooleanLiteral`.
4. `constant Gravity is Real = prompt("the gravitational constant")` parses; `value` is a `PromptValue`.
5. `constant Name is String = "Fred"` still parses; `value` is a `LiteralString`.
6. `constant Max is Natural = "10"` parses AND produces a **deprecation** message.

For case 6, assert with `TopLevelParser.parseInputWithMessages` — `parseAndValidate` DISCARDS parse-time messages. Pattern: `RecognizedOptionSetTest:98`.

- [ ] **Step 2: Run to verify it fails**

```bash
sbt -batch "language/testOnly *ConstantValueTest"
```

- [ ] **Step 3: Add the `ConstantValue` union and widen `Constant`**

In `AST.scala`, just before `case class Constant` (`:2952`):

```scala
  /** What a `constant` may hold. A narrowing of [[Value]], defined the same way [[Comparand]] is.
    *
    * Deliberately NOT the full [[Value]] union, which would admit `Call`, `Ask` and `Initiate` in
    * a constant. A [[PromptValue]] IS admitted and is a typed hole: the type is declared by the
    * constant and the computation is prose an AI fills in at generation time, so it needs no
    * `as T` ascription and is exempt from the conformance checks.
    */
  type ConstantValue = LiteralString | NumericLiteral | BooleanLiteral | PromptValue
```

Then `Constant.value: ConstantValue`, and fix `format` (`:2962`), which has TWO bugs:

```scala
    /** Format the node to a string.
      *
      * This emitted `const` — not a keyword; `constant` is (`Keywords.scala:584`) — so the text
      * did not re-parse. It survived because `PrettifyVisitor` routes through
      * `RiddlFileEmitter.emitConstant`, which was correct: the same two-copies-of-one-dispatch
      * trap as `WhenStatement.format` vs `emitStatement`, where the exercised copy concealed the
      * broken one. Keep the two in step.
      */
    override def format: String =
      s"constant ${id.format}: ${typeEx.format} = ${value.format}"
```

- [ ] **Step 4: Widen the parser and add the deprecation**

In `TypeParser.scala:825`, replace `literalString` with an alternation over the four arms. Try the keyword-led and punctuation-led forms first and `literalString` last:

```scala
  def constant[u: P]: P[Constant] = {
    P(
      Index ~ Keywords.constant ~ identifier ~ is ~ typeExpression ~
        Punctuation.equalsSign ~ constantValue ~ withMetaData ~/ Index
    ).map { case (start, id, typeEx, value, descriptives, end) =>
      Constant(at(start, end), id, typeEx, value, descriptives.toContents)
    }
  }
```

`constantValue` needs `numericLiteral`, `booleanLiteral` and `promptValue`, all currently `private` in `StatementParser`. Promote exactly those three to package-visible (`private[parsing]`) rather than duplicating them — and add a one-line comment at each saying why it is not fully private, so the next reader does not "tidy" it back.

The deprecation for a quoted number is scoped precisely: it fires only when the declared type is a `NumericType` (or `Boolean`) **and** the string's content parses as a literal of that type. A `String`-typed constant is never warned. Because that test needs the resolved type expression, emit it from the parser's `.map` where `typeEx` is in hand:

```scala
      value match
        case ls: LiteralString if isNumericLike(typeEx, ls.s) =>
          deprecation(
            ls.loc,
            s"A ${typeEx.format} constant should hold a numeric literal, not a string",
            code = None,
            autoFixable = true
          )
        case _ => ()
```

Follow `promptStatement` (`StatementParser.scala:33-42`) for the exact `deprecation(...)` signature, and check whether a `DeprecationCode` should be added in `Messages.scala`. **If you add a `KindOfMessage`, two non-exhaustive matches in `Messages.scala` must be updated or `runMain` dies with a `MatchError` and exit 8.**

- [ ] **Step 5: Emit `:` from prettify**

In `RiddlFileEmitter.scala:253`, change `add(" is ")` to `add(": ")`, and update the comment at `:249-250` from `constant <id> is <type> = <value>` to the colon form, noting that `is`/`are`/`=`/omission all still parse and none warns.

- [ ] **Step 6: Update the EBNF**

The constant rule's value becomes `( literal_string | numeric_literal | boolean_literal | prompt_value )`.

- [ ] **Step 7: Run tests**

```bash
sbt -batch "; language/testOnly * ; passes/testOnly *"
```

Expect existing prettify goldens containing `constant X is …` to fail. Update them to `constant X: …` — that is the intended change. Also expect `ConstantAndMethodBASTRoundTripTest` to still pass; Task 6 changes the wire format, not this task.

- [ ] **Step 8: Regenerate grammars, then commit**

```bash
cd language/src/test/scalajvm/python
.venv/bin/python ebnf_tatsu_validator.py && .venv/bin/python ebnf_to_gbnf.py && .venv/bin/python gbnf_validator.py
cd - && git add -A language/ passes/
git commit -m "Let a constant hold a number, a boolean, or a prompt

The value was a LiteralString, so every constant's value was quoted --
which is the likeliest reason the whole 189-model corpus contains exactly
one constant. ConstantValue narrows Value to the four arms that make sense;
Call, Ask and Initiate are deliberately excluded. The prompt arm is a typed
hole: the constant declares the type, so it needs no ascription.

No parser change for the separator -- CommonParser.is has always accepted
is, are, :, = and omission. Prettify now emits the colon, reading a constant
as a solo field. No spelling warns.

Constant.format emitted `const`, which is not a keyword, so its output did
not re-parse; invisible because PrettifyVisitor routes through emitConstant
instead. Same trap as WhenStatement.format. Both copies now agree.

Breaking change to a public field's type, permitted in a major release and
taken deliberately here rather than as a side effect.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 5: Define the integer types, then enforce them

**Files:**
- Modify: `AST.scala` (`Integer` `:2518`, `Whole` `:2521`, `Natural` `:2524`)
- Modify: `ValidationPass.scala` (new check, called from `validateValue` and `Constant` validation)
- Test: `passes/src/test/scala-jvm-native/com/ossuminc/riddl/passes/validate/NumericLiteralConformanceTest.scala`

**Interfaces:**
- Consumes: `AST.NumericLiteral` (Task 1), `ConstantValue` (Task 4).
- Produces: `private def checkNumericLiteralConformance(nl: NumericLiteral, expected: TypeExpression): Unit` in `ValidationPass`.

**Document before enforcing.** `Natural` and `Whole` have no doc comment, no language-reference entry and no Computational Model definition — nothing in the repository says what they mean. Reid ruled 2026-08-14: `Integer` signed, `Whole ≥ 0`, `Natural ≥ 1`. A check cannot enforce a rule the language never states.

- [ ] **Step 1: Write the failing test**

Create `NumericLiteralConformanceTest.scala` with one case per rule:

1. `constant N is Natural = 10` — no messages.
2. `constant N is Natural = 0` — **Error**, message mentions that `Natural` is positive.
3. `constant N is Natural = -1` — **Error**.
4. `constant W is Whole = 0` — no messages.
5. `constant W is Whole = -1` — **Error**.
6. `constant I is Integer = -1` — no messages.
7. `constant N is Natural = 1.5` — **Error**, message says a whole number is required.
8. `constant R is Real = 1.5` — no messages.
9. `constant R is Real = 5` — no messages (an integer literal is a fine real).
10. **Negative control:** a `let x: Natural = someRealField` reading a `Real`-typed field produces **no** Error — references stay loose; only literals are strict.

Case 10 is the one that stops an over-eager implementation from tightening `isAssignmentCompatible` itself, which would change behaviour far beyond literals.

- [ ] **Step 2: Run to verify it fails**

```bash
sbt -batch "passes/testOnly *NumericLiteralConformanceTest"
```

- [ ] **Step 3: Document the three integer types**

In `AST.scala`, replace the bare declarations at `:2518-2525` with:

```scala
  /** A signed whole number: `… -2, -1, 0, 1, 2 …`.
    *
    * The three integer types were undocumented until 2026-08-14 — nothing in the code, the
    * grammar, the language reference or the Computational Model said what they meant, so a check
    * could not enforce them. Ruled by Reid: `Integer` signed, [[Whole]] non-negative, [[Natural]]
    * positive. Note the grammar's lexical `natural = /[0-9]+/` admits `0` and is UNAFFECTED — it
    * is the rule for version components, not this type.
    */
  @JSExportTopLevel("Integer")
  case class Integer(loc: At) extends PredefinedType with IntegerTypeExpression

  /** A non-negative whole number: `0, 1, 2 …`. The counting type. See [[Integer]]. */
  @JSExportTopLevel("Whole")
  case class Whole(loc: At) extends PredefinedType with IntegerTypeExpression

  /** A positive whole number: `1, 2 …`. The ordinal type; excludes zero. See [[Integer]]. */
  @JSExportTopLevel("Natural")
  case class Natural(loc: At) extends PredefinedType with IntegerTypeExpression
```

- [ ] **Step 4: Implement the check**

Add to `ValidationPass`:

```scala
  /** A literal's value is statically known where a reference's is not, so a literal is held to a
    * STRICTER standard than the surrounding assignment rules. `NumericType.isAssignmentCompatible`
    * (`AST.scala:1912`) deliberately lets ANY numeric accept any other, and that stays true for
    * references — `let x: Natural = someRealField` is unchanged. Only literals are checked here.
    */
  private def checkNumericLiteralConformance(
    nl: NumericLiteral,
    expected: TypeExpression
  ): Unit =
    expected match
      case _: IntegerTypeExpression if !nl.isInteger =>
        messages.addError(
          nl.loc,
          s"${expected.format} requires a whole number, but ${nl.text} has a fractional part",
          suggestion = s"Remove the fractional part, or declare the type as Real or Decimal."
        )
      case _: Natural if nl.isInteger && nl.asLong < 1 =>
        messages.addError(
          nl.loc,
          s"Natural is a positive whole number, but ${nl.text} is not greater than zero",
          suggestion = "Use Whole to admit zero, or Integer to admit negative values."
        )
      case _: Whole if nl.isInteger && nl.asLong < 0 =>
        messages.addError(
          nl.loc,
          s"Whole is a non-negative whole number, but ${nl.text} is negative",
          suggestion = "Use Integer to admit negative values."
        )
      case _ => ()
  end checkNumericLiteralConformance
```

Order matters: the `IntegerTypeExpression` arm must precede the `Natural`/`Whole` arms, since both are `IntegerTypeExpression`s and a fractional value must report the fraction rather than a range violation. Guard the range arms on `isInteger` so `asLong` is never called on a decimal — `"1.5".toLong` throws.

Call it from `Constant` validation with `constant.typeEx`, and from `validateValue`'s `NumericLiteral` arm wherever an expected type is in hand.

- [ ] **Step 5: Run to verify it passes**

```bash
sbt -batch "; language/testOnly * ; passes/testOnly *"
```

- [ ] **Step 6: Commit**

```bash
git add -A language/ passes/
git commit -m "Define Natural and Whole, then hold literals to them

The three integer types had no definition anywhere in the repository -- no
scaladoc, no language reference, no Computational Model entry -- so the
approved 'negative into Natural is an Error' rule had nothing to enforce.
Ruled Integer signed, Whole >= 0, Natural >= 1, and written down, because a
check cannot enforce a rule the language never states.

Literals are deliberately stricter than references. isAssignmentCompatible
lets any numeric accept any other and that stays true -- a Real-typed field
still assigns to a Natural. Only a literal, whose value the compiler can
see, is range-checked. A test pins that asymmetry from the loose side so a
later tightening of isAssignmentCompatible cannot pass unnoticed.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 6: BAST — value tag 10, comparand tag 3, `Constant` payload, revision 18

**Files:**
- Modify: `language/src/main/scala/com/ossuminc/riddl/language/bast/package.scala` (`FORMAT_REVISION` `:77`)
- Modify: `BASTWriter.scala` (`writeValue` `:1366`, `writeComparand` `:1466`, `writeConstant` `:675`)
- Modify: `BASTReader.scala` (`readValue` `:2528`, `readComparand` `:2622`, `readConstantNode` `:735`)
- Modify: `language/input/import/NotImplemented.bast` (regenerated fixture)
- Test: `passes/src/test/scalajvm/com/ossuminc/riddl/passes/NumericLiteralBASTRoundTripTest.scala`

**Interfaces:**
- Consumes: everything from Tasks 1, 3, 4.

**Two tags per the taken-tag tables above: value `10`, comparand `3`.** Do not reuse a number; two node kinds may share a tag only if they write byte-identical payloads, and a known-ambiguous decode is a latent corruption, not a rough edge.

- [ ] **Step 1: Write the failing test**

Model on `passes/src/test/scalajvm/com/ossuminc/riddl/passes/ConstantAndMethodBASTRoundTripTest.scala` — read it for the `roundTrip` helper shape (parse → `BASTWriterPass` → `BASTReader`). Cases:

1. Every literal form from Task 1 survives with `text` unchanged.
2. A `count > 5` comparison survives with its comparand a `NumericLiteral`.
3. `constant Max is Integer = 5` survives with `value` a `NumericLiteral`.
4. `constant Enabled is Boolean = true` survives as a `BooleanLiteral`.
5. `constant Gravity is Real = prompt("…")` survives as a `PromptValue`.
6. `constant Name is String = "Fred"` survives as a `LiteralString`.
7. A model with a literal followed by **several more definitions** decodes fully — a misaligned stream shows up as damage AFTER the literal, not at it.

Case 7 is the one that catches a tag/payload mismatch. A BAST error names where the reader DERAILED, never what derailed it.

- [ ] **Step 2: Run to verify it fails**

```bash
sbt -batch "passes/testOnly *NumericLiteralBASTRoundTripTest"
```

- [ ] **Step 3: Write the writer arms**

In `writeValue` (`:1366`), after the `Initiate` arm:

```scala
      case nl: NumericLiteral =>
        writer.writeU8(10)
        writeLocation(nl.loc)
        writeString(nl.text)
```

In `writeComparand` (`:1466`), after the `ConstantRef` arm:

```scala
      case nl: NumericLiteral =>
        writer.writeU8(3)
        writeLocation(nl.loc)
        writeString(nl.text)
```

In `writeConstant` (`:675`), replace `writeLiteralString(c.value)` with `writeValue(c.value)`. `ConstantValue` is a subset of `Value`, so `writeValue` handles all four arms and the reader gains the discriminator byte it needs to tell them apart.

- [ ] **Step 4: Write the reader arms**

In `readValue` (`:2528`):

```scala
      case 10 => // NumericLiteral -- text as written
        val loc = readLocation()
        NumericLiteral(loc, readString())
```

In `readComparand` (`:2622`):

```scala
      case 3 => // NumericLiteral
        val loc = readLocation()
        NumericLiteral(loc, readString())
```

In `readConstantNode` (`:735`), replace `readLiteralString()` with a `readValue()` narrowed to `ConstantValue`, throwing on anything else — a wrong arm here is corruption, and silently substituting a plausible value is how the `ShownBy` and `Constant`/`Method` bugs became confident wrong answers:

```scala
    val value = readValue() match
      case cv: (LiteralString | NumericLiteral | BooleanLiteral | PromptValue) => cv
      case other =>
        throw new RuntimeException(
          s"Constant value decoded as ${other.getClass.getSimpleName}, which is not a ConstantValue"
        )
```

- [ ] **Step 5: Bump `FORMAT_REVISION` to 18**

In `bast/package.scala:77`, append to the running comment and change the value:

```scala
    // 18 adds numeric literals -- value tag 10 and comparand discriminator 3, both of which a
    // revision-17 reader rejects as invalid -- and changes `Constant` to write a full tagged
    // VALUE rather than a bare literal string, so a revision-17 reader misreads the discriminator
    // byte as the start of the string and derails on everything after it. Incompatible both ways,
    // deliberately. This bump is SHARED: BACKLOG § 2 reserves ONE bump for numeric literals, A20
    // typed holes and A38. It is now SPENT -- neither of those may move it again.
    18 // numeric literals + Constant carries a tagged value
```

- [ ] **Step 6: Regenerate the in-repo BAST fixture**

**From its own directory**, or the file embeds a different source path and the diff stops being a one-field revision bump:

```bash
sbt riddlc/stage
cd language/input/import
/Users/reid/Code/ossuminc/riddl/target/out/jvm/scala-3.9.0-RC4/riddlc/universal/stage/bin/riddlc bastify NotImplemented.riddl
```

Verify: the file should be 93 bytes and `cmp` against the previous version should differ at byte 12 (the revision short) **and nowhere else**. A larger diff means the path got baked in — redo it from the right directory.

- [ ] **Step 7: Run tests**

```bash
sbt -batch "; language/testOnly * ; passes/testOnly *"
```

`IncludeAndImportTest` reddens if the fixture was not regenerated correctly.

- [ ] **Step 8: Commit**

```bash
git add -A language/ passes/
git commit -m "Carry numeric literals through BAST, spending revision 18

Value tag 10 and comparand discriminator 3, both of which a revision-17
reader rejects outright. Constant now writes a full tagged VALUE instead of
a bare literal string -- it can hold four different things as of this
branch, and a reader that assumed a string would take the discriminator
byte as the first character and derail on everything after it. The reader
THROWS on a non-ConstantValue rather than substituting a plausible one:
that substitution is precisely how the Constant/Method and ShownBy bugs
became confident wrong answers instead of failures.

The bump is SHARED and now SPENT. BACKLOG section 2 reserves one revision
for this work, A20 typed holes and A38; neither may move it again.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 7: JSON

**Files:**
- Modify: `riddlLib/src/main/scala/com/ossuminc/riddl/json/JsonModel.scala` (`ValueDto` family `:820-840`, `readValueObj` `:1647`, `writeValue` `:1698`, `ConstantDto` `:467`)
- Modify: `riddlLib/src/main/scala/com/ossuminc/riddl/json/JsonAstBuilder.scala` (`:1492` area, `buildConstant` `:701`)
- Modify: `JSON_COVERAGE.md`
- Test: alongside the existing JSON round-trip tests — find them with `find . -name "*Json*Test.scala"` and follow the established suite.

**Interfaces:**
- Consumes: `AST.NumericLiteral`, `ConstantValue`.
- Produces: `NumericLiteralDto(text: String) extends ValueDto`, serialized as `{ "value": "numericLiteral", "text": "<as written>" }`.

**Store the text, not a JSON number.** `ujson.Num` is a `Double` and would silently destroy `1.50`, `007`, `+3` and any large integer's precision — the same mistake the AST node exists to avoid.

- [ ] **Step 1: Write the failing test**

Assert a JSON round trip preserves `text` exactly for every form from Task 1, plus all four `ConstantValue` arms.

- [ ] **Step 2: Run to verify it fails**

- [ ] **Step 3: Add the DTO**

In `JsonModel.scala`, beside `BooleanLiteralDto` (`:837`):

```scala
  /** `{ "value": "numericLiteral", "text": "1.50" }` — the literal AS WRITTEN.
    *
    * A STRING, deliberately, not a `ujson.Num`: `ujson.Num` is a Double and would destroy `1.50`,
    * `007`, `+3` and the precision of any large integer — exactly what the AST node stores text to
    * avoid.
    */
  case class NumericLiteralDto(text: String) extends ValueDto
```

Add to `readValueObj` (`:1651` area):

```scala
      case "numericLiteral" => NumericLiteralDto(m("text").str)
```

And to `writeValue` (`:1716` area):

```scala
      case NumericLiteralDto(text) =>
        ujson.Obj("value" -> ujson.Str("numericLiteral"), "text" -> ujson.Str(text))
```

- [ ] **Step 4: Widen `ConstantDto`**

Change `value: String` to `value: ValueDto` (`:467-473`). `given valueDtoRW: ReadWriter[ValueDto]` already exists (`:2438`), so the `macroRW` for `ConstantDto` (`:2444`) picks it up with no further work.

Update `buildConstant` (`JsonAstBuilder.scala:701`) to build the value through the existing value-building path instead of wrapping in `LiteralString`, and update the DTO-emitting side to match. Add the `NumericLiteral` arm to the AST→DTO conversion beside `PromptValue` (`:1492`) and `BooleanLiteral` (`:1506`).

- [ ] **Step 5: Run tests, update the ledger**

```bash
sbt -batch "; riddlLib/testOnly * ; passes/testOnly *"
```

Record the new node in `JSON_COVERAGE.md`. The JSON fidelity ratchet is at **0** — it must stay there.

- [ ] **Step 6: Commit**

```bash
git add -A riddlLib/ JSON_COVERAGE.md
git commit -m "Carry numeric literals and the widened constant through JSON

The DTO holds the literal as a STRING, not a ujson.Num: ujson.Num is a
Double and would destroy 1.50, 007, +3 and any large integer's precision --
exactly the loss the AST node stores text to avoid.

ConstantDto.value becomes a ValueDto now that a constant can hold four
different things. valueDtoRW already exists, so macroRW picks it up.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 8: Corpus fixture, cross-platform verification, and the documentation debt

**Files:**
- Create: `language/input/numeric-literals.riddl`
- Modify: `../ossum.tech/docs/riddl/references/language-reference.md` (separate repo — see below)
- Modify: `../RIDDL-Computational-Model.md` (type-system section)

- [ ] **Step 1: Add a corpus fixture**

Create `language/input/numeric-literals.riddl` exercising every new form: each literal shape, a literal comparand, and all four constant value kinds. Without a fixture under `**/input/**/*.riddl`, the CI TatSu and GBNF validators never see the new syntax and the grammar sync is unverified.

- [ ] **Step 2: Run the grammar validators and confirm the count moved**

```bash
cd language/src/test/scalajvm/python
.venv/bin/python ebnf_tatsu_validator.py
```

The TatSu baseline was **105/128**. Adding one fixture should make it **106/129**. If the denominator moved but the numerator did not, the fixture does not parse under the EBNF — fix the grammar, not the fixture.

- [ ] **Step 3: Run the full suite on all three platforms**

```bash
sbt -batch "; language/testOnly * ; passes/testOnly * ; riddlLib/testOnly * ; commands/testOnly * ; riddlc/testOnly *"
sbt -batch "; cJS ; cNative"
```

`tJVM` cannot run as one `;` chain — it aborts at `commands`, so `riddlLib` and `riddlc` never run and the leg looks complete. **Count the `Suites: completed` lines against the five modules asked for.**

Run `cJS` and `cNative` specifically: `@JSExportTopLevel` binds to the very next definition, so inserting `NumericLiteral` near an exported type can silently reattach an annotation — invisible to `cJVM`.

- [ ] **Step 4: Take a corpus baseline, THEN run the corpus**

```bash
git stash
sbt -batch "passes/testOnly *RiddlModelsRoundTripTest" 2>&1 | tail -30   # baseline
git stash pop
sbt -batch "passes/testOnly *RiddlModelsRoundTripTest" 2>&1 | tail -30   # after
```

The corpus is red for three pre-existing reasons. Compare against the baseline; only a NEW failure belongs to this work. **Do not soften a check to green the corpus.**

- [ ] **Step 5: Document the integer types where authors will read them**

The `Natural ≥ 1` / `Whole ≥ 0` ruling must reach the language reference at
`../ossum.tech/docs/riddl/references/language-reference.md` and the Computational Model's type-system section (`../RIDDL-Computational-Model.md`, the "vocabulary of information shapes" passage that already lists the predefined types).

**ossum.tech is a separate repo.** Per the one-instance-per-project rule, do NOT edit it from this session: drop a task file in `../ossum.tech/task/` describing the ruling, the three ranges, and why it matters (a validation Error now enforces it). The Computational Model lives at the `ossuminc/` level and BACKLOG § 0 already carries an item to update it for everything `release/2` changed — add the integer-type ruling to that item rather than making a separate pass.

- [ ] **Step 6: Commit**

```bash
git add language/input/numeric-literals.riddl BACKLOG.md
git commit -m "Cover the new numeric syntax in the CI grammar validators

Without a fixture under input/, the TatSu and GBNF validators never see the
new forms and the parser/EBNF sync is asserted but unverified. Baseline
moves 105/128 -> 106/129; a moved denominator with a static numerator means
the fixture does not parse under the EBNF.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

## Post-Plan: BACKLOG and NOTEBOOK

Not a task — do this when the work lands.

- Remove the numeric-literals item from `BACKLOG.md` § 2.
- Update the "THESE TWO AND A38 SHARE ONE `FORMAT_REVISION` BUMP (17 -> 18)" note to say the bump is **SPENT at 18**, and that A20 and A38 now ride it.
- Add to `NOTEBOOK.md`: the A28 reversal and its corpus evidence (one constant in 189 models); that `Constant.format` emitted `const` and why the duplicated dispatch hid it; and that the three integer types were undefined until this work.
- Graduate to `CLAUDE.md`: the integer-type ranges, and that literals are checked more strictly than references.

## Self-Review Notes

**Spec coverage:** § 1 syntax → Task 1. § 2 AST → Task 1. § 3 unions and the A28 reversal → Tasks 1, 3. § 4 Constant → Task 4. § 5.1 integer-type definitions → Task 5. § 5.2 conformance → Task 5. § 5.3 style → Task 3. § 6 reflection surfaces → Tasks 1, 2, 6, 7. § 7 dispatch sites → Task 1 Step 6. § 8 testing → distributed, plus Task 8. § 9 out of scope → nothing to build.

**Deliberately deferred:** the `Constant.format` fix rides Task 4 rather than being its own task; it is one line and belongs with the `Constant` change that makes the separator wrong anyway.
