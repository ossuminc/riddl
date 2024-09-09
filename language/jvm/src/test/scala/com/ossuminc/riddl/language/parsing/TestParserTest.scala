package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.{AST,At}
import com.ossuminc.riddl.language.Messages.* 

import java.nio.file.Path
import org.scalatest.TestData

class TestParserTest extends ParsingTest {

  "TestParser" should {
    val path = Path.of("language/jvm/src/test/input/everything.riddl")
    val input = RiddlParserInput.fromCwdPath(path)
    val tp = TestParser(input)

    "provide expect" in { (td: TestData) =>
      tp.expect[Root](tp.root) match {
        case Left(messages) => fail(messages.justErrors.format)
        case Right(root: Root) =>
          val domains = AST.getTopLevelDomains(root)
          domains.size must be(1)
          domains.head.isEmpty must be(false)
          domains.head.identify must be("Domain 'Everything'")
      }
    }
    "provide parse" in { (td: TestData) =>
      tp.parse[Root, Domain](tp.root, AST.getTopLevelDomains(_).head) match {
        case Left(messages) => fail(messages.justErrors.format)
        case Right((domain: Domain, rpi: RiddlParserInput)) =>
          rpi must be(input)
          domain.identify must be("Domain 'Everything'")
      }
    }
    "provide parserRoot" in { (td: TestData) =>
      tp.parseRoot match {
        case Left(messages) => fail(messages.justErrors.format)
        case Right(root: Root) =>
          val domains = AST.getTopLevelDomains(root)
          domains.size must be(1)
          domains.head.isEmpty must be(false)
          domains.head.identify must be("Domain 'Everything'")
      }
    }
    "provide parseTopLevelDomain" in { (td: TestData) =>
      tp.parseTopLevelDomains match {
        case Left(messages) => fail(messages.justErrors.format)
        case Right(root: Root) =>
          val domains = AST.getTopLevelDomains(root)
          domains.size must be(1)
          domains.head.isEmpty must be(false)
          domains.head.identify must be("Domain 'Everything'")
      }
    }
    "provide parseTopLevelDomain(extractor)" in { (td: TestData) =>
      tp.parseTopLevelDomain[Domain](_.domains.head) match {
        case Left(messages: Messages) => fail(messages.justErrors.format)
        case Right(domain: Domain) =>
          domain.identify must be("Domain 'Everything'")
      }
    }
    "provide parseDefinition(extractor)" in { (td: TestData) =>
      tp.parseDefinition[Root, Domain](_.domains.head) match {
        case Left(messages: Messages) => fail(messages.justErrors.format)
        case Right((domain: Domain, rpi: RiddlParserInput)) =>
          domain.identify must be("Domain 'Everything'")
          rpi must be(input)
      }
    }
    "provide parseDefinition[TYPE]" in { (td: TestData) =>
      tp.parseDefinition[Root] match {
        case Left(messages: Messages) => fail(messages.justErrors.format)
        case Right((root: Root, rpi: RiddlParserInput)) =>
          root.identify must be("Root")
          rpi must be(input)
      }
    }

    "provide parseDomainDefinition(extractor)" in { (td: TestData) =>
      val raw: String = """domain foo is { type x is Integer }"""
      val input = RiddlParserInput.apply(raw, td.toString)
      val tp = TestParser(input)
      tp.parseDomainDefinition[Type](_.types.head) match {
        case Left(messages: Messages) => fail(messages.justErrors.format)
        case Right((typ: Type, rpi: RiddlParserInput)) =>
          rpi must be(input)
          typ.typEx must be(Integer(At((1, 27))))
      }
    }
    "provide parseContextDefinition(extractor)" in { (td: TestData) =>
      val raw = """context foo is { type x is Integer }"""
      val input = RiddlParserInput(raw, td)
      val tp = TestParser(input)
      tp.parseContextDefinition[Type](_.types.head) match {
        case Left(messages: Messages) => fail(messages.justErrors.format)
        case Right((typ: Type, rpi: RiddlParserInput)) =>
          rpi.origin must be(input.origin)
          typ.typEx must be(Integer(At((1, 28))))
      }
    }
  }
}
