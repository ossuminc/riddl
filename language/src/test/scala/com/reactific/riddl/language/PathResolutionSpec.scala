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

  "PathResolver" must {
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
    "resolve ^^^.Name" in {
      val input = RiddlParserInput(
        """domain A {
          |  context D {
          |    type DSimple = Number
          |    entity E {
          |      event blah is {
          |        dSimple: ^^^.DSimple
          |      }
          |      state only is {
          |        a : Number
          |      }
          |      handler foo for state ^only is {
          |        on event ^^.blah {
          |          then set a to @^^^^.dSimple
          |        }
          |      }
          |    }
          |  }
          |}
          |""".stripMargin
      )
      parseResult(input)
    }
    "resolve many" in {
      val input =
        """
          |domain A {
          |  type Top = String
          |  domain B {
          |    context C {
          |      type Simple = String(,30)
          |    }
          |    type Simple = .C.Simple // relative to context
          |    type BSimple = A.B.C.Simple // full path
          |    type CSimple = ^C.Simple // partial path
          |    context D {
          |      type ATop = ^^^.Top
          |      type DSimple = ^E.ESimple // partial path
          |      entity E {
          |        type ESimple = ^^^C.Simple // partial path
          |        event blah is { dSimple: ^DSimple }
          |        state only is {
          |          a : ^^^^^Top
          |        }
          |        handler foo for state ^only is {
          |          on event ^^blah {
          |            then set a to @dSimple
          |          }
          |        }
          |      }
          |    }
          |  }
          |}
          |
          |""".stripMargin

      parseResult(RiddlParserInput(input))
    }
  }
}
