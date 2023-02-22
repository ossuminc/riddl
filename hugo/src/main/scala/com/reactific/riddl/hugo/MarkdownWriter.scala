/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.hugo

import com.reactific.riddl.language.Riddl
import com.reactific.riddl.language.AST.*
import com.reactific.riddl.utils.TextFileWriter

import java.nio.file.Path
import scala.annotation.unused

case class MarkdownWriter(filePath: Path, state: HugoTranslatorState)
    extends TextFileWriter {

  def fileHead(
    title: String,
    weight: Int,
    desc: Option[String],
    extras: Map[String, String] = Map.empty[String, String]
  ): this.type = {
    val adds: String = extras
      .map { case (k: String, v: String) => s"$k: $v" }
      .mkString("\n")
    val headTemplate = s"""---
                          |title: "$title"
                          |weight: $weight
                          |draft: "false"
                          |description: "${desc.getOrElse("")}"
                          |geekdocAnchor: true
                          |geekdocToC: 4
                          |$adds
                          |---
                          |""".stripMargin
    sb.append(headTemplate)
    this
  }

  private def containerWeight: Int = 2 * 5

  private def tbd(definition: Definition): this.type = {
    if (definition.isEmpty) { p("TBD: To Be Defined") }
    else { this }
  }

  private def containerHead(
    cont: Definition,
    titleSuffix: String
  ): this.type = {

    fileHead(
      cont.id.format + s": $titleSuffix",
      containerWeight,
      Option(
        cont.brief.fold(cont.id.format + " has no brief description.")(_.s)
      ),
      Map(
        "geekdocCollapseSection" -> "true",
        "geekdocFilePath" ->
          s"${state.makeFilePath(cont).getOrElse("no-such-file")}"
      )
    )
  }

  private def leafHead(definition: Definition, weight: Int): this.type = {
    fileHead(
      s"${definition.id.format}: ${definition.getClass.getSimpleName}",
      weight,
      Option(
        definition.brief
          .fold(definition.id.format + " has no brief description.")(_.s)
      )
    )
    tbd(definition)
  }

  def heading(heading: String, level: Int = 2): this.type = {
    level match {
      case 1 => h1(heading)
      case 2 => h2(heading)
      case 3 => h3(heading)
      case 4 => h4(heading)
      case 5 => h5(heading)
      case 6 => h6(heading)
      case _ => h2(heading)
    }
  }

  def h1(heading: String): this.type = {
    sb.append(s"\n# ${bold(heading)}\n")
    this
  }

  def h2(heading: String): this.type = {
    sb.append(s"\n## ${bold(heading)}\n")
    this
  }

  def h3(heading: String): this.type = {
    sb.append(s"\n### ${italic(heading)}\n")
    this
  }

  def h4(heading: String): this.type = {
    sb.append(s"\n#### $heading\n")
    this
  }

  def h5(heading: String): this.type = {
    sb.append(s"\n##### $heading\n")
    this
  }

  def h6(heading: String): this.type = {
    sb.append(s"\n###### $heading\n")
    this
  }

  def p(paragraph: String): this.type = {
    sb.append(paragraph)
    nl
    this
  }

  private def italic(phrase: String): String = { s"_${phrase}_" }

  private def bold(phrase: String): String = { s"*$phrase*" }

  private def mono(phrase: String): String = { s"`$phrase`" }

  def listOf[T <: Definition](
    kind: String,
    items: Seq[T],
    level: Int = 2
  ): this.type = {
    heading(kind, level)
    val refs = items.map { definition =>
      state.makeDocAndParentsLinks(definition)
    }
    list(refs)
  }

  def list[T](items: Seq[T]): this.type = {
    def emitPair(prefix: String, body: String): Unit = {
      if (prefix.startsWith("[") && body.startsWith("(")) {
        sb.append(s"* $prefix$body\n")
      } else { sb.append(s"* ${italic(prefix)}: $body\n") }
    }

    for { item <- items } {
      item match {
        case (
              prefix: String,
              description: String,
              sublist: Seq[String] @unchecked,
              desc: Option[Description] @unchecked
            ) =>
          emitPair(prefix, description)
          sublist.foreach(s => sb.append(s"    * $s\n"))
          if (desc.nonEmpty) {
            sb.append(desc.get.lines.map(line => s"  ${line.s}\n"))
          }
        case (
              prefix: String,
              definition: String,
              description: Option[Description] @unchecked
            ) =>
          emitPair(prefix, definition)
          if (description.nonEmpty) {
            sb.append(description.get.lines.map(line => s"    * ${line.s}\n"))
          }
        case (
              prefix: String,
              definition: String,
              briefly: Option[LiteralString] @unchecked,
              description: Option[Description] @unchecked
            ) =>
          emitPair(
            prefix,
            definition ++ " - " ++ briefly.map(_.s).getOrElse("{no brief}")
          )
          if (description.nonEmpty) {
            sb.append(description.get.lines.map(line => s"    * ${line.s}\n"))
          }
        case (prefix: String, body: String) => emitPair(prefix, body)
        case (prefix: String, docBlock: Seq[String] @unchecked) =>
          sb.append(s"* $prefix\n")
          docBlock.foreach(s => sb.append(s"    * $s\n"))
        case body: String    => sb.append(s"* $body\n")
        case rnod: RiddlNode => sb.append(s"* ${rnod.format}")
        case x: Any          => sb.append(s"* ${x.toString}\n")
      }
    }
    this
  }

  def list[T](typeOfThing: String, items: Seq[T], level: Int = 2): this.type = {
    if (items.nonEmpty) {
      heading(typeOfThing, level)
      list(items)
    }
    this
  }

  def toc(
    kindOfThing: String,
    contents: Seq[String],
    level: Int = 2
  ): this.type = {
    if (contents.nonEmpty) {
      val items = contents.map { name =>
        s"[$name]" -> s"(${name.toLowerCase})"
      }
      list[(String, String)](kindOfThing, items, level)
    }
    this
  }

  private def mkTocSeq(
    list: Seq[Definition]
  ): Seq[String] = {
    val result = list.map(c => c.id.value)
    result
  }

  def emitMermaidDiagram(lines: Seq[String]): this.type = {
    p("{{< mermaid class=\"text-center\">}}")
    lines.foreach(p)
    p("{{< /mermaid >}}")
    if (state.commonOptions.debug) {
      p("```")
      lines.foreach(p)
      p("```")
    } else { this }
  }

  def makeERDRelationship(
    from: String,
    to: Field,
    parents: Seq[Definition]
  ): String = {
    val typeName = makeTypeName(to.typeEx, parents)
    if (typeName.nonEmpty) {
      to.typeEx match {
        case _: OneOrMore =>
          if (typeName.isEmpty) typeName
          else { from + " ||--|{ " + typeName + " : references" }
        case _: ZeroOrMore =>
          if (typeName.isEmpty) typeName
          else { from + " ||--o{ " + typeName + " : references" }
        case _: Optional =>
          if (typeName.isEmpty) typeName
          else { from + " ||--o| " + typeName + " : references" }
        case _: AliasedTypeExpression | _: EntityReferenceTypeExpression |
            _: UniqueId =>
          if (typeName.isEmpty) typeName
          else { from + " ||--|| " + typeName + " : references" }
        case _ => ""
      }
    } else { typeName }
  }

  def emitERD(state: State, parents: Seq[Definition]): this.type = {
    h2("Entity Relationships")
    val fields = state.record.fields
    val typ: Seq[String] = s"${state.id.format} {" +: fields.map { f =>
      val typeName = makeTypeName(f.typeEx, parents)
      val fieldName = f.id.format.replace(" ", "-")
      val comment = "\"" + f.brief.map(_.s).getOrElse("") + "\""
      s"  $typeName $fieldName $comment"
    } :+ "}"
    val relationships: Seq[String] = fields
      .map(makeERDRelationship(state.id.format, _, parents)).filter(_.nonEmpty)
    val lines = Seq("erDiagram") ++ typ ++ relationships
    emitMermaidDiagram(lines)
  }

  private case class Level(name: String, href: String, children: Seq[Level]) {
    override def toString: String = {
      s"{name:\"$name\",href:\"$href\",children:[${children.map(_.toString).mkString(",")}]}"
    }
  }

  private def makeData(container: Definition, parents: Seq[String]): Level = {
    Level(
      container.identify,
      this.state.makeDocLink(container, parents),
      children = {
        val newParents = container.id.value +: parents
        container.contents
          .filter(d =>
            d.nonEmpty && !d.isInstanceOf[OnMessageClause] &&
              !d.isInstanceOf[Example]
          )
          .map(makeData(_, newParents))
      }
    )
  }

  def emitUsage(definition: Definition): this.type = {
    state.result.usedBy.get(definition) match {
      case Some(users) => listOf("Used By", users)
      case None        => h2("Used By None")
    }
    state.result.uses.get(definition) match {
      case Some(usages) => listOf("Uses", usages)
      case None         => h2("Uses Nothing")
    }
    this
  }

  def emitIndex(
    kind: String,
    top: Definition,
    parents: Seq[String]
  ): this.type = {
    if (state.options.withGraphicalTOC) {
      h2(s"Graphical $kind Index")
      val json = makeData(top, parents).toString
      val resourceName = "js/tree-map-hierarchy2.js"
      val javascript =
        s"""
           |<div id="graphical-index">
           |  <script src="https://d3js.org/d3.v7.min.js"></script>
           |  <script src="/$resourceName"></script>
           |  <script>
           |    console.log('d3', d3.version)
           |    let data = $json ;
           |    let svg = treeMapHierarchy(data, 932);
           |    var element = document.getElementById("graphical-index");
           |    element.appendChild(svg);
           |  </script>
           |</div>
          """.stripMargin
      p(javascript)
    }
    h2(s"Textual $kind Index")
    p("{{< toc-tree >}}")
  }

  def emitC4ContainerDiagram(
    defntn: Context,
    parents: Seq[Definition]
  ): this.type = {
    val name = defntn.identify
    val brief: Definition => String = { defn: Definition =>
      defn.brief.fold(s"$name is not described.")(_.s)
    }

    val heading = s"""C4Context
                     |  title C4 Containment Diagram for [$name]
                     |""".stripMargin.split('\n').toSeq

    val containers = parents.filter(_.isContainer).reverse
    val systemBoundaries = containers.zipWithIndex
    val openedBoundaries = systemBoundaries.map { case (dom, n) =>
      val nm = dom.id.format
      val keyword = if (n == 0) "Enterprise_Boundary" else "System_Boundary"
      " ".repeat((n + 1) * 2) + s"$keyword($nm,$nm,\"${brief(dom)}\") {"
    }
    val closedBoundaries = systemBoundaries.reverse.map { case (_, n) =>
      " ".repeat((n + 1) * 2) + "}"
    }
    val prefix = " ".repeat(parents.size * 2)
    val context_head = prefix +
      s"Boundary($name, $name, \"${brief(defntn)}\") {"
    val context_foot = prefix + "}"

    val body = defntn.entities.map(e =>
      prefix + s"  System(${e.id.format}, ${e.id.format}, \"${brief(e)}\")"
    )
    val lines: Seq[String] = heading ++ openedBoundaries ++ Seq(context_head) ++
      body ++ Seq(context_foot) ++ closedBoundaries
    emitMermaidDiagram(lines)
  }

  def emitTerms(terms: Seq[Term]): this.type = {
    list(
      "Terms",
      terms.map(t =>
        (t.id.format, t.brief.map(_.s).getOrElse("{no brief}"), t.description)
      )
    )
    this
  }

  def emitFields(fields: Seq[Field]): this.type = {
    list(fields.map { field =>
      (field.id.format, field.typeEx.format, field.brief, field.description)
    })
  }

  def emitBriefly(
    d: Definition,
    parents: Seq[String],
    @unused level: Int = 2
  ): this.type = {
    emitTableHead(Seq("Item" -> 'C', "Value" -> 'L'))
    val brief: String =
      d.brief.map(_.s).getOrElse("Brief description missing.").trim
    emitTableRow(italic("Briefly"), brief)
    if (d.isVital) {
      val authors = d.asInstanceOf[VitalDefinition[?, ?]].authors
      emitTableRow(italic("Authors"), authors.map(_.format).mkString(", "))
    }
    val path = (parents :+ d.id.format).mkString(".")
    emitTableRow(italic("Definition Path"), path)
    val link = state.makeSourceLink(d)
    emitTableRow(italic("View Source Link"), s"[${d.loc}]($link)")
  }

  def emitDescription(d: Option[Description], level: Int = 2): this.type = {
    if (d.nonEmpty) {
      heading("Description", level)
      val description = d.get.lines.map(_.s)
      description.foreach(p)
    }
    this
  }

  def emitOptions[OT <: OptionValue](
    options: Seq[OT],
    level: Int = 2
  ): this.type = {
    list("Options", options.map(_.format), level)
    this
  }

  def emitDefDoc(
    definition: Definition,
    parents: Seq[String],
    level: Int = 2
  ): this.type = {
    emitBriefly(definition, parents, level)
    emitDescription(definition.description, level)
  }

  def emitShortDefDoc(
    definition: Definition
  ): this.type = {
    definition.brief.foreach(b => p(italic(b.s)))
    definition.description.foreach(d => p(d.lines.mkString("\n")))
    this
  }

  private def makePathIdRef(
    pid: PathIdentifier,
    parents: Seq[Definition]
  ): String = {
    val resolved = state.resolvePath(pid, parents)()()
    if (resolved.isEmpty) { s"unresolved path: ${pid.format}" }
    else {
      val slink = state.makeSourceLink(resolved.head)
      val link = state
        .makeDocLink(resolved.head, state.makeParents(resolved.tail))
      s"[${resolved.head.identify}]($link) [{{< icon \"gdoc_code\" >}}]($slink)"
    }
  }

  private def makeTypeName(
    pid: PathIdentifier,
    parents: Seq[Definition]
  ): String = {
    state.resolvePathIdentifier[Definition](pid, parents) match {
      case None       => s"unresolved path: ${pid.format}"
      case Some(defn) => defn.id.format
    }
  }

  private def makeTypeName(
    typeEx: TypeExpression,
    parents: Seq[Definition]
  ): String = {
    val name = typeEx match {
      case AliasedTypeExpression(_, pid)         => makeTypeName(pid, parents)
      case EntityReferenceTypeExpression(_, pid) => makeTypeName(pid, parents)
      case UniqueId(_, pid)                      => makeTypeName(pid, parents)
      case Alternation(_, of) =>
        of.map(ate => makeTypeName(ate.pathId, parents))
          .mkString("-")
      case _: Mapping                        => "Mapping"
      case _: Aggregation                    => "Aggregation"
      case _: AggregateUseCaseTypeExpression => "Message"
      case _                                 => typeEx.format
    }
    name.replace(" ", "-")
  }

  private def resolveTypeExpression(
    typeEx: TypeExpression,
    parents: Seq[Definition]
  ): String = {
    typeEx match {
      case a: AliasedTypeExpression =>
        s"Alias of ${makePathIdRef(a.pathId, parents)}"
      case er: EntityReferenceTypeExpression =>
        s"Entity reference to ${makePathIdRef(er.entity, parents)}"
      case uid: UniqueId =>
        s"Unique identifier for entity ${makePathIdRef(uid.entityPath, parents)}"
      case alt: Alternation =>
        val data = alt.of.map { te: AliasedTypeExpression =>
          makePathIdRef(te.pathId, parents)
        }
        s"Alternation of: " + data.mkString(", ")
      case agg: Aggregation =>
        val data = agg.fields.map { f: Field =>
          (f.id.format, resolveTypeExpression(f.typeEx, parents))
        }
        "Aggregation of:" + data.mkString(", ")
      case mt: AggregateUseCaseTypeExpression =>
        val data = mt.fields.map { f: Field =>
          (f.id.format, resolveTypeExpression(f.typeEx, parents))
        }
        s"${mt.usecase.kind} message of: " + data.mkString(", ")
      case _ => typeEx.format
    }
  }

  def emitTypeExpression(
    typeEx: TypeExpression,
    parents: Seq[Definition],
    headLevel: Int = 2
  ): this.type = {
    typeEx match {
      case a: AliasedTypeExpression =>
        heading("Alias Of", headLevel)
        p(makePathIdRef(a.pathId, parents))
      case er: EntityReferenceTypeExpression =>
        heading("Entity Reference To", headLevel)
        p(makePathIdRef(er.entity, parents))
      case uid: UniqueId =>
        heading("Unique Identifier To", headLevel)
        p(s"Entity ${makePathIdRef(uid.entityPath, parents)}")
      case alt: Alternation =>
        heading("Alternation Of", headLevel)
        val data = alt.of.map { te: AliasedTypeExpression =>
          makePathIdRef(te.pathId, parents)
        }
        list(data)
      case agg: Aggregation =>
        heading("Aggregation Of", headLevel)
        val data = agg.fields.map { f: Field =>
          val pars = f +: parents
          (f.id.format, resolveTypeExpression(f.typeEx, pars))
        }
        list("Fields", data, headLevel + 1)
      case mt: AggregateUseCaseTypeExpression =>
        h2(s"${mt.usecase.format} Of")
        val data = mt.fields.map { f: Field =>
          val pars = f +: parents
          (f.id.format, resolveTypeExpression(f.typeEx, pars))
        }
        list("Fields", data, headLevel + 1)
      case map: Mapping =>
        heading("Mapping Of", headLevel)
        val from = resolveTypeExpression(map.from, parents)
        val to = resolveTypeExpression(map.to, parents)
        p(s"From:\n: $from").nl
        p(s"To:\n: $to")
      case enum: Enumeration =>
        heading("Enumeration Of", headLevel)
        val data = enum.enumerators.map { e: Enumerator =>
          val docBlock = e.brief.map(_.s).toSeq ++
            e.description.map(_.lines.map(_.s)).toSeq.flatten
          (e.id.format, docBlock)
        }
        list(data)
      case Pattern(_, strs) =>
        heading("Pattern Of", headLevel)
        list(strs.map("`" + _.s + "`"))
      case _ =>
        heading("Type", headLevel)
        p(resolveTypeExpression(typeEx, parents))
    }
  }

  def emitType(typ: Type, stack: Seq[Definition]): this.type = {
    val suffix = typ.typ match {
      case mt: AggregateUseCaseTypeExpression => mt.usecase.kind.capitalize
      case _                                  => "Type"
    }
    containerHead(typ, suffix)
    emitDefDoc(typ, state.makeParents(stack))
    emitTypeExpression(typ.typ, typ +: stack)
    emitUsage(typ)
  }

  def emitTypesToc(definition: WithTypes): this.type = {
    val groups = definition.types
      .groupBy { typ =>
        typ.typ match {
          case mt: AggregateUseCaseTypeExpression => mt.usecase.format
          case _                                  => "Others"
        }
      }
      .toSeq
      .sortBy(_._1)
    h2("Types")
    for { (label, list) <- groups } { toc(label, mkTocSeq(list), 3) }
    this
  }

  def emitAuthorInfo(authors: Seq[Author], level: Int = 2): this.type = {
    for (a <- authors) {
      val items = Seq("Name" -> a.name.s, "Email" -> a.email.s) ++
        a.organization.fold(Seq.empty[(String, String)])(ls =>
          Seq("Organization" -> ls.s)
        ) ++
        a.title.fold(Seq.empty[(String, String)])(ls => Seq("Title" -> ls.s))
      list("Author", items, level)
    }
    this
  }

  def emitDomain(domain: Domain, parents: Seq[String]): this.type = {
    containerHead(domain, "Domain")
    emitDefDoc(domain, parents)
    toc("Subdomains", mkTocSeq(domain.domains))
    emitTypesToc(domain)
    toc("Contexts", mkTocSeq(domain.contexts))
    toc("Applications", mkTocSeq(domain.applications))
    toc("Stories", mkTocSeq(domain.stories))
    emitUsage(domain)
    emitTerms(domain.terms)
    emitIndex("Domain", domain, parents)
    this
  }

  def emitExamples(
    examples: Seq[Example],
    level: Int = 2
  ): this.type = {
    heading("Examples", level)
    var count = 0
    examples.foreach { example =>
      if (example.isImplicit) {
        count += 1
        heading(s"Anonymous Example $count", level + 1)
      } else { heading(example.id.format, level) }
      emitExample(example, level + 1)
    }
    this
  }

  def emitExample(
    example: Example,
    level: Int = 2
  ): this.type = {
    emitShortDefDoc(example)
    if (example.givens.nonEmpty) {
      heading("GIVEN", level)
      list(example.givens.map { given =>
        given.scenario.map("    *" + _.s + "\n")
      })
    }
    if (example.whens.nonEmpty) {
      heading("WHEN", level)
      list(example.whens.map { when => when.condition.format })
    }
    if (example.thens.nonEmpty) {
      heading("THEN", level)
      list(example.thens.map { then_ => then_.action.format })
    }
    if (example.buts.nonEmpty) {
      heading("BUT", level)
      list(example.buts.map { but => but.action.format })
    }
    this
  }

  def emitInputOutput(
    input: Option[Aggregation],
    output: Option[Aggregation]
  ): this.type = {
    if (input.nonEmpty) {
      h4("Requires (Input)")
      emitFields(input.get.fields)
    }
    if (output.nonEmpty) {
      h4("Yields (Output)")
      emitFields(output.get.fields)
    }
    this
  }

  def emitFunction(function: Function, parents: Seq[String]): this.type = {
    containerHead(function, "Function")
    h2(function.id.format)
    emitDefDoc(function, parents)
    emitTypesToc(function)
    emitInputOutput(function.input, function.output)
    h2("Examples")
    emitExamples(function.examples)
    emitUsage(function)
    emitTerms(function.terms)
    this
  }

  def emitContextMap(focus: Context, parents: Seq[Definition]): this.type = {
    h2("Context Map")
    emitC4ContainerDiagram(focus, parents)
  }

  def emitContext(context: Context, stack: Seq[Definition]): this.type = {
    containerHead(context, "Context")
    val parents = state.makeParents(stack)
    emitDefDoc(context, parents)
    emitContextMap(context, stack)
    emitOptions(context.options)
    emitTypesToc(context)
    toc("Functions", mkTocSeq(context.functions))
    toc("Adaptors", mkTocSeq(context.adaptors))
    toc("Entities", mkTocSeq(context.entities))
    toc("Sagas", mkTocSeq(context.sagas))
    // TODO: generate a diagram for the processors and pipes
    toc("Streamlets", mkTocSeq(context.streamlets))
    list("Connections", mkTocSeq(context.connections))
    emitUsage(context)
    emitTerms(context.terms)
    emitIndex("Context", context, parents)
    this
  }

  def emitState(state: State, parents: Seq[Definition]): this.type = {
    containerHead(state, "State")
    emitDefDoc(state, this.state.makeParents(parents))
    emitERD(state, parents)
    h2("Fields")
    emitFields(state.record.fields)
    emitUsage(state)
  }

  def emitInvariants(invariants: Seq[Invariant]): this.type = {
    if (invariants.nonEmpty) {
      h2("Invariants")
      invariants.foreach { invariant =>
        h3(invariant.id.format)
        val expr = invariant.expression
          .map(_.format)
          .getOrElse("<not specified>")
        sb.append("* ").append(expr).append("\n")
        emitDescription(invariant.description, 4)
      }
    }
    this
  }

  def emitHandler(handler: Handler, parents: Seq[String]): this.type = {
    containerHead(handler, "Handler")
    emitDefDoc(handler, parents)
    handler.clauses.foreach { clause =>
      clause match {
        case oic: OnInitClause    => h3(oic.kind)
        case omc: OnMessageClause => h3(clause.kind + " " + omc.msg.format)
        case otc: OnTermClause    => h3(otc.kind)
        case ooc: OnOtherClause   => h3(ooc.kind)
      }
      emitShortDefDoc(clause)
      emitExamples(clause.examples, 4)
    }
    emitUsage(handler)
    this
  }

  def emitFiniteStateMachine(
    @unused entity: Entity
  ): this.type = { this }

  def emitEntity(entity: Entity, parents: Seq[String]): this.type = {
    containerHead(entity, "Entity")
    emitDefDoc(entity, parents)
    emitOptions(entity.options)
    if (entity.hasOption[EntityIsFiniteStateMachine]) {
      h2("Finite State Machine")
      emitFiniteStateMachine(entity)
    }
    emitInvariants(entity.invariants)
    emitTypesToc(entity)
    toc("States", mkTocSeq(entity.states))
    toc("Functions", mkTocSeq(entity.functions))
    toc("Handlers", mkTocSeq(entity.handlers))
    emitUsage(entity)
    emitTerms(entity.terms)
    emitIndex("Entity", entity, parents)
  }

  def emitSagaSteps(actions: Seq[SagaStep]): this.type = {
    h2("Saga Actions")
    actions.foreach { step =>
      h3(step.identify)
      emitShortDefDoc(step)
      h4("Do Examples")
      emitExamples(step.doAction, 5)
      h4("Undo Examples")
      emitExamples(step.undoAction, 5)
    }
    this
  }

  def emitSaga(saga: Saga, parents: Seq[String]): this.type = {
    containerHead(saga, "Saga")
    emitDefDoc(saga, parents)
    emitOptions(saga.options)
    emitInputOutput(saga.input, saga.output)
    emitSagaSteps(saga.sagaSteps)
    emitUsage(saga)
    emitTerms(saga.terms)
    emitIndex("Saga", saga, parents)
  }

  def emitApplication(
    application: Application,
    stack: Seq[Definition]
  ): this.type = {
    containerHead(application, "Application")
    val parents = state.makeParents(stack)
    emitDefDoc(application, parents)
    for { group <- application.groups } {
      h2(group.identify)
      list(group.elements.map(_.format))
    }
    emitUsage(application)
    emitTerms(application.terms)
  }

  def emitStory(story: Story, stack: Seq[Definition]): this.type = {
    containerHead(story, "Story")
    val parents = state.makeParents(stack)
    emitBriefly(story, parents)
    if (story.userStory.nonEmpty) {
      val actorPid = story.userStory.get.actor.pathId
      val maybeActor = state.resolvePathIdentifier[Actor](actorPid, stack)
      h2("User Story")
      maybeActor match {
        case None => p(s"Unresolvable Actor id: ${actorPid.format}")
        case Some(actor) =>
          val name = actor.id.value
          val role = actor.is_a.s
          val us = story.userStory.get
          val benefit = us.benefit.s
          val capability = us.capability.s
          val storyText =
            s"I, $name, as $role, want $capability, so that $benefit"
          p(italic(storyText))
      }
    }
    list("Visualizations", story.shownBy.map(u => s"($u)[$u]"))
    h2("Sequence Diagram")
    val diagram = SequenceDiagrammer(state, story, stack)
    val lines = diagram.toLines
    emitMermaidDiagram(lines)
    emitUsage(story)
    emitTerms(story.terms)
    emitDescription(story.description)
  }

  def emitConnection(conn: Connector, parents: Seq[String]): this.type = {
    leafHead(conn, weight = 20)
    emitDefDoc(conn, parents)
    if (conn.from.nonEmpty && conn.to.nonEmpty) {
      val prefix =
        if (conn.flows.nonEmpty) s"flows ${conn.flows.get.format}"
        else ""
      p(s"$prefix from ${conn.from.get.format} to ${conn.to.get.format}")

    }
    emitUsage(conn)
  }

  def emitStreamlet(proc: Streamlet, parents: Seq[Definition]): this.type = {
    leafHead(proc, weight = 30)
    val parList = state.makeParents(parents)
    emitDefDoc(proc, parList)
    h2("Inlets")
    proc.inlets.foreach { inlet =>
      val typeRef = makePathIdRef(inlet.type_.pathId, parents)
      h3(inlet.id.format)
      p(typeRef)
      emitShortDefDoc(inlet)
    }
    h2("Outlets")
    proc.outlets.foreach { outlet =>
      val typeRef = makePathIdRef(outlet.type_.pathId, parents)
      h3(outlet.id.format)
      p(typeRef)
      emitShortDefDoc(outlet)
    }
    emitUsage(proc)
    emitTerms(proc.terms)
    emitIndex("Processor", proc, parList)
  }

  def emitProjection(
    projection: Projection,
    parents: Seq[String]
  ): this.type = {
    containerHead(projection, "Projection")
    emitDefDoc(projection, parents)
    if (projection.types.nonEmpty) {
      emitTypesToc(projection)
    }
    listOf("Handlers", projection.handlers)
    emitUsage(projection)
    emitTerms(projection.terms)
  }

  def emitAdaptor(adaptor: Adaptor, parents: Seq[String]): this.type = {
    containerHead(adaptor, "Adaptor")
    emitDefDoc(adaptor, parents)
    p(s"Direction: ${adaptor.direction.format} ${adaptor.context.format}")
    toc("Handlers", mkTocSeq(adaptor.handlers))
    emitUsage(adaptor)
    emitTerms(adaptor.terms)
    emitIndex("Adaptor", adaptor, parents)
  }

  def emitTableHead(columnTitles: Seq[(String, Char)]): this.type = {
    sb.append(columnTitles.map(_._1).mkString("| ", " | ", " |\n"))
    val dashes = columnTitles.map { case (s, c) =>
      c match {
        case 'C' => ":---:" ++ " ".repeat(Math.max(s.length - 5, 0))
        case 'L' => ":---" ++ " ".repeat(Math.max(s.length - 4, 0))
        case 'R' => " ".repeat(Math.max(s.length - 4, 0)) ++ "---:"
      }
    }
    sb.append(dashes.mkString("| ", " | ", " |\n"))
    this
  }

  def emitTableRow(firstCol: String, remainingCols: String*): this.type = {
    val row = firstCol +: remainingCols
    sb.append(row.mkString("| ", " | ", " |\n"))
    this
  }

  def makeIconLink(id: String, title: String, link: String): String = {
    if (link.nonEmpty) { s"[{{< icon \"$id\" >}}]($link \"$title\")" }
    else { "" }
  }

  def emitTermRow(term: GlossaryEntry): Unit = {
    val slink = makeIconLink("gdoc_github", "GitHub Link", term.sourceLink)
    val trm = s"[${mono(term.term)}](${term.link})$slink"
    val typ =
      s"[${term.typ}](https://riddl.tech/concepts/${term.typ.toLowerCase}/)"
    emitTableRow(trm, typ, term.brief)
  }

  def emitGlossary(
    weight: Int,
    terms: Seq[GlossaryEntry]
  ): this.type = {
    fileHead("Glossary Of Terms", weight, Some("A generated glossary of terms"))

    emitTableHead(Seq("Term" -> 'C', "Type" -> 'C', "Brief Description" -> 'L'))

    terms.sortBy(_.term).foreach { entry => emitTermRow(entry) }
    this
  }

  def emitToDoList(weight: Int, map: Map[String, Seq[String]]): Unit = {
    fileHead(
      "To Do List",
      weight,
      Option("A list of definitions needing more work")
    )
    h2("Definitions With Missing Content")
    for { (key, items) <- map } {
      h3(key)
      list(items)
    }

  }

  def emitStatistics(weight: Int, root: RootContainer): this.type = {
    fileHead(
      "Model Statistics",
      weight,
      Some("Statistical information about the RIDDL model documented")
    )

    val stats = Riddl.collectStats(root)
    emitTableHead(
      Seq(
        "Category" -> 'L',
        "count" -> 'R',
        "% of All" -> 'R',
        "avg. maturity" -> 'R',
        "tot. maturity" -> 'R',
        "% complete" -> 'R',
        "% document" -> 'R'
      )
    )
    stats.categories.foreach { case (key, s) =>
      emitTableRow(
        key,
        s.count.toString,
        s.percentOfDefinitions.toString,
        s.averageMaturity.toString,
        s.totalMaturity.toString,
        s.percentComplete.toString,
        s.percentDocumented.toString
      )
    }
    this
  }
}

case class GlossaryEntry(
  term: String,
  typ: String,
  brief: String,
  path: Seq[String],
  link: String = "",
  sourceLink: String = ""
)
