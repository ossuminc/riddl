package com.yoppworks.ossum.riddl.generation.hugo.rendering

import com.yoppworks.ossum.riddl.generation.hugo._

trait Renderer[A <: HugoNode] {
  def render(element: A): Option[HugoContent]
}

object Renderer {
  implicit val rendererForHugoRoot: Renderer[HugoRoot] = (_: HugoRoot) => None
  implicit val rendererForHugoDomain: Renderer[HugoDomain] = HugoDomainRenderer
  implicit val rendererForHugoContext: Renderer[HugoContext] = HugoContextRenderer
  implicit val rendererForHugoEntity: Renderer[HugoEntity] = HugoEntityRenderer
  implicit val rendererForHugoType: Renderer[HugoType] = HugoTypeRenderer

  final def render[A <: HugoNode: Renderer](node: A): Option[HugoContent] = implicitly[Renderer[A]]
    .render(node)
}

private object RendererUtils {
  implicit class MarkdownFilenameExt(val str: String) extends AnyVal {
    def md: String = str + ".md"
  }

  @inline
  final def makeNamespace(node: HugoNode): String =
    if (node.parent.isRoot) { "(global)" }
    else { node.parent.fullName }

  @inline
  final def makeNamespace(fullName: String): String = fullName.split('.').toSeq match {
    case Nil | Seq(_) => "(global)"
    case nodes :+ _   => nodes.mkString(".")
  }

  @inline
  final def justTypeName(typeFullName: String): String = typeFullName.split('.').last

  def makeLinkTo(node: HugoNode): MarkdownPrinter = {
    val linkParts = RelativePath.of(node).absParts
    val filteredParts = linkParts.filterNot(_ == "_index.md").map(_.stripSuffix(".md"))
    MarkdownPrinter.empty.link(node.name.capitalize, filteredParts: _*)
  }

  @inline
  final def makeRegion(name: String)(endo: MarkdownPrinter => MarkdownPrinter): MarkdownPrinter =
    endo(MarkdownPrinter.empty.newline.title(2)(name)).newline

  final val none: MarkdownPrinter = MarkdownPrinter.empty.print("(none)")

  def renderDescription(desc: HugoDescription): MarkdownPrinter = {
    val citationsPrinter =
      if (desc.citations.isEmpty) { MarkdownPrinter.empty }
      else { MarkdownPrinter.empty.newline.title(4)("Citations").listSimple(desc.citations) }

    val descPrinter = (desc.brief, desc.details) match {
      case (e, Nil) if e.isEmpty     => MarkdownPrinter.empty.println("(none)")
      case (brief, Nil)              => MarkdownPrinter.empty.println(brief)
      case (e, details) if e.isEmpty => MarkdownPrinter.empty.printLines(details)
      case (brief, details) => MarkdownPrinter.empty.println(brief).newline.printLines(details)
    }

    descPrinter append citationsPrinter
  }

