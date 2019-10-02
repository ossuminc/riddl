package com.yoppworks.ossum.riddl.parser

import org.scalatest.Assertion

import scala.io.Source

/** Unit Tests For TraversalTest */
class TraversalTest extends ParsingTest {

  def runOne(fileName: String): Assertion = {
    val everything = s"parser/src/test/input/$fileName"
    val source = Source.fromFile(everything)
    val input: String =
      source.getLines().filter(!_.contains("//")).mkString("\n")
    source.close()
    TopLevelParser.parseString(input, TopLevelParser.topLevelDomains(_)) match {
      case Left(message) ⇒
        fail(message)
      case Right(domains) ⇒
        domains.map { domain ⇒
          val generator = new Generator.DomainTraveler(domain)
          val lines = generator.traverse
          val output = lines.mkString
          TopLevelParser.parseString(output, TopLevelParser.topLevelDomains(_)) match {
            case Left(message) ⇒
              fail(message)
            case Right(_) ⇒
              println(s"INPUT:\n$input")
              println(s"OUTPUT:\n$output")
              input must equal(output)
          }
        }
        succeed
    }
  }

  "Traversal" should {
    "reflect everything.riddl" in {
      pending // runOne("everything.riddl")
    }
    "reflect rbbq.riddl" in {
      pending // runOne("rbbq.riddl")
    }
  }
}
