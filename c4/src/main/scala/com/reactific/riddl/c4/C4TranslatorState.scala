package com.reactific.riddl.c4
import com.reactific.riddl.language.Folding.PathResolutionState
import com.reactific.riddl.language.CommonOptions
import com.reactific.riddl.language.SymbolTable
import com.reactific.riddl.language.TranslatingState
import com.reactific.riddl.language.TranslationResult
import com.reactific.riddl.language.Validation
import com.reactific.riddl.language.AST.*
import com.reactific.riddl.utils.TextFileWriter
import com.structurizr.Workspace
import com.structurizr.model.Model
import com.structurizr.model.StaticStructureElement
import com.structurizr.view.View
import com.structurizr.view.ViewSet

import scala.collection.mutable

case class C4TranslatorState(
  result: Validation.Result,
  options: C4Command.Options = C4Command.Options(),
  commonOptions: CommonOptions = CommonOptions())
    extends TranslatingState[TextFileWriter]
    with PathResolutionState[C4TranslatorState]
    with TranslationResult {

  val symbolTable: SymbolTable = result.symTab

  val workspace = new Workspace(
    options.projectName.getOrElse("Unknown Project"),
    options.projectDescription.getOrElse("Unknown Project Description")
  )

  val model: Model = workspace.getModel

  val views: ViewSet = workspace.getViews

  val elementByDef: mutable.Map[Definition, StaticStructureElement] = mutable
    .Map.empty[Definition, StaticStructureElement]

  val viewByDef: mutable.Map[Definition, View] = mutable.Map
    .empty[Definition, View]
}