  def renderRiddlType(riddl: RiddlType): MarkdownPrinter = {
    val empty = MarkdownPrinter.empty
    val RT = RiddlType

    @inline
    def printEnumeration(enum: RT.Enumeration) =
      if (enum.of.isEmpty) { empty.print("Any of ()") }
      else { empty.print("Any of ( ").print(enum.of.map(_.name).mkString(", ")).print(" )") }

    @inline
    def printVariant(vnt: RT.Variant) =
      if (vnt.of.isEmpty) { empty.print("One of ()") }
      else {
        val opts = vnt.of.map(renderRiddlType).reduce((l, r) => l.print(", ").append(r))
        empty.print("One of ( ").append(opts).print(" )")
      }

    @inline
    def printMapping(map: RT.Mapping) = {
      val from = renderRiddlType(map.from)
      val to = renderRiddlType(map.to)
      empty.print("Mapping ").italic("from ").append(from).space.italic("to").space.append(to)
    }

    @inline
    def printRange(rng: RT.Range) = empty.print("Numeric range ").italic("from").space
      .print(s"${rng.min}").space.italic("to").space.print(s"${rng.max}")

    @inline
    def printRecord(rec: RT.Record) =
      if (rec.fields.isEmpty) { empty.print("Record of ()") }
      else { empty.print("Record of ( ").print(rec.fields.map(_.name).mkString(", ")).print(" )") }

    riddl match {
      case pre: RT.PredefinedType => empty.print(pre.fullName)
      case ref: RT.EntityRef => empty.print("Reference to Entity: ").append(makeLinkTo(ref.entity))
      case ref: RT.TypeRef   => empty.print("Reference to Type: ").append(makeLinkTo(ref.hugoType))
      case als: RT.Alias     => empty.print("Alias of ").append(renderRiddlType(als.aliasForType))
      case opt: RT.Optional  => empty.print("Optional of ").append(renderRiddlType(opt.innerType))
      case RT.Collection(cont, true) => empty.print("Collection of ").append(renderRiddlType(cont))
      case RT.Collection(cont, false) => empty.print("Non-empty collection of ")
          .append(renderRiddlType(cont))
      case enm: RT.Enumeration => printEnumeration(enm)
      case vnt: RT.Variant     => printVariant(vnt)
      case map: RT.Mapping     => printMapping(map)
      case rng: RT.Range       => printRange(rng)
      case _: RT.RegexPattern  => empty.print("Regular expression")
      case id: RT.UniqueId     => empty.print("UniqueID for ").append(renderRiddlType(id.appliesTo))
      case rec: RT.Record      => printRecord(rec)
      case _                   => empty.print("unknown or unresolved type")
    }
  }

  def renderRiddlShort(riddl: RiddlType): MarkdownPrinter = {
    val empty = MarkdownPrinter.empty
    val RT = RiddlType

    riddl match {
      case pre: RT.PredefinedType     => empty.print(pre.fullName)
      case ref: RT.EntityRef          => empty.print("Entity Reference")
      case ref: RT.TypeRef            => empty.print("Type Reference")
      case als: RT.Alias              => empty.print("Alias")
      case opt: RT.Optional           => empty.print("Optional")
      case RT.Collection(cont, true)  => empty.print("Collection")
      case RT.Collection(cont, false) => empty.print("Non-empty Collection")
      case enm: RT.Enumeration        => empty.print("Any Of")
      case vnt: RT.Variant            => empty.print("One Of")
      case map: RT.Mapping            => empty.print("Mapping")
      case rng: RT.Range              => empty.print("Range")
      case _: RT.RegexPattern         => empty.print("Regex")
      case id: RT.UniqueId            => empty.print("UniqueID")
      case rec: RT.Record             => empty.print("Record")
      case _                          => empty.print("Unknown Type")
    }
  }

  def renderField(field: HugoField): MarkdownPrinter = {
    val fieldPrinter = MarkdownPrinter.empty.bold(field.name).print(": ")
      .append(renderRiddlType(field.fieldType))
    val descPrinter = field.description.fold(MarkdownPrinter.empty) { desc =>
      val brief = if (desc.brief.isBlank) None else Some(desc.brief)
      val details = desc.details
      val links = desc.citations.map(str => s"See: $str")
      val all = (brief ++ details ++ links).toSeq
      MarkdownPrinter.empty.ifEndo(all.nonEmpty)(_.listSimple(all))
    }
    fieldPrinter append descPrinter
  }

  def renderFields(fields: Seq[HugoField]): MarkdownPrinter =
    if (fields.isEmpty) { MarkdownPrinter.empty }
    else {
      MarkdownPrinter.empty
        .listEndo(fields.map(field => (p: MarkdownPrinter) => p.append(renderField(field)).n): _*)
    }

}

