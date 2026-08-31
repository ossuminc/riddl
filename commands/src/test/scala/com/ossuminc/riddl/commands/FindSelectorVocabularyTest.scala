/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands

import com.ossuminc.riddl.commands.find.{FindExpression, FindPredicates}
import com.ossuminc.riddl.utils.AbstractTestingBasisWithTestData
import org.scalatest.TestData

/** A selector with a closed vocabulary must REJECT an unknown value, not return zero matches.
  *
  * `0 matched` for a typo is indistinguishable from `0 matched` for a correct query with no hits,
  * so the command answers confidently over nothing — the exact failure `find` was built to end,
  * and the reason `-type` was given a vocabulary at rc.26.
  *
  * riddl-generator hit it on `-shape alternation`, which returned `0 matched` against a model
  * holding 47 alternations and produced a wrong conclusion within minutes.
  *
  * **Four selectors had the hole, not one.** Fixing only the reported `-shape` would have left
  * `-intention`, `-cardinality` and `-option` failing identically — the instance-fix reflex this
  * repo keeps paying for. Every selector with a closed vocabulary is asserted here, in both
  * directions, so a new one cannot be added without a decision about its argument.
  */
class FindSelectorVocabularyTest extends AbstractTestingBasisWithTestData {

  private def parse(args: String): Either[String, ?] =
    FindExpression.parse(args.split(" ").toSeq.filter(_.nonEmpty))

  private def rejects(args: String): String =
    parse(args) match
      case Left(err) => err
      case Right(_)  => fail(s"'$args' was ACCEPTED; an unknown selector value must be an error")

  private def accepts(args: String): Unit =
    parse(args) match
      case Right(_)  => ()
      case Left(err) => fail(s"'$args' was REJECTED ($err); it is a legal value")

  "a selector with a closed vocabulary" should {

    "reject an unknown -shape, naming the selector" in { (td: TestData) =>
      rejects("-shape notashape") must include("-shape")
    }

    // The case from the report. `alternation` is a real RIDDL concept but not a SHAPE, so the
    // old behaviour was a false zero against a model containing 47 of them.
    "reject -shape alternation — a real concept, but not a shape" in { (td: TestData) =>
      rejects("-shape alternation") must include("unknown -shape")
    }

    "reject an unknown -intention" in { (td: TestData) =>
      rejects("-intention notanintention") must include("unknown -intention")
    }

    "reject an unknown -cardinality" in { (td: TestData) =>
      rejects("-cardinality notacardinality") must include("unknown -cardinality")
    }

    "reject an unknown -option" in { (td: TestData) =>
      rejects("-option notanoption") must include("unknown -option")
    }

    "suggest near matches rather than dumping the vocabulary" in { (td: TestData) =>
      // `sinkk` is one keystroke from `sink`; the message should say so.
      rejects("-shape sinkk") must include("did you mean")
    }
  }

  "every legal value must still be accepted" should {

    "accept all shape keywords, INCLUDING the deprecated synonyms" in { (td: TestData) =>
      // The synonyms still parse, so a model may contain them and a query for one must work.
      FindPredicates.shapeVocabulary.foreach(v => accepts(s"-shape $v"))
    }

    "accept every intention across entity, context and connector" in { (td: TestData) =>
      FindPredicates.intentionVocabulary.foreach(v => accepts(s"-intention $v"))
    }

    /** The projection emits the enum NAME (`EventSourced`) while every model is written with the
      * KEYWORD (`event-sourced`). Both must be accepted AND both must match, or accepting one
      * merely relocates the false zero this suite exists to remove.
      */
    "accept BOTH the keyword and the enum-name spelling of an intention" in { (td: TestData) =>
      accepts("-intention event-sourced")
      accepts("-intention EventSourced")
      accepts("-intention at-least-once")
      accepts("-intention AtLeastOnce")
      FindPredicates.normalizeIntention("event-sourced") mustBe
        FindPredicates.normalizeIntention("EventSourced")
      FindPredicates.normalizeIntention("at-least-once") mustBe
        FindPredicates.normalizeIntention("AtLeastOnce")
    }

    // The predicate is a PREFIX match, so a prefix of a legal value is a legal argument.
    "accept a cardinality PREFIX, since that is what the predicate matches" in { (td: TestData) =>
      accepts("-cardinality optional")
      accepts("-cardinality one-")
      accepts("-cardinality range")
    }

    "accept a registered option name" in { (td: TestData) =>
      FindPredicates.optionVocabulary.take(5).foreach(v => accepts(s"-option $v"))
    }

    "still accept a legal -type, unchanged" in { (td: TestData) =>
      accepts("-type entity")
    }
  }
}
