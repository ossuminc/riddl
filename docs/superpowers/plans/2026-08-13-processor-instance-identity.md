# Processor Instance Identity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give RIDDL a way to denote a processor instance — to name its identity, to
read its own, to bring one into being, and to address a message to a specific one.

**Architecture:** `Id(P)` widens from Entity to any Processor and becomes the type that
carries instance identity. `self` is a well-known value whose type is a *synthesized
Aggregation* — which is the key simplification, because it means `self.id` and
`let me = self; me.id` both resolve through the existing `ValueRef` path walk with no
bespoke machinery. `initiate` (a value yielding `Id(P)`) and `terminate` (a statement)
invoke `on init` / `on term`, which gain parameter lists. A `tell`'s target instance is
derived structurally from the message's `Id(target)`-typed field.

**Tech Stack:** Scala 3.9.0-RC4, sbt 2.0.6 / sbt-ossuminc 3.0.3, fastparse, ScalaTest,
TatSu (Python) for EBNF validation.

**Source spec:** `docs/superpowers/specs/2026-08-13-processor-instance-identity-design.md`

**Out of scope (filed separately in `BACKLOG.md`):** the cross-context isolation seam
(spec §4) — it needs a counting mode and its own corpus migration; clusterability and
`self.isClustered`; the three Computational Model amendments.

## Global Constraints

- **Scala 3 syntax only.** `while i < end do … end while`, `if … then`, no `null` — use
  `Option(x)`. Build files are Scala 3 too.
- **Backward compatibility:** never remove or change a public signature. New case-class
  parameters must be **defaulted AND trailing**, except where an existing trailing
  default (`contents`, `metadata`) forces a non-defaulted parameter before it — that is
  the A55/A57 precedent and is the only sanctioned exception.
- **`@JSExportTopLevel` binds to the NEXT definition.** Never insert an `enum`/`object`
  between an annotation and its case class. Any AST edit near an exported type must be
  checked with `cJS` and `cNative`, not `cJVM` alone.
- **`Definition.equals` is structural.** Any new field participating in equality must
  keep `loc` at `At.empty` on every surface (parser, BAST, JSON) or write-form makes two
  identical definitions compare unequal.
- **No silent fall-through.** A `case _ => ()` on a sealed hierarchy is forbidden where
  it means "I do not know what this is"; `throw` instead. Enumerate the domain of the
  **function**, not of the nearest-looking type.
- **`FORMAT_REVISION` is bumped ONCE, in Task 1, from 14 to 15.** No later task bumps it
  again. Regenerate `language/input/import/NotImplemented.bast` in Task 1 **from its own
  directory** (see `BACKLOG.md` § 0); done right it is 93 bytes and differs from the
  committed one at byte 12 only.
- **Every parser change needs a matching EBNF change**
  (`language/src/main/resources/riddl/grammar/ebnf-grammar.ebnf`), TatSu re-validated,
  and GBNF regenerated. TatSu needs `{rule}+`, not `rule+`.
- **Reflectivity:** a construct is not done until PrettifyPass emits it AND a
  parse → prettify → re-parse round trip preserves it, AND BAST round-trips it, AND
  JSON covers it.
- **Run validators with `.venv/bin/python`**, never the Homebrew `python3`.
- **Never run `pkill -f sbt`.**
- Do NOT run `scalafmt` or report `scalafmtCheck` — formatting is deliberately deferred
  to the 2.0 release.

## Verification commands (used by every task)

```bash
# Compile all three platforms — cJVM alone will not catch @JSExport breakage
sbt "cJVM; cJS; cNative"

# Run ONE suite (testOnly ignores incremental state; plain `test` resolves to testQuick)
sbt "passes/testOnly *SuiteName"

# EBNF + GBNF (from language/src/test/scalajvm/python)
.venv/bin/python ebnf_tatsu_validator.py    # the number that matters is "Unexpected failures"
.venv/bin/python gbnf_validator.py
```

## File Structure

| file | responsibility in this plan |
|---|---|
| `language/src/main/scala/com/ossuminc/riddl/language/AST.scala` | `UniqueId.kindKeyword`; new `SelfValue`, `Initiate`, `TerminateStatement`; `On*Clause.parameters`; `TellStatement.by` |
| `.../language/parsing/TypeParser.scala` | capture the `Id(kind Name)` keyword |
| `.../language/parsing/StatementParser.scala` | `self`, `initiate`, `terminate`, `tell … by` |
| `.../language/parsing/HandlerParser.scala` | `on init` / `on term` parameter lists |
| `.../language/parsing/Keywords.scala` | `self`, `initiate`, `terminate`, `by` |
| `.../language/bast/package.scala` | `FORMAT_REVISION` → 15 |
| `.../language/bast/BASTWriter.scala` / `BASTReader.scala` | value tags 8/9, statement sub-kind 20, new fields |
| `passes/.../validate/TypeValidation.scala` | `Id(P)` widening + kind match |
| `passes/.../validate/ValidationPass.scala` | `self` legality, initiate/terminate typing, addressing, effect bans |
| `passes/.../prettify/RiddlFileEmitter.scala` | emission for every new construct |
| `passes/.../JsonAstBuilder.scala` | JSON for every new construct |
| `language/src/main/resources/riddl/grammar/ebnf-grammar.ebnf` | grammar of record |

---

## Task 1: `Id(P)` widens to Processor, with the kind keyword captured and checked

**Files:**
- Modify: `language/src/main/scala/com/ossuminc/riddl/language/AST.scala:2416-2426`
- Modify: `language/src/main/scala/com/ossuminc/riddl/language/parsing/TypeParser.scala:379-385`
- Modify: `passes/src/main/scala/com/ossuminc/riddl/passes/validate/TypeValidation.scala:264`
- Modify: `passes/src/main/scala/com/ossuminc/riddl/passes/prettify/RiddlFileEmitter.scala:370`
- Modify: `language/src/main/scala/com/ossuminc/riddl/language/bast/package.scala` (`FORMAT_REVISION`)
- Modify: `language/src/main/scala/com/ossuminc/riddl/language/bast/BASTWriter.scala`, `BASTReader.scala`
- Modify: `passes/.../JsonifierPass.scala` / `JsonAstBuilder.scala` + `JSON_COVERAGE.md`
  (**corrected 2026-08-13**: the first draft of this list omitted JSON, which the
  reflectivity mandate and §5.3 both require — a `kindKeyword` the JSON surface drops
  pushes the coverage ratchet off zero)
- Modify: `passes/.../resolve/ResolutionPass.scala` (**corrected**: it resolves `UniqueId`
  as `Entity` and runs BEFORE `TypeValidation`, so widening validation alone leaves the
  refMap empty and the repository case still fails)
- Modify: `language/src/main/resources/riddl/grammar/ebnf-grammar.ebnf:141`
- Regenerate: `language/input/import/NotImplemented.bast`, `riddl-grammar.gbnf`
- Test: `passes/src/test/scala-jvm-native/com/ossuminc/riddl/passes/validate/UniqueIdKindTest.scala`

**Interfaces:**
- Produces: `UniqueId(loc: At, entityPath: PathIdentifier, kindKeyword: Option[String] = None)`.
  `kindKeyword` holds the literal keyword as written (`"entity"`, `"repository"`, …) or
  `None` for the bare form. Later tasks read it only through `UniqueId`.

- [ ] **Step 1: Write the failing test**

Create `passes/src/test/scala-jvm-native/com/ossuminc/riddl/passes/validate/UniqueIdKindTest.scala`:

```scala
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

/** `Id(P)` names any Processor, and the optional kind keyword must tell the truth.
  *
  * The keyword form is CANONICAL, not deprecated (Reid, 2026-08-13): keyword-name
  * disambiguation is a RIDDL-wide idiom, and `Order` alone could name a context, a message
  * or an entity. Keeping it earns the check below — a keyword that contradicts the
  * resolved kind is a lie a reader would believe.
  */
class UniqueIdKindTest extends AbstractValidatingTest {

  private def diagnostics(src: String, origin: String): Messages =
    var captured: Messages = Messages.empty
    pc.withOptions(CommonOptions(showStyleWarnings = true, showWarnings = true)) { _ =>
      parseAndValidate(src, origin, shouldFailOnErrors = false) { (_, _, messages) =>
        captured = messages
        succeed
      }
    }
    captured

  private def model(idType: String): String =
    s"""domain Dom is {
       |  context Ctx is {
       |    repository Inventory is { ??? } with { briefly "r" }
       |    entity Order is {
       |      state S of record R is { ??? } with { briefly "s" }
       |    } with { briefly "e" }
       |    record R is { key: $idType } with { briefly "rec" }
       |  } with { briefly "c" }
       |} with { briefly "d" }
       |""".stripMargin

  "Id(P)" should {

    "accept a repository, not only an entity" in { (td: TestData) =>
      // Before this change TypeValidation had checkPathRef[Entity], so this was
      // "Path 'Inventory' was not resolved" -- an Entity-shaped question asked of a repository.
      diagnostics(model("Id(repository Inventory)"), "id-repo").justErrors mustBe empty
    }

    "accept the bare form" in { (td: TestData) =>
      diagnostics(model("Id(Order)"), "id-bare").justErrors mustBe empty
    }

    "accept a matching keyword" in { (td: TestData) =>
      diagnostics(model("Id(entity Order)"), "id-entity").justErrors mustBe empty
    }

    "REJECT a keyword that contradicts the resolved kind" in { (td: TestData) =>
      // THE case that justifies keeping the keyword. `Id(entity Inventory)` reads as a
      // promise about Inventory that is false.
      val text = diagnostics(model("Id(entity Inventory)"), "id-mismatch")
        .justErrors.map(_.message).mkString("\n")
      text must include("declared as 'entity'")
      text must include("Repository")
    }
  }
}
```

