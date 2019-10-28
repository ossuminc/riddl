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

    def addLine(str: String): FormatState = {
      this.copy(lines = lines.append(str))
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
        this.addLine(" explained as {\n")
      ) {
        case (s, line) =>
          s.addLine(s.spc + "  \"" + line + "\"\n")
      }
      result.addLine(result.spc + "}")
    }

    def visitSeeAlso(
      sa: SeeAlso
    ): FormatState = {
      val result = sa.citations.foldLeft(
        this.addLine(s" see also {\n")
      ) {
        case (s, line) =>
          s.addLine(s.spc + "  \"" + line + "\"\n")
      }
      result.addLine(result.spc + "}")
    }

    def visitTypeExpr(typEx: AST.TypeExpression): String = {
      typEx match {
        case Pattern(_, pat)      => "Pattern(\"" + pat + "\")"
        case UniqueId(_, id)      => s"Id($id)"
        case Strng(_)             => "String"
        case Bool(_)              => "Boolean"
        case Number(_)            => "Number"
        case Integer(_)           => "Integer"
        case Decimal(_)           => "Decimal"
        case Date(_)              => "Date"
        case Time(_)              => "Time"
        case DateTime(_)          => "DateTime"
        case TimeStamp(_)         => "TimeStamp"
        case TypeRef(_, id)       => "type " + id.value
        case Optional(_, typex)   => visitTypeExpr(typex) + "?"
        case ZeroOrMore(_, typex) => visitTypeExpr(typex) + "*"
        case OneOrMore(_, typex)  => visitTypeExpr(typex) + "+"
        case AST.Enumeration(_, of) =>
          s"any { ${of.map(_.value).mkString(" ")} }"
        case AST.Alternation(_, of) =>
          s"choose { ${of.map(visitTypeExpr).mkString(" or ")} }"
        case AST.Aggregation(_, of) =>
          s"combine {\n${of
            .map {
              case (k: Identifier, v: TypeExpression) =>
                s"$spc  ${k.value} is ${visitTypeExpr(v)}"
            }
            .mkString(s",\n")}\n$spc}"
        case AST.Mapping(_, from, to) =>
          s"mapping from ${visitTypeExpr(from)} to ${visitTypeExpr(to)}"
        case AST.RangeType(_, min, max) =>
          s"range from $min to $max"
        case AST.ReferenceType(_, id) =>
          s"reference to $id"
        case x: PredefinedType =>
          require(requirement = false, s"Unknown type $x"); ""
      }
    }

    def close(container: Container): FormatState = {
      close()
      visitAddendum(container.addendum)
      terminus()
    }

    def close(): FormatState = {
      this.addLine(s"$spc}")
    }

    protected def terminus(): FormatState = {
      this.addLine("\n")
    }
  }

  class FormatFolding extends Folding[FormatState] {

    override def openDomain(
      state: FormatState,
      container: Container,
      domain: DomainDef
    ): FormatState = {
      domain.subdomain
        .foldLeft(
          state.addLine(s"domain ${domain.id.value}")
        ) {
          case (s, subd) =>
            s.addLine(s" is subdomain of ${subd.value}")
        }
        .addLine(" {\n")
    }

    override def closeDomain(
      state: FormatState,
      container: Container,
      domain: DomainDef
    ): FormatState = { state.close(domain) }

    override def openContext(
      state: FormatState,
      container: Container,
      context: ContextDef
    ): FormatState = {
      state.addLine(s"${state.spc}context ${context.id.value} {\n")
    }

    override def closeContext(
      state: FormatState,
      container: Container,
      context: ContextDef
    ): FormatState = { state.close(context) }

    override def openEntity(
      state: FormatState,
      container: Container,
      entity: EntityDef
    ): FormatState = {
      state
        .addLine(
          s"${state.spc}entity ${entity.id.value} is ${state.visitTypeExpr(entity.typ)} {\n"
        )
        .step { st =>
          entity.options.size match {
            case 1 =>
              st.addLine(
                s"${st.spc}  option is ${entity.options.head.id.value}\n"
              )
            case x: Int if x > 1 =>
              st.addLine(s"${st.spc}  options { ")
                .addLine(entity.options.map(_.id.value).mkString(" "))
                .addLine(" }\n")
            case _ =>
              st
          }
        }
        .step { st =>
          entity.produces.foldLeft(st) {
            case (s, producer) =>
              s.addLine(s"${s.spc}  produces topic ${producer.id.value}\n")
          }
        }
        .step { st =>
          entity.consumes.foldLeft(st) {
            case (s, consumer) =>
              s.addLine(s"${s.spc}  consumes topic ${consumer.id.value}\n")
          }
        }
    }

    override def closeEntity(
      state: FormatState,
      container: Container,
      entity: EntityDef
    ): FormatState = {
      state.close(entity)
    }

    override def openFeature(
      state: FormatState,
      container: Container,
      feature: FeatureDef
    ): FormatState = {
      state
        .addLine(s"${state.spc}feature ${feature.id.value} {\n")
        .addLine(s"${state.spc}  description {\n")
        .step { st =>
          feature.description.foldLeft(st) {
            case (s, line) =>
              s.addLine(s.spc + line + "\n")
          }
        }
        .addLine(s"${state.spc}\n")
    }

    override def closeFeature(
      state: FormatState,
      container: Container,
      feature: FeatureDef
    ): FormatState = { state.close(feature) }

    override def openAdaptor(
      state: FormatState,
      container: Container,
      adaptor: AdaptorDef
    ): FormatState = {
      state.addLine(s"${state.spc}adaptor ${adaptor.id.value} {\n")
    }

    override def closeAdaptor(
      state: FormatState,
      container: Container,
      adaptor: AdaptorDef
    ): FormatState = { state.close(adaptor) }

    override def openTopic(
      state: FormatState,
      container: Container,
      topic: TopicDef
    ): FormatState = {
      state.addLine(s"${state.spc} topic ${topic.id.value} is {\n")
    }

    override def closeTopic(
      state: FormatState,
      container: Container,
      topic: TopicDef
    ): FormatState = { state.close(topic) }

    override def openInteraction(
      state: FormatState,
      container: Container,
      interaction: InteractionDef
    ): FormatState = {
      state.addLine(s"${state.spc}interaction ${interaction.id.value} {\n")

    }

    override def closeInteraction(
      state: FormatState,
      container: Container,
      interaction: InteractionDef
    ): FormatState = { state.close(interaction) }

    override def doCommand(
      st: FormatState,
      container: Container,
      command: CommandDef
    ): FormatState = {
      val keyword = if (command.events.size > 1) "events" else "event"
      st.addLine(
          s"${st.spc}  command ${command.id.value} is ${st.visitTypeExpr(command.typ)}"
        )
        .addLine(
          s" yields $keyword ${command.events.map(_.id.value).mkString(", ")}\n"
        )
        .addLine("}\n")
    }

    override def doEvent(
      state: FormatState,
      container: Container,
      event: EventDef
    ): FormatState = {
      state.addLine(
        s"${state.spc}  event ${event.id.value} is ${state.visitTypeExpr(event.typ)}\n"
      )
    }

    override def doQuery(
      state: FormatState,
      container: Container,
      query: QueryDef
    ): FormatState = {
      state
        .addLine(
          s"${state.spc}  query ${query.id.value} is ${state.visitTypeExpr(query.typ)}"
        )
        .addLine(
          s" yields result ${query.result.id.value}\n"
        )
    }

    override def doResult(
      state: FormatState,
      container: Container,
      result: ResultDef
    ): FormatState = {
      state.addLine(
        s"${state.spc}  result ${result.id.value} is ${state.visitTypeExpr(result.typ)}\n"
      )
    }

    override def doType(
      state: FormatState,
      container: Container,
      typeDef: TypeDef
    ): FormatState = {
      state
        .addLine(s"${state.spc}type ${typeDef.id.value} is ")
        .addLine(state.visitTypeExpr(typeDef.typ))
        .visitAddendum(typeDef.addendum)
    }

    override def doAction(
      state: FormatState,
      container: Container,
      action: ActionDef
    ): FormatState = {
      state
    }

    override def doExample(
      state: FormatState,
      container: Container,
      example: ExampleDef
    ): FormatState = { state }

    override def doFunction(
      state: FormatState,
      container: Container,
      function: FunctionDef
    ): FormatState = { state }

    override def doInvariant(
      state: FormatState,
      container: Container,
      invariant: InvariantDef
    ): FormatState = { state }

    override def doRole(
      state: FormatState,
      container: Container,
      role: RoleDef
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
