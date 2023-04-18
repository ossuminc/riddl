/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.testkit

import com.reactific.riddl.language.AST.RootContainer
import com.reactific.riddl.language.Messages.*
import com.reactific.riddl.language.parsing.RiddlParserInput
import com.reactific.riddl.language.CommonOptions
import com.reactific.riddl.passes.Riddl
import org.scalatest.Assertion

import java.io.File
import java.nio.file.{Files, Path}
import scala.collection.mutable

//noinspection ScalaStyle

/** CheckMessage This test suite runs through the files in input/check directory
  * and validates them each as their own test case. Each .riddl file can have a
  * .check file that lists the expected messages. If there is no .check file the
  * .riddl file is expected to validate completely with no messages.
  */
class CheckMessagesTest extends ValidatingTest {

  val checkPathStr = "testkit/src/test/input/check"
  val checkPath: Path = Path.of(checkPathStr)
  if !Files.isDirectory(checkPath) then {
    fail(s"Path of pos test cases must exist and be a directory, not a file.")
  }

  override def validateFile(
    label: String,
    fileName: String,
    options: CommonOptions = CommonOptions()
  )(
    validation: (RootContainer, Messages) => Assertion
  ): Assertion = {
    val input = RiddlParserInput(Path.of(fileName))
    Riddl.parseAndValidate(input, shouldFailOnError = false) match {
      case Left(errors) =>
        fail(s"In $label:\n${errors.format}")
      case Right(result) =>
        validation(result.root, result.messages)
    }
  }

  def runForFile(file: File, readMessages: Set[String]): Unit = {
    val expectedMessages = readMessages.map(_.trim)
    validateFile(file.getName, file.getAbsolutePath) { (_, msgs) =>
      val msgSet = msgs.map(_.format).filter(_.nonEmpty).map(_.trim).toSet
      if msgSet == expectedMessages then {succeed}
      else {
        val missingMessages = expectedMessages.diff(msgSet).toSeq.sorted
        val unexpectedMessages = msgSet.diff(expectedMessages).toSeq.sorted

        val errMsg = new mutable.StringBuilder()
        errMsg
          .append(msgSet.mkString("Got these messages:\n\t", "\n\t", ""))
        errMsg.append("\nBUT\n")
        if missingMessages.nonEmpty then {
          errMsg.append(missingMessages.mkString(
            "Expected to find the following messages and did not:\n\t",
            "\n\t",
            "\n"
          ))
        }
        if unexpectedMessages.nonEmpty then {
          errMsg.append(unexpectedMessages.mkString(
            "Found the following messages which were not expected: \n\t",
            "\n\t",
            "\n"
          ))
        }
        fail(errMsg.toString())
      }
    }
  }

  def checkADirectory(dirName: String): Unit = {
    val dir = checkPath.resolve(dirName)
    val dirContents = dir.toFile.listFiles()
    val checkFiles = dirContents
      .filter(dc => dc.isFile && dc.getName.endsWith(".check"))
    val riddlFiles = dirContents
      .filter(dc => dc.isFile && dc.getName.endsWith(".riddl"))

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
        java.nio.file.Files.readAllLines(file.toPath).iterator().asScala
      }
      val fixedLines = checkFileLines.filterNot { (l: String) => l.isEmpty }
        .foldLeft(Seq.empty[String]) { (list, next) =>
          if next.startsWith(" ") then
            val last = list.last + "\n" + next.drop(1)
            list.dropRight(1) :+ last
          else
            list :+ next
        }.toSet

      runForFile(riddlFile, fixedLines)
    }
  }



  "Check Messages" should {
    "check ambiguity" in { checkADirectory("ambiguity") }
    "check domain" in { checkADirectory("domain") }
    "check everything" in { checkADirectory("everything") }
    "check overloading" in { checkADirectory("overloading") }
    "check saga" in { checkADirectory("saga") }
    "check streaming" in { checkADirectory("streaming") }
    "check t0001" in { checkADirectory("t0001") }
  }
}
