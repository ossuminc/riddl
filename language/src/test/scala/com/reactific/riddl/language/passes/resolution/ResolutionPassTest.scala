package com.reactific.riddl.language.passes.resolution

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.ast.At
import com.reactific.riddl.language.parsing.RiddlParserInput
import com.reactific.riddl.language.{CommonOptions, Messages}

import java.nio.file.Path

/** Unit Tests For the ResolutionPass */
class ResolutionPassTest extends ResolvingTest {

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
      parseAndResolve(RiddlParserInput(rpi)) { ro =>
        val target: Type = ro.root.domains.head.domains.head.domains.head.types.head
        val pid: Type = ro.root.domains.head.types.head
        val parent = ro.root.domains.head
        ro.refMap.definitionOf[Type](pid.typ.asInstanceOf[AliasedTypeExpression].pathId, parent) match {
          case Some(definition) =>
            definition must be(target)
          case None =>
            fail("APrime reference not found")
        }
      }
    }

    "resolve a relative path, B.C.D" in {
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
      parseAndResolve(RiddlParserInput(rpi)) { ro =>
        val target = ro.root.domains.head.domains.head.domains.head.types.head
        val parent = ro.root.domains.head.domains.head.types.head
        val pid = parent.typ.asInstanceOf[AliasedTypeExpression].pathId
        ro.refMap.definitionOf[Type](pid, parent) match {
          case Some(resolved) =>
            resolved mustBe(target)
          case None => fail(s"${pid} not resolved")
        }
      }
    }

    "resolve A.Top" in {
      val input =
        """domain A {
          |  type Top = String
          |  type aTop = type A.Top
          |}
          |""".stripMargin
      parseAndResolve(RiddlParserInput(input)) { ro =>
        val target = ro.root.domains.head.types.find(_.id.value == "Top").get
        val parent = ro.root.domains.head.types.find(_.id.value == "aTop").get
        val pid = parent.typ.asInstanceOf[AliasedTypeExpression].pathId
        ro.refMap.definitionOf[Type](pid, parent) match {
          case Some(resolvedDef) =>
            resolvedDef mustBe(target)
          case None =>
            fail(s"${pid.format} not resolved")
        }
      }
    }

    "resolve A.B.InB" in {
      val input =
        """domain A {
          |  domain B {
          |    type InB = String
          |  }
          |  domain C {
          |    type InC = A.B.InB
          |  }
          |}
          |""".stripMargin
      parseAndResolve(RiddlParserInput(input)) { _ => succeed }
    }

    "resolve entity field" in {
      val input =
        """domain A {
          |  context D {
          |    type DSimple = Number
          |    entity E {
          |      record fields is { a : D.DSimple }
          |      state only of E.fields is {
          |        handler OnlyFoo is { ??? }
          |      }
          |      handler ForE is { ??? }
          |    }
          |  }
          |}
          |""".stripMargin
      parseAndResolve(RiddlParserInput(input)) { _ => succeed }
    }

    "resolve nested fields - old " in {
      val input =
        """
          |domain D {
          |  type Bottom = { a: String }
          |  type Middle = { b: Bottom }
          |  type Top = { m: Middle }
          |
          |  context C {
          |    function foo {
          |      requires: { t: D.Top }
          |      returns: { a: String }
          |      example impl {
          |        then return @C.foo.t.m.b.a
          |      }
          |    }
          |  }
          |}
          |""".stripMargin
      parseAndResolve(RiddlParserInput(input)) { _ => succeed }
    }

    "resolve nested fields - new" in {
      val input =
        """
          |domain D {
          |  type Bottom = { a: String }
          |  type Middle = { b: D.Bottom }
          |  type Top = { m: D.Middle }
          |
          |  context C {
          |    function foo {
          |      requires: { t: D.Top }
          |      returns: { a: String }
          |      example impl {
          |        then return @foo.t.m.b.a
          |      }
          |    }
          |  }
          |}
          |""".stripMargin
      parseAndResolve(RiddlParserInput(input)) { _ => succeed }
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
          |    type CSimple = B.C.Simple // partial path
          |    context D {
          |      type ATop = A.Top
          |      type DSimple = D.E.ESimple // partial path
          |      entity E {
          |        type ESimple = B.CSimple // partial path
          |        event blah is { dSimple: D.DSimple }
          |        record fields is { a : A.Top }
          |        state only of E.fields is {
          |          handler foo  is {
          |            on event E.blah {
          |              then set E.fields.a to @E.blah.dSimple
          |            }
          |          }
          |        }
          |      }
          |    }
          |  }
          |}
          |""".stripMargin
      parseAndResolve(RiddlParserInput(input)) { ro: ResolutionOutput =>
        ro.messages mustBe (Messages.empty)
        val Top = ro.root.domains.head.types.head
        val D = ro.root.domains.head.domains.head.contexts.find(_.id.value == "D").get
        val ATop = D.types.find(_.id.value == "ATop").get
        val pid = ATop.typ.asInstanceOf[AliasedTypeExpression].pathId
        ro.refMap.definitionOf[Type](pid, ATop) match {
          case Some(resolved) => resolved mustBe(Top)
          case None => fail(s"${pid} not resolved")
        }
      }
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
          |      type DSimple = D.E.ESimple // partial path
          |      entity E {
          |        type ESimple = B.C.Simple // E->D->B->C->Simple
          |        type Complicated = B.D.DSimple // E->D->B->C->B->D->DSimple
          |        handler foo is { ??? }
          |      }
          |    }
          |  }
          |}
          |""".stripMargin
      parseAndResolve(RiddlParserInput(input)) { _ => succeed }
    }
    "deal with cyclic references" in {
      val input =
        """domain A {
          |  type T is { tp: A.TPrime } // Refers to T.TPrime
          |  type TPrime is { t: A.T } // Refers to A.T cyclically
          |  command DoIt is {}
          |  context C {
          |    entity E {
          |      record fields is {
          |        f: A.TPrime
          |      }
          |      state S of E.fields is  {
          |        handler foo is {
          |         on command DoIt {
          |           then set E.S.f.t to true
          |         }
          |        }
          |      }
          |    }
          |  }
          |}
          |""".stripMargin
      parseAndResolve(RiddlParserInput(input)) { _ => succeed } { messages =>
        println(messages.format)
        messages.size mustBe 1
        messages.head.format must include("Path resolution encountered a loop")
      }
    }

    "resolve simple path directly" in {
      val input =
        """domain D {
          |  context C {
          |    command DoIt is { value: Number }
          |    type Info is { g: C.DoIt }
          |    entity E is {
          |      record fields is { f: C.Info }
          |      state S of E.fields is {
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
      parseAndResolve(RiddlParserInput(input)) { _ => succeed }
    }
    "resolve simple path through an include" in {
      val eL = At.empty
      val root = RootContainer(
        domains = Seq(
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
        Seq.empty[Author],
        Seq.empty[RiddlParserInput]
      )
      root.contents.head.contents.length mustBe 2
      root.contents.head.contents.forall(_.kind == "Context")
      val result = resolve(root, CommonOptions())
      val messages = result.messages
      val errors = messages.filter(_.kind >= Messages.Error)
      if (errors.nonEmpty) fail(errors.format) else succeed
    }
    "resolve entity references" in {
      val input = RiddlParserInput(
        """domain ReactiveBBQ is {
          |  type CustomerId is Id(ReactiveBBQ.Customer.Customer) explained as {
          |    "Unique identifier for a customer"
          |  }
          |  context Customer is {
          |    entity Customer is { ??? }
          |  }
          |}
          |""".stripMargin
      )
      parseAndResolve(input) { ro =>
        val entity = ro.root.domains.head.contexts.head.entities.head
        entity.getClass mustBe (classOf[Entity])
        val cid = ro.root.domains.head.types.head
        cid.getClass mustBe (classOf[Type])
        cid.typ.getClass mustBe (classOf[UniqueId])
        val pid = cid.typ.asInstanceOf[UniqueId].entityPath
        pid.value mustBe (Seq("ReactiveBBQ", "Customer", "Customer"))
        ro.refMap.definitionOf[Entity](pid, cid) match {
          case Some(definition) =>
            if (definition == entity) {
              succeed
            } else {
              fail("Didn't resolve to entity")
            }
          case None =>
            fail("reference not found")
        }
      }
    }
    "resolve rbbq.riddl" in {
      val input = RiddlParserInput(Path.of("language/src/test/input/domains/rbbq.riddl"))
      parseAndResolve(input) { _ => succeed }
    }
    "resolve references in morph action" in {
      val input = RiddlParserInput(
        """domain Ignore is {
          |  context Ignore2 is {
          |    entity OfInterest is {
          |      command MorphIt is {}
          |      record Data is { field: Integer }
          |      state First of Data is { ??? }
          |      state Second of Data is {
          |        handler only is {
          |          on command MorphIt {
          |            then morph entity Ignore2.OfInterest to state OfInterest.First
          |            with !OfInterest.Data(field=3)
          |          }
          |        }
          |      }
          |    }
          |  }
          |}
          |""".stripMargin)
      parseAndResolve(input)  { _ => succeed }
    }
  }
}
