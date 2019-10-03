package com.yoppworks.ossum.riddl.parser

import org.scalatest.Assertion

import scala.io.Source

/** Test Generator and Traversal */
class GeneratorTest extends ParsingTest {

  def runOne(fileName: String): Assertion = {
    val everything = s"parser/src/test/input/$fileName"
    val source = Source.fromFile(everything)
    val spacesOnly = "^\\s+$".r
    val comment = "(.*)//.*".r
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
    TopLevelParser.parseString(input, TopLevelParser.topLevelDomains(_)) match {
      case Left(message) ⇒
        fail(message)
      case Right(domains) ⇒
        domains.map { domain ⇒
          val generator = new Generator.DomainGenerator(domain)
          val lines = generator.traverse
          val output = lines.mkString
          TopLevelParser.parseString(output, TopLevelParser.topLevelDomains(_)) match {
            case Left(message) ⇒
              fail(message)
            case Right(_) ⇒
              input must equal(output)
              println(s"INPUT:\n$input\n")
              println(s"OUTPUT:\n$output")
          }
        }
        succeed
    }
  }

  "Generator" should {
    "reflect everything.riddl" in {
      pending // runOne("everything.riddl")
    }
    "reflect rbbq.riddl" in {
      runOne("rbbq.riddl")
    }
  }
}
