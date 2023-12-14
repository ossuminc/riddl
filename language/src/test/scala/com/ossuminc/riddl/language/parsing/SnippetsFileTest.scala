package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.Messages.Messages

import java.nio.file.Path
import org.apache.commons.io.FileUtils

import java.io.File
import scala.jdk.CollectionConverters.*
import scala.io.AnsiColor.*

/** Parsing tests that try a variety of code snippets that should parse */
class SnippetsFileTest extends ParsingTest {

  val topDir = Path.of(s"language/src/test/input/snippets")
  val files: Iterable[File] = FileUtils.listFiles(topDir.toFile, Array("riddl"), true).asScala

  "Snippet Files" should {
    "parse correctly" in {
      for { file <- files } do
        val input = RiddlParserInput(file)
        parseTopLevelDomains(input) match
          case Left(msgs: Messages) =>
            fail(msgs.format)
          case Right(_) =>
            info(s"${GREEN}Passed: $file$RESET")
            succeed
    }
  }
}