- [ ] **Step 2: Run the test and watch it fail**

```bash
sbt "passes/testOnly *UniqueIdKindTest"
```

Expected: FAIL — the repository case reports an unresolved path, and the mismatch case
reports no error at all.

- [ ] **Step 3: Add `kindKeyword` to the AST node**

In `AST.scala`, replace the `UniqueId` case class (currently at :2416). The new parameter
is **trailing and defaulted**, which satisfies both the compatibility policy and the
`@JSExportTopLevel` rule:

```scala
  @JSExportTopLevel("UniqueId")
  case class UniqueId(
    loc: At,
    entityPath: PathIdentifier,
    // The kind keyword AS WRITTEN -- `Id(entity Order)` -> Some("entity"), `Id(Order)` -> None.
    // Kept rather than deprecated (Reid, 2026-08-13): keyword-name disambiguation is a
    // RIDDL-wide idiom and a bare `Order` could be a context, a message or an entity. Storing
    // the literal keyword (not an enum) keeps prettify byte-exact without a mapping table.
    kindKeyword: Option[String] = None
  ) extends PredefinedType {
    inline override def kind: String = "Id"

    override def format: String =
      s"$kind(${kindKeyword.map(_ + " ").getOrElse("")}${entityPath.format})"

    override def isAssignmentCompatible(other: TypeExpression): Boolean = {
      super.isAssignmentCompatible(other) || other.isInstanceOf[String_] ||
      other.isInstanceOf[Pattern]
    }
  }
```

- [ ] **Step 4: Capture the keyword in the parser**

In `TypeParser.scala`, replace `uniqueIdType` (:379). `Keyword.*` values are the literal
strings, so matching on them keeps the written form:

```scala
  private def uniqueIdType[u: P]: P[UniqueId] = {
    // The keyword generalizes from `entity` to every processor kind. Longest-first is not
    // needed here (no keyword is a prefix of another) but the alternation order still
    // mirrors ReferenceParser.processorRef for readability.
    def kindKw[u: P]: P[String] = P(
      StringIn(
        Keyword.adaptor, Keyword.context, Keyword.entity,
        Keyword.projector, Keyword.repository, Keyword.streamlet
      ).!
    )
    (Index ~ PredefType.Id ~ Punctuation.roundOpen ~/
      kindKw.? ~ pathIdentifier ~ Punctuation.roundClose ~/ Index) map {
      case (start, kw, pid, end) =>
        UniqueId(at(start, end), pid, kw)
    }
  }
```

The file's implicit whitespace handling already separates the keyword from the path, so no
explicit whitespace combinator is needed — this mirrors how `maybe(Keyword.entity)` worked
before, only capturing rather than discarding.

- [ ] **Step 5: Widen the validation and add the kind check**

In `TypeValidation.scala`, replace line 264:

```scala
      case UniqueId(loc, pid, kindKeyword) =>
        checkPathRef[Processor[?]](pid, parents)
        // The keyword must TELL THE TRUTH. Resolution has already run, so the referent's
        // real kind is available; a keyword that contradicts it is worse than no keyword,
        // because a reader believes it.
        kindKeyword.foreach { kw =>
          resolution.refMap.definitionOf[Processor[?]](pid, parents.head).foreach { referent =>
            val actual = referent.getClass.getSimpleName
            check(
              actual.equalsIgnoreCase(kw),
              s"Id names ${referent.identify}, which is a $actual, but it is declared as '$kw'",
              Error,
              loc,
              suggestion = s"Write 'Id(${actual.toLowerCase} ${pid.format})' or drop the keyword."
            )
          }
        }
```

- [ ] **Step 6: Run the test — it should pass**

```bash
sbt "passes/testOnly *UniqueIdKindTest"
```

Expected: PASS, 4 cases.

- [ ] **Step 7: Emit the keyword in prettify**

In `RiddlFileEmitter.scala` replace line 370:

```scala
      case uid: UniqueId => this.add(s"${uid.format} ")
```

Using `format` rather than rebuilding the string keeps prettify and the AST from drifting.

- [ ] **Step 8: Add a round-trip test**

Append to `UniqueIdKindTest.scala`, inside the class:

```scala
  "the Id keyword" should {
    "survive a prettify round trip" in { (td: TestData) =>
      // Reflectivity: anything that parses must be emitted, and re-parsing must recover it.
      // Without this, `Id(repository Inventory)` silently prettifies to `Id(Inventory)`.
      val src = model("Id(repository Inventory)")
      val (first, second) = prettifyTwice(src, "id-roundtrip")
      first mustBe second
      first must include("Id(repository Inventory)")
    }
  }
```

If `prettifyTwice` does not exist on the test base, use the pattern in
`passes/src/test/.../prettify/IdentifierQuotingRoundTripTest.scala` — parse,
`PrettifyPass(flatten = true)`, re-parse, prettify again, compare the two strings.

- [ ] **Step 9: BAST — bump the revision and carry the keyword**

`language/src/main/scala/com/ossuminc/riddl/language/bast/package.scala`:

```scala
  val FORMAT_REVISION: Short =
    // 14 gave Constant and Method distinct tags. 15 adds the Id kind keyword, `self`,
    // `initiate`, `terminate`, on-clause parameter lists and the tell `by` clause -- every
    // one of which appends bytes an older reader would leave in the stream.
    15 // instance identity: Id keyword, self, initiate/terminate, on-clause params, tell by
```

In `BASTWriter.scala`, find where `UniqueId` is written and append the keyword as an
option after the path. In `BASTReader.scala`, read it back in the same position. Use the
existing `writeOption`/`readOption` helpers with `writeString`/`readString`.

- [ ] **Step 10: Regenerate the BAST fixture FROM ITS OWN DIRECTORY**

```bash
sbt riddlc/stage
cd language/input/import && \
  ../../../target/out/jvm/scala-3.9.0-RC4/riddlc/universal/stage/bin/riddlc bastify NotImplemented.riddl
cd - && cmp <(git show HEAD:language/input/import/NotImplemented.bast) \
             language/input/import/NotImplemented.bast
```

Expected: 93 bytes, and `cmp` reports a difference at **byte 12 only** (the revision
short). A larger diff means the source path got baked in — regenerate from the right
directory.

- [ ] **Step 11: Update the EBNF and validate**

`ebnf-grammar.ebnf:141`:

```ebnf
unique_id_type = "Id" "(" [processor_kind] path_identifier ")" ;
processor_kind = "adaptor" | "context" | "entity" | "projector" | "repository" | "streamlet" ;
```

```bash
cd language/src/test/scalajvm/python
.venv/bin/python ebnf_tatsu_validator.py     # "Unexpected failures" must be absent
.venv/bin/python gbnf_validator.py
```

- [ ] **Step 12: Compile all three platforms**

```bash
sbt "cJVM; cJS; cNative"
```

`cJS`/`cNative` are not optional — `UniqueId` carries `@JSExportTopLevel`.

- [ ] **Step 13: Commit**

```bash
git add language/src/main/scala/com/ossuminc/riddl/language/AST.scala \
        language/src/main/scala/com/ossuminc/riddl/language/parsing/TypeParser.scala \
        language/src/main/scala/com/ossuminc/riddl/language/bast/ \
        language/input/import/NotImplemented.bast \
        language/src/main/resources/riddl/grammar/ebnf-grammar.ebnf \
        passes/src/main/scala/com/ossuminc/riddl/passes/validate/TypeValidation.scala \
        passes/src/main/scala/com/ossuminc/riddl/passes/prettify/RiddlFileEmitter.scala \
        passes/src/test/scala-jvm-native/com/ossuminc/riddl/passes/validate/UniqueIdKindTest.scala
git commit -m "Widen Id(P) to any Processor and check its kind keyword

Id was validated with checkPathRef[Entity], so Id(repository Inventory) reported
an unresolved path. It now names any Processor.

The optional keyword is KEPT and generalized rather than deprecated (Reid,
2026-08-13): keyword-name disambiguation is a RIDDL-wide idiom, and a bare
'Order' could be a context, a message or an entity. Keeping it earns a check --
a keyword contradicting the resolved kind is an Error, because it is a lie a
reader believes rather than a harmless redundancy.

FORMAT_REVISION 14 -> 15.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

## Task 2: `self`, typed as a synthesized Aggregation

**Files:**
- Modify: `AST.scala` (new `SelfValue`, added to the `Value` union)
- Modify: `.../parsing/StatementParser.scala` (`selfValue` in `value`)
- Modify: `.../parsing/Keywords.scala` (`self`)
- Modify: `passes/.../validate/ValidationPass.scala` (legality + field check)
- Modify: `passes/.../prettify/RiddlFileEmitter.scala`, `.../bast/BASTWriter.scala`/`BASTReader.scala`
- Test: `passes/src/test/scala-jvm-native/com/ossuminc/riddl/passes/validate/SelfValueTest.scala`

**Interfaces:**
- Consumes: `UniqueId(loc, path, kindKeyword)` from Task 1.
- Produces:
  - `SelfValue(loc: At, field: Option[Identifier] = None)` — `self` and `self.id`.
  - `object SelfValue { def aggregation(p: Processor[?], loc: At): Aggregation }` — the
    synthesized two-field record. **This is the load-bearing design decision:** because
    `self`'s type is an ordinary `Aggregation`, `let me = self` then `me.id` resolves
    through the existing `ValueRef` walk with no special casing anywhere.
  - `val selfFieldNames: Seq[String] = Seq("id", "version")` — the CLOSED set.

- [ ] **Step 1: Write the failing test**

Create `passes/src/test/scala-jvm-native/com/ossuminc/riddl/passes/validate/SelfValueTest.scala`:

```scala
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

