/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes

import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.Messages.Message
import com.ossuminc.riddl.language.RuleId
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.utils.{AbstractTestingBasis, CommonOptions, PathUtils}
import com.ossuminc.riddl.utils.{ec, pc, Await}

import java.nio.file.Path
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt

/** The three FooBar models, moved here from riddl-examples when that corpus was retired
  * (2026-11-19). They are kept because riddl-models, being all VALID models, has no counterpart:
  * two sibling includes or domains defining the same type name, resolved by qualification
  * (`success`, `two-domains`), and the same name defined twice in ONE domain, which must be
  * reported as ambiguous (`same-domain`).
  */
class FooBarFixturesTest extends AbstractTestingBasis {

  private def validate(dir: String): Seq[Message] =
    implicit val ec: ExecutionContext = pc.ec
    val url = PathUtils.urlFromCwdPath(Path.of(s"language/input/foobar/$dir/FooBar.riddl"))
    var out: Seq[Message] = Seq.empty
    pc.withOptions(CommonOptions.default) { _ =>
      val future = RiddlParserInput.fromURL(url).map { rpi =>
        Riddl.parseAndValidate(rpi, shouldFailOnError = false) match
          case Right(result) => result.messages
          case Left(msgs)    => msgs
      }
      out = Await.result(future, 10.seconds)
    }
    out

  private def clue(msgs: Seq[Message]): String = msgs.map(_.format).mkString("\n")

  "The FooBar fixtures" must {
    "validate two qualified sibling definitions of one name cleanly" in {
      Seq("success", "two-domains").foreach { dir =>
        val msgs = validate(dir)
        withClue(s"$dir:\n${clue(msgs)}") {
          msgs.filter(_.kind.isError) mustBe empty
          msgs.filter(_.kind.isWarning) mustBe empty
        }
      }
    }
    "report one name defined twice in one domain as ambiguous" in {
      val msgs = validate("same-domain")
      withClue(clue(msgs)) {
        msgs.filter(_.ruleId.contains(RuleId.Ambiguous)) must not be empty
      }
    }
  }
}
