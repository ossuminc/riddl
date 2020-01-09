package com.yoppworks.ossum.riddl.translator

import java.io.File
import java.nio.file.Path

import com.yoppworks.ossum.riddl.language.AST._
import com.yoppworks.ossum.riddl.language.AST
import com.yoppworks.ossum.riddl.language.Folding
import com.yoppworks.ossum.riddl.language.Riddl
import com.yoppworks.ossum.riddl.language.Translator
import com.yoppworks.ossum.riddl.language.Folding.Folding
import pureconfig.ConfigReader
import pureconfig.ConfigSource
import pureconfig.generic.auto._

import scala.collection.mutable

/** This is the RIDDL Prettifier to convert an AST back to RIDDL plain text */
class FormatTranslator extends Translator {

  type Lines = mutable.StringBuilder

  object Lines {

    def apply(s: String = ""): Lines = {
      val lines = new mutable.StringBuilder(s)
      lines.append(s)
      lines
    }
  }

  case class FormatConfig(
    showTimes: Boolean = false,
    showWarnings: Boolean = false,
    showMissingWarnings: Boolean = false,
    showStyleWarnings: Boolean = false,
    inputPath: Option[Path] = None,
    outputPath: Option[Path] = None
  ) extends Configuration

  val defaultConfig = FormatConfig()

  type CONF = FormatConfig

  def loadConfig(path: Path): ConfigReader.Result[FormatConfig] = {
    ConfigSource.file(path).load[FormatConfig]
  }

  def translate(
    root: RootContainer,
    outputRoot: Option[Path],
    log: Riddl.Logger,
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
    newState.lines.toString()
  }

