package com.reactific.riddl.language

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.ast.Location
import com.reactific.riddl.language.parsing.RiddlParserInput
import com.reactific.riddl.language.parsing.TopLevelParser
import org.scalatest.Assertion
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.nio.file.Path

class PathResolutionSpec extends AnyWordSpec with Matchers {

  def parseAndValidate(
    input: RiddlParserInput
  )(onFailure: Messages.Messages => Assertion,
    onSuccess: => Assertion
  ): Assertion = {
    TopLevelParser.parse(input) match {
      case Left(errors) => fail(errors.map(_.format).mkString("\n"))
      case Right(model) =>
        val result = Validation.validate(
          model,
          CommonOptions(showMissingWarnings = false, showStyleWarnings = false)
        )
        result.messages match {
          case msgs: Messages.Messages if msgs.nonEmpty => onFailure(msgs)
          case _                                        => onSuccess
        }
    }
  }

  def parseResult(
    input: RiddlParserInput
  ): Assertion = {
    parseAndValidate(input)(
      msgs => fail(msgs.map(_.format).mkString("\n")),
      succeed
    )
  }

  "PathResolution" must {
    "resolve a full path" in {
      val rpi = """domain A {
                  |  domain B {
                  |    domain C {
                  |      type D = String
                  |    }
                  |  }
                  |  type APrime = A.B.C.D
                  |}""".stripMargin
      parseResult(RiddlParserInput(rpi))
    }

    "resolve a relative path" in {
      val rpi = """domain A {
                  |  domain B {
                  |    domain C {
                  |      type D = String
                  |    }
                  |    type FromB = B.C.D
                  |  }
                  |
                  |}""".stripMargin
      parseResult(RiddlParserInput(rpi))
    }

    "resolve ^Name" in {
      val input = """domain A {
                    |  type Top = String
                    |  type aTop = type ^Top
                    |}
                    |""".stripMargin
      parseResult(RiddlParserInput(input))
    }

    "resolve ^^Name.Item" in {
      val input = """domain A {
                    |  domain B {
                    |    type InB = String
                    |  }
                    |  domain C {
                    |    type InC = ^^B.InB
                    |  }
                    |}
                    |""".stripMargin
      parseResult(RiddlParserInput(input))
    }
    "resolve ^^^Name" in {
      val input = """domain A {
                    |  context D {
                    |    type DSimple = Number
                    |    entity E {
                    |      state only is {
                    |        fields { a : DSimple }
                    |        handler OnlyFoo is { ??? }
                    |      }
                    |      handler ForE is { ??? }
                    |    }
                    |  }
                    |}
                    |""".stripMargin
      parseResult(RiddlParserInput(input))
    }
    "resolve nested fields" in {
      val input = """
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
      val input = """
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
                    |          fields { a : Top }
                    |          handler foo  is {
                    |            on event ^^^blah {
                    |              then set ^^only.a to @dSimple
                    |            }
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
    "deal with cyclic references" in {
      val input = """domain A {
                    |  type T is { tp: ^.TPrime }
                    |  type TPrime is { t: ^.T }
                    |  command DoIt is {}
                    |  context C {
                    |    entity E {
                    |      state S {
                    |        fields {
                    |          f: ^^^^.TPrime
                    |        }
                    |        handler foo is {
                    |         on command DoIt {
                    |           then set S.f.t to true
                    |         }
                    |        }
                    |      }
                    |    }
                    |  }
                    |}
                    |""".stripMargin
      parseAndValidate(RiddlParserInput(input))(
        { messages =>
          messages.format matches "^.*requires assignment compatibility"
          println(messages.format)
          messages.size mustBe 1
        },
        fail("Should have failed")
      )
    }

    "resolve simple path directly" in {
      val input = """domain D {
                    |  context C {
                    |    command DoIt is { value: Number }
                    |    type Info is { g: C.DoIt }
                    |    entity E is {
                    |      state S is {
                    |        fields { f: C.Info }
                    |        handler E_Handler is {
                    |          on command C.DoIt {
                    |            then set S.f.g.value to @C.DoIt.value
                    |          }
                    |        }
                    |      }
                    |    }
                    |  }
                    |}
                    |""".stripMargin
      parseResult(RiddlParserInput(input))
    }
    "resolve simple path through an include" in {
      val eL = Location.empty
      val root = RootContainer(
        contents = Seq(Domain(
          eL,
          Identifier(eL, "D"),
          includes = Seq(Include(
            eL,
            contents = Seq(
              Context(
                eL,
                Identifier(eL, "C1"),
                types = Seq(Type(eL, Identifier(eL, "C1_T"), Number(eL)))
              ),
              Context(
                eL,
                Identifier(eL, "C2"),
                types = Seq(Type(
                  eL,
                  Identifier(eL, "C2_T"),
                  AliasedTypeExpression(
                    eL,
                    PathIdentifier(eL, Seq("D", "C1", "C1_T"))
                  )
                ))
              )
            ),
            Some(Path.of("foo"))
          ))
        )),
        Seq.empty[RiddlParserInput]
      )
      root.contents.head.contents.length mustBe 2
      root.contents.head.contents.forall(_.kind == "Context")
      val result = Validation.validate(root, CommonOptions())
      val messages = result.messages
      val errors = messages.filter(_.kind >= Messages.Error)
      if (errors.nonEmpty) fail(errors.format) else succeed

    }
  }
}
