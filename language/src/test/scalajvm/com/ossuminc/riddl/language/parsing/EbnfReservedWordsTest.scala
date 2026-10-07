/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.utils.AbstractTestingBasis
import fastparse.*
import fastparse.MultiLineWhitespace.*

import java.nio.file.{Files, Path}

/** The EBNF's word lists behind `empty`'s ascription guard must agree with the parser's (BACKLOG
  * [1.26]).
  *
  * `ascription_stop` refuses a reserved word (`reserved_word`) or a readability word
  * (`readability_word`) as the start of `empty`'s optional ascription. Both lists are spelled out
  * in the grammar by hand, and a hand-kept list with nothing comparing it is exactly how the
  * tokenizer's keyword table drifted by 17 words (see [[KeywordTableDriftTest]]). If the EBNF
  * lists fall behind the parser, TatSu and fastparse disagree about where an `empty` ends.
  */
class EbnfReservedWordsTest extends AbstractTestingBasis {

  private val grammar: String =
    Files.readString(Path.of("language/src/main/resources/riddl/grammar/ebnf-grammar.ebnf"))

  /** The quoted alternatives of a top-level `name = … ;` rule. */
  private def ruleWords(name: String): Set[String] =
    val start = grammar.indexOf(s"\n$name =")
    require(start >= 0, s"rule '$name' not found in the EBNF")
    val body = grammar.substring(start + name.length + 3, grammar.indexOf(';', start))
    "\"([^\"]+)\"".r.findAllMatchIn(body).map(_.group(1)).toSet

  private object R extends Readability
  private def readability[u: P]: P[Unit] = R.anyReadability ~ End

  "the EBNF `reserved_word` rule" should {
    "list exactly Keyword.allKeywords" in {
      val ebnf = ruleWords("reserved_word")
      val scala = Keyword.allKeywords.toSet
      withClue(s"missing from EBNF: ${(scala -- ebnf).toSeq.sorted.mkString(", ")}\n" +
        s"not keywords: ${(ebnf -- scala).toSeq.sorted.mkString(", ")}\n") {
        ebnf mustBe scala
      }
    }
  }

  "the EBNF `readability_word` rule" should {
    "list only words the parser's `anyReadability` accepts" in {
      val rejected = ruleWords("readability_word").filterNot { w =>
        parse(w, readability(using _)).isSuccess
      }
      withClue(s"not readability words: ${rejected.toSeq.sorted.mkString(", ")}\n") {
        rejected mustBe empty
      }
    }

    "list every word `ReadabilityWords` declares" in {
      // Scala 3 compiles a `final val` constant to a nullary accessor, not a field.
      val declared = ReadabilityWords.getClass.getDeclaredMethods.toSeq
        .filter(m => m.getParameterCount == 0 && m.getReturnType == classOf[String])
        .map(_.invoke(ReadabilityWords).asInstanceOf[String])
        .toSet
      val missing = declared -- ruleWords("readability_word")
      withClue(s"missing from EBNF: ${missing.toSeq.sorted.mkString(", ")}\n") {
        declared must not be empty
        missing mustBe empty
      }
    }
  }
}
