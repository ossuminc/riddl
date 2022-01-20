package com.yoppworks.ossum.riddl.language

import com.yoppworks.ossum.riddl.language.AST.*
import com.yoppworks.ossum.riddl.language.Folding.Folding
import pureconfig.{ConfigReader, ConfigSource}
import pureconfig.generic.auto.*

import java.io.File
import java.nio.file.Path
import scala.collection.mutable

case class FormatConfig(
  showTimes: Boolean = false,
  showWarnings: Boolean = false,
  showMissingWarnings: Boolean = false,
  showStyleWarnings: Boolean = false,
  inputPath: Option[Path] = None,
  outputPath: Option[Path] = None)
    extends TranslatorConfiguration

/** This is the RIDDL Prettifier to convert an AST back to RIDDL plain text */
class FormatTranslator extends Translator[FormatConfig] {

  private type Lines = mutable.StringBuilder

  private object Lines {

    def apply(s: String = ""): Lines = {
      val lines = new mutable.StringBuilder(s)
      lines.append(s)
      lines
    }
  }

  val defaultConfig: FormatConfig = FormatConfig()

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
    folding.foldLeft(root, root, state).files
  }

  def translateToString(
    root: RootContainer
  ): String = {
    val state = FormatState(FormatConfig())
    val folding = new FormatFolding()
    val newState = folding.foldLeft(root, root, state)
    newState.toString()
  }

  case class FormatState(config: FormatConfig) extends Folding.State[FormatState] {
    def step(f: FormatState => FormatState): FormatState = f(this)

    private final val nl = "\n"
    private var indentLevel: Int = 0
    private val lines: Lines = Lines()
    private var generatedFiles: Seq[File] = Seq.empty[File]

    override def toString: String = lines.toString

    def files: Seq[File] = generatedFiles

    def addFile(file: File): FormatState = {
      generatedFiles = generatedFiles :+ file
      this
    }

    def addNL: FormatState = { lines.append(nl); this }

    def add(str: String): FormatState = {
      lines.append(s"$str")
      this
    }

    def add(strings: Seq[LiteralString]): FormatState = {
      if (strings.length > 1) {
        lines.append("\n")
        strings.foreach(s => lines.append(s"""$spc"${s.s}"$nl"""))
      } else { strings.foreach(s => lines.append(s""" "${s.s}" """)) }
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

    def openDef(definition: Definition, withBrace: Boolean = true): FormatState = {
      lines.append(s"$spc${AST.keyword(definition)} ${definition.id.format} is ")
      if (withBrace) lines.append("{\n")
      indent
    }

    def closeDef(definition: Definition, withBrace: Boolean = true): FormatState = {
      outdent
      if (withBrace) { addIndent("} ") }
      emitDescription(definition.description).add("\n")
    }

    def indent: FormatState = { indentLevel = indentLevel + 2; this }

    def outdent: FormatState = {
      require(indentLevel > 1, "unmatched indents")
      indentLevel = indentLevel - 2
      this
    }

    def spc: String = { " ".repeat(indentLevel) }

    def emitDescription(description: Option[Description]): FormatState = {
      description.foldLeft(this) { (s, desc: Description) =>
        val s2 = s.add(" described as {\n").indent
        desc.lines.foldLeft(s2) { case (s, line) => s.add(s.spc + "|" + line.s + "\n") }.outdent
          .addLine("}")
      }
    }

    def emitString(s: Strng): FormatState = {
      (s.min, s.max) match {
        case (Some(n), Some(x)) => this.add(s"String($n,$x")
        case (None, Some(x)) => this.add(s"String(,$x")
        case (Some(n), None) => this.add(s"String($n)")
        case (None, None) => this.add(s"String")
      }
    }

    def mkEnumeratorDescription(description: Option[Description]): String = {
      description match {
        case Some(desc) => " described as { " + {
          desc.lines.map(_.format).mkString("", s"\n$spc", " }\n")
        }
        case None => ""
      }
    }

    def emitEnumeration(enumeration: AST.Enumeration): FormatState = {
      val head = this.add(s"any of {\n").indent
      val enumerators: String = enumeration.enumerators.map { enumerator =>
        enumerator.id.value + enumerator.enumVal.map("(" + _.format + ")").getOrElse("") +
          mkEnumeratorDescription(enumerator.description)
      }.mkString(s"$spc", s",\n$spc", s"\n")
      head.add(enumerators).outdent.addLine("}")
    }

    def emitAlternation(alternation: AST.Alternation): FormatState = {
      val s = this.add(s"one of {\n").indent.addIndent().emitTypeExpression(alternation.of.head)
      alternation.of.tail.foldLeft(s) { (s, te) => s.add(" or ").emitTypeExpression(te) }.add("\n")
        .outdent.addLine("} ")
    }

    def emitField(field: Field): FormatState = {
      this.add(s"${field.id.value}: ").emitTypeExpression(field.typeEx)
        .emitDescription(field.description)
    }

    def emitFields(of: Seq[Field]): FormatState = {
      if (of.isEmpty) { this.add("{}") }
      else if (of.size == 1) {
        val f: Field = of.head
        add(s"{ ").emitField(f).add(" }").emitDescription(f.description)
      } else {
        this.add("{\n").indent
        val result = of.foldLeft(this) { case (s, f) =>
          s.add(spc).emitField(f).emitDescription(f.description).add(",\n")
        }
        result.lines.deleteCharAt(result.lines.length - 2)
        result.outdent.add(s"$spc} ")
      }
    }

    def emitAggregation(aggregation: AST.Aggregation): FormatState = {
      emitFields(aggregation.fields).add("\n")
    }

    def emitMapping(mapping: AST.Mapping): FormatState = {
      this.add(s"mapping from ").emitTypeExpression(mapping.from).add(" to ")
        .emitTypeExpression(mapping.to)
    }

    def emitPattern(pattern: AST.Pattern): FormatState = {
      val line =
        if (pattern.pattern.size == 1) { "Pattern(\"" + pattern.pattern.head.s + "\"" + s") " }
        else {
          s"Pattern(\n" + pattern.pattern.map(l => spc + "  \"" + l.s + "\"\n")
          s"\n) "
        }
      this.add(line)
    }

    def emitMessageType(mt: AST.MessageType): FormatState = {
      this.add(mt.messageKind.kind.toLowerCase).add(" ").emitFields(mt.fields)
    }

    def emitMessageRef(mr: AST.MessageRef): FormatState = {
      this.add(mr.messageKind.kind).add(" ").add(mr.id.format)
    }

    def emitTypeExpression(typEx: AST.TypeExpression): FormatState = {
      typEx match {
        case string: Strng => emitString(string)
        case b: Bool => this.add(b.kind)
        case n: Number => this.add(n.kind)
        case i: Integer => this.add(i.kind)
        case d: Decimal => this.add(d.kind)
        case d: Date => this.add(d.kind)
        case t: Time => this.add(t.kind)
        case dt: DateTime => this.add(dt.kind)
        case ts: TimeStamp => this.add(ts.kind)
        case ll: LatLong => this.add(ll.kind)
        case n: Nothing => this.add(n.kind)
        case TypeRef(_, id) => this.add(id.format)
        case URL(_, scheme) => this.add(s"URL${scheme.map(s => "\"" + s.s + "\"").getOrElse("")}")
        case enumeration: Enumeration => emitEnumeration(enumeration)
        case alternation: Alternation => emitAlternation(alternation)
        case aggregation: Aggregation => emitAggregation(aggregation)
        case mapping: Mapping => emitMapping(mapping)
        case RangeType(_, min, max) => this.add(s"range(${min.n},${max.n}) ")
        case ReferenceType(_, er) => this.add(s"refer to ${er.format}")
        case pattern: Pattern => emitPattern(pattern)
        case mt: MessageType => emitMessageType(mt)
        case UniqueId(_, id) => this.add(s"Id(${id.format}) ")
        case Optional(_, typex) => this.emitTypeExpression(typex).add("?")
        case ZeroOrMore(_, typex) => this.emitTypeExpression(typex).add("*")
        case OneOrMore(_, typex) => this.emitTypeExpression(typex).add("+")
        case x: TypeExpression =>
          require(requirement = false, s"Unknown type $x")
          this
      }
    }

    def emitType(t: Type): FormatState = {
      this.add(s"${spc}type ${t.id.value} is ").emitTypeExpression(t.typ)
        .emitDescription(t.description).add("\n")
    }

    def emitGherkinStrings(strings: Seq[LiteralString]): FormatState = {
      strings.size match {
        case 0 => add("\"\"")
        case 1 => add(strings.head.format)
        case _ =>
          indent.add("\n")
          strings.foreach { fact => addLine(fact.format) }
          outdent
      }
    }

    def emitGherkinClause(kind: String, clauses: Seq[GherkinClause]): FormatState = {
      clauses.size match {
        case 0 =>
        case 1 => addIndent(kind).add(" ").emitGherkinStrings(clauses.head.fact)
        case _ =>
          addIndent(kind).add(" ").emitGherkinStrings(clauses.head.fact)
          clauses.tail.foldLeft(this) { (next, clause) =>
            next.addNL.addIndent("and ").emitGherkinStrings(clause.fact)
          }
      }
      this
    }

    def emitExample(example: Example): FormatState = {
      openDef(example).emitGherkinClause("given ", example.givens)
        .emitGherkinClause("when", example.whens).emitGherkinClause("then", example.thens)
        .emitGherkinClause("but", example.buts).closeDef(example)
    }

    def emitExamples(examples: Seq[Example]): FormatState = {
      examples.foreach(emitExample)
      this
    }

    def emitUndefined(): FormatState = { add(" ???") }

    def emitOptions(optionDef: OptionsDef[?]): FormatState = {
      if (optionDef.options.nonEmpty) this.addLine(optionDef.format) else this
    }
  }

  class FormatFolding extends Folding[FormatState] {

    override def openRootDomain(
      state: FormatState,
      container: RootContainer,
      domain: Domain
    ): FormatState = {state.openDef(domain)}

    override def closeRootDomain(
      state: FormatState,
      container: RootContainer,
      domain: Domain
    ): FormatState = {state.closeDef(domain)}

    override def openDomain(
      state: FormatState,
      container: Domain,
      domain: Domain
    ): FormatState = {state.openDef(domain)}

    override def closeDomain(
      state: FormatState,
      container: Domain,
      domain: Domain
    ): FormatState = { state.closeDef(domain) }

    override def openContext(
      state: FormatState,
      container: Domain,
      context: Context
    ): FormatState = { state.openDef(context).emitOptions(context) }

    override def closeContext(
      state: FormatState,
      container: Domain,
      context: Context
    ): FormatState = { state.closeDef(context) }

    override def openEntity(
      state: FormatState,
      container: Context,
      entity: Entity
    ): FormatState = { state.openDef(entity).emitOptions(entity) }

    override def closeEntity(
      state: FormatState,
      container: Context,
      entity: Entity
    ): FormatState = { state.closeDef(entity) }

    override def openFeature(
      state: FormatState,
      container: Context,
      feature: Feature
    ): FormatState = { state.openDef(feature) }

    override def closeFeature(
      state: FormatState,
      container: Context,
      feature: Feature
    ): FormatState = { state.closeDef(feature) }

    override def openAdaptor(
      state: FormatState,
      container: Context,
      adaptor: Adaptor
    ): FormatState = {
      val s = state.addIndent(AST.keyword(adaptor)).add(" ").add(adaptor.id.format).add(" for ")
        .add(adaptor.ref.format).add(" is {\n").indent
      if (adaptor.adaptations.isEmpty) { s.add(s.spc).emitUndefined() }
      else { s }
    }

    override def doAdaptation(
      state: FormatState,
      container: Adaptor,
      adaptation: Adaptation
    ): FormatState = adaptation match {
      case Adaptation(_, _, event, command, examples, description) => state
          .addIndent(s"adapt ${adaptation.id.format} is {\n").indent.addIndent("from ")
          .emitMessageRef(event).add(" to ").emitMessageRef(command).add(" as {\n").indent
          .emitExamples(examples).outdent.add("\n").addIndent("} ").emitDescription(description)
    }

    override def closeAdaptor(
      state: FormatState,
      container: Context,
      adaptor: Adaptor
    ): FormatState = { state.closeDef(adaptor) }

    override def openInteraction(
      state: FormatState,
      container: Container[Interaction],
      interaction: Interaction
    ): FormatState = { state.openDef(interaction).emitOptions(interaction) }

    override def closeInteraction(
      state: FormatState,
      container: Container[Interaction],
      interaction: Interaction
    ): FormatState = { state.closeDef(interaction) }

    override def doType(
      state: FormatState,
      container: Container[Definition],
      typeDef: Type
    ): FormatState = { state.emitType(typeDef) }

    override def doAction(
      state: FormatState,
      container: Interaction,
      action: ActionDefinition
    ): FormatState = {
      action match {
        case m: MessageAction =>
          // TODO: fix this
          state.openDef(m)
          state.closeDef(m)
      }
    }

    override def doFeatureExample(
      state: FormatState,
      feature: Feature,
      example: Example
    ): FormatState = {state.emitExample(example)}

    override def doFunctionExample(
      state: FormatState,
      function: Function,
      example: Example
    ): FormatState = {state.emitExample(example)}

    override def doProcessorExample(
      state: FormatState,
      processor: Processor,
      example: Example
    ): FormatState = {state.emitExample(example)}

    override def doInvariant(
      state: FormatState,
      container: Entity,
      invariant: Invariant
    ): FormatState = {state}

    override def openState(state: FormatState, container: Entity, s: State): FormatState = {
      state.openDef(s, withBrace = false).emitFields(s.typeEx.fields)
    }

    override def doStateField(
      state: FormatState,
      container: State,
      field: Field
    ): FormatState = {state}

    override def closeState(state: FormatState, container: Entity, s: State): FormatState = {
      state.closeDef(s, withBrace = false)
    }

    override def openSaga(
      state: FormatState,
      container: Context,
      saga: Saga
    ): FormatState = { state.openDef(saga) }

    override def doSagaAction(
      state: FormatState,
      container: Saga,
      action: SagaAction
    ): FormatState = { state }

    override def closeSaga(
      state: FormatState,
      container: Context,
      saga: Saga
    ): FormatState = { state.closeDef(saga) }

    override def doHandler(
      state: FormatState,
      container: Entity,
      handler: Handler
    ): FormatState = {
      val s = state.openDef(handler)
      handler.clauses.foldLeft(s) { (s, clause) =>
        val s2 = s.addIndent("on ").emitMessageRef(clause.msg).add(" {\n").indent
        clause.actions.foldLeft(s2) { (s, action) => s.addLine(action.format) }.outdent
          .addIndent("}\n")
      }.closeDef(handler)
    }

    override def openPlant(state: FormatState, container: Domain, plant: Plant): FormatState = {
      state.openDef(plant)
    }

    override def doPipe(state: FormatState, container: Plant, pipe: Pipe): FormatState = {
      state.openDef(pipe).step { state =>
        pipe.transmitType match {
          case Some(typ) => state.addIndent("transmit ").emitTypeExpression(typ)
          case None      => state.add(state.spc).emitUndefined()
        }
      }.closeDef(pipe)
    }

    override def doJoint(state: FormatState, container: Plant, joint: Joint): FormatState = {
      val s = state.addIndent(s"${AST.keyword(joint)} ${joint.id.format} is ")
      joint.streamletRef match {
        case InletRef(_, id) => s.addIndent(s"inlet ${id.format} from")
            .add(s" pipe ${joint.pipe.id.format}\n")
        case OutletRef(_, id) => s.addIndent(s"outlet ${id.format} to")
            .add(s" pipe ${joint.pipe.id.format}\n")
      }
    }

    override def openProcessor(
      state: FormatState,
      container: Plant,
      processor: Processor
    ): FormatState = state.openDef(processor)

    override def doInlet(state: FormatState, container: Processor, inlet: Inlet): FormatState = {
      state.addLine(s"inlet ${inlet.id.format} is ${inlet.type_.format}")
    }

    override def doOutlet(state: FormatState, container: Processor, outlet: Outlet): FormatState = {
      state.addLine(s"outlet ${outlet.id.format} is ${outlet.type_.format}")
    }

    override def closeProcessor(
      state: FormatState,
      container: Plant,
      processor: Processor
    ): FormatState = state.closeDef(processor)

    override def closePlant(
      state: FormatState,
      container: Domain,
      plant: Plant
    ): FormatState = { state.closeDef(plant) }

    override def openFunction(
      state: FormatState,
      container: Entity,
      function: Function
    ): FormatState = {
      state.openDef(function).step { s =>
        function.input.map(te => s.addIndent("requires ").emitTypeExpression(te).addNL).getOrElse(s)
      }.step { s =>
        function.output.map(te => s.addIndent("yields ").emitTypeExpression(te).addNL).getOrElse(s)
      }
    }

    override def closeFunction(
      state: FormatState,
      container: Entity,
      function: Function
    ): FormatState = { state.closeDef(function) }
  }

}
