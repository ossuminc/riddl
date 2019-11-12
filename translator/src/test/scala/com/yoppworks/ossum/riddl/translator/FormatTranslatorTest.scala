package com.yoppworks.ossum.riddl.translator

import java.io.File

import com.yoppworks.ossum.riddl.language.ParsingTest
import com.yoppworks.ossum.riddl.language.RiddlParserInput
import org.scalatest.Assertion

/** Test Generator and Traversal */
class FormatTranslatorTest extends ParsingTest {

  def runOne(fileName: String): Assertion = {
    val fullName = s"language/src/test/input/$fileName"
    val input = RiddlParserInput(new File(fullName))
    parseTopLevelDomains(input) match {
      case Left(errors) =>
        val msg = errors.map(_.format).mkString
        fail(msg)
      case Right(roots) =>
        val trans = new FormatTranslator
        val output = trans.translateToString(roots)
        parseTopLevelDomains(output) match {
          case Left(errors) =>
            val message = errors.map(_.format).mkString("\n")
            println(output)
            fail("On First Generation:\n" + message)
          case Right(roots2) =>
            input mustEqual output
            val trans2 = new FormatTranslator
            val output2 = trans2.translateToString(roots2)
            parseTopLevelDomains(output2) match {
              case Left(errors) =>
                val message = errors.map(_.format).mkString("\n")
                fail("On Second Generation: " + message)
              case Right(_) =>
                output mustEqual output2
            }
        }
    }
    succeed
  }

  // TODO: Fix FormatTranslator so this works again
  "FormatTranslator" should {
    "reflect everything.riddl" in {
      pending //runOne("everything.riddl")
    }
    "reflect rbbq.riddl" in {
      pending // runOne("rbbq.riddl")
    }
    "error out on nestedInvalid.riddl" in {
      pending
    }
  }
}
