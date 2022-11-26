/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.language

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.ast.At
import org.scalatest.matchers.*
import org.scalatest.wordspec.AnyWordSpec

/** Unit Tests For Abstract Syntax Tree */
class ASTTest extends AnyWordSpec with must.Matchers {

  "Types" should {
    "support domain definitions" in {
      Domain((0, 0), Identifier((1, 1), "foo")) must be
      Domain((0, 0), Identifier((1, 1), "foo"))
    }
    "support all type constructs" in {
      AliasedTypeExpression(0 -> 0, PathIdentifier(0 -> 0, Seq("Foo"))) mustBe
        AliasedTypeExpression(0 -> 0, PathIdentifier(0 -> 0, Seq("Foo")))

      Enumeration((0, 0), Seq.empty[Enumerator]) mustBe
        Enumeration((0, 0), Seq.empty[Enumerator])
      Alternation((0, 0), Seq.empty[AliasedTypeExpression]) mustBe
        Alternation((0, 0), Seq.empty[AliasedTypeExpression])
      Aggregation((0, 0), Seq.empty[Field]) mustBe
        Aggregation((0, 0), Seq.empty[Field])
      Optional(
        (0, 0),
        AliasedTypeExpression((0, 0), PathIdentifier((0, 0), Seq("String")))
      ) mustBe Optional(
        (0, 0),
        AliasedTypeExpression((0, 0), PathIdentifier((0, 0), Seq("String")))
      )
      ZeroOrMore(
        (0, 0),
        AliasedTypeExpression((0, 0), PathIdentifier((0, 0), Seq("Time")))
      ) mustBe ZeroOrMore(
        (0, 0),
        AliasedTypeExpression((0, 0), PathIdentifier((0, 0), Seq("Time")))
      )
      OneOrMore(
        (0, 0),
        AliasedTypeExpression((0, 0), PathIdentifier((0, 0), Seq("URL")))
      ) mustBe OneOrMore(
        (0, 0),
        AliasedTypeExpression((0, 0), PathIdentifier((0, 0), Seq("URL")))
      )
    }
  }

  "PathIdentifier" should {
    "format" in {
      PathIdentifier(At(), Nil).format mustBe ""
      PathIdentifier(At(), List("", "foo", "baz")).format mustBe "^foo.baz"
      PathIdentifier(At(), List("foo", "bar", "baz")).format mustBe
        "foo.bar.baz"
      PathIdentifier(At(), List("foo")).format mustBe "foo"
    }
  }

  "String" should {
    "have kind 'String'" in { Strng(At()).kind mustBe "String" }
  }

  "Bool" should {
    "have kind 'Boolean'" in { Bool(At()).kind mustBe "Boolean" }
  }

  "EntityAggregate" should {
    "have correct name" in {
      EntityIsAggregate(At()).name mustBe "aggregate"
      EntityTransient(At()).name mustBe "transient"
      EntityIsConsistent(At()).name mustBe "consistent"
      EntityIsAvailable(At()).name mustBe "available"
    }
  }

  val actor = Actor(
    At.empty,
    Identifier(At.empty, "actor"),
    LiteralString(At.empty, "role")
  )
  val adaptor = Adaptor(
    At.empty,
    Identifier(At.empty, "adaptor"),
    InboundAdaptor(At.empty),
    ContextRef(At.empty, PathIdentifier(At.empty, Seq("a", "b", "context")))
  )
  val application = Application(At.empty, Identifier(At.empty, "application"))
  val author =
    Author(At.empty, Identifier.empty, LiteralString.empty, LiteralString.empty)

  val sagastep = SagaStep(At.empty, Identifier(At.empty,"sagastep"))
  val state =
    State(At.empty, Identifier(At.empty, "state"), Aggregation.empty())
  val storycase = StoryCase(At.empty, Identifier(At.empty, "storycase"))
  val story = Story(At.empty, Identifier(At.empty, "story"))
  val term = Term(At.empty, Identifier(At.empty, "term"))
  "Actor" should {
    "have a test" in {
      actor.format mustBe s"actor ${actor.id.format} is ${actor.is_a.format}"
    }
  }