/** `self` denotes the currently executing processor instance.
  *
  * `self` carries what CANNOT be known statically -- that is the admission principle for its
  * fields, and it is why `id` (minted at runtime) and `version` (a composed coordinate) are in
  * while `isClustered` waits for the clusterability spec.
  *
  * The type is a SYNTHESIZED Aggregation rather than a bespoke node, which is what makes
  * `let me = self` + `me.id` work through the ordinary ValueRef walk. A test for that indirect
  * form is therefore worth more than a test for `self.id`: it proves the type is real, not that
  * one parser arm fires.
  */
class SelfValueTest extends AbstractValidatingTest {

  private def diagnostics(src: String, origin: String): Messages =
    var captured: Messages = Messages.empty
    pc.withOptions(CommonOptions(showStyleWarnings = true, showWarnings = true)) { _ =>
      parseAndValidate(src, origin, shouldFailOnErrors = false) { (_, _, messages) =>
        captured = messages
        succeed
      }
    }
    captured

  private def inEntity(body: String): String =
    s"""domain Dom is {
       |  context Ctx is {
       |    command Go is { why: String } with { briefly "c" }
       |    record R is { total: Integer } with { briefly "r" }
       |    entity Order is {
       |      state S of record R is {
       |        handler H is {
       |          on command Go { $body }
       |        } with { briefly "h" }
       |      } with { briefly "s" }
       |    } with { briefly "e" }
       |  } with { briefly "c" }
       |} with { briefly "d" }
       |""".stripMargin

  "self" should {

    "type self.id as Id of the enclosing processor" in { (td: TestData) =>
      diagnostics(inEntity("""let mine = self.id"""), "self-id").justErrors mustBe empty
    }

    "type self.version" in { (td: TestData) =>
      diagnostics(inEntity("""let v = self.version"""), "self-version").justErrors mustBe empty
    }

    "support `let me = self` then `me.id`" in { (td: TestData) =>
      // THE case that proves self's type is a real Aggregation rather than a parser trick.
      // If self were special-cased at the `self.<field>` syntax only, this would fail to resolve.
      diagnostics(inEntity("""let me = self
                             |            let mine = me.id""".stripMargin), "self-let")
        .justErrors mustBe empty
    }

    "REJECT an unknown field" in { (td: TestData) =>
      // The field set is CLOSED. A fall-through would silently accept self.anything.
      val text = diagnostics(inEntity("""let x = self.nonesuch"""), "self-bad-field")
        .justErrors.map(_.message).mkString("\n")
      text must include("nonesuch")
      text must include("id")
      text must include("version")
    }

    "REJECT self outside a processor" in { (td: TestData) =>
      val src =
        """domain Dom is {
          |  function F is {
          |    requires { a: Integer }
          |    returns { b: Integer }
          |    return self.id
          |  } with { briefly "f" }
          |} with { briefly "d" }
          |""".stripMargin
      val text = diagnostics(src, "self-no-processor").justErrors.map(_.message).mkString("\n")
      text must include("self")
    }
  }
}
```

- [ ] **Step 2: Run it and watch it fail**

```bash
sbt "passes/testOnly *SelfValueTest"
```

Expected: FAIL — `self` does not parse.

- [ ] **Step 3: Add the AST node and its synthesized type**

In `AST.scala`, near the other `Value` members (after `Ask`, around :3083). **Do not place
this between an existing `@JSExportTopLevel` and its case class.**

```scala
  /** `self` -- the currently executing processor instance, and `self.<field>` on it.
    *
    * Its TYPE is a synthesized [[Aggregation]] rather than a bespoke node. That is deliberate
    * and load-bearing: because the type is an ordinary record, `let me = self` followed by
    * `me.id` resolves through the SAME `ValueRef` path walk every other value uses, so no
    * resolution rule anywhere needs to know `self` exists.
    *
    * The type cannot be user-nameable -- `self.id` is `Id(Order)` in an Order handler and
    * `Id(Shipping)` in a Shipping one -- so `let me: T = self` has no `T` to write, and `self`
    * itself is not assignable into a message field. Pass `self.id`.
    */
  @JSExportTopLevel("SelfValue")
  case class SelfValue(loc: At, field: Option[Identifier] = None) extends RiddlValue:
    override def kind: String = "Self"
    def format: String = s"self${field.map("." + _.format).getOrElse("")}"
  end SelfValue

  object SelfValue:
    /** The CLOSED set of fields. Adding one is a language change, not a detail: see the
      * admission principle in the design spec -- `self` carries what cannot be known
      * statically, which is why `version` is here and `isClustered` is not.
      */
    val fieldNames: Seq[String] = Seq("id", "version")

    /** The synthesized record type of `self` within `p`. */
    def aggregation(p: Processor[?], path: PathIdentifier): Aggregation =
      Aggregation(
        At.empty,
        Contents(
          Field(At.empty, Identifier(At.empty, "id"), UniqueId(At.empty, path)),
          Field(At.empty, Identifier(At.empty, "version"), String_(At.empty))
        )
      )
  end SelfValue
```

Add `SelfValue` to the `Value` union type where `Ask` and `GetValue` appear.

- [ ] **Step 4: Parse it**

In `Keywords.scala` add `final val self = "self"` beside the other keyword constants, and
`def self[u: P]: P[Unit] = keyword(Keyword.self)` beside the other keyword parsers.

In `StatementParser.scala`, add the parser and wire it into `value` (:448) **before**
`valueRef`, so a bare `self` is not consumed as a path:

```scala
  // `self` -- the running processor instance. `self.id` is parsed as ONE value rather than as a
  // path walk, because the anchor is a keyword and not a name in scope; the FIELD then types
  // through the synthesized aggregation, which is what lets `let me = self; me.id` work.
  private def selfValue[u: P]: P[SelfValue] = {
    P(
      Index ~ Keywords.self ~ (Punctuation.dot ~ identifier).? ~ Index
    )./.map { case (start, field, end) => SelfValue(at(start, end), field) }
  }
```

In `value`:

```scala
        askValue.map(a => a: Value) |
        selfValue.map(sv => sv: Value) |   // before valueRef: `self` is a keyword, not a path
        constructor.map(c => c: Value) |
```

- [ ] **Step 5: Run the test — parsing passes, validation still fails**

```bash
sbt "passes/testOnly *SelfValueTest"
```

Expected: the unknown-field and outside-a-processor cases still FAIL.

- [ ] **Step 6: Validate legality and the field set**

In `ValidationPass.scala`, add a helper and call it from `validateStatement` (:935), beside
the existing `checkStateReadScope(statement, parents)` call:

```scala
  /** `self` is legal only where a Processor encloses it, and only `id`/`version` exist on it.
    *
    * A Saga is NOT a Processor -- the CM calls a saga step "a phase of a saga execution
    * instance" rather than an instance -- so `self` there is an Error naming that reason
    * rather than silently resolving to the enclosing context.
    */
  private def checkSelfValues(statement: Statement, parents: Parents): Unit =
    val enclosing: Option[Processor[?]] = parents.collectFirst { case p: Processor[?] => p }
    statementValues(statement).foreach { v =>
      selfValuesIn(v).foreach { sv =>
        enclosing match
          case None =>
            messages.addError(
              sv.loc,
              "'self' names the running processor instance, so it is only meaningful inside a " +
                "processor (context, entity, projector, repository, streamlet or adaptor)",
              suggestion = "Remove the 'self' reference, or move this into a processor's handler."
            )
          case Some(_) =>
            sv.field.foreach { f =>
              if !SelfValue.fieldNames.contains(f.value) then
                messages.addError(
                  f.loc,
                  s"'self' has no field '${f.value}'; it carries " +
                    SelfValue.fieldNames.map("'" + _ + "'").mkString(" and "),
                  suggestion = s"Use ${SelfValue.fieldNames.map("self." + _).mkString(" or ")}."
                )
            }
      }
    }
  end checkSelfValues
```

`selfValuesIn` is a recursive walk over a value, enumerated exactly like `stateReadsIn` —
including a `case _: Identifier => Seq.empty` arm, because `statementValues` yields a
domain **wider than `Value`** and a missing arm throws at runtime rather than at compile
time. Follow `stateReadsIn`'s arm list exactly and end with the same `throw`, never a
catch-all.

- [ ] **Step 7: Type `self` and `self.<field>` in the resolver**

Where `letType` infers a `let`'s type, add:

```scala
      case sv: SelfValue =>
        enclosingProcessorOf(parents).map { p =>
          val agg = SelfValue.aggregation(p, pathOf(p))
          sv.field match
            case None    => agg
            case Some(f) => agg.fields.find(_.id.value == f.value).map(_.typeEx).getOrElse(agg)
        }
```

`pathOf(p)` builds the `PathIdentifier` naming `p` from the parent chain — the same
construction `SymbolsOutput` uses for a definition's fully-qualified path.

- [ ] **Step 8: Run the test — all five cases pass**

```bash
sbt "passes/testOnly *SelfValueTest"
```

- [ ] **Step 9: Prettify, BAST, JSON**

`RiddlFileEmitter.scala` — `self` appears inside values, which are emitted via `.format`,
so `SelfValue.format` already covers it. Add a round-trip case to `SelfValueTest`
asserting `let me = self` and `let mine = self.id` survive prettify unchanged.

`BASTWriter.writeValue` — add, using free tag **9**:

```scala
      case sv: SelfValue =>
        writer.writeU8(9)
        writeLocation(sv.loc)
        writeOption(sv.field)(writeIdentifierInline)
