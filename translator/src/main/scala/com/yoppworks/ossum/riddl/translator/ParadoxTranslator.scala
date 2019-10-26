package com.yoppworks.ossum.riddl.translator

import java.io.File

import com.yoppworks.ossum.riddl.language.AST._
import com.yoppworks.ossum.riddl.language.Folding
import com.yoppworks.ossum.riddl.language.RiddlParserInput
import com.yoppworks.ossum.riddl.language.TopLevelParser
import com.yoppworks.ossum.riddl.language.Validation
import com.yoppworks.ossum.riddl.language.Validation.ValidationMessage
import pureconfig._
import pureconfig.generic.auto._
import pureconfig.error.ConfigReaderFailure
import pureconfig.error.ConfigReaderFailures

/** A Translator that generates Paradox documentation */
object ParadoxTranslator extends Translator {

  case class Configuration()

  case class State(
    config: Configuration,
    generatedFiles: Seq[File] = Seq.empty[File]
  ) {

    def addFile(f: File): State = {
      this.copy(generatedFiles = this.generatedFiles :+ f)
    }
  }

  def parseValidateTranslate(
    input: RiddlParserInput,
    errorLog: (=> String) => Unit,
    configFile: File
  ): Seq[File] = {
    TopLevelParser.parse(input) match {
      case Left(error) =>
        errorLog(error)
        Seq.empty[File]
      case Right(root) =>
        val errors: Seq[ValidationMessage] =
          Validation.validate[RootContainer](root)
        if (errors.nonEmpty) {
          errors.map(_.format(input.origin)).foreach(errorLog(_))
          Seq.empty[File]
        } else {
          translate(root, configFile)
        }
    }
  }

  def translate(root: RootContainer, configFile: File): Seq[File] = {
    ConfigSource.default.load[Configuration] match {
      case Left(failures: ConfigReaderFailures) =>
        failures.toList.foreach { crf: ConfigReaderFailure =>
          val location = crf.location match {
            case Some(cvl) => cvl.description
            case None      => "unknown location"
          }
          System.err.println(s"In $location:")
          System.err.println(crf.description)
        }
        Seq.empty[File]
      case Right(configuration: Configuration) =>
        val state: State = State(configuration, Seq.empty[File])
        val finalState = Folding.foldLeft[State](root, root, state)(dispatch)
        finalState.generatedFiles
    }
  }

  def dispatch(
    container: Container,
    definition: Definition,
    state: State
  ): State = {
    definition match {
      case root: RootContainer =>
        doRoot(state, container, root)
      case domain: DomainDef =>
        doDomain(state, container, domain)
      case context: ContextDef =>
        doContext(state, container, context)
      case entity: EntityDef =>
        doEntity(state, container, entity)
      case command: CommandDef =>
        doCommand(state, container, command)
      case event: EventDef =>
        doEvent(state, container, event)
      case query: QueryDef =>
        doQuery(state, container, query)
      case rslt: ResultDef =>
        doResult(state, container, rslt)
      case feature: FeatureDef =>
        doFeature(state, container, feature)
      case adaptor: AdaptorDef =>
        doAdaptor(state, container, adaptor)
      case channel: ChannelDef =>
        doChannel(state, container, channel)
      case interaction: InteractionDef =>
        doInteraction(state, container, interaction)
      case typ: TypeDef =>
        doType(state, container, typ)
      case action: ActionDef =>
        doAction(state, container, action)
      case example: ExampleDef =>
        doExample(state, container, example)
      case function: FunctionDef =>
        doFunction(state, container, function)
      case invariant: InvariantDef =>
        doInvariant(state, container, invariant)
      case role: RoleDef =>
        doRole(state, container, role)
      case predef: PredefinedType =>
        doPredefinedType(state, container, predef)
      case rule: TranslationRule =>
        doTranslationRule(state, container, rule)
    }
  }

  def doRoot(state: State, container: Container, root: RootContainer): State = {
    state
  }

  def doDomain(state: State, container: Container, domain: DomainDef): State = {
    state
  }

  def doContext(
    state: State,
    container: Container,
    context: ContextDef
  ): State = { state }

  def doEntity(state: State, container: Container, entity: EntityDef): State = {
    state
  }

  def doCommand(
    state: State,
    container: Container,
    command: CommandDef
  ): State = { state }

  def doEvent(state: State, container: Container, event: EventDef): State = {
    state
  }

  def doQuery(state: State, container: Container, query: QueryDef): State = {
    state
  }

  def doResult(state: State, container: Container, rslt: ResultDef): State = {
    state
  }

  def doFeature(
    state: State,
    container: Container,
    feature: FeatureDef
  ): State = { state }

  def doAdaptor(
    state: State,
    container: Container,
    adaptor: AdaptorDef
  ): State = { state }

  def doChannel(
    state: State,
    container: Container,
    channel: ChannelDef
  ): State = { state }

  def doInteraction(
    state: State,
    container: Container,
    interaction: InteractionDef
  ): State = { state }

  def doType(state: State, container: Container, typ: TypeDef): State = {
    state
  }

  def doAction(state: State, container: Container, action: ActionDef): State = {
    state
  }

  def doExample(
    state: State,
    container: Container,
    example: ExampleDef
  ): State = { state }

  def doFunction(
    state: State,
    container: Container,
    function: FunctionDef
  ): State = { state }

  def doInvariant(
    state: State,
    container: Container,
    invariant: InvariantDef
  ): State = { state }

  def doRole(state: State, container: Container, role: RoleDef): State = {
    state
  }

  def doPredefinedType(
    state: State,
    container: Container,
    predef: PredefinedType
  ): State = { state }

  def doTranslationRule(
    state: State,
    container: Container,
    rule: TranslationRule
  ): State = { state }
}
