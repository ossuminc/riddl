package com.yoppworks.ossum.riddl.translator

import java.io.File
import java.nio.file.Path

import com.yoppworks.ossum.riddl.language.ParsingTest
import com.yoppworks.ossum.riddl.language.RiddlParserInput
import org.scalatest.Assertion

/** Test Generator and Traversal */
class ParadoxTranslatorTest extends ParsingTest {

  def runOne(fileName: String): Assertion = {
    val fullName = s"language/src/test/input/$fileName"
    val input = RiddlParserInput(new File(fullName))
    parseTopLevelDomains(input) match {
      case Left(errors) =>
        val msg = errors.map(_.format).mkString
        fail(msg)
      case Right(roots) =>
        val trans = new ParadoxTranslator
        val config = trans.ParadoxConfig()
        trans.translate(roots, config)
    }
    succeed
  }

  // TODO: Fix FormatTranslator so this works again
  "FormatTranslator" should {
    "translate everything.riddl" in {
      pending //runOne("everything.riddl")
    }
    "translate rbbq.riddl" in {
      runOne("rbbq.riddl")
    }
  }
}
