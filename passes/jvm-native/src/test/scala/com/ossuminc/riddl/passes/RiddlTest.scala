/*
 * Copyright 2019-2026 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes

import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.language.AST
import com.ossuminc.riddl.utils.{AbstractTestingBasis, PathUtils, PlatformContext}
import com.ossuminc.riddl.utils.{ec, pc, Await}
import com.ossuminc.riddl.utils.Timer

import java.nio.file.Path
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt

class RiddlTest extends AbstractTestingBasis {

  val testPath = "language/input/everything.riddl"
  val url = PathUtils.urlFromCwdPath(Path.of(testPath))

  "Riddl" must {

    "parse a file" in {
      implicit val ec: ExecutionContext = pc.ec
      val future = RiddlParserInput.fromURL(url).map { rpi =>
        Riddl.parse(rpi) match
          case Left(messages) => fail(messages.format)
          case Right(root) =>
            val domains = AST.getTopLevelDomains(root)
            domains.size mustBe 1
            val domain = domains.head
            val contexts = AST.getContexts(domain)
            contexts.size mustBe 3
            AST.getDomains(domain) mustBe empty

      }
      Await.result(future, 10.seconds)
    }

    "validate a file" in {
      implicit val ec: ExecutionContext = pc.ec
      val future = RiddlParserInput.fromURL(url).map { rpi =>
        Riddl.parse(rpi) match
          case Left(messages) => fail(messages.format)
          case Right(root) =>
            Riddl.validate(root) match
              case Left(messages) => fail(messages.justErrors.format)
              case Right(result)  => succeed
        end match
      }
      Await.result(future, 10.seconds)
    }

    "parse and validate a RiddlParserInput" in {
      implicit val ec: ExecutionContext = pc.ec
      val future = RiddlParserInput.fromURL(url).map { rpi =>
        Riddl.parseAndValidate(rpi) match
          case Left(messages) => fail(messages.justErrors.format)
          case Right(result)  => succeed
        end match
      }
      Await.result(future, 10.seconds)
    }

    "parse and validate a Path" in {
      Riddl.parseAndValidatePath(testPath) match
        case Left(messages) => fail(messages.justErrors.format)
        case Right(result)  => succeed
      end match
    }

    "convert a Root back to text" in {
      val input =
        """domain foo is {
          |  domain bar is { ??? }
          |}""".stripMargin
      val rpi = RiddlParserInput(input, "")
      Riddl.parse(rpi) match
        case Left(messages) => fail(messages.format)
        case Right(root) =>
          val converted =
            Timer.time("toRiddlText", true) {
              Riddl.toRiddlText(root)
            }
          converted.stripTrailing() must be(input)
    }

    "indent closing braces correctly" in {
      val input =
        """domain foo is {
          |  context bar is {
          |    handler baz is { ??? }
          |  }
          |}""".stripMargin
      val rpi = RiddlParserInput(input, "")
      Riddl.parse(rpi) match
        case Left(messages) => fail(messages.format)
        case Right(root) =>
          val output = Riddl.toRiddlText(root)
          val lines = output.split("\n")
          // context's closing } should be indented 2 spaces
          lines must contain("  }")
          // domain's closing } should be at column 0
          lines.last.trim must be("}")
          lines.last.indexOf("}") must be(0)
    }

    "emit newlines between sibling on clauses" in {
      val input =
        """domain foo is {
          |  context bar is {
          |    entity baz is {
          |      handler main is {
          |        on command com.DoA {
          |          tell command com.DoA to entity baz
          |        }
          |        on command com.DoB {
          |          tell command com.DoB to entity baz
          |        }
          |      }
          |    }
          |  }
          |}""".stripMargin
      val rpi = RiddlParserInput(input, "")
      Riddl.parse(rpi) match
        case Left(messages) => fail(messages.format)
        case Right(root) =>
          val output = Riddl.toRiddlText(root)
          // Each on clause should start on its own line
          val onLines = output.split("\n").filter(_.trim.startsWith("on "))
          onLines.length must be(2)
          // No two closing braces should be on the same line (no run-together)
          output must not include "}on"
          output must not include "} on"
    }

    "indent schema of/index clauses correctly" in {
      val input =
        """domain foo is {
          |  context bar is {
          |    repository baz is {
          |      schema Stuff is relational
          |        of things as type foo.bar.Thing
          |          index on field foo.bar.Thing.id
          |    }
          |  }
          |}""".stripMargin
      val rpi = RiddlParserInput(input, "")
      Riddl.parse(rpi) match
        case Left(messages) => fail(messages.format)
        case Right(root) =>
          val output = Riddl.toRiddlText(root)
          val lines = output.split("\n")
          val schemaLine = lines.find(_.trim.startsWith("schema "))
          val ofLine = lines.find(_.trim.startsWith("of "))
          val indexLine = lines.find(_.trim.startsWith("index "))
          schemaLine mustBe defined
          ofLine mustBe defined
          indexLine mustBe defined
          // of should be indented more than schema
          val schemaIndent = schemaLine.get.indexOf("schema")
          val ofIndent = ofLine.get.indexOf("of")
          val indexIndent = indexLine.get.indexOf("index")
          ofIndent must be > schemaIndent
          // index should be indented more than of
          indexIndent must be > ofIndent
    }

  }
}
