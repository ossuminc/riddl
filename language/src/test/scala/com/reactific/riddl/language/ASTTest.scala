package com.reactific.riddl.language

import com.reactific.riddl.language.AST.*
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
      TypeRef(0 -> 0, PathIdentifier(0 -> 0, Seq("Foo"))) mustBe
        TypeRef(0 -> 0, PathIdentifier(0 -> 0, Seq("Foo")))

      Strng mustBe Strng
      Bool mustBe Bool
      Number mustBe Number
      Date mustBe Date
      Time mustBe Time
      TimeStamp mustBe TimeStamp
      URL mustBe URL
      Enumeration((0, 0), Seq.empty[Enumerator]) mustBe
        Enumeration((0, 0), Seq.empty[Enumerator])
      Alternation((0, 0), Seq.empty[TypeExpression]) mustBe
        Alternation((0, 0), Seq.empty[TypeExpression])
      Aggregation((0, 0), Seq.empty[Field]) mustBe
        Aggregation((0, 0), Seq.empty[Field])
      Optional(
        (0, 0),
        TypeRef((0, 0), PathIdentifier((0, 0), Seq("String")))
      ) mustBe
        Optional((0, 0), TypeRef((0, 0), PathIdentifier((0, 0), Seq("String"))))
      ZeroOrMore(
        (0, 0),
        TypeRef((0, 0), PathIdentifier((0, 0), Seq("Time")))
      ) mustBe
        ZeroOrMore((0, 0), TypeRef((0, 0), PathIdentifier((0, 0), Seq("Time"))))
      OneOrMore(
        (0, 0),
        TypeRef((0, 0), PathIdentifier((0, 0), Seq("URL")))
      ) mustBe
        OneOrMore((0, 0), TypeRef((0, 0), PathIdentifier((0, 0), Seq("URL"))))
    }
  }

  "PathIdentifier" should {
    "format" in {
      PathIdentifier(Location(), Nil).format mustBe ""
      PathIdentifier(Location(), List("foo", "bar", "baz")).format mustBe
        "baz.bar.foo"
      PathIdentifier(Location(), List("foo")).format mustBe "foo"
    }
  }

  "RootContainer" should {
    "be at location 0,0" in {
      RootContainer(Nil).loc mustBe Location(0, 0, "Root")
    }
    "have no description" in { RootContainer(Nil).description mustBe None }
    "have no brief" in { RootContainer(Nil).brief mustBe None }
    "have no id" in { RootContainer(Nil).identify mustBe "Root" }
  }

  "String" should {
    "have kind 'String'" in { Strng(Location()).kind mustBe "String" }
  }
  "Bool" should {
    "have kind 'Boolean'" in { Bool(Location()).kind mustBe "Boolean" }
  }

  "EntityAggregate" should {
    "have correct name" in {
      EntityAggregate(Location()).name mustBe "aggregate"
      EntityTransient(Location()).name mustBe "transient"
      EntityConsistent(Location()).name mustBe "consistent"
      EntityAvailable(Location()).name mustBe "available"
    }
  }

  "Domain" should {
    "empty domain should have empty contents" in {
      Domain(Location(), Identifier(Location(), "test")).contents mustBe empty
    }
    "non-empty domain should have non-empty contents" in {
      val types =
        List(Type(Location(), Identifier(Location(), "A"), Bool(Location())))
      Domain(Location(), Identifier(Location(), "test"), types = types)
        .contents mustBe types
    }
  }

  "Entity" should {
    "contents" should {
      "contain all contents" in {
        val options = Seq(
          EntityAggregate(Location()),
          EntityTransient(Location()),
          EntityKind(Location(), Seq(LiteralString(Location(), "concept")))
        )
        val states = Seq(State(
          Location(),
          Identifier(Location(), "bar"),
          Aggregation(
            Location(),
            Seq[Field](Field(
              Location(),
              Identifier(Location(), "foo"),
              Integer(Location())
            ))
          )
        ))
        val handlers = Seq(Handler(Location(), Identifier(Location(), "con")))

        val functions = Seq(Function(
          Location(),
          Identifier(Location(), "my_func"),
          None,
          Option(Aggregation(
            Location(),
            Seq(
              Field(Location(), Identifier(Location(), "a"), Bool(Location()))
            )
          )),
          Seq.empty[Example],
          None
        ))

        val invariants = Seq(Invariant(
          Location(),
          Identifier(Location(), "my_id"),
          True(Location()),
          None
        ))
        val types = Seq(
          Type(Location(), Identifier(Location(), "mytype"), Bool(Location())),
          Type(Location(), Identifier(Location(), "mytype2"), Bool(Location()))
        )
        val entity = AST.Entity(
          loc = Location(),
          id = Identifier(Location(), "foo"),
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

}
