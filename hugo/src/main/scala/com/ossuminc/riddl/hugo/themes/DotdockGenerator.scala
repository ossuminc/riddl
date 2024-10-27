/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.hugo.themes

import com.ossuminc.riddl.hugo.HugoPass
import com.ossuminc.riddl.language.AST.{Author, Definition, PathIdentifier, UseCase}
import com.ossuminc.riddl.passes.{PassInput, PassesOutput}
import com.ossuminc.riddl.diagrams.mermaid.UseCaseDiagramSupport
import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.utils.PlatformContext

import scala.reflect.ClassTag

case class DotdockGenerator(
  options: HugoPass.Options,
  input: PassInput,
  outputs: PassesOutput,
  messages: Messages.Accumulator
)(using PlatformContext) extends ThemeGenerator
    with UseCaseDiagramSupport {

  def makeDocLink(definition: Definition, parents: Seq[String]): String = "" // TODO: implement makeDocLink

  def makeSourceLink(definition: Definition): String = "" // TODO: implement makeSourceLink

  def makeDocAndParentsLinks(definition: Definition): String = "" // TODO: implement makeDocAndParentsLink

  def makeTomlFile(options: HugoPass.Options, author: Option[Author]): String = "" // TODO: implement makeTomlFile
}
