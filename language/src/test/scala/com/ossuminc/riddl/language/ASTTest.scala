/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language

import com.ossuminc.riddl.language.parsing.Keyword
import com.ossuminc.riddl.language.AST.*
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Unit Tests For Abstract Syntax Tree */
class ASTTest extends AnyWordSpec with Matchers {

  "Types" should {
    "support domain definitions" in {
      Domain((0, 0), Identifier((1, 1), "foo")) must be
      Domain((0, 0), Identifier((1, 1), "foo"))
    }
    "support all type constructs" in {
      AliasedTypeExpression(0 -> 0, "record", PathIdentifier(0 -> 0, Seq("Foo"))).format mustBe "record Foo"
      Enumeration((0, 0), Seq.empty[Enumerator]).format mustBe "{  }"
      Alternation((0, 0), Seq.empty[AliasedTypeExpression]).format mustBe "one of {  }"
      Aggregation((0, 0), Seq.empty[Field]).format mustBe "{  }"
      Optional(
        (0, 0),
        AliasedTypeExpression((0, 0), "record", PathIdentifier((0, 0), Seq("String")))
      ).format mustBe "record String?"
      ZeroOrMore(
        (0, 0),
        AliasedTypeExpression((0, 0), "record", PathIdentifier((0, 0), Seq("Time")))
      ) mustBe ZeroOrMore(
        (0, 0),
        AliasedTypeExpression((0, 0), "record", PathIdentifier((0, 0), Seq("Time")))
      )
      OneOrMore(
        (0, 0),
        AliasedTypeExpression((0, 0), "record", PathIdentifier((0, 0), Seq("URL")))
      ) mustBe OneOrMore(
        (0, 0),
        AliasedTypeExpression((0, 0), "record", PathIdentifier((0, 0), Seq("URL")))
      )
      ZonedDateTime((0, 0)).format mustBe "ZonedDateTime(\"UTC\")"
      UUID((0, 0)).format mustBe "UUID"
      URL((0, 0)).format mustBe "URL(\"https\")"
      Location((0, 0)).format mustBe "Location"

      Blob((0, 0), BlobKind.Audio).format mustBe "Blob(Audio)"
      Blob((0, 0), BlobKind.Video).format mustBe "Blob(Video)"
      Blob((0, 0), BlobKind.CSV).format mustBe "Blob(CSV)"
      Blob((0, 0), BlobKind.FileSystem).format mustBe "Blob(FileSystem)"
      Blob((0, 0), BlobKind.Text).format mustBe "Blob(Text)"
      Blob((0, 0), BlobKind.XML).format mustBe "Blob(XML)"
      Blob((0, 0), BlobKind.JSON).format mustBe "Blob(JSON)"
      Blob((0, 0), BlobKind.Image).format mustBe "Blob(Image)"
    }
  }

  "PathIdentifier" should {
    "format" in {
      PathIdentifier(At(), Nil).format mustBe ""
      PathIdentifier(At(), List("foo", "baz")).format mustBe "foo.baz"
      PathIdentifier(At(), List("foo", "bar", "baz")).format mustBe "foo.bar.baz"
      PathIdentifier(At(), List("foo")).format mustBe "foo"
    }
  }

  "String" should {
    "have kind 'String'" in { String_(At()).kind mustBe "String" }
  }

  "Bool" should {
    "have kind 'Boolean'" in { Bool(At()).kind mustBe "Boolean" }
  }

  "Entity RiddlOptions" should {
    "have correct names" in {
      EntityIsAggregate(At()).name mustBe "aggregate"
      EntityTransient(At()).name mustBe "transient"
      EntityIsConsistent(At()).name mustBe "consistent"
      EntityIsAvailable(At()).name mustBe "available"
    }
  }

  val actor: User = User(
    At.empty,
    Identifier(At.empty, "user"),
    LiteralString(At.empty, "role")
  )
  val adaptor: Adaptor = Adaptor(
    At.empty,
    Identifier(At.empty, "adaptor"),
    InboundAdaptor(At.empty),
    ContextRef(At.empty, PathIdentifier(At.empty, Seq("a", "b", "context")))
  )
  val authorRef: AuthorRef =
    AuthorRef(At.empty, PathIdentifier(At.empty, Seq("a", "b", "c")))
  val application: Application = Application(
    At.empty,
    Identifier(At.empty, "application"),
    contents = Seq(authorRef)
  )
  val author: Author = Author(
    At.empty,
    Identifier(At(), "Reid"),
    LiteralString.empty,
    LiteralString.empty
  )

  val sagastep: SagaStep = SagaStep(At.empty, Identifier(At.empty, "sagastep"))
  val state: State =
    State(At.empty, Identifier(At.empty, "state"), TypeRef())
  val storycase: UseCase =
    UseCase(At.empty, Identifier(At.empty, "storycase"))
  val epic: Epic = Epic(At.empty, Identifier(At.empty, "epic"))
  val term: Term = Term(At.empty, Identifier(At.empty, "term"))
  "User" should {
    "have a test" in {
      actor.format mustBe s"user ${actor.id.format} is ${actor.is_a.format}"
    }
  }
  val domain: AST.Domain =
    Domain(At(), Identifier(At(), "test"), contents = Seq(author))
  val context: AST.Context = Context(At(), Identifier(At(), "test"))

