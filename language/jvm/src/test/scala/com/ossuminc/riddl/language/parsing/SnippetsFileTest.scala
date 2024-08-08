package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.Messages.Messages

import java.nio.file.Path
import org.apache.commons.io.FileUtils

import java.io.File
import scala.jdk.CollectionConverters.*
import scala.io.AnsiColor.*
import org.scalatest.TestData 

/** Parsing tests that try a variety of code snippets that should parse */
class SnippetsFileTest extends ParsingTest {

  import com.ossuminc.riddl.language.parsing.RiddlParserInput._

  val topDir: Path = Path.of(s"language/jvm/src/test/input/snippets")
  val files: Iterable[File] = FileUtils.listFiles(topDir.toFile, Array("riddl"), true).asScala

  "Snippet Files" should {
    "parse correctly" in {  (td:TestData) =>

      var failures = 0

      for { file <- files } do
        val input = rpiFromFile(file)
        parseTopLevelDomains(input) match
          case Left(msgs: Messages) =>
            info(s"$RED${msgs.format}$RESET")
            failures += 1
          case Right(_) =>
            info(s"${GREEN}Passed: $file$RESET")
      if failures > 0 then fail(s"$failures failures")
      else succeed
    }
  }
}
