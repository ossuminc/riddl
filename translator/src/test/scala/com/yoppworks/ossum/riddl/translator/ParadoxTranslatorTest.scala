package com.yoppworks.ossum.riddl.translator

import java.io.File

import com.yoppworks.ossum.riddl.language.ParsingTest
import com.yoppworks.ossum.riddl.language.Riddl
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
      case Right(root) =>
        val trans = new ParadoxTranslator
        val config = ParadoxConfig()
        val files = trans.translate(root, None, Riddl.SysLogger, config)
        assert(files.nonEmpty)
    }
    succeed
  }

  // TODO: Fix FormatTranslator so this works again
  "ParadoxTranslator" should {
    "translate everything.riddl" in {
      runOne("everything.riddl")
      // pending
    }
    "translate rbbq.riddl" in { runOne("rbbq.riddl") }
  }
}
