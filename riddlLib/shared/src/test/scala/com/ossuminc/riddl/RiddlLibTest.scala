/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.At
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

    "parseAsDomain parses domain body content" in {
      RiddlLib.parseAsDomain(
        "context C is { ??? }\ntype T is String"
      ) match
        case RiddlResult.Success(domain) =>
          domain.contexts must not be empty
          domain.contexts.head.id.value mustBe "C"
          domain.types must not be empty
          domain.types.head.id.value mustBe "T"
        case RiddlResult.Failure(errors) =>
          fail(s"parseAsDomain failed: $errors")
      end match
    }

    "parseAsContext parses context body content" in {
      RiddlLib.parseAsContext(
        "entity E is { ??? }\ntype T is Integer"
      ) match
        case RiddlResult.Success(context) =>
          context.entities must not be empty
          context.entities.head.id.value mustBe "E"
          context.types must not be empty
        case RiddlResult.Failure(errors) =>
          fail(s"parseAsContext failed: $errors")
      end match
    }

    "parseAsEntity parses entity body content" in {
      RiddlLib.parseAsEntity(
        "handler input is { ??? }"
      ) match
        case RiddlResult.Success(entity) =>
          entity.handlers must not be empty
          entity.handlers.head.id.value mustBe "input"
        case RiddlResult.Failure(errors) =>
          fail(s"parseAsEntity failed: $errors")
      end match
    }

    "parseAsEpic parses epic body with user story" in {
      val userStory = UserStory(
        At.empty,
        UserRef(At.empty, PathIdentifier(At.empty, Seq("u"))),
        LiteralString(At.empty, "do something"),
        LiteralString(At.empty, "it works")
      )
      RiddlLib.parseAsEpic(
        """case HappyPath is {
          |  user u wants "complete it"
          |    so that "it is done"
          |  ???
          |}""".stripMargin,
        userStory
      ) match
        case RiddlResult.Success(epic) =>
          epic.cases must not be empty
          epic.cases.head.id.value mustBe "HappyPath"
          epic.userStory mustBe userStory
        case RiddlResult.Failure(errors) =>
          fail(s"parseAsEpic failed: $errors")
      end match
    }

    "parseAsStreamlet parses body with provided ports" in {
      val shape = Flow(At.empty)
      val inlets = Seq(Inlet(
        At.empty,
        Identifier(At.empty, "in"),
        TypeRef(At.empty, "type",
          PathIdentifier(At.empty, Seq("String")))
      ))
      val outlets = Seq(Outlet(
        At.empty,
        Identifier(At.empty, "out"),
        TypeRef(At.empty, "type",
          PathIdentifier(At.empty, Seq("String")))
      ))
      RiddlLib.parseAsStreamlet(
        "handler input is { ??? }",
        shape, inlets, outlets
      ) match
        case RiddlResult.Success(streamlet) =>
          streamlet.shape mustBe a[Flow]
          streamlet.inlets.size mustBe 1
          streamlet.outlets.size mustBe 1
          streamlet.handlers must not be empty
        case RiddlResult.Failure(errors) =>
          fail(s"parseAsStreamlet failed: $errors")
      end match
    }

    "parseAsModule parses module body content" in {
      RiddlLib.parseAsModule(
        "domain Inner is { ??? }"
      ) match
        case RiddlResult.Success(module) =>
          module.domains must not be empty
          module.domains.head.id.value mustBe "Inner"
        case RiddlResult.Failure(errors) =>
          fail(s"parseAsModule failed: $errors")
      end match
    }

    "parseAsAdaptor parses adaptor body content" in {
      import com.ossuminc.riddl.language.AST.{
        InboundAdaptor, ContextRef, PathIdentifier
      }
      val direction = InboundAdaptor(At.empty)
      val ctxRef = ContextRef(
        At.empty,
        PathIdentifier(At.empty, Seq("OtherCtx"))
      )
      RiddlLib.parseAsAdaptor(
        "handler input is { ??? }",
        direction, ctxRef
      ) match
        case RiddlResult.Success(adaptor) =>
          adaptor.handlers must not be empty
        case RiddlResult.Failure(errors) =>
          fail(s"parseAsAdaptor failed: $errors")
      end match
    }

    "parseAsProjector parses projector body content" in {
      RiddlLib.parseAsProjector(
        "handler input is { ??? }"
      ) match
        case RiddlResult.Success(projector) =>
          projector.handlers must not be empty
        case RiddlResult.Failure(errors) =>
          fail(s"parseAsProjector failed: $errors")
      end match
    }

    "parseAsRepository parses repository body content" in {
      RiddlLib.parseAsRepository(
        "handler commands is { ??? }"
      ) match
        case RiddlResult.Success(repository) =>
          repository.handlers must not be empty
        case RiddlResult.Failure(errors) =>
          fail(s"parseAsRepository failed: $errors")
      end match
    }

    "parseAsSaga parses saga body content" in {
      RiddlLib.parseAsSaga(
        """step One is {
          |  ???
          |} reverted by {
          |  ???
          |}
          |step Two is {
          |  ???
          |} reverted by {
          |  ???
          |}""".stripMargin
      ) match
        case RiddlResult.Success(saga) =>
          saga.sagaSteps.size mustBe 2
        case RiddlResult.Failure(errors) =>
          fail(s"parseAsSaga failed: $errors")
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