```

`BASTReader` — mirror it in the same position. `JsonAstBuilder` — add a `SelfValue` case
and a row in `JSON_COVERAGE.md`.

- [ ] **Step 10: EBNF, then compile all platforms**

```ebnf
self_value = "self" ["." identifier] ;
```

Add `self_value` to the `value` alternation. Then:

```bash
cd language/src/test/scalajvm/python && .venv/bin/python ebnf_tatsu_validator.py && \
  .venv/bin/python gbnf_validator.py && cd - && sbt "cJVM; cJS; cNative"
```

- [ ] **Step 11: Commit**

```bash
git add -A && git commit -m "Add 'self', typed as a synthesized Aggregation

'self' names the running processor instance and carries what cannot be known
statically: 'self.id', minted at runtime, and 'self.version', the composed
version coordinate. 'self.isClustered' is deliberately absent -- it would
forward-reference vocabulary the clusterability work has not defined.

Its type is a synthesized Aggregation rather than a bespoke node, which is the
decision the rest hangs on: because the type is an ordinary record, 'let me =
self' then 'me.id' resolves through the SAME ValueRef walk every other value
uses, and no resolution rule anywhere needs to know 'self' exists.

The field set is closed and checked -- 'self.anything' is an Error naming the
two legal fields, not a silent acceptance.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

## Task 3: `on init` and `on term` parameter lists

**Files:**
- Modify: `AST.scala` (`OnInitializationClause`, `OnTerminationClause`)
- Modify: `.../parsing/HandlerParser.scala:42-56`
- Modify: `passes/.../Pass.scala` (traverse case — see the trap below)
- Modify: `passes/.../validate/ValidationPass.scala:478` neighbourhood
- Modify: prettify, BAST, JSON, EBNF
- Test: `passes/src/test/scala-jvm-native/com/ossuminc/riddl/passes/validate/LifecycleParametersTest.scala`

**Interfaces:**
- Consumes: `UniqueId` from Task 1.
- Produces: `OnInitializationClause(loc, parameters: Seq[MethodArgument], contents, metadata)`
  and `OnTerminationClause(loc, parameters: Seq[MethodArgument], contents, metadata)`.
  `parameters` is declared **before** `contents`/`metadata` and **without** a default,
  because those two are defaulted and `@JSExportTopLevel` requires defaults to be trailing
  — the A55/A57 precedent. Tasks 4 and 5 type their arguments against these.

- [ ] **Step 1: Write the failing test**

```scala
/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.utils.{CommonOptions, pc}
import org.scalatest.TestData

/** `on init` and `on term` become invocable, so they need parameters.
  *
  * `on term`'s leading parameter is REQUIRED to be Id(this processor): it is invoked from
  * outside, so the caller must say which instance. `on init` has no such parameter -- there is
  * no instance yet, and the identity is minted by initiating.
  */
class LifecycleParametersTest extends AbstractValidatingTest {

  private def diagnostics(src: String, origin: String): Messages =
    var captured: Messages = Messages.empty
    pc.withOptions(CommonOptions(showStyleWarnings = true, showWarnings = true)) { _ =>
      parseAndValidate(src, origin, shouldFailOnErrors = false) { (_, _, messages) =>
        captured = messages
        succeed
      }
    }
    captured

  private def entityWith(clauses: String): String =
    s"""domain Dom is {
       |  context Ctx is {
       |    record R is { total: Integer } with { briefly "r" }
       |    entity Order is {
       |      state S of record R is {
       |        handler H is { $clauses } with { briefly "h" }
       |      } with { briefly "s" }
       |    } with { briefly "e" }
       |  } with { briefly "c" }
       |} with { briefly "d" }
       |""".stripMargin

  "on init parameters" should {
    "parse and validate" in { (td: TestData) =>
      diagnostics(
        entityWith("""on init(total: Integer) is { do "start" }"""), "init-params"
      ).justErrors mustBe empty
    }

    "remain optional" in { (td: TestData) =>
      diagnostics(entityWith("""on init is { do "start" }"""), "init-none")
        .justErrors mustBe empty
    }

    "REJECT a parameter naming an undefined type" in { (td: TestData) =>
      // THE case proving parameters are TRAVERSED. They are held in a FIELD, not in
      // `contents`, and Pass.traverse's generic Branch arm walks contents ONLY -- so without
      // its own traverse case this model validates clean while naming a type that need not
      // exist. Same shape as Correlation.timeoutStatements.
      val text = diagnostics(
        entityWith("""on init(x: Nonexistent) is { do "start" }"""), "init-bad-type"
      ).justErrors.map(_.message).mkString("\n")
      text must include("Nonexistent")
    }
  }

  "on term parameters" should {
    "accept a leading Id of the enclosing processor" in { (td: TestData) =>
      diagnostics(
        entityWith("""on term(oid: Id(entity Order), why: String) is { do "end" }"""),
        "term-ok"
      ).justErrors mustBe empty
    }

    "REJECT a missing leading id parameter" in { (td: TestData) =>
      val text = diagnostics(
        entityWith("""on term(why: String) is { do "end" }"""), "term-no-id"
      ).justErrors.map(_.message).mkString("\n")
      text must include("first parameter")
      text must include("Id(")
    }
  }
}
```

- [ ] **Step 2: Run it and watch it fail**

```bash
sbt "passes/testOnly *LifecycleParametersTest"
```

- [ ] **Step 3: Add the parameters to both AST nodes**

```scala
  @JSExportTopLevel("OnInitializationClause")
  case class OnInitializationClause(
    loc: At,
    // Declared BEFORE contents/metadata and WITHOUT a default: @JSExportTopLevel requires
    // defaulted parameters to be trailing, and those two are defaulted. Same rule as A55's
    // `binding` and A57's `envelopeType`.
    parameters: Seq[MethodArgument],
    contents: Contents[Statements] = Contents.empty[Statements](),
    metadata: Contents[MetaData] = Contents.empty[MetaData]()
  ) extends OnClause {
```

Same shape for `OnTerminationClause`. Update every construction site the compiler reports.

- [ ] **Step 4: Parse the lists**

In `HandlerParser.scala`, replace `onInitClause` and `onTermClause`. Reuse the existing
method-argument parser so nothing new is invented:

```scala
  private def lifecycleParameters[u: P]: P[Seq[MethodArgument]] = {
    P(
      (Punctuation.roundOpen ~/ methodArgument.rep(0, Punctuation.comma) ~
        Punctuation.roundClose).?
    ).map(_.map(_.toSeq).getOrElse(Seq.empty))
  }

  private def onInitClause[u: P](set: StatementsSet): P[OnInitializationClause] = {
    P(
      Index ~ Keywords.onInit ~ lifecycleParameters ~ is ~/ pseudoCodeBlock(set) ~
        withMetaData ~/ Index
    ).map { case (start, params, statements, descriptives, end) =>
      OnInitializationClause(at(start, end), params, statements.toContents, descriptives.toContents)
    }
  }
```

`onTermClause` is identical with `Keywords.onTerm` and `OnTerminationClause`. Import
`methodArgument` from `TypeParser` if it is not already in scope.

- [ ] **Step 5: Add the traverse case — the silent-breakage guard**

In `passes/.../Pass.scala`, add a case for both clauses **BEFORE** the generic
`case branch: Branch[?]` arm:

```scala
      // Parameters live in a FIELD, not in `contents`, and the generic Branch arm below walks
      // `contents` only. Without this the parameter types are never resolved and never
      // validated -- the model validates clean while naming types that need not exist.
      case oic: OnInitializationClause =>
        oic.parameters.foreach(a => processValue(a, parents))
        traverseBranch(oic, oic.contents, parents)
      case otc: OnTerminationClause =>
        otc.parameters.foreach(a => processValue(a, parents))
        traverseBranch(otc, otc.contents, parents)
```

Match the exact helper names used by the neighbouring `Correlation` case, which solves the
same problem.

- [ ] **Step 6: Validate `on term`'s leading parameter**

In `ValidationPass.scala`, near the existing `OnActivationClause` handling (:478):

```scala
      case otc: OnTerminationClause =>
        val enclosing = parents.collectFirst { case p: Processor[?] => p }
        enclosing.foreach { p =>
          val ok = otc.parameters.headOption.exists { a =>
            a.typeEx match
              case uid: UniqueId => uid.entityPath.value.lastOption.contains(p.id.value)
              case _             => false
          }
          check(
            ok,
            s"'on term' in ${p.identify} must declare its first parameter as the id of the " +
              s"instance to terminate",
            Error,
            otc.loc,
            suggestion = s"Write 'on term(id: Id(${p.id.value}), …) is { … }'."
          )
        }
```

- [ ] **Step 7: Run the tests — all five pass**

```bash
sbt "passes/testOnly *LifecycleParametersTest"
```

- [ ] **Step 8: Prettify, BAST, JSON, EBNF**

Prettify: emit `on init(a: T, b: U) is {` — where the clause keyword is written, append
the parameter list when non-empty. Add a round-trip case asserting parameters survive.

BAST: append `writeSeq(parameters)(writeMethodArgument)` after the location in both
clauses; mirror in the reader. **No FORMAT_REVISION bump** — Task 1 already moved it to 15.

EBNF:

