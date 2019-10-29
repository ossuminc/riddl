package com.yoppworks.ossum.riddl.language

import java.io.File

import com.yoppworks.ossum.riddl.language.AST.RootContainer
import fastparse._
import ScalaWhitespace._

/** Top level parsing rules */
abstract class TopLevelParser extends DomainParser {

  def fileRoot[_: P]: P[RootContainer] = {
    P(Start ~ P(domainDef).rep(0) ~ End).map(RootContainer(_))
  }

}

case class RPIParser(rpi: RiddlParserInput) extends TopLevelParser {
  stack.push(rpi)
}

case class FileParser(topFile: File) extends TopLevelParser {
  stack.push(topFile)
  override def root: File = topFile.getParentFile
}

case class StringParser(content: String) extends TopLevelParser {
  stack.push(RiddlParserInput(content))
}

object TopLevelParser {

  def parse(
    input: RiddlParserInput
  ): Either[Seq[ParserError], RootContainer] = {
    val tlp = RPIParser(input)
    tlp.expect(tlp.fileRoot(_))
  }

  def parse(file: File): Either[Seq[ParserError], RootContainer] = {
    val fp = FileParser(file)
    fp.expect(fp.fileRoot(_))
  }

  def parse(
    input: String
  ): Either[Seq[ParserError], RootContainer] = {
    val tlp = StringParser(input)
    tlp.expect(tlp.fileRoot(_))
  }
}
