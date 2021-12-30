package com.yoppworks.ossum.riddl.language

import org.scalatest.matchers.*
import org.scalatest.wordspec.AnyWordSpec

/** Unit Tests For Abstract Syntax Tree */
class ASTTest extends AnyWordSpec with must.Matchers {
  import AST.*

  "Types" should {
    "support domain definitions" in {
      Domain((0, 0), Identifier((1, 1), "foo")) must be
      Domain((0, 0), Identifier((1, 1), "foo"), Seq.empty[Type])
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
      Enumeration((0, 0), Seq.empty[Enumerator]) mustBe Enumeration((0, 0), Seq.empty[Enumerator])
      Alternation((0, 0), Seq.empty[TypeExpression]) mustBe
        Alternation((0, 0), Seq.empty[TypeExpression])
      Aggregation((0, 0), Seq.empty[Field]) mustBe Aggregation((0, 0), Seq.empty[Field])
      Optional((0, 0), TypeRef((0, 0), PathIdentifier((0, 0), Seq("String")))) mustBe
        Optional((0, 0), TypeRef((0, 0), PathIdentifier((0, 0), Seq("String"))))
      ZeroOrMore((0, 0), TypeRef((0, 0), PathIdentifier((0, 0), Seq("Time")))) mustBe
        ZeroOrMore((0, 0), TypeRef((0, 0), PathIdentifier((0, 0), Seq("Time"))))
      OneOrMore((0, 0), TypeRef((0, 0), PathIdentifier((0, 0), Seq("URL")))) mustBe
        OneOrMore((0, 0), TypeRef((0, 0), PathIdentifier((0, 0), Seq("URL"))))
    }
  }

  "PathIdentifier" should {
    "format" in {
      PathIdentifier(Location(), Nil).format mustBe "<empty>"
      PathIdentifier(Location(), List("foo", "bar", "baz")).format mustBe "baz.bar.foo"
      PathIdentifier(Location(), List("foo")).format mustBe "foo"
    }
  }

  "RootContainer" should {
    "be at location 0,0" in { RootContainer(Nil).loc mustBe Location() }
    "have no description" in { RootContainer(Nil).description mustBe None }
  }

  "String" should { "have kind 'String'" in { Strng(Location()).kind mustBe "String" } }
  "Bool" should { "have kind 'Boolean'" in { Bool(Location()).kind mustBe "Boolean" } }

  "Topic" should {
    "empty topic" should {
      "have empty contents" in {
        Topic(Location(), Identifier(Location(), "foo")).contents mustBe Nil
      }
    }
  }
  "EntityAggregate" should {
    "have correct name" in {
      EntityAggregate(Location()).name mustBe "aggregate"
      EntityPersistent(Location()).name mustBe "persistent"
      EntityConsistent(Location()).name mustBe "consistent"
      EntityAvailable(Location()).name mustBe "available"
    }
  }

  "Domain" should {
    "empty domain should have empty contents" in {
      Domain(Location(), Identifier(Location(), "test")).contents mustBe empty
    }
    "non-empty domain should have non-empty contents" in {
      val types = List(Type(Location(), Identifier(Location(), "A"), Bool(Location())))
      val topics = List(Topic(Location(), Identifier(Location(), "foo")))
      Domain(Location(), Identifier(Location(), "test"), types = types, topics = topics)
        .contents mustBe (types ++ topics)
    }
  }

  "Entity" should {
    "contents" should {
      "contain all contents" in {
        val options = Seq(EntityAggregate(Location()), EntityPersistent(Location()))
        val states = Seq(State(
          Location(),
          Identifier(Location(), "bar"),
          RangeType(
            Location(),
            LiteralInteger(Location(), BigInt(0)),
            LiteralInteger(Location(), BigInt(0))
          )
        ))
        val handlers = Seq(Handler(Location(), Identifier(Location(), "con")))
        val features = Seq(
          Feature(
            Location(),
            id = Identifier(Location(), "the_feature1"),
            background = None,
            examples = Nil,
            description = None
          ),
          Feature(
            Location(),
            id = Identifier(Location(), "the_feature2"),
            background = None,
            examples = Nil,
            description = None
          )
        )

        val functions = Seq(Function(
          Location(),
          Identifier(Location(), "my_func"),
          None,
          Some(Bool(Location())),
          Seq.empty[Example],
          None
        ))

        val invariants = Seq(Invariant(Location(), Identifier(Location(), "my_id"), Nil, None))
        val types = Seq(
          Type(Location(), Identifier(Location(), "mytype"), Bool(Location())),
          Type(Location(), Identifier(Location(), "mytype2"), Bool(Location()))
        )
        val entity = AST.Entity(
          entityKind = ConceptEntityKind(Location()),
          loc = Location(),
          id = Identifier(Location(), "foo"),
          options = options,
          states = states,
          types = types,
          handlers = handlers,
          features = features,
          functions = functions,
          invariants = invariants,
          description = None
        )

        entity.contents.toSet mustBe
          (states.iterator ++ handlers ++ features ++ functions ++ invariants ++ types).toSet
      }
    }
  }

}
