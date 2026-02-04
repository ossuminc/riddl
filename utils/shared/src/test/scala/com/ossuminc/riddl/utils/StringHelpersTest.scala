/*
 * Copyright 2019-2026 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.utils

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Test suite for StringHelpers extension methods and utilities
  *
  * Tests all edge cases including: - Empty strings - Single character strings - No matches - All matches - Boundary
  * conditions - Special characters - Unicode
  */
class StringHelpersTest extends AnyWordSpec with Matchers {

  import StringHelpers.*

  "StringHelpers.dropUntil" should {

    "return empty string when input is empty" in {
      "".dropUntil(_ == 'a') mustBe ""
    }

    "return empty string when no character matches" in {
      "hello".dropUntil(_ == 'z') mustBe ""
    }

    "return string from first match onwards" in {
      "hello".dropUntil(_ == 'l') mustBe "llo"
    }

    "return entire string when first character matches" in {
      "hello".dropUntil(_ == 'h') mustBe "hello"
    }

    "return single character when last character matches" in {
      "hello".dropUntil(_ == 'o') mustBe "o"
    }

    "work with single character string that matches" in {
      "x".dropUntil(_ == 'x') mustBe "x"
    }

    "work with single character string that doesn't match" in {
      "x".dropUntil(_ == 'y') mustBe ""
    }

    "handle predicates that match multiple characters" in {
      "hello world".dropUntil(_ == ' ') mustBe " world"
    }

    "work with numeric characters" in {
      "abc123".dropUntil(_.isDigit) mustBe "123"
    }

    "work with uppercase check" in {
      "helloWORLD".dropUntil(_.isUpper) mustBe "WORLD"
    }

    "handle complex predicates" in {
      "abc123xyz".dropUntil(c => c.isDigit && c > '1') mustBe "23xyz"
    }

    "work with special characters" in {
      "hello@world.com".dropUntil(_ == '@') mustBe "@world.com"
    }

    "work with unicode characters" in {
      "hello世界".dropUntil(_ > 127) mustBe "世界"
    }

    "handle whitespace predicates" in {
      "hello world".dropUntil(_.isWhitespace) mustBe " world"
    }
  }

  "StringHelpers.dropRightWhile" should {

    "return empty string when input is empty" in {
      "".dropRightWhile(_ == 'a') mustBe ""
    }

    "return string unchanged when no trailing characters match" in {
      "hello".dropRightWhile(_ == 'z') mustBe "hello"
    }

    "remove trailing characters that match" in {
      "hello!!!".dropRightWhile(_ == '!') mustBe "hello"
    }

    "remove all characters when all match" in {
      "!!!".dropRightWhile(_ == '!') mustBe ""
    }

    "return string unchanged when last character doesn't match" in {
      "hello".dropRightWhile(_ == 'x') mustBe "hello"
    }

    "work with single character string that doesn't match" in {
      "x".dropRightWhile(_ == 'y') mustBe "x"
    }

    "work with whitespace trimming" in {
      "hello   ".dropRightWhile(_.isWhitespace) mustBe "hello"
    }

    "work with numbers" in {
      "text123".dropRightWhile(_.isDigit) mustBe "text"
    }

    "work with complex predicates" in {
      "hello...".dropRightWhile(c => c == '.' || c == '!') mustBe "hello"
    }

    "handle multiple trailing character types" in {
      "hello...!!!".dropRightWhile(c => c == '.' || c == '!') mustBe "hello"
    }

    "work with unicode" in {
      "hello世世".dropRightWhile(_ == '世') mustBe "hello"
    }
  }

  "StringHelpers.toPrettyString" should {

    "format simple string" in {
      val result = toPrettyString("hello")
      result must include("hello")
    }

    "format integer" in {
      val result = toPrettyString(42)
      result must include("42")
    }

    "format case class" in {
      case class Person(name: String, age: Int)
      val result = toPrettyString(Person("Alice", 30))
      result must include("Person")
      result must include("name: Alice")
      result must include("age: 30")
    }

    "format nested case class" in {
      case class Address(street: String, city: String)
      case class Person(name: String, address: Address)
      val result = toPrettyString(Person("Alice", Address("Main St", "NYC")))
      result must include("Person")
      result must include("name: Alice")
      result must include("Address")
      result must include("street: Main St")
      result must include("city: NYC")
    }

    "format list" in {
      val result = toPrettyString(List(1, 2, 3))
      result must include("1")
      result must include("2")
      result must include("3")
    }

    "format list of case classes" in {
      case class Item(id: Int, name: String)
      val result = toPrettyString(List(Item(1, "First"), Item(2, "Second")))
      result must include("Item")
      result must include("id: 1")
      result must include("name: First")
      result must include("id: 2")
      result must include("name: Second")
    }

    "respect depth parameter" in {
      case class Inner(value: Int)
      case class Outer(inner: Inner)
      val result = toPrettyString(Outer(Inner(42)), depth = 2)
      // Should have extra indentation
      result must include("  ")
    }

    "format with parameter name" in {
      case class Person(name: String)
      val result = toPrettyString(Person("Alice"), paramName = Some("user"))
      result must include("user:")
    }

    "handle empty list" in {
      val result = toPrettyString(List.empty[Int])
      result must not be empty
    }

    "handle nested lists" in {
      val result = toPrettyString(List(List(1, 2), List(3, 4)))
      result must include("1")
      result must include("4")
    }

    "handle Option types" in {
      case class Person(name: String, age: Option[Int])
      val resultSome = toPrettyString(Person("Alice", Some(30)))
      resultSome must include("Some")
      resultSome must include("30")

      val resultNone = toPrettyString(Person("Bob", None))
      resultNone must include("None")
    }

    "handle deeply nested structures" in {
      case class Level3(value: Int)
      case class Level2(l3: Level3)
      case class Level1(l2: Level2)
      case class Root(l1: Level1)

      val result = toPrettyString(Root(Level1(Level2(Level3(42)))))
      result must include("Root")
      result must include("Level1")
      result must include("Level2")
      result must include("Level3")
      result must include("value: 42")
    }

    "use platform newline" in {
      val result = toPrettyString("test")
      result must include(pc.newline)
    }

    "handle tuple" in {
      val result = toPrettyString((1, "two", 3.0))
      result must include("Tuple3")
      result must include("1")
      result must include("two")
      result must include("3")
    }

    "handle Map (as Iterable)" in {
      val result = toPrettyString(Map("key1" -> "value1", "key2" -> "value2"))
      // Map is an Iterable, so it will iterate over tuples
      result must include("key1")
      result must include("value1")
    }
  }

  "StringHelpers edge cases" should {

    "dropUntil handles repeated characters" in {
      "aaabbbccc".dropUntil(_ == 'b') mustBe "bbbccc"
    }

    "dropRightWhile handles no trailing match but internal match" in {
      "a!!!b".dropRightWhile(_ == '!') mustBe "a!!!b"
    }

    "toPrettyString handles null in case class fields" in {
      case class WithNull(value: String)
      val result = toPrettyString(WithNull(null))
      result must include("null")
    }

    "toPrettyString handles very deep nesting" in {
      case class Node(value: Int, next: Option[Node])
      val deepList = Node(1, Some(Node(2, Some(Node(3, Some(Node(4, None)))))))
      val result = toPrettyString(deepList)
      result must include("value: 1")
      result must include("value: 4")
    }
  }
}