  case class FormatState(
    config: FormatConfig,
    indentLevel: Int = 0,
    lines: Lines = Lines(),
    generatedFiles: Seq[File] = Seq.empty[File]
  ) extends Folding.State[FormatState] {
    def step(f: FormatState => FormatState): FormatState = f(this)

    private final val q = "\""
    private final val nl = "\n"

    def addFile(file: File): FormatState = {
      this.copy(generatedFiles = generatedFiles :+ file)
    }

    def add(str: String): FormatState = {
      lines.append(s"$str")
      this
    }

    def add(strings: Seq[LiteralString]): FormatState = {
      if (strings.length > 1) {
        lines.append("\n")
        strings.foreach(s => lines.append(s"""$spc"${s.s}"$nl"""))
      } else {
        strings.foreach(s => lines.append(s""" "${s.s}" """))
      }
      this
    }

    def add[T](opt: Option[T])(map: T => String): FormatState = {
      opt match {
        case None => this
        case Some(t) =>
          lines.append(map(t))
          this
      }
    }

    def addIndent(): FormatState = {
      lines.append(s"$spc")
      this
    }

    def addIndent(str: String): FormatState = {
      lines.append(s"$spc$str")
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
      this.outdent
        .add(s"$spc}")
        .visitDescription(definition.description)
        .add("\n")
    }

    def indent: FormatState = {
      this.copy(indentLevel = indentLevel + 2)
    }

    def outdent: FormatState = {
      require(indentLevel > 1, "unmatched indents")
      this.copy(indentLevel = indentLevel - 2)
    }
    def spc: String = { " ".repeat(indentLevel) }

    def visitDescription(description: Option[Description]): FormatState = {
      description.foldLeft(this) { (s, desc: Description) =>
        s.step { s: FormatState =>
            s.add(" described as {\n")
              .indent
              .addIndent("brief {")
              .indent
              .add(desc.brief)
              .outdent
              .add("}\n")
              .addLine(s"details {")
              .indent
          }
          .step { s: FormatState =>
            desc.details
              .foldLeft(s) {
                case (s, line) =>
                  s.add(s.spc + "|" + line.s + "\n")
              }
              .outdent
              .addLine(s"}\n${spc}items")
              .add[LiteralString](desc.nameOfItems)(
                ls => "(\"" + ls.s + "\") {"
              )
              .indent
          }
          .step { s: FormatState =>
            desc.items
              .foldLeft(s) {
                case (s, (id, desc)) =>
                  s.addLine(s"$id: " + q + desc + q)
              }
              .outdent
              .add(s"$spc}\n")
              .indent
          }
          .step { s: FormatState =>
            desc.citations
              .foldLeft(s) {
                case (s, cite) =>
                  s.add(s"${spc}see " + q + cite.s + q + nl)
              }
              .outdent
              .add(s"$spc}\n")
          }
      }
    }

    def visitTypeExpr(typEx: AST.TypeExpression): FormatState = {
      typEx match {
        case Strng(_, min, max) =>
          (min, max) match {
            case (Some(n), Some(x)) =>
              this.add(s"String($n,$x")
            case (None, Some(x)) =>
              this.add(s"String(,$x")
            case (Some(n), None) =>
              this.add(s"String($n)")
            case (None, None) =>
              this.add(s"String")
          }
        case URL(_, scheme) =>
          this.add(s"URL${scheme.map(s => "\"" + s.s + "\"").getOrElse("")}")
        case Bool(_)        => this.add("Boolean")
        case Number(_)      => this.add("Number")
        case Integer(_)     => this.add("Integer")
        case Decimal(_)     => this.add("Decimal")
        case Date(_)        => this.add("Date")
        case Time(_)        => this.add("Time")
        case DateTime(_)    => this.add("DateTime")
        case TimeStamp(_)   => this.add("TimeStamp")
        case LatLong(_)     => this.add("LatLong")
        case Nothing(_)     => this.add("Nothing")
        case TypeRef(_, id) => this.add(id.value.mkString("."))
        case AST.Enumeration(_, of, desc) =>
          this.add(s"any of {\n")
          of.foldLeft(this) { (s, e) =>
              s.add(e.id.value)
              e.value.map(visitTypeExpr).getOrElse(s)
            }
            .visitDescription(desc)
        case AST.Alternation(_, of, desc) =>
          val s = this.add(s"one of {\n").visitTypeExpr(of.head)
          of.tail
            .foldLeft(s) { (s, te) =>
              s.add(" or ").visitTypeExpr(te)
            }
            .visitDescription(desc)
        case AST.Aggregation(_, of, desc) =>
          if (of.isEmpty) {
            this.add("{}")
          } else if (of.size == 1) {
            val f: Field = of.head
            this
              .add(s"{ ${f.id.value}: ")
              .visitTypeExpr(f.typeEx)
              .add(" ")
              .visitDescription(f.description)
          } else {
            this.add("{\n")
            val result = of.foldLeft(this) {
              case (s, f) =>
                s.add(s"$spc  ${f.id.value}: ")
                  .visitTypeExpr(f.typeEx)
                  .add(" ")
                  .visitDescription(f.description)
                  .add(",")
            }
            result.lines.deleteCharAt(result.lines.length - 1)
            result.visitDescription(desc)
          }
        case AST.Mapping(_, from, to, desc) =>
          this
            .add(s"mapping from ")
            .visitTypeExpr(from)
            .add(" to ")
            .visitTypeExpr(to)
            .visitDescription(desc)
        case AST.RangeType(_, min, max, desc) =>
          this.add(s"range from $min to $max ").visitDescription(desc)
        case AST.ReferenceType(_, id, desc) =>
          this.add(s"reference to $id").visitDescription(desc)
        case Pattern(_, pat, desc) =>
          val line = if (pat.size == 1) {
            "Pattern(\"" +
              pat.head.s +
              "\"" + s") "
          } else {
            s"Pattern(\n" +
              pat.map(l => spc + "  \"" + l.s + "\"\n")
            s"\n) "
          }
          this.add(line).visitDescription(desc)
        case UniqueId(_, id, desc) =>
          this.add(s"Id(${id.value}) ").visitDescription(desc)

        case Optional(_, typex) =>
          this.visitTypeExpr(typex).add("?")

        case ZeroOrMore(_, typex) =>
          this.visitTypeExpr(typex).add("*")

        case OneOrMore(_, typex) =>
          this.visitTypeExpr(typex).add("+")

        case x: TypeExpression =>
          require(requirement = false, s"Unknown type $x")
          this
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
        .step { st =>
          entity.states.foldLeft(st) { (s, state) =>
            s.addLine(s"state ${state.id.value} is").visitTypeExpr(state.typeEx)
          }
        }
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
      state.open(s"feature ${feature.id.value} {")
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
    ): FormatState = {
      state.close(adaptor)
    }

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
    ): FormatState = {
      state.close(interaction)
    }

    override def doCommand(
      state: FormatState,
      container: Container,
      command: Command
    ): FormatState = {
      val keyword = if (command.events.size > 1) "events" else "event"
      state
        .addIndent(s"command ${command.id.value} is ")
        .visitTypeExpr(command.typ)
        .add(
          s" yields $keyword ${command.events.map(_.id.value.mkString(".")).mkString(", ")}"
        )
        .add("\n")
    }

    override def doEvent(
      state: FormatState,
      container: Container,
      event: Event
    ): FormatState = {
      state
        .addIndent(s"event ${event.id.value} is ")
        .visitTypeExpr(event.typ)
        .add("\n")
    }

    override def doQuery(
      state: FormatState,
      container: Container,
      query: Query
    ): FormatState = {
      state
        .addIndent(s"query ${query.id.value} is ")
        .visitTypeExpr(query.typ)
        .add(s" yields result ${query.result.id.value.mkString(".")}")
        .add("\n")
    }

    override def doResult(
      state: FormatState,
      container: Container,
      result: Result
    ): FormatState = {
      state
        .addIndent(s"result ${result.id.value} is ")
        .visitTypeExpr(result.typ)
        .add("\n")
    }

    override def doType(
      state: FormatState,
      container: Container,
      typeDef: Type
    ): FormatState = {
      state
        .addIndent()
        .add(s"type ${typeDef.id.value} is ")
        .visitTypeExpr(typeDef.typ)
        .visitDescription(typeDef.description)
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