```ebnf
on_init_clause = "on" "init" [ "(" [method_arguments] ")" ] is pseudo_code_block [with_metadata] ;
on_term_clause = "on" "term" "(" method_arguments ")" is pseudo_code_block [with_metadata] ;
```

- [ ] **Step 9: Compile all platforms and commit**

```bash
sbt "cJVM; cJS; cNative"
git add -A && git commit -m "Give 'on init' and 'on term' parameter lists

They are the constructor and destructor, and become invocable in the next
commits, so they need to declare what they take. 'on term' REQUIRES a leading
Id parameter because it is invoked from outside -- the caller must say which
instance. 'on init' has none: there is no instance yet, and the identity is
minted by initiating.

Parameters are held in a FIELD, not in contents, so Pass.traverse gets its own
case for both clauses. Without it the generic Branch arm walks contents only,
the parameter types are never resolved, and a model naming an undefined
parameter type validates clean -- the same trap as Correlation.timeoutStatements.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

## Task 4: `initiate` — the value that mints an instance

**Files:**
- Modify: `AST.scala` (`Initiate`), `StatementParser.scala`, `Keywords.scala`
- Modify: `ValidationPass.scala` (argument typing), prettify, BAST (value tag **8**), JSON, EBNF
- Test: `passes/src/test/scala-jvm-native/com/ossuminc/riddl/passes/validate/InitiateTerminateTest.scala`

**Interfaces:**
- Consumes: `OnInitializationClause.parameters` (Task 3), `UniqueId` (Task 1).
- Produces: `Initiate(loc: At, processor: ProcessorRef[Processor[?]], args: Seq[ConstructorArg])`,
  a `Value` whose inferred type is `UniqueId(At.empty, <path of processor>)`. Task 5's
  `TerminateStatement` mirrors its shape.

- [ ] **Step 1: Write the failing test** (the file also serves Task 5)

```scala
/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.utils.{CommonOptions, pc}
import org.scalatest.TestData

/** `initiate` mints an instance; `terminate` ends one.
  *
  * Neither contradicts activate-on-first-message (CM line 999): construction still completes
  * only when `on init` finishes, and what was missing was the invocation. The codebase already
  * partitions the two -- `on init` is once-ever, `on activate` is per-rehydration.
  */
class InitiateTerminateTest extends AbstractValidatingTest {

  private def diagnostics(src: String, origin: String): Messages =
    var captured: Messages = Messages.empty
    pc.withOptions(CommonOptions(showStyleWarnings = true, showWarnings = true)) { _ =>
      parseAndValidate(src, origin, shouldFailOnErrors = false) { (_, _, messages) =>
        captured = messages
        succeed
      }
    }
    captured

  private def model(orderInit: String, callerBody: String): String =
    s"""domain Dom is {
       |  context Ctx is {
       |    command Go is { why: String } with { briefly "c" }
       |    record R is { total: Integer } with { briefly "r" }
       |    entity Order is {
       |      state S of record R is {
       |        handler OH is { $orderInit } with { briefly "oh" }
       |      } with { briefly "s" }
       |    } with { briefly "e" }
       |    entity Caller is {
       |      state CS of record R is {
       |        handler CH is { on command Go { $callerBody } } with { briefly "ch" }
       |      } with { briefly "cs" }
       |    } with { briefly "ce" }
       |  } with { briefly "c" }
       |} with { briefly "d" }
       |""".stripMargin

  "initiate" should {
    "accept matching arguments and yield an Id" in { (td: TestData) =>
      diagnostics(
        model("""on init(total: Integer) is { do "start" }""",
              """let oid = initiate entity Order(1)"""),
        "initiate-ok"
      ).justErrors mustBe empty
    }

    "accept the no-parens form when on init takes nothing" in { (td: TestData) =>
      diagnostics(
        model("""on init is { do "start" }""", """let oid = initiate entity Order"""),
        "initiate-bare"
      ).justErrors mustBe empty
    }

    "REJECT the wrong argument count" in { (td: TestData) =>
      val text = diagnostics(
        model("""on init(total: Integer) is { do "start" }""",
              """let oid = initiate entity Order(1, 2)"""),
        "initiate-arity"
      ).justErrors.map(_.message).mkString("\n")
      text must include("2")
      text must include("1")
    }

    "REJECT parens where on init declares no parameters" in { (td: TestData) =>
      val text = diagnostics(
        model("""on init is { do "start" }""", """let oid = initiate entity Order(1)"""),
        "initiate-extra"
      ).justErrors.map(_.message).mkString("\n")
      text must include("no parameters")
    }
  }

  "terminate" should {
    "accept a leading id argument" in { (td: TestData) =>
      diagnostics(
        model("""on init is { do "start" }
                |          on term(oid: Id(entity Order)) is { do "end" }""".stripMargin,
              """let oid = initiate entity Order
                |            terminate entity Order(oid)""".stripMargin),
        "terminate-ok"
      ).justErrors mustBe empty
    }

    "REJECT arguments that do not match on term" in { (td: TestData) =>
      val text = diagnostics(
        model("""on init is { do "start" }
                |          on term(oid: Id(entity Order)) is { do "end" }""".stripMargin,
              """terminate entity Order"""),
        "terminate-arity"
      ).justErrors.map(_.message).mkString("\n")
      text must include("1")
    }
  }
}
```

- [ ] **Step 2: Run it and watch it fail**

```bash
sbt "passes/testOnly *InitiateTerminateTest"
```

- [ ] **Step 3: Add the AST node**

```scala
  /** `initiate <processor>(args)` -- bring an instance into being and yield its identity.
    *
    * Creation still completes only when `on init` finishes, so this does NOT introduce a second
    * way for an instance to exist (CM line 999): it supplies the invocation that was missing.
    * The value is the newly minted `Id(P)`, which is system-generated and opaque -- a BUSINESS
    * key belongs in `on init`'s parameters and lives in state.
    */
  @JSExportTopLevel("Initiate")
  case class Initiate(
    loc: At,
    processor: ProcessorRef[Processor[?]],
    args: Seq[ConstructorArg]
  ) extends RiddlValue:
    override def kind: String = "Initiate"
    def format: String =
      val argList = if args.isEmpty then "" else args.map(_.format).mkString("(", ", ", ")")
      s"initiate ${processor.format}$argList"
  end Initiate
```

Add `Initiate` to the `Value` union.

- [ ] **Step 4: Parse it**

`Keywords.scala`: `final val initiate = "initiate"` plus its parser. In
`StatementParser.scala`, beside `askValue`:

```scala
  // Parens are OPTIONAL and present exactly when there are arguments (Reid, 2026-08-13: one
  // keyword, not two). ARITY IS NOT CHECKED HERE -- a parser error() preempts the whole pass
  // chain, so the argument diagnostics live in ValidationPass, which is also the only place
  // that has resolved `on init`.
  private def initiateValue[u: P]: P[Initiate] = {
    P(
      Index ~ Keywords.initiate ~/ processorRef ~
        (Punctuation.roundOpen ~/ constructorArg.rep(0, Punctuation.comma) ~
          Punctuation.roundClose).? ~/ Index
    )./.map { case (start, pRef, args, end) =>
      Initiate(at(start, end), pRef, args.map(_.toSeq).getOrElse(Seq.empty))
    }
  }
```

Wire into `value` before `constructor`.

- [ ] **Step 5: Type-check the arguments**

In `ValidationPass.scala`, add a check reached from the value walk:

```scala
  private def checkInitiate(init: Initiate, parents: Parents): Unit =
    resolution.refMap.definitionOf[Processor[?]](init.processor.pathId, parents.head).foreach { p =>
      val declared: Seq[MethodArgument] =
        p.handlers.flatMap(_.clauses).collectFirst { case oic: OnInitializationClause =>
          oic.parameters
        }.getOrElse(Seq.empty)
      if declared.isEmpty && init.args.nonEmpty then
        messages.addError(
          init.loc,
          s"${p.identify} declares 'on init' with no parameters, but ${init.args.size} " +
            s"argument(s) were supplied",
          suggestion = s"Write 'initiate ${init.processor.format}' with no parentheses."
        )
      else if declared.size != init.args.size then
        messages.addError(
          init.loc,
          s"${p.identify} declares 'on init' with ${declared.size} parameter(s), but " +
            s"${init.args.size} argument(s) were supplied",
          suggestion =
            s"Supply ${declared.size}: ${declared.map(a => s"${a.name}: ${a.typeEx.format}").mkString(", ")}."
        )
      else
        // Reuse the EXISTING per-argument helper (ValidationPass.scala:5616) rather than
        // writing a second one — its scaladoc records that two copies were free to drift, so a
        // rule tightened for constructors would silently not apply here. It wants Seq[Field],
        // and a lifecycle clause declares Seq[MethodArgument], so adapt rather than fork:
        val asFields: Seq[Field] = declared.map { a =>
          Field(a.loc, Identifier(a.loc, a.name), a.typeEx)
        }
        checkArgumentTypes(init.args, asFields, "parameter", parents, lets)
    }
  end checkInitiate
```

`lets` is the in-scope `let`-local list already threaded through `checkStatementScopes`;
pass the same value the neighbouring `validateConstructor` call site passes.

- [ ] **Step 6: Run the four initiate cases — they pass**

```bash
sbt "passes/testOnly *InitiateTerminateTest -- -z initiate"
```

- [ ] **Step 7: Prettify, BAST (value tag 8), JSON, EBNF, compile, commit**

BAST `writeValue`:

```scala
      case init: Initiate =>
        writer.writeU8(8)
        writeLocation(init.loc)
        writeProcessorRef(init.processor)
        writeSeq(init.args)(writeConstructorArg)
