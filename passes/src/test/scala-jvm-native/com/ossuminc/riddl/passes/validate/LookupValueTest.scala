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

/** `<collection> at <index>[, <index>…]` (Reid, 2026-08-10; Table arity and index type-checking
  * confirmed 2026-08-17).
  *
  * Outside a `foreach` a mapping was WRITE-ONLY — nothing in the `Value` union indexed, so a model
  * could declare a mapping and never name what it stored.
  *
  * **The parser hazard this construct exposed, pinned here because it is invisible from the AST:**
  * `Keywords.keyword` ends in `./`, a CUT, so matching the word `at` commits the parser. The rule
  * therefore wraps the optional `at …` clause in `NoCut`; without it a lookup as a COMPARISON
  * OPERAND works while a BARE lookup fails, because only the bare case has to backtrack out of
  * `comparison`'s first alternative. Both forms are asserted below, and a regression would show up
  * as one passing and the other failing rather than as both breaking.
  */
class LookupValueTest extends AbstractValidatingTest {

  private def model(decls: String, stmt: String): String =
    s"""domain D is {
       |  context C is {
       |    type Inventory is mapping from String to Integer with { briefly "m" }
       |    type Grid is table of Integer of [3,3] with { briefly "t" }
       |    type Names is sequence of String with { briefly "q" }
       |    type Tags is set of String with { briefly "st" }
       |    command Go is { why: String } with { briefly "g" }
       |    $decls
       |    record R is {
       |      inv: Inventory, grid: Grid, names: Names, tags: Tags, plain: String
       |    } with { briefly "r" }
       |    entity E is {
       |      state S of record R is {
       |        handler H is { on command Go is { $stmt } } with { briefly "h" }
       |      } with { briefly "s" }
       |    } with { briefly "e" }
       |  } with { briefly "c" }
       |} with { briefly "d" }
       |""".stripMargin

  private def errorsIn(decls: String, stmt: String, td: TestData): String =
    var captured: Messages = Messages.empty
    pc.withOptions(CommonOptions(showStyleWarnings = true, showWarnings = true)) { _ =>
      parseAndValidate(model(decls, stmt), td.name, shouldFailOnErrors = false) { (_, _, msgs) =>
        captured = msgs
        succeed
      }
    }
    captured.find(_.message.contains("Expected one of")) match
      case Some(m) => fail(s"fixture did not parse, so any absence proves nothing:\n${m.format}")
      case None    => captured.justErrors.map(_.message).mkString("\n")

  "a lookup" should {

    "ACCEPT a mapping indexed by its declared key type" in { (td: TestData) =>
      errorsIn("", """let n = inv at "sku"""", td) mustBe ""
    }

    "ACCEPT a sequence indexed by ordinal" in { (td: TestData) =>
      errorsIn("", """let s = names at 0""", td) mustBe ""
    }

    "ACCEPT a table indexed once per dimension" in { (td: TestData) =>
      errorsIn("", """let g = grid at 1, 2""", td) mustBe ""
    }

    // The parser hazard. These two reach the rule by DIFFERENT routes -- `booleanAtom` and
    // `comparand` -- and the NoCut fix is what lets both work at once.
    "ACCEPT a bare lookup and a lookup as a comparison operand alike" in { (td: TestData) =>
      errorsIn("", """let n = inv at "sku"""", td) mustBe ""
      errorsIn("", """when inv at "sku" > 0 then do "y" end""", td) mustBe ""
    }

    "REJECT indexing something that is not a collection" in { (td: TestData) =>
      errorsIn("", """let n = plain at 0""", td) must include("requires a mapping, sequence or table")
    }

    "REJECT indexing a set, which has no index" in { (td: TestData) =>
      errorsIn("", """let n = tags at 0""", td) must include("requires a mapping, sequence or table")
    }

    "REJECT too few indices for a table" in { (td: TestData) =>
      errorsIn("", """let g = grid at 1""", td) must include("takes 2 indices, but 1 given")
    }

    "REJECT too many indices for a mapping" in { (td: TestData) =>
      errorsIn("", """let n = inv at "sku", "other"""", td) must include("takes 1 index, but 2 given")
    }

    // Index type checking (Reid, 2026-08-17: "they must be type-checked").
    "REJECT a numeric index on a string-keyed mapping" in { (td: TestData) =>
      errorsIn("", """let n = inv at 3""", td) must include("is a number, but")
    }

    "REJECT a string index on an ordinal-indexed sequence" in { (td: TestData) =>
      errorsIn("", """let s = names at "first"""", td) must include("is a string, but")
    }

    // The conservative boundary, matching `checkTerminate`: an index that is a REFERENCE goes
    // through the ordinary type machinery rather than being judged here, so this must not error.
    "stay SILENT about a reference used as an index" in { (td: TestData) =>
      errorsIn("", """let n = inv at why""", td) mustBe ""
    }
  }
}
