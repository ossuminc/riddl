package com.ossuminc.riddl.hugo.themes

import com.ossuminc.riddl.hugo.HugoPass
import com.ossuminc.riddl.hugo.diagrams.mermaid.UseCaseDiagramSupport
import com.ossuminc.riddl.language.AST.{Author, Definition, NamedValue, PathIdentifier, UseCase}
import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.passes.{PassInput, PassesOutput}

import scala.reflect.ClassTag

case class DotdockGenerator(
  options: HugoPass.Options,
  input: PassInput,
  outputs: PassesOutput,
  messages: Messages.Accumulator
) extends ThemeGenerator with UseCaseDiagramSupport {

  def makeDocLink(definition: NamedValue, parents: Seq[String]): String = "" // TODO: implement makeDocLink

  def makeSourceLink(definition: Definition): String = "" // TODO: implement makeSourceLink

  def makeDocAndParentsLinks(definition: NamedValue): String = "" // TODO: implement makeDocAndParentsLink
  
  def makeTomlFile(options: HugoPass.Options, author: Option[Author]): String = "" // TODO: implement makeTomlFile
}
