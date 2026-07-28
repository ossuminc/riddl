/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.{filter, Finder, Messages}
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.utils.pc

import org.scalatest.{Assertion, TestData}

/** A `step show <output> to <user>` interaction must be able to validate.
  *
  * It could not. `EpicParser` built every `ShowOutputInteraction` with `LiteralString.empty` as its
  * relationship, and validation rejects any `TwoReferenceInteraction` whose relationship is empty —
  * so the parser guaranteed the condition the validator forbids, and no source spelling avoided it.
  * Adding `with { briefly … }` did not help, because the relationship is not metadata. One
  * documented step kind was simply unusable, and it blocked any model aiming for zero missing
  * warnings.
  *
  * The relationship reads as `<from> <relationship> <to>`, so the synthesized word is "shown" —
  * "Gallery shown to Artist", the past-tense form of the step itself.
  */
class ShowOutputStepTest extends AbstractValidatingTest {

  private val src =
    """domain D is {
      |  user Artist is "someone who draws"
      |  application context ui is {
      |    result Picture is { data: String }
      |    group Gallery is {
      |      output List presents result D.ui.Picture
      |    }
      |  }
      |  epic Drawing is {
      |    user Artist wants to "see the gallery" so that "they can review their work"
      |    case Show {
      |      user Artist wants to "see the gallery" so that "they can review their work"
      |      step show output D.ui.Gallery.List to user D.Artist
      |    }
      |  }
      |}
      |""".stripMargin

  "a `show ... to ...` step" should {

    "carry a non-empty relationship after parsing" in { (td: TestData) =>
      TopLevelParser.parseInput(RiddlParserInput(src, td)) match
        case Left(msgs) => fail(s"parse failed:\n${msgs.format}")
        case Right(root) =>
          val steps = Finder(root).recursiveFindByType[ShowOutputInteraction]
          steps.size mustBe 1
          steps.head.relationship.s mustBe "shown"
    }

    "not draw the empty-relationship complaint" in { (td: TestData) =>
      parseAndValidateInput(RiddlParserInput(src, td), shouldFailOnErrors = false) {
        case (_, _, msgs: Messages.Messages) =>
          val complaint = msgs.filter(_.message.contains("non-empty relationship"))
          withClue(s"messages were:\n${msgs.format}\n") { complaint mustBe empty }
      }
    }
  }
}
