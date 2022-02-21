package com.yoppworks.ossum.riddl.language

import com.yoppworks.ossum.riddl.language.AST.*
import com.yoppworks.ossum.riddl.language.Folding.Folder
import com.yoppworks.ossum.riddl.language.Terminals.Keywords

import java.io.File
import java.nio.file.Path
import scala.annotation.unused
import scala.collection.mutable

case class ReformattingOptions(
  inputFile: Option[Path] = None,
  outputDir: Option[Path] = None,
  projectName: Option[String] = None,
  singleFile: Boolean = true)
    extends TranslatingOptions

/** This is the RIDDL Prettifier to convert an AST back to RIDDL plain text */
object ReformatTranslator extends Translator[ReformattingOptions] {

  def translateImpl(
    root: RootContainer,
    @unused
    log: Logger,
    commonOptions: CommonOptions,
    options: ReformattingOptions
  ): Seq[File] = {
    doTranslation(root, log, commonOptions, options).files
  }

  def doTranslation(
    root: RootContainer,
    @unused log: Logger,
    commonOptions: CommonOptions,
    options:ReformattingOptions
  ): ReformatState
  = {
    val state = ReformatState(commonOptions, options)
    val folder = new ReformatFolder
    Folding.foldAround(state, root, folder)
  }

  def translateToString(
    root: RootContainer,
    logger: Logger,
    commonOptions: CommonOptions,
    options: ReformattingOptions
  ): String = {
    val state = doTranslation(root, logger, commonOptions, options.copy
      (singleFile = false))
    state.toString
  }

