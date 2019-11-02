package com.yoppworks.ossum.riddl.translator

import java.io.File

import com.yoppworks.ossum.riddl.language.AST._
import com.yoppworks.ossum.riddl.language.AST
import com.yoppworks.ossum.riddl.language.Folding
import com.yoppworks.ossum.riddl.language.Folding.Folding
import pureconfig.ConfigSource
import pureconfig.generic.auto._
import scala.collection.mutable

/** This is the RIDDL Prettifier to convert an AST back to RIDDL plain text */
object FormatTranslator extends Translator {

  type Lines = mutable.StringBuilder

  object Lines {

    def apply(s: String = ""): Lines = {
      val lines = new mutable.StringBuilder(s)
      lines.append(s)
      lines
    }
  }

  case class FormatConfig(outputFile: Option[File] = None) extends Configuration

  def translate(root: RootContainer, confFile: File): Seq[File] = {
    val readResult = ConfigSource.file(confFile).load[FormatConfig]
    handleConfigLoad[FormatConfig](readResult) match {
      case Some(c) => translate(root, c)
      case None    => Seq.empty[File]
    }
  }

  def translate(
    root: RootContainer,
    configuration: FormatConfig
  ): Seq[File] = {
    val state = FormatState(configuration)
    val folding = new FormatFolding()
    folding.foldLeft(root, root, state).generatedFiles
  }

  def translateToString(
    root: RootContainer
  ): String = {
    val state = FormatState(FormatConfig())
    val folding = new FormatFolding()
    val newState = folding.foldLeft(root, root, state)
    newState.lines.mkString
  }

