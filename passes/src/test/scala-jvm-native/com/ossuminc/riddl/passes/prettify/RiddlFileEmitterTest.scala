/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.prettify

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.{Contents, *}
import com.ossuminc.riddl.language.At
import com.ossuminc.riddl.passes.prettify.RiddlFileEmitter
import com.ossuminc.riddl.utils.{AbstractTestingBasis, URL, pc}

import java.nio.file.{Files, Path}

/** Tests For RiddlFileEmitter */
class RiddlFileEmitterTest extends AbstractTestingBasis {

  private val path: URL = URL.fromCwdPath("prettify/target/test/rfe.out")
  val rfe = RiddlFileEmitter(path)

  "RiddlFileEmitter" should {
    "add literal strings" in {
      val string = LiteralString(At.empty, "string")
      val strings = Seq(string)
      val twoStrings = Seq(string, string)
      rfe.clear()
      rfe.add(strings)
      // No surrounding spaces as of 2026-08-25. The caller supplies its own separator, and the
      // padding here produced `term Name is  "text" ` -- two spaces and a trailing one. The
      // MULTI-string branch below is unchanged: it lays out its own lines, so it owns its spacing.
      rfe.toString mustBe "\"string\""
      rfe.clear()
      rfe.add(twoStrings)
      rfe.toString mustBe "\n\"string\"\n\"string\"\n"
    }
    "add string" in {
      rfe.clear()
      rfe.add("string")
      rfe.toString mustBe "string"
    }
    "add option" in {
      rfe.clear()
      rfe.add(Some("string"))(identity)
      rfe.toString mustBe "string"
      rfe.clear()
      rfe.add(Option.empty[String])(identity)
      rfe.toString mustBe ""
    }
    "add indent" in {
      rfe.clear()
      rfe.addIndent()
      rfe.toString mustBe ""
      rfe.incr
      rfe.addIndent()
      rfe.toString mustBe "  "
    }
    "outdent catches unmatched" in {
      rfe.clear()
      intercept[IllegalArgumentException] { rfe.decr }
    }
    "starts a definition with/out a brace" in {
      rfe.clear()
      // Named "domain" on purpose: a definition keyword used as an identifier must come back
      // QUOTED, because `domain domain is …` no longer re-parses. The emitter's job is source
      // that round-trips, not source that looks like what was typed.
      val defn = Domain(At.empty, Identifier(At.empty, "domain"))
      rfe.openDef(defn)
      defn.isEmpty mustBe true
      rfe.toString mustBe "domain 'domain' is { ??? }\n"
      rfe.clear()
      rfe.openDef(defn, withBrace = false)
      rfe.toString mustBe "domain 'domain' is "
    }
    "emits Strngs" in {
      rfe.clear()
      val s1 = String_(At.empty, Some(3L), Some(6L))
      val s2 = String_(At.empty, Some(3L), None)
      val s3 = String_(At.empty, None, Some(6L))
      val s4 = String_(At.empty)
      rfe.emitString(s1).toString mustBe "String(3,6)"
      rfe.clear()
      // `String(3)` does not PARSE — the grammar makes the comma mandatory — so the emitter must
      // not produce it; a min with a default max renders as `String(3,)`.
      rfe.emitString(s2).toString mustBe "String(3,)"
      rfe.clear()
      rfe.emitString(s3).toString mustBe "String(,6)"
      rfe.clear()
      rfe.emitString(s4).toString mustBe "String"
    }
    "emits descriptions" in {
      rfe.clear()
      val desc = BlockDescription(At.empty, Seq(LiteralString(At.empty, "foo")))
      // This case originally called `rfe.emitDescription(Some(desc))`, which rendered only the
      // `described as { ... }` block. That helper became private and the call site was retargeted
      // to `emitMetaData`, which additionally emits the enclosing ` with { ... }` metadata block.
      // The expected value below is the same description rendering (re-indented one level) inside
      // that wrapper — the original assertion's intent, at the current helper's contract.
      rfe.emitMetaData(Contents(desc))
      rfe.toString mustBe " with {\n  described as {\n    |foo\n  }\n}\n"
    }

    val patt = Pattern(At.empty, Seq(LiteralString(At.empty, "^stuff.*$")))

    "emit patterns" in {
      rfe.clear()
      rfe.emitPattern(patt)
      rfe.toString mustBe "Pattern(\"^stuff.*$\")"
    }

    "emit type expressions" in {
      rfe.clear()
      rfe.emitTypeExpression(Decimal(At.empty, 8, 3)).toString mustBe "Decimal(8,3)"
      rfe.clear()
      rfe.emitTypeExpression(Real(At.empty)).toString mustBe "Real"
      rfe.clear()
      rfe.emitTypeExpression(DateTime(At.empty)).toString mustBe "DateTime"
      rfe.clear()
      rfe.emitTypeExpression(Location(At.empty)).toString mustBe "Location"
      rfe.clear()
      rfe.emitTypeExpression(patt).toString mustBe "Pattern(\"^stuff.*$\")"
      rfe.clear()
      rfe.emitTypeExpression(Anything(At.empty)).toString mustBe "Anything"
      rfe.clear()
      rfe
        .emitTypeExpression(SpecificRange(At.empty, Integer(At.empty), 24, 42))
        .toString mustBe "Integer{24,42}"
    }
  }
}
