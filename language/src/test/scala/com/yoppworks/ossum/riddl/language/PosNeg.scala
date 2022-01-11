package com.yoppworks.ossum.riddl.language

import com.yoppworks.ossum.riddl.language.AST.RootContainer
import com.yoppworks.ossum.riddl.language.Validation.{ValidationMessages, ValidationOptions}
import com.yoppworks.ossum.riddl.language.parsing.TopLevelParser
import org.scalatest.Assertion

import java.io.File
import java.nio.file.Path

//noinspection ScalaStyle
class PosNeg extends ValidatingTest {

  val negPathStr = "language/src/test/input/posneg/neg"
  val posPathStr = "language/src/test/input/posneg/pos"
  val negFile = new File(negPathStr)
  val posFile = new File(posPathStr)
  val negPath: Path = negFile.toPath
  val posPath: Path = posFile.toPath

  override def validateFile(
    label: String,
    fileName: String,
    options: ValidationOptions = ValidationOptions.Default
  )(validation: (RootContainer, ValidationMessages) => Assertion
  ): Assertion = {
    val file = new File(fileName)
    TopLevelParser.parse(file) match {
      case Left(errors) =>
        val msgs = errors.iterator.map(_.format).mkString("\n")
        fail(s"In $label:\n$msgs")
      case Right(root) =>
        val messages = Validation.validate(root, options)
        validation(root, messages)
    }
  }

  "Pos" should {
    def runForFile(file: File, expectedMessages: Set[String]): Unit = {
      val relativeFileName = posPath.relativize(file.toPath).toString

      s"check $relativeFileName" in {
        validateFile(file.getName, file.getAbsolutePath) { (_, msgs) =>
          val msgSet = msgs.map(_.format).filter(_.nonEmpty).toSet
          if (msgSet == expectedMessages) {succeed}
          else {
            val missingMessages = expectedMessages.diff(msgSet)
            val unexpectedMessages = msgSet.diff(expectedMessages)

            val errMsg = new StringBuilder()
            errMsg.append(msgSet.mkString("Got these messages:\n\t", "\n\t", ""))
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
    val posDir = posFile
    if (!posDir.isDirectory) {
      fail(s"Path of pos test cases must exist and be a directory, not a file.")
    }
    val posDirChildren = posDir.listFiles()

    val (files, dirs) = posDirChildren.partition(_.isFile())

    files.foreach(file => runForFile(file, Set.empty))
    dirs.foreach { dir =>
      val dirContents = dir.listFiles()
      val checkFiles = dirContents.filter(dc => dc.isFile && dc.getName.endsWith(".check"))
      val riddlFiles = dirContents.filter(dc => dc.isFile && dc.getName.endsWith(".riddl"))

      if (riddlFiles.isEmpty) { fail(s"No riddl files in directory ${dir.getName}.") }
      else if (riddlFiles.length > 1) {
        fail(
          s"Multiple root-level riddl files in directory ${dir.getName}. Unsure which to validate"
        )
      } else {
        assert(riddlFiles.length == 1)
        val riddlFile = riddlFiles.head
        import scala.jdk.CollectionConverters.*
        val checkFileLines = checkFiles.iterator.flatMap { file =>
          java.nio.file.Files.readAllLines(file.toPath).iterator().asScala
        }.map(_.trim).filter(_.nonEmpty).toSet

        runForFile(riddlFile, checkFileLines)
      }
    }
  }

  "Neg" should {
    def runForFile(file: File, expectedMessages: Set[String]): Unit = {
      val relativeFileName = negPath.relativize(file.toPath).toString
      s"check $relativeFileName" in {
        validateFile(file.getName, file.getAbsolutePath) { (_, msgs) =>
          val errors = msgs.filter(_.kind.isError)
          if (errors.isEmpty) {
            val errMsg = new StringBuilder("No validation errors found.\n")

            if (expectedMessages.nonEmpty) {
              errMsg.append(
                expectedMessages
                  .mkString("Expected to find the following messages, but did not:\n\t", "\n\t", "")
              )
            }
            fail(errMsg.toString)
          } else {
            val msgSet = errors.iterator.map(_.format.trim).filter(_.nonEmpty).toSet

            if (expectedMessages.subsetOf(msgSet)) { succeed }
            else {
              val missingMsgs = expectedMessages &~ msgSet

              fail(
                missingMsgs
                  .mkString("Expected to find the following messages, but did not:\n\t", "\n\t", "")
              )
            }
          }
        }
      }
    }
    val negDir = negFile
    if (!negDir.isDirectory) {
      throw new RuntimeException(
        s"Path of neg test cases must exist and be a directory, not a file."
      )
    }
    val posDirChildren = negDir.listFiles()

    val (files, dirs) = posDirChildren.partition(_.isFile())

    files.foreach(file => runForFile(file, Set.empty))
    dirs.foreach { dir =>
      val dirContents = dir.listFiles()
      val checkFiles = dirContents.filter(dc => dc.isFile && dc.getName.endsWith(".check"))
      val riddlFiles = dirContents.filter(dc => dc.isFile && dc.getName.endsWith(".riddl"))

      if (riddlFiles.isEmpty) { fail(s"No riddl files in directory ${dir.getName}.") }
      else if (riddlFiles.length > 1) {
        fail(
          s"Multiple root-level riddl files in directory ${dir.getName}. Unsure which to validate"
        )
      } else {
        assert(riddlFiles.length == 1)
        val riddlFile = riddlFiles.head
        import scala.jdk.CollectionConverters.*
        val checkFileLines = checkFiles.iterator.flatMap { file =>
          java.nio.file.Files.readAllLines(file.toPath).iterator().asScala
        }.map(_.trim).filter(_.nonEmpty).toSet

        runForFile(riddlFile, checkFileLines)
      }
    }
  }
}