abstract private[hugo] class HugoTemplateRenderer[A <: HugoNode] extends Renderer[A] {
  private[this] var replacers = Map.empty[String, A => MarkdownPrinter]
  private def template(node: A) = Templates.forHugo(node).fold(throw _, identity)
  private def replacements(node: A): Map[String, String] = replacers.view
    .mapValues(f => f(node).toString).toMap

  protected def replace(name: String)(fn: A => MarkdownPrinter): Unit = {
    replacers += (name -> fn)
    ()
  }

  protected def replaceStr(name: String)(strFn: A => String): Unit = {
    val fn = (node: A) => MarkdownPrinter.empty.print(strFn(node))
    replacers += (name -> fn)
    ()
  }

  protected def replaceIf(name: String)(pfn: PartialFunction[A, MarkdownPrinter]): Unit = {
    val fn = (node: A) => if (pfn.isDefinedAt(node)) pfn(node) else MarkdownPrinter.empty
    replacers += (name -> fn)
    ()
  }

  protected def replaceDescription(name: String): Unit = replace(name) { node =>
    node.description match {
      case Some(desc) => RendererUtils.renderDescription(desc)
      case None       => MarkdownPrinter.empty
    }
  }

  protected def replaceEither(
    name: String,
    isLeft: A => Boolean
  )(leftFn: A => MarkdownPrinter
  )(rightFn: A => MarkdownPrinter
  ): Unit = {
    val fn = (node: A) => if (isLeft(node)) leftFn(node) else rightFn(node)
    replacers += (name -> fn)
    ()
  }

  protected def replaceEither(
    name: String,
    isLeft: Boolean
  )(leftFn: A => MarkdownPrinter
  )(rightFn: A => MarkdownPrinter
  ): Unit = replaceEither(name, _ => isLeft)(leftFn)(rightFn)

  protected def replaceList[B](
    name: String,
    items: A => Seq[B]
  )(perItem: B => MarkdownPrinter
  ): Unit = {
    val perItemEndo: B => MarkdownPrinter => MarkdownPrinter =
      item => printer => printer.append(perItem(item)).n

    val fn = (node: A) =>
      items(node) match {
        case Nil   => RendererUtils.none
        case elems => MarkdownPrinter.empty.listEndo(elems.map(perItemEndo): _*)
      }

    replacers += (name -> fn)
    ()
  }

  def render(node: A): Option[HugoContent] =
    Some(HugoTemplate(template(node), RelativePath.of(node), replacements(node)))
}

object HugoTypeRenderer extends HugoTemplateRenderer[HugoType] {
  replaceStr("typeName")(_.name)
  replace("riddlTypeShort")(t => RendererUtils.renderRiddlShort(t.typeDef))
  replaceDescription("typeDescription")
  replaceStr("namespace")(RendererUtils.makeNamespace)
  replace("riddlType") {
    case HugoType(_, _, RiddlType.Record(_), _) => MarkdownPrinter.empty.print("Aggregation")
    case t                                      => RendererUtils.renderRiddlType(t.typeDef)
  }

  replaceIf("typeNotes") { case HugoType(name, _, RiddlType.Record(fields), _) =>
    RendererUtils.makeRegion("Fields") { printer =>
      printer.bold(name).println(" defines the following fields:")
        .append(RendererUtils.renderFields(fields.toSeq))
    }
  }
}

object HugoDomainRenderer extends HugoTemplateRenderer[HugoDomain] {
  replaceStr("domainName")(_.name)
  replaceDescription("domainDescription")
  replaceList("domainTypes", _.types.toSeq) { hugoType =>
    RendererUtils.makeLinkTo(hugoType).print(" - ")
      .append(RendererUtils.renderRiddlShort(hugoType.typeDef))
  }
  replaceList("domainBoundedContexts", _.contexts.toSeq) {
    case ctx @ HugoContext(_, _, Some(desc)) => RendererUtils.makeLinkTo(ctx).print(" - ")
        .print(desc.brief)
    case ctx => RendererUtils.makeLinkTo(ctx)
  }
}

object HugoContextRenderer extends HugoTemplateRenderer[HugoContext] {
  replaceStr("contextName")(_.name)
  replaceDescription("contextDescription")
  replaceList("contextTypes", _.types.toSeq) { hugoType =>
    RendererUtils.makeLinkTo(hugoType).print(" - ")
      .append(RendererUtils.renderRiddlShort(hugoType.typeDef))
  }
  replaceList("contextEntities", _.entities.toSeq) {
    case ctx @ HugoEntity(_, _, _, _, _, _, _, Some(desc)) => RendererUtils.makeLinkTo(ctx)
        .print(" - ").print(desc.brief)
    case ctx => RendererUtils.makeLinkTo(ctx)
  }
}

