package com.yoppworks.ossum.riddl.language

import java.io.File

import com.yoppworks.ossum.riddl.language.AST.RootContainer
import fastparse.Parsed.Failure
import fastparse.Parsed.Success
import fastparse._

/** Top level parsing rules */
abstract class TopLevelParser extends DomainParser {

  def annotated_input(index: Int): String = {
    current.slice(0, index) + "^" + current.slice(index, current.length)
  }

  def expect[T](parser: P[_] => P[T]): Either[String, T] = {
    fastparse.parse(current, parser(_)) match {
      case Success(content, _) =>
        Right(content)
      case failure @ Failure(_, index, _) =>
        val marked_up = annotated_input(index)
        val trace = failure.trace()
        Left(s"""Parse of '$marked_up' failed at position $index"
                |${trace.longAggregateMsg}
                |""".stripMargin)
    }
  }

  def root: File = new File(System.getProperty("user.dir"))
}

case class RPIParser(rpi: RiddlParserInput, throwOnError: Boolean = false)
    extends TopLevelParser {
  stack.push(rpi)
}

case class FileParser(topFile: File, throwOnError: Boolean = false)
    extends TopLevelParser {
  stack.push(topFile)
  override def root: File = topFile.getParentFile
}

case class StringParser(content: String, throwOnError: Boolean = false)
    extends TopLevelParser {
  stack.push(RiddlParserInput(content))
}

object TopLevelParser {

  def parse(
    input: RiddlParserInput
  ): Either[String, RootContainer] = {
    val tlp = RPIParser(input)
    tlp.expect(tlp.fileRoot(_))
  }

  def parse(file: File): Either[String, RootContainer] = {
    val fp = FileParser(file)
    fp.expect(fp.fileRoot(_))
  }

  def parse(
    input: String
  ): Either[String, RootContainer] = {
    val tlp = StringParser(input)
    tlp.expect(tlp.fileRoot(_))
  }
}
