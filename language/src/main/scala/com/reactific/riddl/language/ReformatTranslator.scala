package com.reactific.riddl.language

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.Folding.Folder
import com.reactific.riddl.language.Terminals.Keywords

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import scala.annotation.unused
import scala.collection.mutable

case class ReformattingOptions(
  inputFile: Option[Path] = None,
  outputDir: Option[Path] = Some(Path.of(System.getProperty("java.io.tmpdir"))),
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
  ): Seq[Path] = {
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
    val state = doTranslation(
      root, logger, commonOptions, options.copy(singleFile = true)
    )
    state.filesAsString
  }

  case class FileEmitter(path: Path) {
    private val lines: mutable.StringBuilder = new mutable.StringBuilder
    private final val nl = "\n"
    private var indentLevel: Int = 0
    override def toString: String = lines.toString
    def asString: String = lines.toString()

    def spc: String = { " ".repeat(indentLevel) }

    def addNL(): FileEmitter = { lines.append(nl); this }

    def add(str: String): FileEmitter = {
      lines.append(str)
      this
    }

    def addSpace(): FileEmitter = add(spc)

    def add(strings: Seq[LiteralString]): FileEmitter = {
      if (strings.sizeIs > 1) {
        lines.append("\n")
        strings.foreach(s => lines.append(s"""$spc"${s.s}"$nl"""))
      } else { strings.foreach(s => lines.append(s""" "${s.s}" """)) }
      this
    }

    def add[T](opt: Option[T])(map: T => String): FileEmitter = {
      opt match {
        case None => this
        case Some(t) =>
          lines.append(map(t))
          this
      }
    }

    def addIndent(): FileEmitter = {
      lines.append(s"$spc")
      this
    }

    def addIndent(str: String): FileEmitter = {
      lines.append(s"$spc$str")
      this
    }

    def addLine(str: String): FileEmitter = {
      lines.append(s"$spc$str\n")
      this
    }
    def indent: FileEmitter = { indentLevel = indentLevel + 2; this }

    def outdent: FileEmitter = {
      require(indentLevel > 1, "unmatched indents")
      indentLevel = indentLevel - 2
      this
    }

    def openDef(
      definition: Definition,
      withBrace: Boolean = true
    ): FileEmitter = {
      addSpace()
        .add(s"${AST.keyword(definition)} ${definition.id.format} is ")
      if (withBrace) {
        if (definition.isEmpty) { add("{ ??? }") }
        else {
          add("{\n").indent
        }
      }
      this
    }

    def closeDef(
      definition: Definition,
      withBrace: Boolean = true
    ): FileEmitter = {
      if (withBrace && !definition.isEmpty) {
        outdent.addIndent("}")
      }
      emitBrief(definition.brief)
      emitDescription(definition.description).add("\n")
    }


    def emitBrief(brief: Option[LiteralString]): FileEmitter = {
      brief.foldLeft(this) { (s, ls: LiteralString) =>
        s.add(s" briefly ${ls.format}")
      }
    }

    def emitDescription(description: Option[Description]): FileEmitter = {
      description.foldLeft(this) { (s, desc: Description) =>
        val s2 = s.add(" described as {\n").indent
        desc.lines.foldLeft(s2) { case (s3, line) =>
          s3.add(s3.spc + "|" + line.s + "\n")
        }.outdent.addLine("}")
      }
    }

    def emitString(s: Strng): FileEmitter = {
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

    def emitEnumeration(enumeration: AST.Enumeration): FileEmitter = {
      val head = this.add(s"any of {\n").indent
      val enumerators: String = enumeration.enumerators.map { enumerator =>
        enumerator.id.value +
          enumerator.enumVal.fold("")("(" + _.format + ")") +
          mkEnumeratorDescription(enumerator.description)
      }.mkString(s"$spc", s",\n$spc", s"\n")
      head.add(enumerators).outdent.addLine("}")
    }

    def emitAlternation(alternation: AST.Alternation): FileEmitter = {
      add(s"one of {\n")
        .indent
        .addIndent("")
        .emitTypeExpression(alternation.of.head)
      val s5 = alternation.of.tail.foldLeft(this) { (s4, te) =>
        s4.add(" or ").emitTypeExpression(te)
      }
      s5.add("\n").outdent.addIndent("}")
    }

    def emitField(field: Field): FileEmitter = {
      this.add(s"${field.id.value}: ").emitTypeExpression(field.typeEx)
        .emitDescription(field.description)
    }

    def emitFields(of: Seq[Field]): FileEmitter = {
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

    def emitAggregation(aggregation: AST.Aggregation): FileEmitter = {
      emitFields(aggregation.fields)
    }

    def emitMapping(mapping: AST.Mapping): FileEmitter = {
      this.add(s"mapping from ").emitTypeExpression(mapping.from).add(" to ")
        .emitTypeExpression(mapping.to)
    }

    def emitPattern(pattern: AST.Pattern): FileEmitter = {
      val line =
        if (pattern.pattern.sizeIs == 1) {
          "Pattern(\"" + pattern.pattern.head.s + "\"" + s") "
        } else {
          s"Pattern(\n" + pattern.pattern.map(l => spc + "  \"" + l.s + "\"\n")
          s"\n) "
        }
      this.add(line)
    }

    def emitMessageType(mt: AST.MessageType): FileEmitter = {
      this.add(mt.messageKind.kind.toLowerCase).add(" ")
        .emitFields(mt.fields.tail)
    }

    def emitMessageRef(mr: AST.MessageRef): FileEmitter = {
      this.add(mr.messageKind.kind).add(" ").add(mr.id.format)
    }

    def emitTypeExpression(typEx: AST.TypeExpression): FileEmitter = {
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

    def emitType(t: Type): FileEmitter = {
      this.add(s"${spc}type ${t.id.value} is ").emitTypeExpression(t.typ)
        .emitDescription(t.description).add("\n")
    }

    def emitCondition(
      condition: Condition
    ): FileEmitter = { this.add(condition.format) }

    def emitAction(
      action: Action
    ): FileEmitter = { this.add(action.format) }

    def emitActions(actions: Seq[Action]): FileEmitter = {
      actions.foldLeft(this)((s, a) => s.emitAction(a))
    }

    def emitGherkinStrings(strings: Seq[LiteralString]): FileEmitter = {
      strings.size match {
        case 0 => add("\"\"")
        case 1 => add(strings.head.format)
        case _ =>
          indent.add("\n")
          strings.foreach { fact => addLine(fact.format) }
          outdent
      }
    }

    def emitAGherkinClause(ghc: GherkinClause): FileEmitter = {
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
    ): FileEmitter = {
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

    def emitExample(example: Example): FileEmitter = {
      if (!example.isImplicit) { openDef(example) }
      emitGherkinClauses("given ", example.givens)
        .emitGherkinClauses("when", example.whens)
        .emitGherkinClauses("then", example.thens)
        .emitGherkinClauses("but", example.buts)
      if (!example.isImplicit) { closeDef(example) }
      this
    }

    def emitExamples(examples: Seq[Example]): FileEmitter = {
      examples.foreach(emitExample)
      this
    }

    def emitUndefined(): FileEmitter = { add(" ???") }

    def emitOptions(optionDef: OptionsDef[?]): FileEmitter = {
      if (optionDef.options.nonEmpty) this.addLine(optionDef.format) else this
    }

    def emit(): Path = {
      Files.writeString(path, lines.toString(), StandardCharsets.UTF_8)
      path
    }

  }

  case class ReformatState(
    commonOptions: CommonOptions,
    options: ReformattingOptions
  ) extends Folding.State[ReformatState] {

    require(options.inputFile.nonEmpty, "No input file specified")
    require(options.outputDir.nonEmpty, "No output directory specified")

    private val inPath: Path = options.inputFile.get
    private val outPath: Path = options.outputDir.get

    def relativeToInPath(path: Path): Path = inPath.relativize(path)

    def outPathFor(path: Path): Path = {
      val suffixPath = if (path.isAbsolute) relativeToInPath(path) else path
      outPath.resolve(suffixPath)
    }

    def step(f: ReformatState => ReformatState): ReformatState = f(this)

    private var generatedFiles: Seq[FileEmitter] = Seq.empty[FileEmitter]

    def files: Seq[Path] = {
      closeStack()
      if (options.singleFile) {
        val content = filesAsString
        Files.writeString(firstFile.path, content, StandardCharsets.UTF_8)
        Seq(firstFile.path)
      } else {
        for { emitter <- generatedFiles } yield {
          emitter.emit()
        }
      }
    }

    def filesAsString: String = {
      closeStack()
      generatedFiles
        .map(fe => s"\n// From '${fe.path.toString}'\n${fe.asString}")
        .mkString
    }

    private val fileStack: mutable.Stack[FileEmitter] =
      mutable.Stack.empty[FileEmitter]

    private def closeStack(): Unit = {
      while (fileStack.nonEmpty) popFile()
    }

    def current: FileEmitter = fileStack.head

    private val firstFile: FileEmitter = {
      val file = FileEmitter(outPathFor(inPath))
      pushFile(file)
      file
    }

    def addFile(file: FileEmitter): ReformatState = {
      generatedFiles = generatedFiles :+ file
      this
    }

    def pushFile(file: FileEmitter): ReformatState = {
      fileStack.push(file)
      addFile(file)
    }

    def popFile(): ReformatState = {
      fileStack.pop() ; this
    }

    def withCurrent(f: FileEmitter => Unit): ReformatState = {
      f(current); this
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
        case typ: Type          => state.current.emitType(typ); state
        case function: Function => openFunction(state, function)
        case st: State =>
          state.withCurrent(_
            .openDef(st, withBrace = false)
            .emitFields(st.typeEx.fields)
          )
        case oc: OnClause           => openOnClause(state, oc)
        case step: SagaStep         => openSagaStep(state, step)
        case include: Include       => openInclude(state, include)
        case adaptation: Adaptation => openAdaptation(state, adaptation)
        case _: RootContainer =>
          // ignore
          state
        case container: ParentDefOf[Definition] with OptionsDef[?] =>
          // Applies To: Context, Entity, Interaction
          state.withCurrent(_.openDef(container).emitOptions(container))
        case container: ParentDefOf[Definition] =>
          // Applies To: Saga, Plant, Handler, Processor
          state.withCurrent(_.openDef(container))
      }
    }

    override def doDefinition(
      state: ReformatState,
      definition: Definition,
      parents: Seq[ParentDefOf[Definition]]
    ): ReformatState = {
      definition match {
        case example: Example => state.withCurrent(_.emitExample(example))
        case invariant: Invariant => state.withCurrent(_.openDef(invariant)
            .closeDef(invariant, withBrace = false))
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
        case st: State        =>
          state.withCurrent(_.closeDef(st, withBrace = false))
        case _: OnClause      => closeOnClause(state)
        case include: Include => closeInclude(state, include)
        case adaptation: AdaptorDefinition => closeAdaptation(state, adaptation)
        case _: RootContainer              =>
          // ignore
          state
        case container: ParentDefOf[Definition] =>
          // Applies To: Domain, Context, Entity, Adaptor, Interaction, Saga,
          // Plant, Processor, Function, SagaStep
          state.withCurrent(_.closeDef(container))
      }
    }

    def openDomain(
      state: ReformatState,
      domain: Domain
    ): ReformatState = {
      state.withCurrent(_.openDef(domain)).step { s1 =>
        if (domain.author.nonEmpty && domain.author.get.nonEmpty) {
          val author = domain.author.get
          s1.withCurrent(_.addIndent(s"author is {\n")
            .indent
            .addIndent(s"name = ${author.name.format}\n")
            .addIndent(s"email = ${author.email.format}\n")
          ).step { s2 =>
              author.organization
                .map(org => s2.withCurrent(
                  _.addIndent(s"organization =${org.format}\n")))
                .orElse(Option(s2)).get
            }.step { s3 =>
              author.title
                .map(title => s3.withCurrent(
                  _.addIndent(s"title = ${title.format}\n")))
                .orElse(Option(s3)).get
            }.withCurrent(_.outdent.addIndent("}\n"))
        } else {
          s1
        }
      }
    }

    def openStory(state: ReformatState, story: Story): ReformatState = {
      state.withCurrent(
        _.openDef(story).addIndent(Keywords.role).add(" is ")
        .add(story.role.format).addNL().addIndent(Keywords.capability)
        .add(" is ").add(story.capability.format).addNL()
        .addIndent(Keywords.benefit).add(" is ").add(story.benefit.format)
        .addNL()
      ).step { state =>
          if (story.examples.nonEmpty) {
            state.withCurrent(
              _.addIndent(Keywords.accepted).add(" by {")
              .addNL().indent
            )
          } else { state }
        }
    }

    def closeStory(state: ReformatState, story: Story): ReformatState = {
      (if (story.examples.nonEmpty) {
        state.withCurrent(_.outdent.addNL().addLine("}")) }
       else { state }).withCurrent(_.closeDef(story))
    }

    def openAdaptor(
      state: ReformatState,
      adaptor: Adaptor
    ): ReformatState = {
      state.withCurrent(
        _.addIndent(AST.keyword(adaptor))
        .add(" ").add(adaptor.id.format)
        .add(" for ").add(adaptor.ref.format).add(" is {")
      ).step { s2 =>
          if (adaptor.isEmpty) { s2.withCurrent(_.emitUndefined().add(" }\n")) }
          else { s2.withCurrent(_.add("\n").indent) }
        }
    }

    def openAdaptation(
      state: ReformatState,
      adaptation: Adaptation
    ): ReformatState = {
      adaptation match {
        case ec8: EventCommandA8n => state.withCurrent(
            _.addIndent(s"adapt ${adaptation.id.format} is {\n").indent
            .addIndent("from ").emitMessageRef(ec8.messageRef).add(" to ")
            .emitMessageRef(ec8.command).add(" as {\n").indent)

        case ea8: EventActionA8n => state.withCurrent(
            _.addIndent(s"adapt ${adaptation.id.format} is {\n").indent
            .addIndent("from ").emitMessageRef(ea8.messageRef).add(" to ")
            .emitActions(ea8.actions).add(" as {\n").indent)
      }
    }

    def closeAdaptation(
      state: ReformatState,
      adaptation: AdaptorDefinition
    ): ReformatState = {
      state.withCurrent(
        _.outdent.addIndent("}\n").outdent.addIndent("}\n")
        .emitBrief(adaptation.brief).emitDescription(adaptation.description)
      )
    }

    def openOnClause(state: ReformatState, onClause: OnClause): ReformatState = {
      state.withCurrent(
        _.addIndent("on ")
          .emitMessageRef(onClause.msg)
          .add(" {\n")
          .indent
      )
    }

    def closeOnClause(state: ReformatState): ReformatState = {
      state.withCurrent(
        _.outdent.addIndent("}\n")
      )
    }

    def doPipe(state: ReformatState, pipe: Pipe): ReformatState = {
      state.withCurrent(
        _.openDef(pipe)
      ).step { state =>
        pipe.transmitType match {
          case Some(typ) =>
            state.withCurrent(
            _.addIndent("transmit ")
            .emitTypeExpression(typ)
          )
          case None =>
            state.withCurrent( _.addSpace().emitUndefined())
        }
      }.withCurrent(_.closeDef(pipe))
    }

    def doJoint(state: ReformatState, joint: Joint): ReformatState = {
      val s = state.withCurrent(
        _.addIndent(s"${AST.keyword(joint)} ${joint.id.format} is ")
      )
      joint match {
        case InletJoint(_, _, inletRef, pipeRef, _, _) =>
          s.withCurrent(
            _.addIndent(s"inlet ${inletRef.id.format} from")
            .add(s" pipe ${pipeRef.id.format}\n"))
        case OutletJoint(_, _, outletRef, pipeRef, _, _) =>
          s.withCurrent(
            _.addIndent(s"outlet ${outletRef.id.format} to")
            .add(s" pipe ${pipeRef.id.format}\n"))
      }
    }

    def doInlet(state: ReformatState, inlet: Inlet): ReformatState = {
      state.withCurrent(
        _.addLine(s"inlet ${inlet.id.format} is ${inlet.type_.format}")
      )
    }

    def doOutlet(state: ReformatState, outlet: Outlet): ReformatState = {
      state.withCurrent(
        _.addLine(s"outlet ${outlet.id.format} is ${outlet.type_.format}")
      )
    }

    def openFunction[TCD <: ParentDefOf[Definition]](
      state: ReformatState,
      function: Function
    ): ReformatState = {
      state.withCurrent(_.openDef(function)).step { s =>
        function.input.fold(s)(te =>
          s.withCurrent(
            _.addIndent("requires ").emitTypeExpression(te).addNL()
          )
        )
      }.step { s =>
        function.output
          .fold(s)(te => s.withCurrent(
            _.addIndent("yields ").emitTypeExpression(te).addNL())
          )
      }
    }

    def openSagaStep(
      state: ReformatTranslator.ReformatState,
      step: AST.SagaStep
    ): ReformatState = {
      state.withCurrent(
        _.openDef(step).emitAction(step.doAction).add("reverted by")
        .emitAction(step.undoAction)
      )
    }

    def openInclude(
      state: ReformatState,
      @unused
      include: Include
    ): ReformatState = {
      if (!state.options.singleFile) {
        include.path match {
          case Some(path) =>
            val relativePath = state.relativeToInPath(path)
            state.current.add(s"include \"$relativePath\"")
            val outPath = state.outPathFor(path)
            state.pushFile(FileEmitter(outPath))
          case None =>
            state.current.add(s"include \"<missing file path>\"")
            state
        }
      } else {
        state
      }
    }


    def closeInclude(
      state: ReformatState,
      @unused
      include: Include
    ): ReformatState = {
      if (!state.options.singleFile) {
        state.popFile()
      } else {
        state
      }
    }
  }
}
