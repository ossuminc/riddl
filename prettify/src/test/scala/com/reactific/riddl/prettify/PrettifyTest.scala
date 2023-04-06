/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.prettify

import com.reactific.riddl.language.{CommonOptions, Riddl}
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
    val input1 = RiddlParserInput(file)
    Riddl.parseAndValidate(input1) match {
      case Left(errors) =>
        fail(errors.format)
      case Right(result1) =>
        val options = PrettifyCommand
          .Options(inputFile = Some(file.toPath), singleFile = singleFile)
        val common: CommonOptions = CommonOptions()
        val log = SysLogger()
        val output1 = PrettifyTranslator
          .translateToString(result1, log, common, options)
        val file2 = Files.createTempFile(file.getName, ".riddl")
        Files.write(file2, output1.getBytes(StandardCharsets.UTF_8))
        val input2 = RiddlParserInput(file2)
        Riddl.parseAndValidate(input2) match {
          case Left(errors) =>
            val message = errors.format
            fail(
              s"In '${file.getPath}': on first generation:\n" + message +
                "\nIn Source:\n" + output1 + "\n"
            )
          case Right(result2) =>
            val input3 = PrettifyTranslator.translateToString(result2, log, common, options)
            Riddl.parseAndValidate(input3) match {
              case Left(errors) =>
                val messages = errors.format
                fail(
                  s"In '${file.getPath}': on second generation: " + messages +
                    "\nIn Source:\n" + input3 + "\n"
                )
              case Right(_) =>
                output1 mustEqual input3
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
    "check rbbq.riddl" in {
      processAFile("testkit/src/test/input/rbbq.riddl")
      println("done")
    }
  }
}