object HugoEntityRenderer extends HugoTemplateRenderer[HugoEntity] {
  private val empty = MarkdownPrinter.empty

  def cmdHandlers(input: Seq[HugoEntity.Handler]): Seq[HugoEntity.Handler] = input
    .filter { case HugoEntity.Handler(_, clauses) =>
      clauses.collect { case cmd: HugoEntity.OnClause.Command => cmd }.nonEmpty
    }

  def eventHandlers(input: Seq[HugoEntity.Handler]): Seq[HugoEntity.Handler] = input
    .filter { case HugoEntity.Handler(_, clauses) =>
      clauses.collect { case event: HugoEntity.OnClause.Event => event }.nonEmpty
    }

  def renderState(state: HugoEntity.State): MarkdownPrinter = MarkdownPrinter.empty
    .title(4)(state.name).title(5)("Fields")
    .append(RendererUtils.renderFields(state.fields.toSeq).n).newline

  def renderInvariant(invar: HugoEntity.Invariant): MarkdownPrinter = {
    val printer = empty.title(4)(invar.name).title(5)("Expressions")
    if (invar.expression.isEmpty) { printer.listSimple("(none)").newline }
    else { printer.listSimple(invar.expression).newline }
  }

  def renderFunction(func: HugoEntity.Function): MarkdownPrinter = {
    val inputs = func.inputs.toSeq
      .map(f => s"${f.name}: **${RendererUtils.renderRiddlShort(f.fieldType).toString}**")
      .mkString(", ")

    val requires = if (func.inputs.isEmpty) "(none)" else inputs
    val yields = s"**${RendererUtils.renderRiddlShort(func.output).toString}**"

    empty.bold(func.name).listEndo(
      _.italic("Requires").print(": ").print(requires),
      _.italic("Yields").print(": ").print(yields)
    )
  }

  replaceStr("entityName")(_.name)
  replaceDescription("entityDescription")
  replaceList("entityOptions", _.options.toSeq) {
    case HugoEntity.EntityOption.EventSourced       => empty.print("event sourced")
    case HugoEntity.EntityOption.FiniteStateMachine => empty.print("finite state machine")
    case other                                      => empty.print(other.toString.toLowerCase)
  }

  replaceList("entityTypes", _.types.toSeq) { hugoType =>
    RendererUtils.makeLinkTo(hugoType).print(" - ")
      .append(RendererUtils.renderRiddlShort(hugoType.typeDef))
  }

  replace("entityStates")(entity =>
    if (entity.states.isEmpty) { RendererUtils.none }
    else { entity.states.foldLeft(empty) { case (p, st) => p.append(renderState(st)) } }
  )

  replace("entityInvariants")(entity =>
    if (entity.invariants.isEmpty) { RendererUtils.none }
    else { entity.invariants.foldLeft(empty) { case (p, in) => p.append(renderInvariant(in)) } }
  )

  replaceList("entityFunctions", _.functions.toSeq)(renderFunction)

  replaceList("entityCommandHandlers", n => cmdHandlers(n.handlers.toSeq)) { handler =>
    val clauses = handler.clauses.collect { case cmd: HugoEntity.OnClause.Command => cmd }
    val clausePrinters = clauses.map(cmd =>
      (p: MarkdownPrinter) =>
        p.italic("Command").print(": ").append(RendererUtils.renderRiddlShort(cmd.onType))
    )

    empty.bold(handler.name).n.listEndo(clausePrinters: _*)
  }

  replaceList("entityEventHandlers", n => eventHandlers(n.handlers.toSeq)) { handler =>
    val clauses = handler.clauses.collect { case cmd: HugoEntity.OnClause.Event => cmd }
    val clausePrinters = clauses.map(cmd =>
      (p: MarkdownPrinter) =>
        p.italic("Event").print(": ").append(RendererUtils.renderRiddlShort(cmd.onType))
    )

    empty.bold(handler.name).n.listEndo(clausePrinters: _*)
  }
}
