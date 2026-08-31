/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.utils.AbstractTestingBasis
import fastparse.*
import fastparse.MultiLineWhitespace.*

import java.nio.file.{Files, Paths}

/** RIDDL's keyword vocabulary lives in THREE places that must agree, and they had drifted.
  *
  *   1. the `final val` declarations in `object Keyword` — 167 of them
  *   2. `Keyword.allKeywords`, which downstream tooling derives from (riddl-vscode builds its
  *      entire TextMate grammar, completion and hover docs out of it)
  *   3. `Keywords.anyKeyword`, the `StringIn` the TOKENIZER matches against
  *
  * On 2026-08-31 (2) was missing 3 and (3) was missing **17**, so `let`, `or`, `prompt`, `match`,
  * `brief`, `description`, `self`, `forward`, `initiate` and `terminate` all tokenized as
  * `Identifier` rather than `Keyword`. riddl-vscode found four of them while re-deriving against
  * 2.0.0; the other thirteen were invisible because nothing compared the lists.
  *
  * **(3) cannot be derived from (2).** fastparse's `StringIn` is a macro requiring literal
  * constants — `StringIn(Keyword.allKeywords*)` fails to compile with "Function can only accept
  * constant singleton type" (tried 2026-08-31). The duplication is unavoidable; this suite is what
  * makes it safe.
  *
  * **The assertions are BEHAVIOURAL where they can be.** Comparing two source lists would just be
  * a fourth copy of the same knowledge; parsing each keyword asks the question that matters — does
  * this word actually tokenize as a keyword?
  */
class KeywordTableDriftTest extends AbstractTestingBasis {

  private def rule[u: P]: P[Unit] = Keywords.anyKeyword ~ End

  private def parsesAsKeyword(word: String): Boolean =
    parse(word, rule(using _)) match
      case Parsed.Success(_, _) => true
      case _: Parsed.Failure    => false

  private def assertAllTokenize(words: Seq[String]): org.scalatest.Assertion =
    val failed = words.filterNot(parsesAsKeyword).sorted
    withClue(s"did not tokenize as Keyword: ${failed.mkString(", ")}\n") { failed mustBe empty }

  "the keyword tables" should {

    /** Reads the SOURCE, because the declarations are `final val`s in an object and neither JS nor
      * Native can reflect over them. Same technique and repo-relative path convention as
      * `PredefinedModuleSourceTest`, including the explicit existence assertion — a missing file
      * must FAIL here, never silently pass, which is how the corpus suites once certified nothing.
      *
      * Captures the string VALUE, not the val name: several names are Scala-escaped and differ
      * from what they hold (`default_` is `"default"`, `match_` is `"match"`, `yield_` is
      * `"yield"`), so comparing names would report three permanent false gaps.
      */
    "declare every keyword val in allKeywords" in {
      val src =
        Paths.get("language/src/main/scala/com/ossuminc/riddl/language/parsing/Keywords.scala")
      Files.exists(src) mustBe true
      val text = Files.readString(src)
      val declared = """final val [a-zA-Z_][a-zA-Z0-9_]* *= *"([^"]+)"""".r
        .findAllMatchIn(text)
        .map(_.group(1))
        .toSet
      // A floor, so an empty regex result cannot pass vacuously — the failure mode this repo has
      // been burned by more than any other.
      declared.size must be > 150
      val missing = (declared -- Keyword.allKeywords.toSet).toSeq.sorted
      withClue(s"declared as a keyword val but absent from allKeywords: ${missing.mkString(", ")}\n") {
        missing mustBe empty
      }
    }

    "tokenize every keyword in allKeywords AS a keyword" in {
      // The guard that would have caught all 17. `anyKeyword` is the copy that RUNS, and it was
      // not the copy anyone read.
      assertAllTokenize(Keyword.allKeywords)
    }

    // Named individually so a regression says WHICH keyword broke, not merely how many.
    "tokenize the four riddl-vscode reported" in {
      assertAllTokenize(Seq("self", "forward", "initiate", "terminate"))
    }

    "tokenize the thirteen the report did not find" in {
      assertAllTokenize(
        Seq("brief", "description", "explanation", "figma", "form", "fully", "let", "match",
          "node", "or", "prompt", "system", "default")
      )
    }
  }
}
