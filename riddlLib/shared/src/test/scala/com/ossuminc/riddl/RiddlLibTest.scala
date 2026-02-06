/*
 * Copyright 2019-2026 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl

import com.ossuminc.riddl.utils.{pc, PlatformContext}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.must.Matchers

/** Cross-platform tests for the shared RiddlLib API.
  * Runs on JVM, JS, and Native.
  */
class RiddlLibTest extends AnyWordSpec with Matchers {

  "RiddlLib" should {

    "parse a simple domain" in {
      val result = RiddlLib.parseString(
        "domain MyDomain is { ??? }"
      )
      result.isRight mustBe true
      val root = result.toOption.get
      root.domains must not be empty
      root.domains.head.id.value mustBe "MyDomain"
    }

    "return errors for invalid input" in {
      val result = RiddlLib.parseString(
        "this is not valid riddl"
      )
      result.isLeft mustBe true
      val messages = result.swap.toOption.get
      messages must not be empty
    }

    "flattenAST on a parsed result" in {
      val result = RiddlLib.parseString(
        "domain D is { context C is { ??? } }"
      )
      result.isRight mustBe true
      val root = result.toOption.get
      val flattened = RiddlLib.flattenAST(root)
      flattened.domains must not be empty
      flattened.domains.head.id.value mustBe "D"
    }

    "getOutline returns entries" in {
      val result = RiddlLib.getOutline(
        "domain D is { context C is { ??? } }"
      )
      result.isRight mustBe true
      val entries = result.toOption.get
      entries must not be empty
      entries.exists(_.kind == "Domain") mustBe true
    }

    "getTree returns nodes" in {
      val result = RiddlLib.getTree(
        "domain D is { context C is { ??? } }"
      )
      result.isRight mustBe true
      val nodes = result.toOption.get
      nodes must not be empty
      // Top level is the Root node with Domain children
      val rootNode = nodes.head
      rootNode.kind mustBe "Root"
      rootNode.children.exists(
        _.kind == "Domain"
      ) mustBe true
    }

    "validateString returns a ValidateResult" in {
      val vr = RiddlLib.validateString(
        "domain D is { context C is { ??? } }"
      )
      // Should not throw; may or may not succeed
      // depending on validation rules
      vr.parseErrors mustBe empty
    }

    "version returns a non-empty string" in {
      RiddlLib.version must not be empty
    }

    "formatInfo returns a non-empty string" in {
      RiddlLib.formatInfo must not be empty
    }
  }
}