  "Adaptor" should {
    "have a test" in {
      adaptor.loc mustBe At.empty
      adaptor.id.value mustBe "adaptor"
      adaptor.direction mustBe InboundAdaptor(At.empty)
      adaptor.context.pathId.value mustBe Seq("a", "b", "context")
    }
  }
  // TODO: Finish the pending cases
  "Application" should { "have a test" in { pending } }
  "Author" should {
    "be sane" in {
      author.isEmpty mustBe true
      author.format mustBe "author Reid"
    }
  }
  "AuthorRef" should {
    "convert to string" in { authorRef.format mustBe "author a.b.c" }
  }
  "AST.findAuthors" should {
    "find authors" in {
      val authors = AST.findAuthors(application, Seq(domain))
      authors mustBe Seq(authorRef)
    }
  }

  "Context" should {
    "correctly identify emptiness" in { context.contents mustBe empty }
    "correctly identify non-emptiness" in {
      val types = List(Type(At(), Identifier(At(), "A"), Bool(At())))
      Context(At(), Identifier(At(), "test"), contents = types).contents mustBe
        types
    }
  }
  "WithTypes" must {
    "be sane" in {
      val wt = Domain(At.empty, Identifier.empty)
      wt.hasAuthors mustBe false
      wt.hasTypes mustBe false
      wt.hasOptions mustBe false
      wt.isEmpty mustBe true
      wt.format mustBe ""
    }
  }

  "Domain" should {
    "empty domain should have empty contents" in {
      domain.contents mustNot be(empty)
    }
    "non-empty domain should have non-empty contents" in {
      val types = List(Type(At(), Identifier(At(), "A"), Bool(At())))
      Domain(At(), Identifier(At(), "test"), contents = types).contents mustBe
        types
    }
  }

  "Epic" should { "format correctly" in { epic.format mustBe "Epic epic" } }

  "Entity" should {
    "contents" should {
      "contain all contents" in {
        val options = Seq(
          EntityIsAggregate(At()),
          EntityTransient(At()),
          EntityKindOption(At(), Seq(LiteralString(At(), "concept")))
        )
        val states = Seq(
          State(
            At(),
            Identifier(At(), "bar"),
            TypeRef()
          )
        )
        val handlers = Seq(Handler(At(), Identifier(At(), "con")))

        val functions = Seq(
          Function(
            At(),
            Identifier(At(), "my_func"),
            None,
            Option(
              Aggregation(
                At(),
                Seq(Field(At(), Identifier(At(), "a"), Bool(At())))
              )
            )
          )
        )

        val invariants = Seq(
          Invariant(At(), Identifier(At(), "my_id"), Option(LiteralString(At(), "true")), None)
        )
        val types = Seq(
          Type(At(), Identifier(At(), "mytype"), Bool(At())),
          Type(At(), Identifier(At(), "mytype2"), Bool(At()))
        )
        val entity = AST.Entity(
          loc = At(),
          id = Identifier(At(), "foo"),
          contents = options ++ states ++ types ++ handlers ++ functions ++ invariants,
          description = None
        )

        entity.contents.toSet mustBe
          (options ++ states ++ types ++ handlers ++ functions ++ invariants).toSet
      }
    }
  }
  "Example" should { "have a test" in { pending } }
  "Field" should { "have a test" in { pending } }
  "Function" should { "have a test" in { pending } }
  "Group" should { "have a test" in { pending } }
  "Handler" should { "have a test" in { pending } }

  "Include" should {
    "identify as root container, etc" in {
      val incl = Include()
      incl.isRootContainer mustBe true
      incl.loc mustBe At.empty
      incl.format mustBe "include \"\""
    }
  }

  "Inlet" should { "have a test" in { pending } }
  "InletJoint" should { "have a test" in { pending } }
  "Input" should { "have a test" in { pending } }
  "Invariant" should { "have a test" in { pending } }
  "OnMessageClause" should { "have a test" in { pending } }
  "OnOtherClause" should { "have a test" in { pending } }
  "Outlet" should { "have a test" in { pending } }
  "OutletJoint" should { "have a test" in { pending } }
  "Output" should { "have a test" in { pending } }
  "Pipe" should { "have a test" in { pending } }
  "Plant" should { "have a test" in { pending } }
  "Processor" should { "have a test" in { pending } }
  "Projector" should { "have a test" in { pending } }
  "Repository" should { "have a test" in { pending } }

  "RootContainer" should {
    "be at location 0,0" in { Root(Nil).loc mustBe At.empty }
    "have no description" in { Root(Nil).description mustBe None }
    "have no brief" in { Root(Nil).brief mustBe None }
    "have no id" in { Root(Nil).identify mustBe "Root" }
    "identify as root container" in {
      Root(Nil).isRootContainer mustBe true
    }
  }

  "Saga" should { "have a test" in { pending } }
  "SagaStep" should { "have a test" in { pending } }
  "State" should { "format correctly" in { state.format mustBe "state state" } }
  "UseCase" should {
    "format correctly" in { storycase.format mustBe "case storycase" }
  }

  "Term" should {
    "format correctly" in {
      term.format mustBe s"${Keyword.term} ${term.id.format}"
    }
  }
}
