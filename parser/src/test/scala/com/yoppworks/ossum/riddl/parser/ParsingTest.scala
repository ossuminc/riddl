package com.yoppworks.ossum.riddl.parser

import java.io.File

import org.scalatest.Assertion
import org.scalatest.MustMatchers
import org.scalatest.WordSpec

/** Unit Tests For ParsingTest */
trait ParsingTest extends WordSpec with MustMatchers {

  import AST._
  import TopLevelParser._
  import fastparse._

  def checkParser[T <: AST, U <: AST](
    input: String,
    expected: U,
    parser: P[_] ⇒ P[T],
    extraction: T ⇒ U
  ): Unit = {
    TopLevelParser.parseString(input, parser) match {
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
    TopLevelParser.parseString(input, topLevelDomains(_)) match {
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
    TopLevelParser.parseString(input, parser(_)) match {
      case Right(content) =>
        content mustBe expected
      case Left(msg) =>
        fail(msg)
    }
  }

  def checkFile(label: String, fileName: String): Assertion = {
    val directory = "parser/src/test/input/"
    val file = new File(directory + fileName)
    val results = TopLevelParser.parseFile(file, topLevelDomains(_))
    var failed = false
    val msg = results match {
      case Left(error) ⇒
        failed = true
        s"$label:$error"
      case Right(_) ⇒
        s"$label: Succeeded"
    }
    if (failed)
      fail(msg)
    else
      succeed
  }

}
