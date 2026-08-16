/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.{Connector, Domain, Root}
import com.ossuminc.riddl.language.Finder
import com.ossuminc.riddl.utils.{pc, ec, Await, PathUtils}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.must.Matchers

import java.nio.file.Path
import scala.concurrent.duration.DurationInt

/** Parity guard for the "Connectors at Domain scope" syntax: the corpus fixture
  * `language/input/domain-connector.riddl` is validated against the EBNF grammar by the TatSu
  * validator. This proves the fastparse parser accepts the SAME file, so the documented grammar and
  * the implementation stay in sync (see CLAUDE.md "Parser/EBNF Synchronization Requirement").
  */
class ConnectorScopeFileTest extends AnyWordSpec with Matchers {

  "domain-connector.riddl" should {
    "parse with fastparse (a connector at domain scope; parity with the EBNF grammar)" in {
      val url = PathUtils.urlFromCwdPath(Path.of("language/input/domain-connector.riddl"))
      val future = RiddlParserInput.fromURL(url).map { rpi =>
        TopLevelParser.parseInput(rpi) match
          case Left(messages) => fail(messages.format)
          case Right(root: Root) =>
            val domain = Finder(root).recursiveFindByType[Domain].head
            // the connector is a direct child of the domain, not of a context
            domain.connectors.map(_.id.value) mustBe Seq("handoff")
            domain.contexts.flatMap(_.connectors) mustBe empty
      }
      Await.result(future, 10.seconds)
    }
  }
}
