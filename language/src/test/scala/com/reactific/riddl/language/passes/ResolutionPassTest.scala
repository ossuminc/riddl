package com.reactific.riddl.language.passes

import com.reactific.riddl.language.ast.At
import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.{CommonOptions, Messages}
import com.reactific.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.reactific.riddl.language.passes.resolution.ResolutionOutput
import org.scalatest.Assertion
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Unit Tests For the ResolutionPass */
class ResolutionPassTest extends AnyWordSpec with Matchers {

  def resolve(root: RootContainer, commonOptions: CommonOptions = CommonOptions(
    showMissingWarnings = false,
    showUnusedWarnings = false,
    showStyleWarnings = false
  )): ResolutionOutput = {
    val input = ParserOutput(root, commonOptions)
    val symbols = Pass.runSymbols(input)
    Pass.runResolution(symbols)
  }

  def parseAndResolve(
    input: RiddlParserInput
  )(
    onFailure: Messages.Messages => Assertion,
    onSuccess: => Assertion
  ): Assertion = {
    TopLevelParser.parse(input) match {
      case Left(errors) =>
        fail(errors.map(_.format).mkString("\n"))
      case Right(model) =>
        val resolutionOutput = resolve(model)
        val messages = resolutionOutput.symbols.messages.toMessages ++
          resolutionOutput.messages.toMessages
        if (messages.isEmpty)
          onSuccess
          else onFailure(messages)
    }
  }


  def parseResult(
    input: RiddlParserInput
  ): Assertion = {
    parseAndResolve(input)(
      msgs => fail(msgs.map(_.format).mkString("\n")),
      succeed
    )
  }

  "PathResolution" must {
    "resolve a full path" in {
      val rpi =
        """domain A {
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
      val rpi =
        """domain A {
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
      val input =
        """domain A {
          |  type Top = String
          |  type aTop = type ^Top
          |}
          |""".stripMargin
      parseResult(RiddlParserInput(input))
    }

    "resolve ^^B.InB" in {
      val input =
        """domain A {
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
      val input =
        """domain A {
          |  context D {
          |    type DSimple = Number
          |    entity E {
          |      record fields is { a : DSimple }
          |      state only of fields is {
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
      val input =
        """
          |domain D {
          |  type Bottom = { a: String }
          |  type Middle = { b: ^Bottom }
          |  type Top = { m: ^Middle }
          |
          |  context C {
          |    function foo {
          |      requires: { t: ^^.Top.m }
          |      returns: { a: String }
          |      example impl {
          |        then return @^foo.t.b.a
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
          |    type Simple = C.Simple // relative to context
          |    type BSimple = A.B.C.Simple // full path
          |    type CSimple = ^C.Simple // partial path
          |    context D {
          |      type ATop = ^^^.Top
          |      type DSimple = ^E.ESimple // partial path
          |      entity E {
          |        type ESimple = ^^^CSimple // partial path
          |        event blah is { dSimple: ^^^.DSimple }
          |        record fields is { a : Top }
          |        state only of ^.fields is {
          |          handler foo  is {
          |            on event ^^^blah {
          |              then set ^^^only.a to @^^^blah.dSimple
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
      val input =
        """domain A {
          |  type T is { tp: ^.TPrime }
          |  type TPrime is { t: ^.T }
          |  command DoIt is {}
          |  context C {
          |    entity E {
          |      record fields is {
          |        f: ^^^^.TPrime
          |      }
          |      state S of ^fields is  {
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
      parseAndResolve(RiddlParserInput(input))(
        { messages =>
          messages.format matches "^.*requires assignment compatibility"
          println(messages.format)
          messages.size mustBe 1
        },
        fail("Should have failed")
      )
    }

    "resolve simple path directly" in {
      val input =
        """domain D {
          |  context C {
          |    command DoIt is { value: Number }
          |    type Info is { g: C.DoIt }
          |    entity E is {
          |      record fields is { f: C.Info }
          |      state S of ^fields is {
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
      val eL = At.empty
      val root = RootContainer(
        contents = Seq(
          Domain(
            eL,
            Identifier(eL, "D"),
            includes = Seq(
              Include(
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
                    types = Seq(
                      Type(
                        eL,
                        Identifier(eL, "C2_T"),
                        AliasedTypeExpression(
                          eL,
                          PathIdentifier(eL, Seq("D", "C1", "C1_T"))
                        )
                      )
                    )
                  )
                ),
                Some("foo")
              )
            )
          )
        ),
        Seq.empty[RiddlParserInput]
      )
      root.contents.head.contents.length mustBe 2
      root.contents.head.contents.forall(_.kind == "Context")
      val result = resolve(root, CommonOptions())
      val messages = result.messages.toMessages
      val errors = messages.filter(_.kind >= Messages.Error)
      if (errors.nonEmpty) fail(errors.format) else succeed
    }
  }
}
