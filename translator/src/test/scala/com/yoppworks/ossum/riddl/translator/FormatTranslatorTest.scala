package com.yoppworks.ossum.riddl.translator

import org.scalatest.Assertion
import com.yoppworks.ossum.riddl.language.ParsingTest

import scala.io.Source

/** Test Generator and Traversal */
class FormatTranslatorTest extends ParsingTest {

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
      case Left(errors) =>
        val msg = errors.map(_.toString).mkString
        fail(msg)
      case Right(roots) =>
        val output = FormatTranslator.translateToString(roots)
        parseTopLevelDomains(output) match {
          case Left(message) =>
            fail("On First Generation:\n" + message)
          case Right(roots2) =>
            input mustEqual output
            val output2 = FormatTranslator.translateToString(roots2)
            parseTopLevelDomains(output2) match {
              case Left(message) =>
                fail("On Second Generation: " + message)
              case Right(_) =>
                output mustEqual output2
            }
        }
    }
    succeed
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