  "Adaptor" should { "have a test" in { pending } }
  "Application" should { "have a test" in { pending } }
  "Author" should { "have a test" in { pending } }

  "Context" should {
    "correctly identify emptiness" in {
      Context(At(), Identifier(At(), "test")).contents mustBe empty
    }
    "correctly identify non-emptiness" in {
      val types = List(Type(At(), Identifier(At(), "A"), Bool(At())))
      Context(At(), Identifier(At(), "test"), types = types).contents mustBe
        types
    }
  }

  "Domain" should {
    "empty domain should have empty contents" in {
      Domain(At(), Identifier(At(), "test")).contents mustBe empty
    }
    "non-empty domain should have non-empty contents" in {
      val types = List(Type(At(), Identifier(At(), "A"), Bool(At())))
      Domain(At(), Identifier(At(), "test"), types = types).contents mustBe
        types
    }
  }

  "Entity" should {
    "contents" should {
      "contain all contents" in {
        val options = Seq(
          EntityIsAggregate(At()),
          EntityTransient(At()),
          EntityKind(At(), Seq(LiteralString(At(), "concept")))
        )
        val states = Seq(State(
          At(),
          Identifier(At(), "bar"),
          Aggregation(
            At(),
            Seq[Field](Field(At(), Identifier(At(), "foo"), Integer(At())))
          )
        ))
        val handlers = Seq(Handler(At(), Identifier(At(), "con")))

        val functions = Seq(Function(
          At(),
          Identifier(At(), "my_func"),
          None,
          Option(Aggregation(
            At(),
            Seq(Field(At(), Identifier(At(), "a"), Bool(At())))
          ))
        ))

        val invariants = Seq(
          Invariant(At(), Identifier(At(), "my_id"), Some(True(At())), None)
        )
        val types = Seq(
          Type(At(), Identifier(At(), "mytype"), Bool(At())),
          Type(At(), Identifier(At(), "mytype2"), Bool(At()))
        )
        val entity = AST.Entity(
          loc = At(),
          id = Identifier(At(), "foo"),
          options = options,
          states = states,
          types = types,
          handlers = handlers,
          functions = functions,
          invariants = invariants,
          description = None
        )

        entity.contents.toSet mustBe
          (states.iterator ++ handlers ++ functions ++ invariants ++ types)
            .toSet
      }
    }
  }
  "Example" should { "have a test" in { pending } }
  "Field" should { "have a test" in { pending } }
  "Function" should { "have a test" in { pending } }
  "Group" should { "have a test" in { pending } }
  "Handler" should { "have a test" in { pending } }

  "Include" should {
    "identify as root container" in {
      Include(At(), Seq.empty[Definition]).isRootContainer mustBe true
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
  "Projection" should { "have a test" in { pending } }
  "Repository" should { "have a test" in { pending } }

  "RootContainer" should {
    "be at location 0,0" in { RootContainer(Nil).loc mustBe At.empty }
    "have no description" in { RootContainer(Nil).description mustBe None }
    "have no brief" in { RootContainer(Nil).brief mustBe None }
    "have no id" in { RootContainer(Nil).identify mustBe "Root" }
    "identify as root container" in {
      RootContainer(Nil).isRootContainer mustBe true
    }
  }

  "Saga" should { "have a test" in { pending } }
  "SagaStep" should { "have a test" in { pending } }
  "State" should { "format correctly" in { state.format mustBe "state state" } }
  "Story" should { "format correctly" in { story.format mustBe "story story" } }
  "StoryCase" should {
    "format correctly" in { storycase.format mustBe "case storycase" }
  }

  "Term" should {
    "format correctly" in {
      term.format mustBe s"${Keywords.term} ${term.id.format}"
    }
  }
}