  case class FormatState(
    config: FormatConfig,
    indentLevel: Int = 0,
    lines: Lines = Lines(),
    generatedFiles: Seq[File] = Seq.empty[File]
  ) extends Folding.State[FormatState] {
    def step(f: FormatState => FormatState): FormatState = f(this)

    def addFile(file: File): FormatState = {
      this.copy(generatedFiles = generatedFiles :+ file)
    }

    def add(str: String): FormatState = {
      lines.append(s"$str")
      this
    }

    def addIndent(): FormatState = {
      lines.append(s"$spc")
      this
    }

    def addLine(str: String): FormatState = {
      lines.append(s"$spc$str\n")
      this
    }

    def open(str: String): FormatState = {
      lines.append(s"$spc$str\n")
      this.indent
    }

    def close(definition: Definition): FormatState = {
      this.outdent.add(s"$spc}").visitAddendum(definition.addendum).add("\n")
    }

    def indent: FormatState = {
      this.copy(indentLevel = indentLevel + 2)
    }

    def outdent: FormatState = {
      require(indentLevel > 1, "unmatched indents")
      this.copy(indentLevel = indentLevel - 2)
    }
    def spc: String = { " ".repeat(indentLevel) }

    def visitAddendum(addendum: Option[Addendum]): FormatState = {
      addendum.foldLeft(this) {
        case (s, a) =>
          val s2 = a.explanation.foldLeft(s) {
            case (s, e) =>
              s.visitExplanation(e)
          }
          a.seeAlso.foldLeft(s2) {
            case (s, sa) =>
              s.visitSeeAlso(sa)
          }
      }
    }

    def visitExplanation(
      exp: Explanation
    ): FormatState = {
      val result = exp.markdown.foldLeft(
        this.add(" explained as {\n")
      ) {
        case (s, line) =>
          s.add(s.spc + "  |" + line.s + "\n")
      }
      result.add(result.spc + "}")
    }

    def visitSeeAlso(
      sa: SeeAlso
    ): FormatState = {
      val result = sa.citations.foldLeft(
        this.add(s" see also {\n")
      ) {
        case (s, line) =>
          s.add(s.spc + "  |" + line.s + "\n")
      }
      result.add(result.spc + "}")
    }

    def visitTypeExpr(typEx: AST.TypeExpression): String = {
      typEx match {
        case Strng(_)       => "String"
        case Bool(_)        => "Boolean"
        case Number(_)      => "Number"
        case Integer(_)     => "Integer"
        case Decimal(_)     => "Decimal"
        case Date(_)        => "Date"
        case Time(_)        => "Time"
        case DateTime(_)    => "DateTime"
        case TimeStamp(_)   => "TimeStamp"
        case URL(_)         => "URL"
        case LatLong(_)     => "LatLong"
        case TypeRef(_, id) => id.value
        case AST.Enumeration(_, of, add) =>
          def doTypex(t: Option[TypeExpression]): String = {
            t match {
              case None        => ""
              case Some(typex) => visitTypeExpr(typex)
            }
          }
          s"any { ${of.map(e => e.id.value + doTypex(e.value)).mkString(" ")} } ${visitAddendum(add)}"
        case AST.Alternation(_, of, add) =>
          s"one { ${of.map(visitTypeExpr).mkString(" or ")} } ${visitAddendum(add)}"
        case AST.Aggregation(_, of, add) =>
          s" {\n${of
            .map {
              case (k: Identifier, v: TypeExpression) =>
                s"$spc  ${k.value} is ${visitTypeExpr(v)}"
            }
            .mkString(s",\n")}\n$spc} ${visitAddendum(add)}"
        case AST.Mapping(_, from, to, add) =>
          s"mapping from ${visitTypeExpr(from)} to ${visitTypeExpr(to)} ${visitAddendum(add)}"
        case AST.RangeType(_, min, max, add) =>
          s"range from $min to $max ${visitAddendum(add)}"
        case AST.ReferenceType(_, id, add) =>
          s"reference to $id ${visitAddendum(add)}"
        case Pattern(_, pat, add) =>
          if (pat.size == 1) {
            "Pattern(\"" +
              pat.head.s +
              "\"" + s") ${visitAddendum(add)}"
          } else {
            s"Pattern(\n" +
              pat.map(l => spc + "  \"" + l.s + "\"\n")
            s"\n) ${visitAddendum(add)}"
          }
        case UniqueId(_, id, add) =>
          s"Id(${id.value}) ${visitAddendum(add)}"

        case Optional(_, typex)   => visitTypeExpr(typex) + "?"
        case ZeroOrMore(_, typex) => visitTypeExpr(typex) + "*"
        case OneOrMore(_, typex)  => visitTypeExpr(typex) + "+"

        case x: TypeExpression =>
          require(requirement = false, s"Unknown type $x")
          ""
      }
    }

  }

  class FormatFolding extends Folding[FormatState] {

    override def openDomain(
      state: FormatState,
      container: Container,
      domain: Domain
    ): FormatState = {
      state.open(s"domain ${domain.id.value} {")
    }

    override def closeDomain(
      state: FormatState,
      container: Container,
      domain: Domain
    ): FormatState = {
      state.close(domain)
    }

    override def openContext(
      state: FormatState,
      container: Container,
      context: Context
    ): FormatState = {
      state.open(s"context ${context.id.value} {")
    }

    override def closeContext(
      state: FormatState,
      container: Container,
      context: Context
    ): FormatState = {
      state.close(context)
    }

    override def openEntity(
      state: FormatState,
      container: Container,
      entity: Entity
    ): FormatState = {
      state
        .open(s"entity ${entity.id.value} {")
        .addLine(s"state is ")
        .add(state.visitTypeExpr(entity.state))
        .step { st =>
          entity.options.size match {
            case 1 =>
              st.addLine(
                s"option is ${entity.options.head.id.value}"
              )
            case x: Int if x > 1 =>
              st.addLine(s"options {")
                .addLine(entity.options.map(_.id.value).mkString(" "))
                .addLine(" }")
            case _ =>
              st
          }
        }
        .step { st =>
          entity.consumers.foldLeft(st) {
            case (s, consumer) =>
              s.addLine(s"consumes topic ${consumer.id.value}")
          }
        }
    }

    override def closeEntity(
      state: FormatState,
      container: Container,
      entity: Entity
    ): FormatState = {
      state.close(entity)
    }

    override def openFeature(
      state: FormatState,
      container: Container,
      feature: Feature
    ): FormatState = {
      state
        .open(s"feature ${feature.id.value} {")
        .addLine(s"description {\n")
        .step { st =>
          feature.description.foldLeft(st) {
            case (s, line) =>
              s.add(s.spc + line + "\n")
          }
        }
        .addLine(s"\n")
    }

    override def closeFeature(
      state: FormatState,
      container: Container,
      feature: Feature
    ): FormatState = {
      state.close(feature)
    }

    override def openAdaptor(
      state: FormatState,
      container: Container,
      adaptor: Adaptor
    ): FormatState = {
      state.open(s"adaptor ${adaptor.id.value} {")
    }

    override def closeAdaptor(
      state: FormatState,
      container: Container,
      adaptor: Adaptor
    ): FormatState = { state.close(adaptor) }

    override def openTopic(
      state: FormatState,
      container: Container,
      topic: Topic
    ): FormatState = {
      state.open(s"topic ${topic.id.value} is {")
    }

    override def closeTopic(
      state: FormatState,
      container: Container,
      topic: Topic
    ): FormatState = {
      state.close(topic)
    }

    override def openInteraction(
      state: FormatState,
      container: Container,
      interaction: Interaction
    ): FormatState = {
      state.open(s"interaction ${interaction.id.value} {")

    }

    override def closeInteraction(
      state: FormatState,
      container: Container,
      interaction: Interaction
    ): FormatState = { state.close(interaction) }

    override def doCommand(
      st: FormatState,
      container: Container,
      command: Command
    ): FormatState = {
      val keyword = if (command.events.size > 1) "events" else "event"
      st.addIndent()
        .add(
          s"command ${command.id.value} is ${st.visitTypeExpr(command.typ)}"
        )
        .add(
          s" yields $keyword ${command.events.map(_.id.value).mkString(", ")}"
        )
        .add("\n")
    }

    override def doEvent(
      state: FormatState,
      container: Container,
      event: Event
    ): FormatState = {
      state.addLine(
        s"event ${event.id.value} is ${state.visitTypeExpr(event.typ)}"
      )
    }

    override def doQuery(
      state: FormatState,
      container: Container,
      query: Query
    ): FormatState = {
      state
        .add(
          s"${state.spc}  query ${query.id.value} is ${state.visitTypeExpr(query.typ)}"
        )
        .add(
          s" yields result ${query.result.id.value}"
        )
    }

    override def doResult(
      state: FormatState,
      container: Container,
      result: Result
    ): FormatState = {
      state.add(
        s"result ${result.id.value} is ${state.visitTypeExpr(result.typ)}"
      )
    }

    override def doType(
      state: FormatState,
      container: Container,
      typeDef: Type
    ): FormatState = {
      state
        .addIndent()
        .add(s"type ${typeDef.id.value} is ")
        .add(state.visitTypeExpr(typeDef.typ))
        .visitAddendum(typeDef.addendum)
        .add("\n")
    }

    override def doAction(
      state: FormatState,
      container: Container,
      action: ActionDefinition
    ): FormatState = {
      action match {
        case m: MessageAction =>
          // TODO: fix this
          state.open(s"action ${action.id.value} is {")
          state.close(m)
        case d: DirectiveAction =>
          // TODO: fix this
          state.open(s"action ${action.id.value} is {")
          state.close(d)
      }
    }

    override def doExample(
      state: FormatState,
      container: Container,
      example: Example
    ): FormatState = { state }

    override def doFunction(
      state: FormatState,
      container: Container,
      function: Function
    ): FormatState = { state }

    override def doInvariant(
      state: FormatState,
      container: Container,
      invariant: Invariant
    ): FormatState = { state }

    override def doRole(
      state: FormatState,
      container: Container,
      role: Role
    ): FormatState = {
      state
    }

    override def doPredefinedType(
      state: FormatState,
      container: Container,
      predef: PredefinedType
    ): FormatState = { state }

    override def doTranslationRule(
      state: FormatState,
      container: Container,
      rule: TranslationRule
    ): FormatState = { state }
  }

}
