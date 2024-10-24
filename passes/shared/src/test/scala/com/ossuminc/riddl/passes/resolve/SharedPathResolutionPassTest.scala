package com.ossuminc.riddl.passes.resolve

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.language.{At, Messages}
import com.ossuminc.riddl.passes.{PassInput, PassesOutput}
import com.ossuminc.riddl.utils.PlatformIOContext
import org.scalatest.{Assertion, TestData}

/** Unit Tests For the ResolutionPass */
class SharedPathResolutionPassTest(using pc: PlatformIOContext) extends SharedResolvingTest {

  "PathResolutionPass" must {
    "resolve a full path" in { (td: TestData) =>
      val rpi = RiddlParserInput(
        """domain A {
          |  domain B {
          |    domain C {
          |      type D = String
          |    }
          |  }
          |  type APrime = A.B.C.D
          |}""".stripMargin,
        td
      )
      parseAndResolve(rpi) { (input, outputs) =>
        val target: Type = input.root.domains.head.domains.head.domains.head.types.head
        val pid: Type = input.root.domains.head.types.head
        val parent = input.root.domains.head
        val resolution = outputs.outputOf[ResolutionOutput](ResolutionPass.name).get
        resolution.refMap.definitionOf[Type](pid.typEx.asInstanceOf[AliasedTypeExpression].pathId, parent) match {
          case Some(definition) =>
            definition must be(target)
          case None =>
            fail("APrime reference not found")
        }
      }
    }

    "resolve a relative path, B.C.D" in { (td: TestData) =>
      val rpi = RiddlParserInput(
        """domain A {
          |  domain B {
          |    domain C {
          |      type D = String
          |    }
          |    type FromB = B.C.D
          |  }
          |
          |}""".stripMargin,
        td
      )
      parseAndResolve(rpi) { (in, outs) =>
        val target: Type = in.root.domains.head.domains.head.domains.head.types.head
        val parent = in.root.domains.head.domains.head.types.head
        val pid = parent.typEx.asInstanceOf[AliasedTypeExpression].pathId
        val resolution = outs.outputOf[ResolutionOutput](ResolutionPass.name).get
        resolution.refMap.definitionOf[Type](pid, parent) match {
          case Some(resolved) =>
            resolved mustBe target
          case None => fail(s"$pid not resolved")
        }
      }
    }

    "resolve A.Top" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain A {
          |  type Top = String
          |  type aTop = type A.Top
          |}
          |""".stripMargin,
        td
      )
      parseAndResolve(input) { (in, outs) =>
        val target: Type = in.root.domains.head.types.find(_.id.value == "Top").get
        val parent: Type = in.root.domains.head.types.find(_.id.value == "aTop").get
        val pid = parent.typEx.asInstanceOf[AliasedTypeExpression].pathId
        val resolution = outs.outputOf[ResolutionOutput](ResolutionPass.name).get
        resolution.refMap.definitionOf[Type](pid, parent) match {
          case Some(resolvedDef) =>
            resolvedDef mustBe target
          case None =>
            fail(s"${pid.format} not resolved")
        }
      }
    }

    "resolve A.B.InB" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain A {
          |  domain B {
          |    type InB = String
          |  }
          |  domain C {
          |    type InC = A.B.InB
          |  }
          |}
          |""".stripMargin,
        td
      )
      parseAndResolve(input) { (_, _) => succeed }
    }

    "resolve entity field" in { (td: TestData) =>
      val input = RiddlParserInput(
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
          |""".stripMargin,
        td
      )
      parseAndResolve(input) { (_, _) => succeed }
    }

    "resolve nested fields - old " in { (td: TestData) =>
      val input = RiddlParserInput(
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
          |""".stripMargin,
        td
      )
      parseAndResolve(input) { (_, _) => succeed }
    }

    "resolve nested fields - new" in { (td: TestData) =>
      val input = RiddlParserInput(
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
          |""".stripMargin,
        td
      )
      parseAndResolve(input) { (_, _) => succeed }
    }

    "resolve complicated paths" in { (td: TestData) =>
      val input = RiddlParserInput(
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
          |""".stripMargin,
        td
      )
      parseAndResolve(input) { (in, outs) =>
        outs.getAllMessages mustBe Messages.empty
        val Top = in.root.domains.head.types.head
        val D = in.root.domains.head.domains.head.contexts.find(_.id.value == "D").get
        val ATop = D.types.find(_.id.value == "ATop").get
        val pid = ATop.typEx.asInstanceOf[AliasedTypeExpression].pathId
        val resolution = outs.outputOf[ResolutionOutput](ResolutionPass.name).get
        resolution.refMap.definitionOf[Type](pid, ATop) match {
          case Some(resolved) => resolved mustBe Top
          case None           => fail(s"$pid not resolved")
        }
      }
    }

    "resolve doc example" in { (td: TestData) =>
      val input = RiddlParserInput(
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
          |""".stripMargin,
        td
      )
      parseAndResolve(input) { (_, _) => succeed }
    }
    "deal with cyclic references" in { td =>
      val input = RiddlParserInput(
        """domain A {
          |  type T is { tp: A.TPrime } // Refers to T.TPrime
          |  type TPrime is { t: A.T } // Refers to A.T cyclically
          |  command DoIt is { ??? }
          |  context C {
          |    entity E {
          |      record fields is {
          |        f: A.TPrime
          |      }
          |      state S of E.fields
          |      handler foo is {
          |       on command DoIt {
          |         set field E.S.f.t to "true"
          |       }
          |      }
          |    }
          |  }
          |}
          |""".stripMargin,
        td
      )
      parseAndResolve(input) { (_, _) => succeed } { messages =>
        val errors = messages.justErrors
        info(errors.format)
        errors must be(empty)
      }
    }

    "resolve simple path directly" in { td =>
      val input = RiddlParserInput(
        """domain D {
          |  context C {
          |    command DoIt is { value: Number }
          |    type Info is { g: C.DoIt }
          |    entity E is {
          |      record fields is { f: C.Info }
          |      state S of E.fields is {
          |        handler E_Handler is {
          |          on command C.DoIt {
          |            |set S.f.g.value to @C.DoIt.value
          |          }
          |        }
          |      }
          |    }
          |  }
          |}
          |""".stripMargin,
        td
      )
      parseAndResolve(input) { (_, _) => succeed }
    }
    "resolve simple path through an include" in { _ =>
      import com.ossuminc.riddl.utils.URL
      val eL = At.empty
      val root = Root(
        contents = Contents(
          Domain(
            eL,
            Identifier(eL, "D"),
            contents = Contents(
              Include(
                eL,
                URL.empty,
                contents = Contents(
                  Context(
                    eL,
                    Identifier(eL, "C1"),
                    contents = Contents(Type(eL, Identifier(eL, "C1_T"), Number(eL)))
                  ),
                  Context(
                    eL,
                    Identifier(eL, "C2"),
                    contents = Contents(
                      Type(
                        eL,
                        Identifier(eL, "C2_T"),
                        AliasedTypeExpression(
                          eL,
                          "type",
                          PathIdentifier(eL, Seq("D", "C1", "C1_T"))
                        )
                      )
                    )
                  )
                )
              )
            )
          )
        )
      )
      val domain: Domain = root.domains.head
      domain.contents.length mustBe 1
      domain.contexts.forall(_.kind == "Include")
      domain.includes.size mustBe 1
      domain.includes.head.contents.filter[Context].length mustBe 2
      val (in, outs) = resolve(root)
      val messages = outs.getAllMessages
      val errors = messages.justErrors
      if errors.nonEmpty then fail(errors.format) else succeed
    }
    "resolve entity references" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain ReactiveBBQ is {
          |  type CustomerId is Id(ReactiveBBQ.Customer.Customer) explained as {
          |    "Unique identifier for a customer"
          |  }
          |  context Customer is {
          |    entity Customer is { ??? }
          |  }
          |}
          |""".stripMargin,
        td
      )
      parseAndResolve(input) { (in, outs) =>
        val entity = in.root.domains.head.contexts.head.entities.head
        entity.getClass mustBe classOf[Entity]
        val cid = in.root.domains.head.types.head
        cid.getClass mustBe classOf[Type]
        cid.typEx.getClass mustBe classOf[UniqueId]
        val pid = cid.typEx.asInstanceOf[UniqueId].entityPath
        pid.value mustBe Seq("ReactiveBBQ", "Customer", "Customer")
        val resolution = outs.outputOf[ResolutionOutput](ResolutionPass.name).get
        resolution.refMap.definitionOf[Entity](pid, cid) match {
          case Some(definition) =>
            if definition == entity then {
              succeed
            } else {
              fail("Didn't resolve to entity")
            }
          case None =>
            fail("reference not found")
        }
      }
    }
    "resolve references in morph action" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain Ignore is {
          |  context Ignore2 is {
          |    entity OfInterest is {
          |      command MorphIt is { ??? }
          |      record Data is { field: Integer }
          |      state First of record Data
          |      state Second of record Data
          |      handler only is {
          |        on command MorphIt {
          |          morph entity Ignore2.OfInterest to state OfInterest.First
          |            with command MorphIt // WTF is this for?
          |        }
          |      }
          |    }
          |  }
          |}
          |""".stripMargin,
        td
      )
      parseAndResolve(input) { (_, _) => succeed } { messages =>
        val errors = messages.justErrors
        if errors.nonEmpty then
          info(errors.format)
          fail("Should have succeeded")
        else succeed
      }
    }
    "resolve a path identifier" in { (td: TestData) =>
      val rpi = RiddlParserInput(
        """domain d is {
          |  context c is {
          |    entity e is {
          |      state s of record c.eState
          |      handler h is {
          |        on command c.foo { ??? }
          |      }
          |    }
          |    record eState is { f: Integer }
          |    command foo is { ??? }
          |  }
          |}
          |""".stripMargin,
        td
      )
      parseAndResolve(rpi)()()
    }
    "groups contain groups" in { (td: TestData) =>
      val rpi = RiddlParserInput(
        """domain foo {
          |  application app {
          |    group contained { ??? }
          |    group container { contains member as group contained }
          |  }
          |}
          |""".stripMargin,
        td
      )
      parseAndResolve(rpi) { (pi: PassInput, po: PassesOutput) =>
        val app: Application = pi.root.domains.head.applications.head
        val contained: Group = app.groups.head
        po.refMap.definitionOf[Group]("contained") match
          case Some(group: Group) =>
            group mustBe contained
          case _ =>
            fail("contained group not found")
      }()
    }
  }
}