```

EBNF:

```ebnf
initiate_value = "initiate" processor_ref [ "(" [constructor_args] ")" ] ;
```

```bash
sbt "cJVM; cJS; cNative"
git add -A && git commit -m "Add 'initiate', the value that mints an instance

RIDDL had no way to bring an instance into being, so no Id value could ever
exist and instance addressing would have been inert. 'initiate' supplies the
invocation and evaluates to the new Id(P).

This does not introduce a second way for an instance to exist: construction
still completes only when 'on init' finishes. The codebase already partitions
creation from rehydration -- 'on init' is once-ever, 'on activate' is
per-rehydration -- so the design fits the existing lifecycle vocabulary rather
than competing with it.

Arity and argument types are checked in ValidationPass, not the parser: a
parse-time error() preempts the whole pass chain, and validation is the only
place that has resolved 'on init'.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

## Task 5: `terminate` — the statement that ends one

**Files:** as Task 4, plus `StatementParser.anyDefStatements`
**Test:** the `terminate` cases already written in `InitiateTerminateTest`.

**Interfaces:**
- Consumes: `OnTerminationClause.parameters` (Task 3), `Initiate` (Task 4, for its shape).
- Produces: `TerminateStatement(loc: At, processor: ProcessorRef[Processor[?]], args: Seq[ConstructorArg])`
  with `override def canFail: Boolean = true`.

- [ ] **Step 1: Confirm the terminate tests still fail**

```bash
sbt "passes/testOnly *InitiateTerminateTest -- -z terminate"
```

- [ ] **Step 2: Add the AST node**

```scala
  /** `terminate <processor>(id, args)` -- end an instance by invoking its `on term`.
    *
    * A STATEMENT, not a value: termination produces nothing. It can fail (it may race a
    * passivation), so it joins the can-fail census alongside send/tell/call/yield/put/get.
    */
  @JSExportTopLevel("TerminateStatement")
  case class TerminateStatement(
    loc: At,
    processor: ProcessorRef[Processor[?]],
    args: Seq[ConstructorArg]
  ) extends Statement {
    override def kind: String = "Terminate Statement"
    override def canFail: Boolean = true
    def format: String =
      val argList = if args.isEmpty then "" else args.map(_.format).mkString("(", ", ", ")")
      s"terminate ${processor.format}$argList"
  }
```

- [ ] **Step 3: Parse it, and add it to the statement set**

```scala
  private def terminateStatement[u: P]: P[TerminateStatement] = {
    P(
      Index ~ Keywords.terminate ~/ processorRef ~
        (Punctuation.roundOpen ~/ constructorArg.rep(0, Punctuation.comma) ~
          Punctuation.roundClose).? ~/ Index
    )./.map { case (start, pRef, args, end) =>
      TerminateStatement(at(start, end), pRef, args.map(_.toSeq).getOrElse(Seq.empty))
    }
  }
```

Add to `anyDefStatements` in GROUP 3b, beside `putStatements` / `returnStatements`:

```scala
        putStatements(set) | returnStatements(set) | terminateStatement |
```

**Do NOT** gate it here with a `keywordAlt ~/ Fail | base` prefix — that form breaks `rep`
termination. Restrictions are applied by *subtracting inside* `base`, and this construct's
bans live in validation (Task 7) rather than the parser, because its sibling `initiate` is
a **value** and `value` does not carry a `StatementsSet`. Splitting the two bans across
layers would be worse than putting both in one.

- [ ] **Step 4: Type-check the arguments**

Mirror `checkInitiate` as `checkTerminate`, reading `OnTerminationClause.parameters`, and
call it from `validateStatement`'s per-kind match.

- [ ] **Step 5: Run the whole suite — all six cases pass**

```bash
sbt "passes/testOnly *InitiateTerminateTest"
```

- [ ] **Step 6: Prettify, BAST (statement sub-kind 20), JSON, EBNF, compile, commit**

```scala
  def writeTerminateStatement(s: TerminateStatement): Unit = {
    writer.writeU8(NODE_STATEMENT)
    writer.writeU8(20) // Terminate statement — next free sub-kind after 19
    writeLocation(s.loc)
    writeProcessorRef(s.processor)
    writeSeq(s.args)(writeConstructorArg)
  }
```

Add `case s: TerminateStatement => writeTerminateStatement(s)` to the writer's statement
dispatch and the mirrored arm to the reader. Prettify:

```scala
      case ts: TerminateStatement =>
        addLine(ts.format)
```

EBNF: `terminate_statement = "terminate" processor_ref [ "(" [constructor_args] ")" ] ;`
added to the `statement` alternation.

```bash
sbt "cJVM; cJS; cNative"
git add -A && git commit -m "Add the 'terminate' statement

The destructor half: invokes 'on term' on a named instance. A statement rather
than a value, because termination produces nothing, and it joins the can-fail
census because it may race a passivation.

Its bans live in validation rather than the parser, deliberately: its sibling
'initiate' is a VALUE, and `value` carries no StatementsSet to gate on, so
parser-gating one and validating the other would split one rule across two
layers.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

## Task 6: Instance addressing — structural derivation and `by`

**Files:**
- Modify: `AST.scala` (`TellStatement.by`), `StatementParser.scala:223`, `ValidationPass.scala`
- Modify: prettify, BAST, JSON, EBNF
- Test: `passes/src/test/scala-jvm-native/com/ossuminc/riddl/passes/validate/TellAddressingTest.scala`

**Interfaces:**
- Consumes: `UniqueId` (Task 1).
- Produces: `TellStatement(loc, msg, processorRef, by: Option[Identifier] = None)` —
  `by` is trailing and defaulted, which is safe because `TellStatement` has no other
  defaulted parameters.

- [ ] **Step 1: Write the failing test**

```scala
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

/** Which INSTANCE a `tell` reaches, derived from the message's Id(target)-typed field.
  *
  * Structural derivation wins over naming the field at the send site because ONE message may be
  * told to two DIFFERENT processor types, and each target then needs its own address; structural
  * derivation gives each one for free.
  *
  * A missing address is a CompletenessWarning, not an Error, and that is a measurement rather
  * than a preference: riddl-models holds 7,556 tells against SEVEN Id-typed fields, so an Error
  * would redden essentially every model and is not mechanically migratable.
  */
class TellAddressingTest extends AbstractValidatingTest {

  private def diagnostics(src: String, origin: String): Messages =
    var captured: Messages = Messages.empty
    pc.withOptions(
      CommonOptions(showStyleWarnings = true, showWarnings = true, showCompletenessWarnings = true)
    ) { _ =>
      parseAndValidate(src, origin, shouldFailOnErrors = false) { (_, _, messages) =>
        captured = messages
        succeed
      }
    }
    captured

  private def model(shipFields: String, tellStmt: String): String =
    s"""domain Dom is {
       |  context Ctx is {
       |    command Go is { why: String } with { briefly "g" }
       |    command Ship is { $shipFields } with { briefly "s" }
       |    record R is { total: Integer } with { briefly "r" }
       |    entity Order is {
       |      state OS of record R is {
       |        handler OH is { on command Ship { do "ship" } } with { briefly "oh" }
       |      } with { briefly "os" }
       |    } with { briefly "e" }
       |    entity Caller is {
       |      state CS of record R is {
       |        handler CH is { on command Go { $tellStmt } } with { briefly "ch" }
       |      } with { briefly "cs" }
       |    } with { briefly "ce" }
       |  } with { briefly "c" }
       |} with { briefly "d" }
       |""".stripMargin

