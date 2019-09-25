package com.yoppworks.ossum.riddl.parser

import org.scalatest.MustMatchers
import org.scalatest.WordSpec

/** Unit Tests For ParsingTest */
trait ParsingTest extends WordSpec with MustMatchers {

  import AST._
  import DomainParser._
  import fastparse._

  def checkParser[T <: AST, U <: AST](
    input: String,
    expected: U,
    parser: P[_] ⇒ P[T],
    extraction: T ⇒ U
  ): Unit = {
    DomainParser.parseString(input, parser) match {
      case Right(content) =>
        extraction(content) mustBe expected
      case Left(msg) =>
        fail(msg)
    }
  }

  def checkDef[T <: AST](
    cases: Map[String, T],
    extraction: DomainDef ⇒ T
  ): Unit = {
    cases.foreach {
      case (statement: String, expected: T @unchecked) ⇒
        val input = "domain test { context foo { " + statement + " } }"
        checkParser[DomainDef, T](
          input,
          expected,
          domainDef(_),
          extraction
        )
    }
  }

  def runParser(
    input: String,
    expected: AST,
    extract: Seq[DomainDef] => AST
  ): Unit = {
    DomainParser.parseString(input, topLevelDomains(_)) match {
      case Right(content) =>
        extract(content) mustBe expected
      case Left(msg) =>
        fail(msg)
    }
  }

  def runParser[T <: AST, U <: AST](
    input: String,
    expected: Seq[U],
    parser: P[_] ⇒ P[Seq[T]],
    extraction: T ⇒ U
  ): Unit = {
    DomainParser.parseString(input, parser(_)) match {
      case Right(content) =>
        content mustBe expected
      case Left(msg) =>
        fail(msg)
    }
  }

}
