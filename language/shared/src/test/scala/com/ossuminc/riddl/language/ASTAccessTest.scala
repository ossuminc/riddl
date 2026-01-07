/*
 * Copyright 2019-2026 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import org.scalatest.funspec.AsyncFunSpec
import org.scalatest.matchers.must.Matchers
import wvlet.airframe.ulid.ULID

class ASTAccessTest extends AsyncFunSpec with Matchers:
  describe("ULID") {
    it("must automatically provide a ulid value") {
      val node = Context(At.empty, Identifier(At.empty, "foo"))
      val created = node.ulid.epochMillis
      val now = java.time.Instant.now().toEpochMilli
      assert(now - created < 25)
    }
    it("must accept storage of arbitrary named string values") {
      val node = Context(At.empty, Identifier(At.empty, "foo"))
      node.metadata.append( StringAttachment(
        At.empty,
        Identifier(At.empty, "foo"),
        "application/json",
        LiteralString(At.empty, "{}")
      ))
      succeed
    }
  }
end ASTAccessTest
