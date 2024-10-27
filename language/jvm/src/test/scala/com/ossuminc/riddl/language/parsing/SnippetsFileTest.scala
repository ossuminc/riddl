/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.utils.{pc, ec}
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.utils.{Await, JVMPlatformContext, PathUtils, PlatformContext}

import java.nio.file.Path
import org.apache.commons.io.FileUtils

import java.io.File
import scala.jdk.CollectionConverters.*
import scala.io.AnsiColor.*
import org.scalatest.TestData

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt

/** Parsing tests that try a variety of code snippets that should parse */
class SnippetsFileTest extends ParsingTest {

  import com.ossuminc.riddl.language.parsing.RiddlParserInput._

  val topDir: Path = Path.of(s"language/jvm/src/test/input/snippets")
  val paths: Iterable[Path] =
    FileUtils.listFiles(topDir.toFile, Array("riddl"), true).asScala.map(_.toPath)

  "Snippet Files" should {
    "parse correctly" in {  (td:TestData) =>

      var failures = 0

      for { file <- paths} do
        val url = PathUtils.urlFromCwdPath(file)
        implicit val ec: ExecutionContext = pc.ec
        val future = RiddlParserInput.fromURL(url, td).map { rpi =>
          parseTopLevelDomains(rpi) match
            case Left(msgs: Messages) =>
              info(s"$RED${msgs.justErrors.format}$RESET")
              failures += 1
            case Right(_) =>
              info(s"${GREEN}Passed: $file$RESET")
          end match
        }
        Await.result(future, 10.seconds)
      if failures > 0 then fail(s"$failures failures")
      else succeed
    }
  }
}
