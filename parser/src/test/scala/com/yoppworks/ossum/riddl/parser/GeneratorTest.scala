package com.yoppworks.ossum.riddl.parser

import org.scalatest.Assertion

import scala.io.Source

/** Test Generator and Traversal */
class GeneratorTest extends ParsingTest {

  def runOne(fileName: String): Assertion = {
    val everything = s"parser/src/test/input/$fileName"
    val source = Source.fromFile(everything)
    val spacesOnly = "^\\s+$".r
    val comment = "(.*?)\\s*//.*$".r
    val input: String =
      source
        .getLines()
        .map {
          case comment(g) ⇒ g
          case x: String ⇒ x
        }
        .filter { l =>
          !(l.isEmpty || spacesOnly.findAllMatchIn(l).hasNext)
        }
        .mkString("\n")
    source.close()
    TopLevelParser.parseString(input, DomainParser.topLevelDomains(_)) match {
      case Left(message) ⇒
        fail(message)
      case Right(domains) ⇒
        domains.map { domain ⇒
          val generator = Generator.DomainGenerator(domain)
          val output = generator.traverse.mkString
          TopLevelParser.parseString(output, DomainParser.topLevelDomains(_)) match {
            case Left(message) ⇒
              fail("On First Generation:\n" + message)
            case Right(domains2) ⇒
              input mustEqual output
              domains2.map { domain2 ⇒
                val generator = new Generator.DomainGenerator(domain2)
                val output2 = generator.traverse.mkString
                TopLevelParser.parseString(
                  output2,
                  DomainParser.topLevelDomains(_)
                ) match {
                  case Left(message) ⇒
                    fail("On Second Generation: " + message)
                  case Right(_) ⇒
                    output mustEqual output2
                }
              }
          }
        }
        succeed
    }
  }

  "Generator" should {
    "reflect everything.riddl" in {
      runOne("everything.riddl")
    }
    "reflect rbbq.riddl" in {
      runOne("rbbq.riddl")
    }
  }
}
