/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.AST.Root
import com.ossuminc.riddl.language.Messages.*
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.passes.Riddl
import com.ossuminc.riddl.utils.{Await, CommonOptions, PathUtils, ec, pc}
import org.scalatest.{Assertion, TestData}

import java.nio.file.{Files, Path}
import scala.collection.mutable
import scala.jdk.StreamConverters.StreamHasToScala
import scala.runtime.stdLibPatches.Predef.assert
import scala.concurrent.duration.DurationInt

/** CheckMessage This test suite runs through the files in input/check directory and validates them each as their own
  * test case. Each .riddl file can have a .check file that lists the expected messages. If there is no .check file the
  * .riddl file is expected to validate completely with no messages.
  */
class CheckMessagesTest extends AbstractValidatingTest {

  val checkPathStr = "passes/jvm/src/test/input/check"
  val checkPath: Path = Path.of(checkPathStr)
  if !Files.isDirectory(checkPath) then {
    fail(s"Path of pos test cases must exist and be a directory, not a file.")
  }

  def validatePath(
    path: Path
  )(
    validation: (Root, Messages) => Assertion
  ): Assertion = {
    val url = PathUtils.urlFromCwdPath(path)
    val future = RiddlParserInput.fromURL(url).map { rpi =>
      Riddl.parseAndValidate(rpi, shouldFailOnError = false) match {
        case Left(errors) =>
          fail(s"In ${path.toString}:\n${errors.format}")
        case Right(result) =>
          validation(result.root, result.messages)
      }
    }
    Await.result(future, 10.seconds)
  }

  def runForFile(path: Path, readMessages: Set[String]): Unit = {
    val expectedMessages = readMessages.map(_.trim)
    validatePath(path) { (_, msgs) =>
      val msgSet = msgs.map(_.format).map(_.trim).toSet
      if msgSet == expectedMessages then { succeed }
      else {
        val missingMessages = expectedMessages.diff(msgSet).toSeq.sorted
        val unexpectedMessages = msgSet.diff(expectedMessages).toSeq.sorted

        val errMsg = new scala.collection.mutable.StringBuilder()
        if missingMessages.nonEmpty then {
          errMsg.append(
            missingMessages.mkString(
              "DID NOT FIND the following expected messages:\n\t",
              "\n\t",
              "\n"
            )
          )
        }
        if unexpectedMessages.nonEmpty then {
          errMsg.append(
            unexpectedMessages.mkString(
              "FOUND the following unexpected messages: \n\t",
              "\n\t",
              "\n"
            )
          )
        }
        if missingMessages.isEmpty && unexpectedMessages.isEmpty then succeed
        else fail(errMsg.toString())
      }
    }
  }

  def checkADirectory(dirName: String, testName: String): Unit = {
    pc.withOptions(CommonOptions.default.copy(noANSIMessages = true)) { _ =>
      val dir = checkPath.resolve(dirName)
      val dirContents: List[Path] = Files.list(dir).toScala(List)
      val checkFiles = dirContents.filter(dc => Files.isRegularFile(dc) && dc.toString.endsWith(".check"))
      val riddlFiles = dirContents.filter(dc => Files.isRegularFile(dc) && dc.toString.endsWith(".riddl"))

      if riddlFiles.isEmpty then {
        fail(s"No riddl files in directory $dirName.")
      } else if riddlFiles.length > 1 then {
        fail(
          s"Multiple root-level riddl files in directory $dirName not allowed"
        )
      } else {
        assert(riddlFiles.length == 1)
        val riddlFile = riddlFiles.head
        import scala.jdk.CollectionConverters.*
        val checkFileLines = checkFiles.iterator.flatMap { file =>
          java.nio.file.Files.readAllLines(file).iterator().asScala
        }
        val fixedLines = checkFileLines
          .filterNot { (l: String) => l.isEmpty }
          .foldLeft(Seq.empty[String]) { (list, next) =>
            if next.startsWith(" ") then
              val last = list.last + "\n" + next.drop(1)
              list.dropRight(1) :+ last
            else list :+ next
          }
          .toSet

        runForFile(riddlFile, fixedLines)
      }
    }
  }

  "Check Messages" should {
    "check ambiguity" in { (td: TestData) => checkADirectory("ambiguity", td) }
    "check domain" in { (td: TestData) => checkADirectory("domain", td) }
    "check everything" in { (td: TestData) => checkADirectory("everything", td) }
    "check fd-success" in { (td: TestData) => checkADirectory("fd-success", td) }
    "check overloading" in { (td: TestData) => checkADirectory("overloading", td) }
    "check references" in { (td: TestData) => checkADirectory("references", td) }
    "check saga" in { (td: TestData) => checkADirectory("saga", td) }
    "check streaming" in { (td: TestData) => checkADirectory("streaming", td) }
    "check t0001" in { (td: TestData) => checkADirectory("t0001", td) }
  }
}
