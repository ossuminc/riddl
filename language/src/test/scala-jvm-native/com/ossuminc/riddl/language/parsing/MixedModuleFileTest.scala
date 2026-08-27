/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.Root
import com.ossuminc.riddl.utils.{pc, ec, Await, PathUtils}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.must.Matchers

import java.nio.file.Path
import scala.concurrent.duration.DurationInt

/** Parity guard for the widened `module_content` grammar rule (S61-1): the corpus fixture
  * `language/input/module/mixed-module.riddl` is validated against the EBNF grammar by the TatSu
  * validator (which scans every input-directory riddl file). This test proves fastparse accepts the
  * SAME file, so the documented grammar and the implementation stay in sync (see CLAUDE.md
  * "Parser/EBNF Synchronization Requirement").
  */
class MixedModuleFileTest extends AnyWordSpec with Matchers {

  "mixed-module.riddl" should {
    "parse with fastparse (parity with the EBNF grammar)" in {
      val url = PathUtils.urlFromCwdPath(Path.of("language/input/module/mixed-module.riddl"))
      val future = RiddlParserInput.fromURL(url).map { rpi =>
        TopLevelParser.parseInput(rpi) match
          case Left(messages) => fail(messages.format)
          case Right(root: Root) =>
            val module = root.modules.headOption.getOrElse(fail("no module parsed"))
            module.id.value mustBe "MixedBag"
            // A Module is FLAT: every kind sits directly in it.
            module.types.map(_.id.value) must contain("Amount")
            module.contexts.map(_.id.value) must contain("Ordering")
            module.entities.map(_.id.value) must contain("LooseEntity")
            module.adaptors.map(_.id.value) must contain("FromOrdering")
            module.projectors.map(_.id.value) must contain("Totals")
            module.repositories.map(_.id.value) must contain("Ledger")
            module.functions.map(_.id.value) must contain("Compute")
            module.sagas.map(_.id.value) must contain("Checkout")
            module.epics.map(_.id.value) must contain("Buying")
            module.domains.map(_.id.value) must contain("Retail")
            module.modules.map(_.id.value) must contain("Nested")
      }
      Await.result(future, 10.seconds)
    }
  }
}
