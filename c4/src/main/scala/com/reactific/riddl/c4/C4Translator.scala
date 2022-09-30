package com.reactific.riddl.c4
import com.reactific.riddl.language.Messages.Messages
import com.reactific.riddl.language.CommonOptions
import com.reactific.riddl.language.Folding
import com.reactific.riddl.language.Translator
import com.reactific.riddl.language.Validation
import com.reactific.riddl.language.AST.*
import com.reactific.riddl.utils.Logger
import com.structurizr.model.Enterprise

import scala.annotation.unused
import scala.collection.mutable

object C4Translator extends Translator[C4Command.Options] {

  override def translate(
    result: Validation.Result,
    @unused log: Logger,
    commonOptions: CommonOptions,
    options: C4Command.Options
  ): Either[Messages, C4TranslatorState] = {
    translateImpl(result, commonOptions, options)
    }

  def translateImpl(
    result: Validation.Result,
    commonOptions: CommonOptions,
    options: C4Command.Options
  ): Either[Messages, C4TranslatorState] = {
    require(options.outputRoot.getNameCount > 2, "Output path is too shallow")
    require(
      options.outputRoot.getFileName.toString.nonEmpty,
      "Output path is empty"
    )
    val state = C4TranslatorState(result, options, commonOptions)
    val parentStack = mutable.Stack[Definition]()
    Right(
      Folding
        .foldLeftWithStack(state, parentStack)(result.root)(processingFolder)
    )
  }

  def processingFolder(
    state: C4TranslatorState,
    definition: Definition,
    parents: Seq[Definition]
  ): C4TranslatorState = {
    definition match {
      case _: RootContainer =>
        // This is the enterprise level
        val enterprise: Enterprise = new Enterprise(
          state.options.enterpriseName.getOrElse("Unknown Enterprise Name")
        )
        state.model.setEnterprise(enterprise)
        state
      case d: Domain =>
        state.model.addSoftwareSystem(
          d.id.value,
          d.brief.map(_.s).getOrElse("No description")
        )
        state
      case c: Context =>
        require(parents.nonEmpty)
        val domain = parents.head
        val ss = state.model.getSoftwareSystemWithName(domain.id.value)
        require(ss != null)
        ss.addContainer(
          c.id.value,
          c.brief.map(_.s).getOrElse("No description")
        )
        state
      case e: Entity =>
        require(parents.length >= 2)
        val domain = parents(1)
        val context = parents.head
        val ss = state.model.getSoftwareSystemWithName(domain.id.value)
        require(ss != null)
        val container = ss.getContainerWithName(context.id.value)
        container.addComponent(
          e.id.value,
          "Entity",
          e.brief.map(_.s).getOrElse("No description")
        )
        state
      case _: Handler    => state // context handlers only
      case _: Function   => state // functions in contexts only
      case _: Adaptor    => state // TBD
      case _: Processor  => state // TBD
      case _: Projection => state // TBD
      case _: Saga       => state // TBD
      case _: Story      => state // TBD
      case _: Plant      => state // TBD
      case _: Adaptation => state // TBD
      case _ => // ignore
        state
    }
  }
}
