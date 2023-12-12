package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.Messages.Messages

import java.nio.file.Path
import org.apache.commons.io.FileUtils

import java.io.File
import scala.jdk.CollectionConverters.*

/** Parsing tests that try a variety of code snippets that should parse */
class SnippetsFileTest extends ParsingTest {

  val topDir = Path.of(s"language/src/test/input/snippets")
  val files: Iterable[File] = FileUtils.listFiles(topDir.toFile, Array("riddl"), true).asScala
  "Snippet Files" should {
    "app parse correctly" in {
      for {file <- files} do
        info(s"File: $file")
        val input = RiddlParserInput(file)
        parseTopLevelDomains(input) match
          case Left(msgs: Messages) =>
            fail(msgs.format)
          case Right(root) =>
            succeed
    }
  }
}
