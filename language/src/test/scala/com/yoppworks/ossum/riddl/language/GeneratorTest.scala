package com.yoppworks.ossum.riddl.language

import org.scalatest.Assertion

import scala.io.Source

/** Test Generator and Traversal */
class GeneratorTest extends ParsingTest {

  def runOne(fileName: String): Assertion = {
    val everything = s"language/src/test/input/$fileName"
    val source = Source.fromFile(everything)
    val spacesOnly = "^[\\s\\t]+\\n$".r
    val comment = "(.*?)\\s*//.*$".r
    val lines: Iterator[String] = source.getLines()
    val lastLine = lines.size - 1
    val input = lines
      .map {
        case comment(g)   => g
        case spacesOnly() => ""
        case x: String    => x
      }
      .zipWithIndex
      .filter {
        case (str, index) =>
          (index != lastLine) && str.isEmpty
      }
      .mkString("\n")
    source.close()
    parseTopLevelDomains(input) match {
      case Left(message) =>
        fail(message)
      case Right(domains) =>
        domains.map { domain =>
          val generator = Generator.DomainGenerator(domain)
          val output = generator.traverse.mkString
          parseTopLevelDomains(output) match {
            case Left(message) =>
              fail("On First Generation:\n" + message)
            case Right(domains2) =>
              input mustEqual output
              domains2.map { domain2 =>
                val generator = new Generator.DomainGenerator(domain2)
                val output2 = generator.traverse.mkString
                parseTopLevelDomains(output2) match {
                  case Left(message) =>
                    fail("On Second Generation: " + message)
                  case Right(_) =>
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
