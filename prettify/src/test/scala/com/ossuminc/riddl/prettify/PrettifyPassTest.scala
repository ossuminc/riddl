/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.prettify

import com.ossuminc.riddl.language.{CommonOptions, RiddlFilesTestBase}
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.passes.Pass.standardPasses
import com.ossuminc.riddl.passes.{PassInput, PassesOutput, Riddl}
import com.ossuminc.riddl.prettify.{PrettifyOutput, PrettifyPass, PrettifyState}
import org.scalatest.{Assertion, TestData}

import java.io.File
import java.nio.file.Path

/** Test The PrettifyPass's ability to generate consistent output */
class PrettifyPassTest extends RiddlFilesTestBase {

  def checkAFile(rootDir: Path, file: File): Assertion = { checkAFile(file) }

  def runPrettify(source: RiddlParserInput, run: String): String = {
    val passes = standardPasses ++ Seq(
      { (input: PassInput, outputs: PassesOutput) =>
        val options = PrettifyPass.Options(inputFile = Some(Path.of("aFile")))
        val state = PrettifyState(options)
        PrettifyPass(input, outputs, state)
      }
    )
    Riddl.parseAndValidate(source, CommonOptions(), shouldFailOnError = true, passes) match {
      case Left(messages) =>
        val errors = messages.justErrors
        require(errors.nonEmpty, "No actual errors!")
        fail(
          s"Errors on $run generation:\n" + errors.format +
            s"\nIn Source:\n ${source.data}\n" + "\n"
        )
      case Right(result) =>
        val prettifyOutput = result.outputOf[PrettifyOutput](PrettifyPass.name).get
        prettifyOutput.state.filesAsString
    }
  }

  /** Parse and prettify a file twice and compare the original with the third version. */
  def checkAFile(
    file: File
  ): Assertion = {
    val input1 = RiddlParserInput.rpiFromPath(file.toPath)
    val output1 = runPrettify(input1, "first")
    val input2 = RiddlParserInput(output1,"checkAFile")
    val output2 = runPrettify(input2, "second")
    val input3 = RiddlParserInput(output2,"checkAFile")
    val output3 = runPrettify(input3, "third")
    output1 mustEqual output3
  }

  "PrettifyTranslator" should {
    "check domains" in { (td: TestData) =>
      processADirectory("passes/jvm/src/test/input/domains")
    }
    "check enumerations" in { (td: TestData) =>
      processADirectory("passes/jvm/src/test/input/enumerations")
    }
    "check mappings" in { (td: TestData) =>
      processADirectory("passes/jvm/src/test/input/mappings")
    }
    "check ranges" in { (td: TestData) =>
      processADirectory("passes/jvm/src/test/input/ranges")
    }
    "check everything.riddl" in { (td: TestData) =>
      pending // FIXME: this isn't handled well by PrettifyPass
      // processAFile("passes/jvm/src/test/input/everything.riddl")
    }
    "check petstore.riddl" in { (td: TestData) =>
      processAFile("passes/jvm/src/test/input/petstore.riddl")
    }
    "check rbbq.riddl" in { (td: TestData) =>
      processAFile("passes/jvm/src/test/input/rbbq.riddl")
      println("done")
    }
  }
}
