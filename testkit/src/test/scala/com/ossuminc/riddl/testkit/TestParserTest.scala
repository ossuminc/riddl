package com.ossuminc.riddl.testkit

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.{AST,At}
import com.ossuminc.riddl.language.Messages.Messages
import java.nio.file.Path

class TestParserTest extends AnyWordSpec with Matchers {

  "TestParser" should {
    val input = RiddlParserInput(Path.of("language/src/test/input/everything.riddl"))
    val tp = TestParser(input)

    "provide expect" in {
      tp.expect[Root](tp.root) match {
        case Left(messages) => fail(messages.justErrors.format)
        case Right(root: Root) =>
          val domains = AST.getTopLevelDomains(root)
          domains.size must be(1)
          domains.head.isEmpty must be(false)
          domains.head.identify must be("Domain 'Everything'")
      }
    }
    "provide parse" in {
      tp.parse[Root,Domain](tp.root, AST.getTopLevelDomains(_).head) match {
        case Left(messages) => fail(messages.justErrors.format)
        case Right((domain: Domain, rpi: RiddlParserInput)) =>
          rpi must be(input)
          domain.identify must be("Domain 'Everything'")
      }
    }
    "provide parserRoot" in {
      tp.parseRoot match {
        case Left(messages) => fail(messages.justErrors.format)
        case Right(root: Root) =>
          val domains = AST.getTopLevelDomains(root)
          domains.size must be(1)
          domains.head.isEmpty must be(false)
          domains.head.identify must be("Domain 'Everything'")
      }
    }
    "provide parseTopLevelDomain" in {
      tp.parseTopLevelDomains match {
        case Left(messages) => fail(messages.justErrors.format)
        case Right(root: Root) =>
          val domains = AST.getTopLevelDomains(root)
          domains.size must be(1)
          domains.head.isEmpty must be(false)
          domains.head.identify must be("Domain 'Everything'")
      }
    }
    "provide parseTopLevelDomain(extractor)" in {
      tp.parseTopLevelDomain[Domain](_.domains.head) match {
        case Left(messages: Messages) => fail(messages.justErrors.format)
        case Right(domain: Domain) =>
          domain.identify must be ("Domain 'Everything'")
      }
    }
    "provide parseDefinition(extractor)" in {
      tp.parseDefinition[Root,Domain](_.domains.head) match {
        case Left(messages: Messages) => fail(messages.justErrors.format)
        case Right((domain: Domain, rpi: RiddlParserInput)) =>
          domain.identify must be ("Domain 'Everything'")
          rpi must be(input)
      }
    }
    "provide parseDefinition[TYPE]" in {
      tp.parseDefinition[Root] match {
        case Left(messages: Messages) => fail(messages.justErrors.format)
        case Right((root: Root, rpi: RiddlParserInput)) =>
          root.identify must be("Root")
          rpi must be(input)
      }
    }

    "provide parseDomainDefinition(extractor)" in {
      val raw = """domain foo is { type x is Integer }"""
      val input = RiddlParserInput(raw)
      val tp = TestParser(input)
      tp.parseDomainDefinition[Type](_.types.head) match {
        case Left(messages: Messages) => fail(messages.justErrors.format)
        case Right((typ: Type, rpi: RiddlParserInput)) =>
          rpi must be(input)
          typ.typ must be(Integer(At((1,27))))
      }
    }
    "provide parseContextDefinition(extractor)" in {
      val raw = """context foo is { type x is Integer }"""
      val input = RiddlParserInput(raw)
      val tp = TestParser(input)
      tp.parseContextDefinition[Type](_.types.head) match {
        case Left(messages: Messages) => fail(messages.justErrors.format)
        case Right((typ: Type, rpi: RiddlParserInput)) =>
          rpi must be(input)
          typ.typ must be(Integer(At((1,28))))
      }
    }
  }
}
