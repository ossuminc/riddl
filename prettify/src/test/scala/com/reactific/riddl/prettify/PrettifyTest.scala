/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.prettify

import com.reactific.riddl.language.CommonOptions
import com.reactific.riddl.language.Messages
import com.reactific.riddl.language.SymbolTable
import com.reactific.riddl.language.Validation
import com.reactific.riddl.language.parsing.RiddlParserInput
import com.reactific.riddl.testkit.RiddlFilesTestBase
import com.reactific.riddl.utils.SysLogger
import org.scalatest.Assertion

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/** Test The ReformatTranslator's ability to generate consistent output */
class PrettifyTest extends RiddlFilesTestBase {

  def checkAFile(rootDir: Path, file: File): Assertion = { checkAFile(file) }

  def outputWithLineNos(output: String): String = {
    output.split('\n').zipWithIndex.map { case (str, n) => f"$n%3d $str" }
      .mkString("\n")
  }

  def checkAFile(
    file: File,
    singleFile: Boolean = true
  ): Assertion = {
    val input = RiddlParserInput(file)
    parseTopLevelDomains(input) match {
      case Left(errors) =>
        val msg = errors.map(_.format).mkString
        fail(msg)
      case Right(root) =>
        val options = PrettifyCommand
          .Options(inputFile = Some(file.toPath), singleFile = singleFile)
        val common: CommonOptions = CommonOptions()
        val log = SysLogger()
        val result = Validation
          .Result(Messages.empty, root, SymbolTable(root), Map.empty, Map.empty)
        val output = PrettifyTranslator
          .translateToString(result, log, common, options)
        val file1 = Files.createTempFile(file.getName, ".riddl")
        Files.write(file1, output.getBytes(StandardCharsets.UTF_8))
        val input2 = RiddlParserInput(file1)
        parseTopLevelDomains(input2) match {
          case Left(errors) =>
            val message = errors.map(_.format).mkString("\n")
            fail(
              s"In '${file.getPath}': on first generation:\n" + message +
                "\nIn Source:\n" + output + "\n"
            )
          case Right(root2) =>
            val result2 = result.copy(root = root2)
            val output2 = PrettifyTranslator
              .translateToString(result2, log, common, options)
            parseTopLevelDomains(output2) match {
              case Left(errors) =>
                val message = errors.map(_.format).mkString("\n")
                fail(
                  s"In '${file.getPath}': on second generation: " + message +
                    "\nIn Source:\n" + output2 + "\n"
                )
              case Right(_) => output mustEqual output2
            }
        }
    }
    succeed
  }

  "PrettifyTranslator" should {
    "check domains" in { processADirectory("testkit/src/test/input/domains") }
    "check enumerations" in {
      processADirectory("testkit/src/test/input/enumerations")
    }
    "check mappings" in { processADirectory("testkit/src/test/input/mappings") }
    "check ranges" in { processADirectory("testkit/src/test/input/ranges") }
    "check empty.riddl" in {
      processAFile("testkit/src/test/input/empty.riddl")
    }
    "check everything.riddl" in {
      processAFile("testkit/src/test/input/everything.riddl")
    }
    "check petstore.riddl" in {
      processAFile("testkit/src/test/input/petstore.riddl")
    }
    "check rbbq.riddl" in { processAFile("testkit/src/test/input/rbbq.riddl") }
  }
}
