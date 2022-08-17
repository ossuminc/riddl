package com.reactific.riddl.language

import com.reactific.riddl.language.AST.RootContainer
import com.reactific.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

class PathResolverSpec extends AnyWordSpec with Matchers{

  val input =
    """
      |domain A {
      |  type Top = String
      |  domain B {
      |    context C {
      |      type Simple = String(,30)
      |    }
      |    type BSimple = A.B.C.Simple // full path
      |    type CSimple = .C.Simple // partial path
      |    context D {
      |      type ATop = ...Top
      |      type DSimple = .E.ESimple // partial path
      |      entity E {
      |        type ESimple = ...C.Simple // partial path
      |        event blah is { dSimple: ..DSimple }
      |        state only is {
      |          a : ....Top
      |        }
      |        handler foo is {
      |          on blah {
      |            set a to @dSimple
      |          }
      |        }
      |      }
      |    }
      |  }
      |}
      |
      |""".stripMargin

  def parseResult(
    input: RiddlParserInput
  )(f: (PathResolver.ResolvedPaths, RootContainer) => Unit): Unit = {
    TopLevelParser.parse(input) match {
      case Left(errors) =>
        fail(errors.map(_.format).mkString("\n"))
      case Right(model) =>
        PathResolver.resolvePathIdentifiers(model) match {
          case Left(errors) =>
            fail(errors.map(_.format).mkString("\n"))
          case Right(map) =>
            f(map, model)
        }
    }
  }

  "PathResolver" must {
    "resolve ." in {
      val rpi = RiddlParserInput(
        """
          |domain A {
          |  type Top = String
          |  type aTop = type .Top
          |}
          |""".stripMargin
      )

      parseResult(rpi)  { (map, model) =>
        map.size mustBe(1)
        val (path,defn) = map.head
        path mustBe List("Top")
        val extracted = model.contents.head.types.head
        defn mustBe extracted
      }
    }

    "resolve .." in { pending }
    "resolve ..." in { pending }
    "resolve full path" in { pending }
  }
}
