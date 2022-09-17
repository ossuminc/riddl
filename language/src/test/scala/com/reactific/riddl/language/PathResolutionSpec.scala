package com.reactific.riddl.language

import com.reactific.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import org.scalatest.Assertion
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

class PathResolutionSpec extends AnyWordSpec with Matchers{

  def parseResult(
    input: RiddlParserInput
  ): Assertion = {
    TopLevelParser.parse(input) match {
      case Left(errors) =>
        fail(errors.map(_.format).mkString("\n"))
      case Right(model) =>
        Validation.validate(model,
          CommonOptions(showMissingWarnings=false, showStyleWarnings=false)
        ) match {
          case msgs: Messages.Messages if msgs.nonEmpty =>
            fail(msgs.map(_.format).mkString("\n"))
          case _ =>
            succeed
        }
    }
  }

  "PathResolution" must {
    "resolve full path" in {
      val rpi = RiddlParserInput(
        """domain A {
          |  domain B {
          |    domain C {
          |      type D = String
          |    }
          |  }
          |  type APrime = A.B.C.D
          |}""".stripMargin
      )
      parseResult(rpi)
    }

    "resolve ^Name" in {
      val rpi = RiddlParserInput(
        """
          |domain A {
          |  type Top = String
          |  type aTop = type ^Top
          |}
          |""".stripMargin
      )
      parseResult(rpi)
    }

    "resolve ^^Name.Item" in {
      val rpi = RiddlParserInput(
        """domain A {
          |  domain B {
          |    type InB = String
          |  }
          |  domain C {
          |    type InC = ^^B.InB
          |  }
          |}
          |""".stripMargin
      )
      parseResult(rpi)
    }
    "resolve ^^^Name" in {
      val input = RiddlParserInput(
        """domain A {
          |  context D {
          |    type DSimple = Number
          |    entity E {
          |      state only is {
          |        a : ^^^DSimple
          |      }
          |      handler foo is { ??? }
          |    }
          |  }
          |}
          |""".stripMargin
      )
      parseResult(input)
    }
    "resolve nested fields" in {
      val input =
        """
          |domain D {
          |  type Bottom = { a: String }
          |  type Middle = { b: ^^Bottom }
          |  type Top = { m: ^Middle }
          |
          |  context C {
          |    function foo {
          |      requires: { t: ^^^.Top }
          |      returns: { a: String }
          |      example impl {
          |        then return @^^foo.t.m.b.a
          |      }
          |    }
          |  }
          |}
          |""".stripMargin
      parseResult(RiddlParserInput(input))
    }

    "resolve complicated paths" in {
      val input =
        """
          |domain A {
          |  type Top = String
          |  domain B {
          |    context C {
          |      type Simple = String
          |    }
          |    type Simple = .C.Simple // relative to context
          |    type BSimple = A.B.C.Simple // full path
          |    type CSimple = ^C.Simple // partial path
          |    context D {
          |      type ATop = ^^^.Top
          |      type DSimple = ^E.ESimple // partial path
          |      entity E {
          |        type ESimple = ^^^C.Simple // partial path
          |        event blah is { dSimple: ^^^.DSimple }
          |        state only is {
          |          a : ^^^^^Top
          |        }
          |        handler foo  is {
          |          on event ^^blah {
          |            then set ^^only.a to @dSimple
          |          }
          |        }
          |      }
          |    }
          |  }
          |}
          |""".stripMargin

      parseResult(RiddlParserInput(input))
    }
    "resolve doc example" in {
      val input =
        """domain A {
          |  domain B {
          |    context C {
          |      type Simple = String(,30)
          |    }
          |    type BSimple = A.B.C.Simple // full path starts from root
          |    context D {
          |      type DSimple = ^E.ESimple // partial path
          |      entity E {
          |        type ESimple = ^^^C.Simple // E->D->B->C->Simple
          |        type Complicated = ^^^C^D.DSimple // E->D->B->C->B->D->DSimple
          |        handler foo is { ??? }
          |      }
          |    }
          |  }
          |}
          |""".stripMargin
      parseResult(RiddlParserInput(input))
    }
  }
}