  "tell addressing" should {

    "derive the address from the single Id(target) field" in { (td: TestData) =>
      val msgs = diagnostics(
        model("orderId: Id(entity Order)",
              """let oid = initiate entity Order
                |            tell command Ship(orderId = oid) to entity Order""".stripMargin),
        "addr-derived"
      )
      msgs.justErrors mustBe empty
      msgs.map(_.message).mkString("\n") must not include "which Order instance"
    }

    "NOT mistake a reply-to field for the address" in { (td: TestData) =>
      // The property the whole scheme rests on: address and reply-to are told apart BY TYPE.
      // Id(entity Caller) is not a candidate for a tell to Order, so this must still be a
      // single unambiguous derivation and not an ambiguity error.
      val msgs = diagnostics(
        model("orderId: Id(entity Order), from: Id(entity Caller)",
              """let oid = initiate entity Order
                |            tell command Ship(orderId = oid, from = self.id) to entity Order""".stripMargin),
        "addr-replyto"
      )
      msgs.justErrors mustBe empty
    }

    "warn when the message carries no Id(target) field" in { (td: TestData) =>
      val text = diagnostics(
        model("why: String", """tell command Ship(why = "x") to entity Order"""),
        "addr-missing"
      ).filter(_.kind == Messages.CompletenessWarning).map(_.message).mkString("\n")
      text must include("Ship")
      text must include("Order")
    }

    "REJECT an ambiguous derivation without 'by'" in { (td: TestData) =>
      val text = diagnostics(
        model("fromOrder: Id(entity Order), toOrder: Id(entity Order)",
              """tell command Ship(fromOrder = f, toOrder = t) to entity Order"""),
        "addr-ambiguous"
      ).justErrors.map(_.message).mkString("\n")
      text must include("fromOrder")
      text must include("toOrder")
    }

    "accept 'by' to disambiguate" in { (td: TestData) =>
      diagnostics(
        model("fromOrder: Id(entity Order), toOrder: Id(entity Order)",
              """tell command Ship(fromOrder = f, toOrder = t) to entity Order by toOrder"""),
        "addr-by"
      ).justErrors mustBe empty
    }

    "REJECT 'by' naming a field that is not Id(target)" in { (td: TestData) =>
      val text = diagnostics(
        model("orderId: Id(entity Order), why: String",
              """tell command Ship(orderId = o, why = "x") to entity Order by why"""),
        "addr-by-wrong"
      ).justErrors.map(_.message).mkString("\n")
      text must include("why")
    }

    "stay SILENT for a repository target" in { (td: TestData) =>
      // A repository is a singleton, reached by path -- there is nothing to distinguish, so
      // the diagnostic is entity-only even though the MECHANISM is uniform.
      val src =
        """domain Dom is {
          |  context Ctx is {
          |    command Save is { why: String } with { briefly "s" }
          |    command Go is { why: String } with { briefly "g" }
          |    record R is { total: Integer } with { briefly "r" }
          |    repository Inv is {
          |      handler IH is { on command Save { do "save" } } with { briefly "ih" }
          |    } with { briefly "repo" }
          |    entity Caller is {
          |      state CS of record R is {
          |        handler CH is {
          |          on command Go { tell command Save(why = "x") to repository Inv }
          |        } with { briefly "ch" }
          |      } with { briefly "cs" }
          |    } with { briefly "ce" }
          |  } with { briefly "c" }
          |} with { briefly "d" }
          |""".stripMargin
      diagnostics(src, "addr-repo")
        .filter(_.kind == Messages.CompletenessWarning)
        .map(_.message).mkString("\n") must not include "instance"
    }
  }
}
```

- [ ] **Step 2: Run it and watch it fail**

```bash
sbt "passes/testOnly *TellAddressingTest"
```

- [ ] **Step 3: Add `by` to the AST node and the parser**

```scala
  case class TellStatement(
    loc: At,
    msg: MessageRef | Constructor | ValueRef,
    processorRef: ProcessorRef[Processor[?]],
    // The disambiguator, needed only when the message carries more than one Id(target) field.
    // Trailing and defaulted, which is safe here because TellStatement has no other defaults.
    by: Option[Identifier] = None
  ) extends Statement {
```

`format` becomes:

```scala
    def format: String =
      s"tell ${msg.format} to ${processorRef.format}${by.map(b => s" by ${b.format}").getOrElse("")}"
```

Parser (:223):

```scala
  private def tellStatement[u: P]: P[TellStatement] = {
    P(
      Index ~ Keywords.tell ~/ deliverableMessageValue ~/ to ~ processorRef ~
        (Keywords.by ~/ identifier).? ~/ Index
    )./.map { (start, msg, proc, by, end) => TellStatement(at(start, end), msg, proc, by) }
  }
```

Add `final val by = "by"` and its parser to `Keywords.scala` if absent.

- [ ] **Step 4: Implement derivation and its diagnostics**

In `ValidationPass.scala`, called from `validateStatement`'s `TellStatement` arm:

```scala
  /** Derive which INSTANCE a tell addresses: the message's field typed Id(target).
    *
    * Uniform across processor kinds -- an Id(projector Foo) field is used if present -- but the
    * "no address" DIAGNOSTIC is entity-only, because an entity is the only multiply-instantiated
    * processor (Reid, 2026-08-13). A repository is reached by path and has nothing to
    * distinguish.
    */
  private def checkTellAddressing(ts: TellStatement, parents: Parents): Unit =
    val target = resolution.refMap.definitionOf[Processor[?]](ts.processorRef.pathId, parents.head)
    val msgType = messageTypeOf(ts.msg, parents)
    (target, msgType) match
      case (Some(p), Some(mt)) =>
        val candidates = fieldsOf(mt).filter { f =>
          f.typeEx match
            case uid: UniqueId => uid.entityPath.value.lastOption.contains(p.id.value)
            case _             => false
        }
        ts.by match
          case Some(name) =>
            check(
              candidates.exists(_.id.value == name.value),
              s"'by ${name.value}' must name a field of ${mt.identify} typed " +
                s"'Id(${p.id.value})'; candidates are " +
                (if candidates.isEmpty then "none" else candidates.map(_.id.value).mkString(", ")),
              Error,
              name.loc,
              suggestion = s"Add a field typed 'Id(${p.id.value})' to ${mt.identify}."
            )
          case None =>
            if candidates.size > 1 then
              messages.addError(
                ts.loc,
                s"${mt.identify} carries ${candidates.size} fields typed 'Id(${p.id.value})' " +
                  s"(${candidates.map(_.id.value).mkString(", ")}), so which instance this " +
                  s"addresses is ambiguous",
                suggestion = s"Add 'by ${candidates.head.id.value}' to choose one."
              )
            else if candidates.isEmpty && p.isInstanceOf[Entity] then
              messages.addCompleteness(
                ts.loc,
                s"${mt.identify} carries no field typed 'Id(${p.id.value})', so which " +
                  s"${p.id.value} instance this addresses is unspecified",
                suggestion =
                  s"Add a field typed 'Id(${p.id.value})' to ${mt.identify} and populate it."
              )
      case _ => () // unresolved target or message: other checks already report it
  end checkTellAddressing
```

`messageTypeOf` and `fieldsOf` follow the existing helpers used by the on-clause message
checks — reuse rather than duplicate.

- [ ] **Step 5: Run the suite — all seven cases pass**

```bash
sbt "passes/testOnly *TellAddressingTest"
```

- [ ] **Step 6: Prettify, BAST, JSON, EBNF, compile, commit**

Prettify already routes through `format`, but confirm with a round-trip case that
`by toOrder` survives. BAST: append `writeOption(s.by)(writeIdentifierInline)` to
`writeTellStatement` and mirror it in the reader.

```ebnf
tell_statement = "tell" deliverable_message_value "to" processor_ref [ "by" identifier ] ;
```

```bash
sbt "cJVM; cJS; cNative"
git add -A && git commit -m "Address a tell's target instance structurally

The instance is the message's field typed Id(target), found without annotation;
'by <field>' disambiguates when two qualify. Address and reply-to are told apart
BY TYPE, which is what lets the common case need no syntax at all.

Structural derivation beats naming the field at the send site because ONE
message may be told to two DIFFERENT processor types, each needing its own
address -- which structural derivation supplies for free and the alternatives
cannot express without collapsing into it.

A missing address is a CompletenessWarning, and that is measured rather than
preferred: riddl-models holds 7,556 tells against SEVEN Id-typed fields, so an
Error would redden essentially every model and cannot be migrated mechanically.
The mechanism is uniform across processor kinds; only the diagnostic is
entity-only, because an entity is the only multiply-instantiated processor.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

## Task 7: Effect bans for `initiate` and `terminate`

**Files:**
- Modify: `passes/.../validate/ValidationPass.scala`
- Test: `passes/src/test/scala-jvm-native/com/ossuminc/riddl/passes/validate/InstanceEffectBanTest.scala`

**Interfaces:** consumes `Initiate` (Task 4) and `TerminateStatement` (Task 5).

- [ ] **Step 1: Write the failing test**

Cover all three bans **and** the positive case for each, because a ban with no legal
counter-example is indistinguishable from one applied too widely:

```scala
/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.utils.{CommonOptions, pc}
import org.scalatest.TestData

/** `initiate` and `terminate` are EFFECTS, so three existing bans apply.
  *
  * Each ban is paired with a POSITIVE case. Without the positive half, a ban wrongly applied to
  * everything would still look green -- the lesson from A70, where "legal in the timeout block"
  * was the case that mattered.
  */
class InstanceEffectBanTest extends AbstractValidatingTest {

  private def errorsIn(src: String, origin: String): String =
    var captured: Messages = Messages.empty
    pc.withOptions(CommonOptions(showStyleWarnings = true, showWarnings = true)) { _ =>
      parseAndValidate(src, origin, shouldFailOnErrors = false) { (_, _, messages) =>
        captured = messages
        succeed
      }
    }
    captured.justErrors.map(_.message).mkString("\n")

  /** Every model below declares the same `entity Order` so `initiate entity Order` resolves;
    * only the CONTEXT the offending statement sits in differs, which is the variable under test.
    */
  private def wrap(inner: String): String =
    s"""domain Dom is {
       |  context Ctx is {
       |    command Go is { why: String } with { briefly "g" }
       |    event Started is { oid: Id(entity Order) } with { briefly "ev" }
       |    command Record is { oid: Id(entity Order) } with { briefly "cmd" }
       |    record R is { total: Integer } with { briefly "r" }
       |    entity Order is {
       |      state OS of record R is {
       |        handler OH is { on command Go { do "go" } } with { briefly "oh" }
       |      } with { briefly "os" }
       |    } with { briefly "e" }
       |$inner
       |  } with { briefly "c" }
       |} with { briefly "d" }
       |""".stripMargin

  private def functionModel(stmt: String): String = wrap(
    s"""    function F is {
       |      requires { a: Integer }
       |      returns { b: Integer }
       |      $stmt
       |      return a
       |    } with { briefly "fn" }""".stripMargin)

  private def entityModel(stmt: String): String = wrap(
    s"""    entity Caller is {
       |      state CS of record R is {
       |        handler CH is { on command Go { $stmt } } with { briefly "ch" }
       |      } with { briefly "cs" }
       |    } with { briefly "ce" }""".stripMargin)

  private def activateModel(stmt: String): String = wrap(
    s"""    entity Caller is {
       |      state CS of record R is {
       |        handler CH is { on activate is { $stmt } } with { briefly "ch" }
       |      } with { briefly "cs" }
       |    } with { briefly "ce" }""".stripMargin)

  private def foldModel(stmt: String): String = wrap(
    s"""    repository Repo is {
       |      handler RH is { on command Record { do "save" } } with { briefly "rh" }
       |    } with { briefly "repo" }
       |    projector Proj is {
       |      record PR is { oid: Id(entity Order) } with { briefly "pr" }
       |      correlation Corr by oid yields command Record is {
       |        on event Started { $stmt }
       |      } times out after "1 hour" { do "give up" }
       |    } with { briefly "proj" }""".stripMargin)

  private def timeoutModel(stmt: String): String = wrap(
    s"""    repository Repo is {
       |      handler RH is { on command Record { do "save" } } with { briefly "rh" }
       |    } with { briefly "repo" }
       |    projector Proj is {
       |      record PR is { oid: Id(entity Order) } with { briefly "pr" }
       |      correlation Corr by oid yields command Record is {
       |        on event Started { do "fold" }
       |      } times out after "1 hour" { let oid = initiate entity Order
       |                                   $stmt }
       |    } with { briefly "proj" }""".stripMargin)

  "initiate/terminate" should {
    "be BANNED in a function body" in { (td: TestData) =>
      errorsIn(functionModel("""let x = initiate entity Order"""), "ban-fn") must
        include("function")
    }

    "be LEGAL in an ordinary entity handler" in { (td: TestData) =>
      errorsIn(entityModel("""let x = initiate entity Order"""), "ok-entity") mustBe ""
    }

    "be BANNED in an on activate clause" in { (td: TestData) =>
      errorsIn(activateModel("""let x = initiate entity Order"""), "ban-activate") must
        include("activat")
    }

    "be BANNED in a projector correlation fold" in { (td: TestData) =>
      errorsIn(foldModel("""let x = initiate entity Order"""), "ban-fold") must include("fold")
    }

    "be LEGAL in a correlation timeout block" in { (td: TestData) =>
      // The timeout block EXISTS to have an effect (design spec §6.7), so banning it there
      // would leave it useless. This is the case that distinguishes a correct ban.
      errorsIn(timeoutModel("""terminate entity Order(oid)"""), "ok-timeout") mustBe ""
    }
  }
}
```

Note that `timeoutModel` needs `terminate` to name an instance, so it binds one with
`initiate` inside the timeout block first — the block is legal for both constructs, which
is exactly the point of that case. If the correlation syntax in `foldModel`/`timeoutModel`
does not parse, copy the exact shape from `CorrelationTest` rather than adjusting it by
guesswork; A70's grammar is fussy about the mandatory `times out after` clause.

- [ ] **Step 2: Run it and watch it fail** — `sbt "passes/testOnly *InstanceEffectBanTest"`

- [ ] **Step 3: Implement the ban**

```scala
  /** Both constructs are effects. Three contexts forbid effects, for three different reasons.  */
  private def checkInstanceEffectScope(statement: Statement, parents: Parents): Unit =
    val offenders: Seq[(At, String)] =
      (statement match
        case ts: TerminateStatement => Seq(ts.loc -> "terminate")
        case _                      => Seq.empty
      ) ++ statementValues(statement).flatMap(initiatesIn).map(_.loc -> "initiate")

    if offenders.isEmpty then return

    val banned: Option[String] = parents.head match
      case _: OnActivationClause | _: OnPassivationClause =>
        Some("an activation or passivation clause, which must be side-effect-free")
      case _ =>
        if parents.exists(_.isInstanceOf[Function]) then
          Some("a function body, which is pure and may not create or destroy instances")
        else if inCorrelationFold(parents) then
          Some("a correlation fold, which must be pure so re-runs are safe")
        else None

    banned.foreach { where =>
      offenders.foreach { case (loc, kw) =>
        messages.addError(loc, s"'$kw' is not allowed in $where")
      }
    }
  end checkInstanceEffectScope
```

`initiatesIn` is a recursive value walk enumerated exactly like `stateReadsIn`, **including
the `case _: Identifier => Seq.empty` arm** and the same terminal `throw`. `inCorrelationFold`
reuses whatever A70 already uses to identify a fold — do not write a second predicate.

Call it from `validateStatement` beside `checkStateReadScope`.

- [ ] **Step 4: Run the suite — all five pass**, then commit

```bash
sbt "passes/testOnly *InstanceEffectBanTest"
git add -A && git commit -m "Ban initiate/terminate where effects are not allowed

Three contexts, three reasons: a correlation fold must be pure so re-runs are
safe (A70); activation and passivation must be transparent; a function is pure.

Every ban is paired with a positive case, including the correlation TIMEOUT
block, which exists to have an effect -- without that case a ban wrongly applied
to the whole correlation would still look green.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

## Task 8: Certify, measure the corpus, and record what was learned

**Files:**
- Modify: `CLAUDE.md`, `NOTEBOOK.md`, `BACKLOG.md`
- Modify: `task/2026-08-13-tell-to-an-entity-cannot-name-which-instance.md`, then move to `task/done/`
- Modify: `.claude/skills/rc/SKILL.md` (floors), `JSON_COVERAGE.md`

- [ ] **Step 1: Full certification from clean, under a throwaway cache**

```bash
sbt -batch shutdown
sbt -batch "show sbtVersion"    # MUST equal project/build.properties
rm -rf /tmp/sbt-certify-identity
sbt --sbt-cache /tmp/sbt-certify-identity -batch \
  'clean; cJVM; cJS; cNative; tJVM; tJS; tNative' 2>&1 | tee /tmp/certify.log
```

- [ ] **Step 2: Verify the run actually ran**

```bash
grep -c "Suites: completed" /tmp/certify.log     # MUST be 19
grep -c "No tests to run"   /tmp/certify.log     # MUST be 0
grep -c "aborted [1-9]"     /tmp/certify.log     # MUST be 0
```

Sum `Tests: succeeded N` per row: first 7 = JVM, next 5 = JS, last 7 = Native. Floors are
**JVM 2267 / JS 712 / Native 1552**. **Work out the expected delta BEFORE reading it** — a
suite in `scala-jvm-native` moves JVM and Native but not JS; one in `scalajvm` moves JVM
only. A delta that does not reconcile is a skipping bug, not a total to accept.

- [ ] **Step 3: Corpus A/B**

```bash
sbt riddlc/stage
RC=target/out/jvm/scala-3.9.0-RC4/riddlc/universal/stage/bin/riddlc
for m in $(find ../riddl-models -name '*.conf'); do
  $RC from "$m" validate 2>&1 | grep -c '\[completeness\]'
done | paste -sd+ | bc
```

**The expected delta is exactly one thing:** new entity-addressing CompletenessWarnings.
Any new *error*, or any warning of another kind, means a rule is too broad — find it before
committing. Confirm 189/189 models still validate.

- [ ] **Step 4: Raise the RC floors to the certified counts**

Edit the table in `.claude/skills/rc/SKILL.md` and add the date to the "Raised" note. Never
lower a floor to make a run pass.

- [ ] **Step 5: Record what is durably true, and what it taught**

`CLAUDE.md` — add to "AST / Language Internals": that `Id(P)` names any Processor and its
keyword is canonical and kind-checked; that `self`'s type is a synthesized Aggregation and
why (it is what makes `let me = self; me.id` need no special casing); that `initiate`
supplies the invocation `on init` always lacked without introducing a second creation path;
and that addressing is structural with `by` as the disambiguator.

`NOTEBOOK.md` — a session entry: what the design taught, especially that the missing piece
was *instantiation*, not addressing, and that the corpus measurement (7,556 tells, 7 Id
fields) is what set the severity.

- [ ] **Step 6: Close the riddlg task**

Append a `## Results` section to
`task/2026-08-13-tell-to-an-entity-cannot-name-which-instance.md` stating which of the
three candidate answers was taken (option 3 — a first-class way to denote an instance) and
that their standing ask about reading an entity's system-provided id is answered by
`self.id`. Say plainly that the cross-context seam is filed separately and NOT delivered
here. Then `mv` it to `task/done/`.

- [ ] **Step 7: Commit**

```bash
git add -A && git commit -m "Certify instance identity, and record what it taught

JVM/JS/Native certified from clean under a throwaway cache; corpus A/B shows the
one expected delta, entity-addressing completeness warnings, with 189/189 models
still validating.

Closes riddl-generator's task: their option 3 (a first-class way to denote an
instance) is what shipped, and their separate ask about reading an entity's
system-provided id is answered by self.id. The cross-context isolation seam is
filed in BACKLOG and deliberately not delivered here.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

## Self-review notes

**Spec coverage.** §1.1 → Task 1. §1.2 (axiom, ULID/definition-ULID distinction) → Task 8's
`CLAUDE.md` entry; no code, since `isAssignmentCompatible` is deliberately unchanged.
§1.3 → Task 2. §2.1 → Task 3. §2.2 → Tasks 4–5. §2.3 → Task 4's commit message plus the CM
amendment filed in `BACKLOG.md`. §2.4 → Task 7, plus the `canFail` overrides in Tasks 4–5.
§3 → Task 6. §4 → **out of scope**, filed. §5 → distributed across every task. §6 → Task 8.

**Known gap, deliberate:** `initiate`/`terminate` joining the can-fail census means
`countValueFailPoints` needs an `Initiate` arm and the statement dispatch a
`TerminateStatement` arm. These are folded into Tasks 4 and 5 as part of "compile all
platforms" — the compiler will not catch them, but the `throw` at the end of each dispatch
will, on the first test that exercises one. If a task's tests pass without touching those
functions, add the arms anyway before committing.
