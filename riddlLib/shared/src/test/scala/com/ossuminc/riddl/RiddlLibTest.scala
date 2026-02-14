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
      RiddlLib.parseString(
        "domain MyDomain is { ??? }"
      ) match
        case RiddlResult.Success(root) =>
          root.domains must not be empty
          root.domains.head.id.value mustBe "MyDomain"
        case RiddlResult.Failure(errors) =>
          fail(s"Parse failed: $errors")
      end match
    }

    "return errors for invalid input" in {
      RiddlLib.parseString(
        "this is not valid riddl"
      ) match
        case RiddlResult.Failure(errors) =>
          errors must not be empty
        case RiddlResult.Success(_) =>
          fail("Expected parse failure")
      end match
    }

    "flattenAST on a parsed result" in {
      RiddlLib.parseString(
        "domain D is { context C is { ??? } }"
      ) match
        case RiddlResult.Success(root) =>
          val flattened = RiddlLib.flattenAST(root)
          flattened.domains must not be empty
          flattened.domains.head.id.value mustBe "D"
        case RiddlResult.Failure(errors) =>
          fail(s"Parse failed: $errors")
      end match
    }

    "getOutline returns entries" in {
      RiddlLib.getOutline(
        "domain D is { context C is { ??? } }"
      ) match
        case RiddlResult.Success(entries) =>
          entries must not be empty
          entries.exists(
            _.kind == "Domain"
          ) mustBe true
        case RiddlResult.Failure(errors) =>
          fail(s"getOutline failed: $errors")
      end match
    }

    "getTree returns nodes" in {
      RiddlLib.getTree(
        "domain D is { context C is { ??? } }"
      ) match
        case RiddlResult.Success(nodes) =>
          nodes must not be empty
          val rootNode = nodes.head
          rootNode.kind mustBe "Root"
          rootNode.children.exists(
            _.kind == "Domain"
          ) mustBe true
        case RiddlResult.Failure(errors) =>
          fail(s"getTree failed: $errors")
      end match
    }

    "validateString returns a ValidateResult" in {
      val vr = RiddlLib.validateString(
        "domain D is { context C is { ??? } }"
      )
      vr.parseErrors mustBe empty
    }

    "version returns a non-empty string" in {
      RiddlLib.version must not be empty
    }

    "formatInfo returns a non-empty string" in {
      RiddlLib.formatInfo must not be empty
    }

    "bast2FlatAST round-trips parse to bast to flatAST" in {
      val source = """domain TestDomain is {
        context TestCtx is { ??? }
      }"""
      RiddlLib.parseString(source) match
        case RiddlResult.Success(root) =>
          RiddlLib.ast2bast(root) match
            case RiddlResult.Success(bastBytes) =>
              bastBytes.length must be > 0
              RiddlLib.bast2FlatAST(bastBytes) match
                case RiddlResult.Success(flatRoot) =>
                  flatRoot.domains must not be empty
                  flatRoot.domains.head.id
                    .value mustBe "TestDomain"
                case RiddlResult.Failure(errors) =>
                  fail(s"bast2FlatAST failed: $errors")
              end match
            case RiddlResult.Failure(errors) =>
              fail(s"ast2bast failed: $errors")
          end match
        case RiddlResult.Failure(errors) =>
          fail(s"Parse failed: $errors")
      end match
    }

    "root2RiddlSource round-trips parse to source" in {
      val source = """domain TestDomain is {
        context TestCtx is { ??? }
      }"""
      RiddlLib.parseString(source) match
        case RiddlResult.Success(root) =>
          val riddlText = RiddlLib.root2RiddlSource(root)
          riddlText must include("domain TestDomain")
          riddlText must include("context TestCtx")
        case RiddlResult.Failure(errors) =>
          fail(s"Parse failed: $errors")
      end match
    }

    "ast2bast converts parsed AST to bytes" in {
      RiddlLib.parseString(
        "domain D is { context C is { ??? } }"
      ) match
        case RiddlResult.Success(root) =>
          RiddlLib.ast2bast(root) match
            case RiddlResult.Success(bytes) =>
              bytes must not be empty
              bytes(0) mustBe 'B'.toByte
              bytes(1) mustBe 'A'.toByte
              bytes(2) mustBe 'S'.toByte
              bytes(3) mustBe 'T'.toByte
            case RiddlResult.Failure(errors) =>
              fail(s"ast2bast failed: $errors")
          end match
        case RiddlResult.Failure(errors) =>
          fail(s"Parse failed: $errors")
      end match
    }
  }
}
