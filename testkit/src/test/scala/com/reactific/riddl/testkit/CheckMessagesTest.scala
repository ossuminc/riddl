/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.testkit

import com.reactific.riddl.language.AST.RootContainer
import com.reactific.riddl.language.Messages.*
import com.reactific.riddl.language.parsing.RiddlParserInput
import com.reactific.riddl.language.{CommonOptions, Riddl}
import org.scalatest.Assertion

import java.io.File
import java.nio.file.Path
import scala.collection.mutable

//noinspection ScalaStyle

/** CheckMessage This test suite runs through the files in input/check directory
  * and validates them each as their own test case. Each .riddl file can have a
  * .check file that lists the expected messages. If there is no .check file the
  * .riddl file is expected to validate completely with no messages.
  */
class CheckMessagesTest extends ValidatingTest {

  val checkPathStr = "testkit/src/test/input/check"
  val checkFile = new File(checkPathStr)
  val checkPath: Path = checkFile.toPath

  override def validateFile(
    label: String,
    fileName: String,
    options: CommonOptions = CommonOptions()
  )(validation: (RootContainer, Messages) => Assertion
  ): Assertion = {
    val input = RiddlParserInput(Path.of(fileName))
    Riddl.parseAndValidate(input) match {
      case Left(errors) =>
        val msgs = errors.format
        fail(s"In $label:\n$msgs")
      case Right(result) =>
        validation(result.root, result.messages)
    }
  }

  "Check Messages" should {
    def runForFile(file: File, readMessages: Set[String]): Unit = {
      val relativeFileName = checkPath.relativize(file.toPath).toString
      val expectedMessages = readMessages.map(_.trim)
      s"check $relativeFileName" in {
        validateFile(file.getName, file.getAbsolutePath) { (_, msgs) =>
          val msgSet = msgs.map(_.format).filter(_.nonEmpty).map(_.trim).toSet
          if (msgSet == expectedMessages) { succeed }
          else {
            val missingMessages = expectedMessages.diff(msgSet).toSeq.sorted
            val unexpectedMessages = msgSet.diff(expectedMessages).toSeq.sorted

            val errMsg = new mutable.StringBuilder()
            errMsg
              .append(msgSet.mkString("Got these messages:\n\t", "\n\t", ""))
            errMsg.append("\nBUT\n")
            if (missingMessages.nonEmpty) {
              errMsg.append(missingMessages.mkString(
                "Expected to find the following messages and did not:\n\t",
                "\n\t",
                "\n"
              ))
            }
            if (unexpectedMessages.nonEmpty) {
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
    }

    val checkDir = checkFile
    if (!checkDir.isDirectory) {
      fail(s"Path of pos test cases must exist and be a directory, not a file.")
    }
    val posDirChildren = checkDir.listFiles()

    val (files, dirs) = posDirChildren.partition(_.isFile())

    files.foreach(file => runForFile(file, Set.empty))
    dirs.foreach { dir =>
      val dirContents = dir.listFiles()
      val checkFiles = dirContents
        .filter(dc => dc.isFile && dc.getName.endsWith(".check"))
      val riddlFiles = dirContents
        .filter(dc => dc.isFile && dc.getName.endsWith(".riddl"))

      if (riddlFiles.isEmpty) {
        fail(s"No riddl files in directory ${dir.getName}.")
      } else if (riddlFiles.length > 1) {
        fail(
          s"Multiple root-level riddl files in directory ${dir.getName}. Unsure which to validate"
        )
      } else {
        assert(riddlFiles.length == 1)
        val riddlFile = riddlFiles.head
        import scala.jdk.CollectionConverters.*
        val checkFileLines = checkFiles.iterator.flatMap { file =>
          java.nio.file.Files.readAllLines(file.toPath).iterator().asScala
        }
        val fixedLines = checkFileLines.filterNot { l: String => l.isEmpty }
          .foldLeft(Seq.empty[String]) { (list, next) =>
            if (next.startsWith(" ")) {
              val last = list.last + "\n" + next.drop(1)
              list.dropRight(1) :+ last
            } else { list :+ next }
          }.toSet

        runForFile(riddlFile, fixedLines)
      }
    }
  }
}
