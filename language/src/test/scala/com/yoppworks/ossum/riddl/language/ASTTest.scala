package com.yoppworks.ossum.riddl.language

import org.scalatest._
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers._

/** Unit Tests For Abstract Syntax Tree */
class ASTTest extends AnyWordSpec with must.Matchers {
  import AST._

  "Types" should {
    "support domain definitions" in {
      Domain((0, 0), Identifier((1, 1), "foo")) must be
      Domain((0, 0), Identifier((1, 1), "foo"), Seq.empty[Type])
    }
    "support all type constructs" in {
      TypeRef(0 -> 0, PathIdentifier(0 -> 0, Seq("Foo"))) mustBe TypeRef(
        0 -> 0,
        PathIdentifier(0 -> 0, Seq("Foo"))
      )

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
      PathIdentifier(Location(), List("foo", "bar", "baz")).format mustBe "foo.bar.baz"
      PathIdentifier(Location(), List("foo")).format mustBe "foo"
    }
  }

  "RootContainer" should {
    "be at location 0,0" in {
      RootContainer(Nil).loc mustBe Location()
    }
    "have no description" in {
      RootContainer(Nil).description mustBe None
    }
  }

  "String" should {
    "have kind 'String'" in {
      Strng(Location()).kind mustBe "String"
    }
  }
  "Bool" should {
    "have kind 'Boolean'" in {
      Bool(Location()).kind mustBe "Boolean"
    }
  }

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
}