  case class ReformatState(
    commonOptions: CommonOptions,
    options: ReformattingOptions
  ) extends Folding.State[ReformatState] {
    def step(f: ReformatState => ReformatState): ReformatState = f(this)

    private type Lines = mutable.StringBuilder

    private object Lines {

      def apply(s: String = ""): Lines = {
        val lines = new mutable.StringBuilder(s)
        lines.append(s)
        lines
      }
    }

    private final val nl = "\n"
    private var indentLevel: Int = 0
    private val lines: Lines = Lines()
    private var generatedFiles: Seq[File] = Seq.empty[File]
    private val fileStack: mutable.Stack[File] = mutable.Stack.empty[File]

    override def toString: String = lines.toString

    def files: Seq[File] = generatedFiles

    def addFile(file: File): ReformatState = {
      generatedFiles = generatedFiles :+ file
      this
    }

    def pushFile(file: File): ReformatState = {
      fileStack.push(file)
      this
    }
    def popFile(): ReformatState = {
      addFile(fileStack.pop())
    }

    def addNL(): ReformatState = { lines.append(nl); this }

    def add(str: String): ReformatState = {
      lines.append(s"$str")
      this
    }

    def add(strings: Seq[LiteralString]): ReformatState = {
      if (strings.sizeIs > 1) {
        lines.append("\n")
        strings.foreach(s => lines.append(s"""$spc"${s.s}"$nl"""))
      } else { strings.foreach(s => lines.append(s""" "${s.s}" """)) }
      this
    }

    def add[T](opt: Option[T])(map: T => String): ReformatState = {
      opt match {
        case None => this
        case Some(t) =>
          lines.append(map(t))
          this
      }
    }

    def addIndent(): ReformatState = {
      lines.append(s"$spc")
      this
    }

    def addIndent(str: String): ReformatState = {
      lines.append(s"$spc$str")
      this
    }

    def addLine(str: String): ReformatState = {
      lines.append(s"$spc$str\n")
      this
    }

    def openDef(
      definition: Definition,
      withBrace: Boolean = true
    ): ReformatState = {
      lines
        .append(s"$spc${AST.keyword(definition)} ${definition.id.format} is ")
      if (withBrace) {
        if (definition.isEmpty) { lines.append("{ ??? }") }
        else {
          lines.append("{\n")
          indent
        }
      }
      this
    }

    def closeDef(
      definition: Definition,
      withBrace: Boolean = true
    ): ReformatState = {
      if (withBrace && !definition.isEmpty) {
        outdent
        addIndent("}")
      }
      emitBrief(definition.brief)
      emitDescription(definition.description).add("\n")
    }

    def indent: ReformatState = { indentLevel = indentLevel + 2; this }

    def outdent: ReformatState = {
      require(indentLevel > 1, "unmatched indents")
      indentLevel = indentLevel - 2
      this
    }

    def spc: String = { " ".repeat(indentLevel) }

    def emitBrief(brief: Option[LiteralString]): ReformatState = {
      brief.foldLeft(this) { (s, ls: LiteralString) =>
        s.add(s" briefly ${ls.format}")
      }
    }

    def emitDescription(description: Option[Description]): ReformatState = {
      description.foldLeft(this) { (s, desc: Description) =>
        val s2 = s.add(" described as {\n").indent
        desc.lines.foldLeft(s2) { case (s3, line) =>
          s3.add(s3.spc + "|" + line.s + "\n")
        }.outdent.addLine("}")
      }
    }

    def emitString(s: Strng): ReformatState = {
      (s.min, s.max) match {
        case (Some(n), Some(x)) => this.add(s"String($n,$x")
        case (None, Some(x))    => this.add(s"String(,$x")
        case (Some(n), None)    => this.add(s"String($n)")
        case (None, None)       => this.add(s"String")
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

    def emitEnumeration(enumeration: AST.Enumeration): ReformatState = {
      val head = this.add(s"any of {\n").indent
      val enumerators: String = enumeration.enumerators.map { enumerator =>
        enumerator.id.value +
          enumerator.enumVal.fold("")("(" + _.format + ")") +
          mkEnumeratorDescription(enumerator.description)
      }.mkString(s"$spc", s",\n$spc", s"\n")
      head.add(enumerators).outdent.addLine("}")
    }

    def emitAlternation(alternation: AST.Alternation): ReformatState = {
      this.add(s"one of {\n").indent.step { s2 =>
        s2.addIndent("").emitTypeExpression(alternation.of.head).step { s3 =>
          alternation.of.tail.foldLeft(s3) { (s4, te) =>
            s4.add(" or ").emitTypeExpression(te)
          }.step { s5 => s5.add("\n").outdent.addIndent("}") }
        }
      }
    }

    def emitField(field: Field): ReformatState = {
      this.add(s"${field.id.value}: ").emitTypeExpression(field.typeEx)
        .emitDescription(field.description)
    }

    def emitFields(of: Seq[Field]): ReformatState = {
      if (of.isEmpty) { this.add("{ ??? }") }
      else if (of.sizeIs == 1) {
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

    def emitAggregation(aggregation: AST.Aggregation): ReformatState = {
      emitFields(aggregation.fields)
    }

    def emitMapping(mapping: AST.Mapping): ReformatState = {
      this.add(s"mapping from ").emitTypeExpression(mapping.from).add(" to ")
        .emitTypeExpression(mapping.to)
    }

    def emitPattern(pattern: AST.Pattern): ReformatState = {
      val line =
        if (pattern.pattern.sizeIs == 1) {
          "Pattern(\"" + pattern.pattern.head.s + "\"" + s") "
        } else {
          s"Pattern(\n" + pattern.pattern.map(l => spc + "  \"" + l.s + "\"\n")
          s"\n) "
        }
      this.add(line)
    }

    def emitMessageType(mt: AST.MessageType): ReformatState = {
      this.add(mt.messageKind.kind.toLowerCase).add(" ")
        .emitFields(mt.fields.tail)
    }

    def emitMessageRef(mr: AST.MessageRef): ReformatState = {
      this.add(mr.messageKind.kind).add(" ").add(mr.id.format)
    }

    def emitTypeExpression(typEx: AST.TypeExpression): ReformatState = {
      typEx match {
        case string: Strng  => emitString(string)
        case b: Bool        => this.add(b.kind)
        case n: Number      => this.add(n.kind)
        case i: Integer     => this.add(i.kind)
        case d: Decimal     => this.add(d.kind)
        case r: Real        => this.add(r.kind)
        case d: Date        => this.add(d.kind)
        case t: Time        => this.add(t.kind)
        case dt: DateTime   => this.add(dt.kind)
        case ts: TimeStamp  => this.add(ts.kind)
        case ll: LatLong    => this.add(ll.kind)
        case n: Nothing     => this.add(n.kind)
        case TypeRef(_, id) => this.add(id.format)
        case URL(_, scheme) => this
            .add(s"URL${scheme.fold("")(s => "\"" + s.s + "\"")}")
        case enumeration: Enumeration => emitEnumeration(enumeration)
        case alternation: Alternation => emitAlternation(alternation)
        case aggregation: Aggregation => emitAggregation(aggregation)
        case mapping: Mapping         => emitMapping(mapping)
        case RangeType(_, min, max)   => this.add(s"range(${min.n},${max.n}) ")
        case ReferenceType(_, er) => this
            .add(s"${Keywords.reference} to ${er.format}")
        case pattern: Pattern     => emitPattern(pattern)
        case mt: MessageType      => emitMessageType(mt)
        case UniqueId(_, id)      => this.add(s"Id(${id.format}) ")
        case Optional(_, typex)   => this.emitTypeExpression(typex).add("?")
        case ZeroOrMore(_, typex) => this.emitTypeExpression(typex).add("*")
        case OneOrMore(_, typex)  => this.emitTypeExpression(typex).add("+")
        case x: TypeExpression =>
          require(requirement = false, s"Unknown type $x")
          this
      }
    }

    def emitType(t: Type): ReformatState = {
      this.add(s"${spc}type ${t.id.value} is ").emitTypeExpression(t.typ)
        .emitDescription(t.description).add("\n")
    }

    def emitCondition(
      condition: Condition
    ): ReformatState = { this.add(condition.format) }

    def emitAction(
      action: Action
    ): ReformatState = { this.add(action.format) }

    def emitActions(actions: Seq[Action]): ReformatState = {
      actions.foldLeft(this)((s, a) => s.emitAction(a))
    }

    def emitGherkinStrings(strings: Seq[LiteralString]): ReformatState = {
      strings.size match {
        case 0 => add("\"\"")
        case 1 => add(strings.head.format)
        case _ =>
          indent.add("\n")
          strings.foreach { fact => addLine(fact.format) }
          outdent
      }
    }

    def emitAGherkinClause(ghc: GherkinClause): ReformatState = {
      ghc match {
        case GivenClause(_, strings)  => emitGherkinStrings(strings)
        case WhenClause(_, condition) => emitCondition(condition)
        case ThenClause(_, action)    => emitAction(action)
        case ButClause(_, action)     => emitAction(action)
      }
    }

    def emitGherkinClauses(
      kind: String,
      clauses: Seq[GherkinClause]
    ): ReformatState = {
      clauses.size match {
        case 0 => this
        case 1 => addIndent(kind).add(" ").emitAGherkinClause(clauses.head)
        case _ =>
          add("\n").addIndent(kind).add(" ").emitAGherkinClause(clauses.head)
          clauses.tail.foldLeft(this) { (next, clause) =>
            next.addNL().addIndent("and ").emitAGherkinClause(clause)
          }
      }
    }

    def emitExample(example: Example): ReformatState = {
      if (!example.isImplicit) { openDef(example) }
      emitGherkinClauses("given ", example.givens)
        .emitGherkinClauses("when", example.whens)
        .emitGherkinClauses("then", example.thens)
        .emitGherkinClauses("but", example.buts)
      if (!example.isImplicit) { closeDef(example) }
      this
    }

    def emitExamples(examples: Seq[Example]): ReformatState = {
      examples.foreach(emitExample)
      this
    }

    def emitUndefined(): ReformatState = { add(" ???") }

    def emitOptions(optionDef: OptionsDef[?]): ReformatState = {
      if (optionDef.options.nonEmpty) this.addLine(optionDef.format) else this
    }
  }

  class ReformatFolder extends Folder[ReformatState] {
    override def openContainer(
      state: ReformatState,
      container: ParentDefOf[Definition],
      parents: Seq[ParentDefOf[Definition]]
    ): ReformatState = {
      container match {
        case s: Story           => openStory(state, s)
        case domain: Domain     => openDomain(state, domain)
        case adaptor: Adaptor   => openAdaptor(state, adaptor)
        case typ: Type          => state.emitType(typ)
        case function: Function => openFunction(state, function)
        case st: State => state.openDef(st, withBrace = false)
            .emitFields(st.typeEx.fields)
        case oc: OnClause           => openOnClause(state, oc)
        case step: SagaStep         => openSagaStep(state, step)
        case include: Include       => openInclude(state, include)
        case adaptation: Adaptation => openAdaptation(state, adaptation)
        case _: RootContainer =>
          // ignore
          state
        case container: ParentDefOf[Definition] with OptionsDef[?] =>
          // Applies To: Context, Entity, Interaction
          state.openDef(container).emitOptions(container)
        case container: ParentDefOf[Definition] =>
          // Applies To: Saga, Plant, Handler, Processor
          state.openDef(container)
      }
    }

    override def doDefinition(
      state: ReformatState,
      definition: Definition,
      parents: Seq[ParentDefOf[Definition]]
    ): ReformatState = {
      definition match {
        case example: Example => state.emitExample(example)
        case invariant: Invariant => state.openDef(invariant)
            .closeDef(invariant, withBrace = false)
        case pipe: Pipe     => doPipe(state, pipe)
        case inlet: Inlet   => doInlet(state, inlet)
        case outlet: Outlet => doOutlet(state, outlet)
        case joint: Joint   => doJoint(state, joint)
        case _: Field => state // was handled by Type case in openContainer
        case _ =>
          require(
            !definition.isInstanceOf[ParentDefOf[Definition]],
            s"doDefinition should not be called for ${definition.getClass.getName}"
          )
          state
      }
    }

    override def closeContainer(
      state: ReformatState,
      container: ParentDefOf[Definition],
      parents: Seq[ParentDefOf[Definition]]
    ): ReformatState = {
      container match {
        case _: Type          => state // openContainer did all of it
        case story: Story     => closeStory(state, story)
        case st: State        => state.closeDef(st, withBrace = false)
        case _: OnClause      => closeOnClause(state)
        case include: Include => closeInclude(state, include)
        case adaptation: AdaptorDefinition => closeAdaptation(state, adaptation)
        case _: RootContainer              =>
          // ignore
          state
        case container: ParentDefOf[Definition] =>
          // Applies To: Domain, Context, Entity, Adaptor, Interaction, Saga,
          // Plant, Processor, Function, SagaStep
          state.closeDef(container)
      }
    }

    def openDomain(
      state: ReformatState,
      domain: Domain
    ): ReformatState = {
      state.openDef(domain).step { s1 =>
        if (domain.author.nonEmpty && domain.author.get.nonEmpty) {
          val author = domain.author.get
          s1.addIndent(s"author is {\n")
            .indent
            .addIndent(s"name = ${author.name.format}\n")
            .addIndent(s"email = ${author.email.format}\n")
            .step { s2 =>
              author.organization
                .map(org => s2.addIndent(s"organization =${org.format}\n"))
                .orElse(Option(s2)).get
            }.step { s3 =>
              author.title
                .map(title => s3.addIndent(s"title = ${title.format}\n"))
                .orElse(Option(s3)).get
            }.outdent.addIndent("}\n")
        } else {
          s1
        }
      }
    }

    def openStory(state: ReformatState, story: Story): ReformatState = {
      state.openDef(story).addIndent(Keywords.role).add(" is ")
        .add(story.role.format).addNL().addIndent(Keywords.capability)
        .add(" is ").add(story.capability.format).addNL()
        .addIndent(Keywords.benefit).add(" is ").add(story.benefit.format)
        .addNL().step { state =>
          if (story.examples.nonEmpty) {
            state.addIndent(Keywords.accepted).add(" by {").addNL().indent
          } else { state }
        }
    }

    def closeStory(state: ReformatState, story: Story): ReformatState = {
      (if (story.examples.nonEmpty) { state.outdent.addNL().addLine("}") }
       else { state }).closeDef(story)
    }

    def openAdaptor(
      state: ReformatState,
      adaptor: Adaptor
    ): ReformatState = {
      state.addIndent(AST.keyword(adaptor)).add(" ").add(adaptor.id.format)
        .add(" for ").add(adaptor.ref.format).add(" is {").step { s2 =>
          if (adaptor.isEmpty) { s2.emitUndefined().add(" }\n") }
          else { s2.add("\n").indent }
        }
    }

    def openAdaptation(
      state: ReformatState,
      adaptation: Adaptation
    ): ReformatState = {
      adaptation match {
        case ec8: EventCommandA8n => state
            .addIndent(s"adapt ${adaptation.id.format} is {\n").indent
            .addIndent("from ").emitMessageRef(ec8.messageRef).add(" to ")
            .emitMessageRef(ec8.command).add(" as {\n").indent

        case ea8: EventActionA8n => state
            .addIndent(s"adapt ${adaptation.id.format} is {\n").indent
            .addIndent("from ").emitMessageRef(ea8.messageRef).add(" to ")
            .emitActions(ea8.actions).add(" as {\n").indent
      }
    }

    def closeAdaptation(
      state: ReformatState,
      adaptation: AdaptorDefinition
    ): ReformatState = {
      state.outdent.addIndent("}\n").outdent.addIndent("}\n")
        .emitBrief(adaptation.brief).emitDescription(adaptation.description)
    }

    def openOnClause(state: ReformatState, onClause: OnClause): ReformatState = {
      state.addIndent("on ").emitMessageRef(onClause.msg).add(" {\n").indent
    }

    def closeOnClause(state: ReformatState): ReformatState = {
      state.outdent.addIndent("}\n")
    }

    def doPipe(state: ReformatState, pipe: Pipe): ReformatState = {
      state.openDef(pipe).step { state =>
        pipe.transmitType match {
          case Some(typ) => state.addIndent("transmit ").emitTypeExpression(typ)
          case None      => state.add(state.spc).emitUndefined()
        }
      }.closeDef(pipe)
    }

    def doJoint(state: ReformatState, joint: Joint): ReformatState = {
      val s = state.addIndent(s"${AST.keyword(joint)} ${joint.id.format} is ")
      joint match {
        case InletJoint(_, _, inletRef, pipeRef, _, _) => s
            .addIndent(s"inlet ${inletRef.id.format} from")
            .add(s" pipe ${pipeRef.id.format}\n")
        case OutletJoint(_, _, outletRef, pipeRef, _, _) => s
            .addIndent(s"outlet ${outletRef.id.format} to")
            .add(s" pipe ${pipeRef.id.format}\n")
      }
    }

    def doInlet(state: ReformatState, inlet: Inlet): ReformatState = {
      state.addLine(s"inlet ${inlet.id.format} is ${inlet.type_.format}")
    }

    def doOutlet(state: ReformatState, outlet: Outlet): ReformatState = {
      state.addLine(s"outlet ${outlet.id.format} is ${outlet.type_.format}")
    }

    def openFunction[TCD <: ParentDefOf[Definition]](
      state: ReformatState,
      function: Function
    ): ReformatState = {
      state.openDef(function).step { s =>
        function.input.fold(s)(te =>
          s.addIndent("requires ").emitTypeExpression(te).addNL()
        )
      }.step { s =>
        function.output
          .fold(s)(te => s.addIndent("yields ").emitTypeExpression(te).addNL())
      }
    }

    def openSagaStep(
      state: ReformatTranslator.ReformatState,
      step: AST.SagaStep
    ): ReformatState = {
      state.openDef(step).emitAction(step.doAction).add("reverted by")
        .emitAction(step.undoAction)
    }

    def openInclude(
      state: ReformatState,
      @unused
      include: Include
    ): ReformatState = {
      // TODO: Implement Include handling, switching I/O etc.
      state
    }

    def closeInclude(
      state: ReformatState,
      @unused
      include: Include
    ): ReformatState = {
      // TODO: Implement Include handling, switching I/O etc.
      state
    }
  }
}
