/*
 * Copyright 2019 Reactific Software LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.reactific.riddl.hugo

import com.reactific.riddl.language.AST
import com.reactific.riddl.language.Riddl
import com.reactific.riddl.language.AST.*
import com.reactific.riddl.utils.TextFileWriter

import java.nio.file.Path
import scala.annotation.unused
import scala.collection.SortedMap

case class MarkdownWriter(
  filePath: Path,
  state: HugoTranslatorState)
    extends TextFileWriter {

  def fileHead(
    title: String,
    weight: Int,
    desc: Option[String],
    extras: Map[String, String] = Map.empty[String, String]
  ): this.type = {
    val adds: String = extras.map { case (k: String, v: String) => s"$k: $v" }
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

  def containerWeight: Int = 2 * 5

  def tbd(defn: Definition): this.type = {
    if (defn.isEmpty) { p("TBD: To Be Defined") }
    else { this }
  }
  def containerHead(cont: Definition, titleSuffix: String): this.type = {

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

  def leafHead(definition: Definition, weight: Int): this.type = {
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
      p("```mermaid")
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
    val fields = state.aggregation.fields
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

  def emitIndex(kind: String): this.type = {
    if (state.options.withGraphicalTOC) {
      h2("Graphical TOC Not Implemented Yet")
      p("{{< toc-tree >}}")
    } else {
      h2(s"$kind Index")
      p("{{< toc-tree >}}")
    }
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
      (field.id.format, AST.kind(field.typeEx), field.brief, field.description)
    })
  }

  def emitBriefly(
    d: Definition,
    parents: Seq[String],
    @unused level: Int = 2
  ): this.type = {
    emitTableHead(Seq("Item" -> 'C', "Value" -> 'L'))
    val brief: String = d.brief.map(_.s).getOrElse("Brief description missing.")
      .trim
    emitTableRow(italic("Briefly"), brief)
    val path = (parents :+ d.id.format).mkString(".")
    emitTableRow(italic("Definition Path"), path)
    val link = state.makeSourceLink(d)
    emitTableRow(italic("View Source Link"), s"[${d.loc}]($link)")
  }

  def emitDetails(d: Option[Description], level: Int = 2): this.type = {
    if (d.nonEmpty) {
      heading("Details", level)
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
    emitDetails(definition.description, level)
    if (definition.hasAuthors) {
      emitAuthorInfo(definition.asInstanceOf[WithAuthors].authors)
    }
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
      s"[${resolved.head.identify}]($link) at [source]($slink)"
    }
  }

  private def makeTypeName(
    pid: PathIdentifier,
    parents: Seq[Definition]
  ): String = {
    val resolved = state.resolvePath(pid, parents)()()
    if (resolved.isEmpty) { s"unresolved path: ${pid.format}" }
    else { resolved.head.id.format }
  }

  private def makeTypeName(
    typeEx: TypeExpression,
    parents: Seq[Definition]
  ): String = {
    val name = typeEx match {
      case AliasedTypeExpression(_, pid)         => makeTypeName(pid, parents)
      case EntityReferenceTypeExpression(_, pid) => makeTypeName(pid, parents)
      case UniqueId(_, pid)                      => makeTypeName(pid, parents)
      case Alternation(_, of) => of.map(ate => makeTypeName(ate.pid, parents))
          .mkString("-")
      case _: Mapping     => "Mapping"
      case _: Aggregation => "Aggregation"
      case _: MessageType => "Message"
      case _              => typeEx.format
    }
    name.replace(" ", "-")
  }

  private def resolveTypeExpression(
    typeEx: TypeExpression,
    parents: Seq[Definition]
  ): String = {
    typeEx match {
      case a: AliasedTypeExpression =>
        s"Alias of ${makePathIdRef(a.pid, parents)}"
      case er: EntityReferenceTypeExpression =>
        s"Entity reference to ${makePathIdRef(er.entity, parents)}"
      case uid: UniqueId =>
        s"Unique identifier for entity ${makePathIdRef(uid.entityPath, parents)}"
      case alt: Alternation =>
        val data = alt.of.map { te: AliasedTypeExpression =>
          makePathIdRef(te.pid, parents)
        }
        s"Alternation of: " + data.mkString(", ")
      case agg: Aggregation =>
        val data = agg.fields.map { f: Field =>
          (f.id.format, resolveTypeExpression(f.typeEx, parents))
        }
        "Aggregation of:" + data.mkString(", ")
      case mt: MessageType =>
        val data = mt.fields.map { f: Field =>
          (f.id.format, resolveTypeExpression(f.typeEx, parents))
        }
        s"${mt.messageKind.kind} message of: " + data.mkString(", ")
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
        p(makePathIdRef(a.pid, parents))
      case er: EntityReferenceTypeExpression =>
        heading("Entity Reference To", headLevel)
        p(makePathIdRef(er.entity, parents))
      case uid: UniqueId =>
        heading("Unique Identifier To", headLevel)
        p(s"Entity ${makePathIdRef(uid.entityPath, parents)}")
      case alt: Alternation =>
        heading("Alternation Of", headLevel)
        val data = alt.of.map { te: AliasedTypeExpression =>
          makePathIdRef(te.pid, parents)
        }
        list(data)
      case agg: Aggregation =>
        heading("Aggregation Of", headLevel)
        val data = agg.fields.map { f: Field =>
          val pars = f +: parents
          (f.id.format, resolveTypeExpression(f.typeEx, pars))
        }
        list("Fields", data, headLevel + 1)
      case mt: MessageType =>
        h2(s"${mt.messageKind.format} Of")
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
      case mt: MessageType => mt.messageKind.kind.capitalize
      case _               => "Type"
    }
    containerHead(typ, suffix)
    emitDefDoc(typ, state.makeParents(stack))
    emitTypeExpression(typ.typ, typ +: stack)
  }

  def emitTypesToc(definition: WithTypes): this.type = {
    val groups = definition.types.groupBy { typ =>
      typ.typ match {
        case mt: MessageType => mt.messageKind match {
            case CommandKind => "Commands"
            case EventKind   => "Events"
            case QueryKind   => "Queries"
            case ResultKind  => "Results"
            case OtherKind   => "Others"
          }
        case _ => "Others"
      }
    }.toSeq.sortBy(_._1)
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
    toc("Stories", mkTocSeq(domain.stories))
    toc("Plants", mkTocSeq(domain.plants))
    emitTerms(domain.terms)
    emitIndex("Domain")
    this
  }

  def emitExample(
    example: Example,
    parents: Seq[String],
    level: Int = 2
  ): this.type = {
    val hLevel = {
      if (example.isImplicit) { level }
      else {
        heading(example.id.format, level)
        level + 1
      }
    }
    emitDefDoc(example, parents, hLevel)
    heading("GIVEN", hLevel)
    list(example.givens.map { given =>
      given.scenario.map("    *" + _.s + "\n")
    })
    heading("WHEN", hLevel)
    list(example.whens.map { when => when.condition.format })
    heading("THEN", hLevel)
    list(example.thens.map { then_ => then_.action.format })
    heading("BUT", hLevel)
    list(example.buts.map { but => but.action.format })
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
    val functionExampleLevel = 3
    function.examples.foreach(emitExample(_, parents, functionExampleLevel))
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
    emitTerms(context.terms)
    emitIndex("Context")
    this
  }

  def emitState(state: State, parents: Seq[Definition]): this.type = {
    containerHead(state, "State")
    emitDefDoc(state, this.state.makeParents(parents))
    emitERD(state, parents)
    h2("Fields")
    emitFields(state.aggregation.fields)
  }

  def emitInvariants(invariants: Seq[Invariant]): this.type = {
    if (invariants.nonEmpty) {
      h2("Invariants")
      invariants.foreach { invariant =>
        h3(invariant.id.format)
        val expr = invariant.expression.map(_.format)
          .getOrElse("<not specified>")
        sb.append("* ").append(expr).append("\n")
        emitDetails(invariant.description, 4)
      }
    }
    this
  }

  def emitHandler(handler: Handler, parents: Seq[String]): this.type = {
    containerHead(handler, "Handler")
    emitDefDoc(handler, parents)
    handler.clauses.foreach { clause =>
      h3("On " + clause.msg.format)
      emitDetails(clause.description, level = 4)
      val clauseParents = parents :+ handler.id.format
      emitBriefly(clause, clauseParents)
      h4("Examples")
      clause.examples.foreach(emitExample(_, clauseParents, 5))
    }
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
    emitIndex("Entity")
  }

  def emitSagaSteps(actions: Seq[SagaStep], parents: Seq[String]): this.type = {
    h2("Saga Actions")
    actions.foreach { action =>
      h3(action.id.format)
      emitBriefly(action, parents, 4)
      h4("Messaging")
      list(Seq(
        "Do:" -> action.doAction.format,
        "Undo:" -> action.undoAction.format
      ))
      p(action.format)
      h4("Examples")
      action.examples.foreach(emitExample(_, parents, 5))
      emitDetails(action.description, 4)
    }
    this
  }
  def emitSaga(saga: Saga, parents: Seq[String]): this.type = {
    containerHead(saga, "Saga")
    emitDefDoc(saga, parents)
    emitOptions(saga.options)
    emitInputOutput(saga.input, saga.output)
    emitSagaSteps(saga.sagaSteps, parents)
  }

  def emitStory(story: Story, stack: Seq[Definition]): this.type = {
    containerHead(story, "Story")
    val parents = state.makeParents(stack)
    emitDefDoc(story, parents)
    if (story.userStory.nonEmpty) {
      val role = story.userStory.get.actor.identify
      val capability = story.userStory.get.capability
      val benefit = story.userStory.get.benefit
      h2("User Story")
      p(s"I, as a $role, want $capability, so that $benefit.")
    }
    list("Visualizations", story.shownBy.map(u => s"($u)[$u]"))
    list(
      "Designs",
      story.cases.map { d =>
        s"[${d.identifyWithLoc}](${state.makeDocLink(d, parents)})"
      }
    )
  }

  def emitPlant(plant: Plant, parents: Seq[String]): this.type = {
    containerHead(plant, "Plant")
    emitDefDoc(plant, parents)
    // TODO: generate a diagram for the plant
    toc("Processors", mkTocSeq(plant.processors))
    list("Pipes", mkTocSeq(plant.pipes))
    list("Input Joints", mkTocSeq(plant.inJoints))
    list("Output Joints", mkTocSeq(plant.outJoints))
    emitTerms(plant.terms)
    emitIndex("Plant")
  }

  def emitPipe(pipe: Pipe, parents: Seq[String]): this.type = {
    leafHead(pipe, weight = 20)
    emitDefDoc(pipe, parents)
    if (pipe.transmitType.nonEmpty) {
      p(s"Transmission Type: ${pipe.transmitType.get.format} ")
    }
    this
  }

  def emitProcessor(proc: Processor, parents: Seq[String]): this.type = {
    leafHead(proc, weight = 30)
    emitDefDoc(proc, parents)
    h2("Inlets")
    proc.inlets.foreach { inlet =>
      h3(inlet.id.format + s": ${inlet.type_.format}")
      emitBriefly(inlet, parents, 4)
      emitDetails(inlet.description, 4)
    }
    h2("Outlets")
    proc.outlets.foreach { outlet =>
      h3(outlet.id.format + s": ${outlet.type_.format}")
      emitBriefly(outlet, parents, 4)
      emitDetails(outlet.description, 4)
    }
    this
  }

  def emitProjection(
    projection: Projection,
    parents: Seq[String]
  ): this.type = {
    containerHead(projection, "Projection")
    emitDefDoc(projection, parents)
    emitFields(projection.aggregation.fields)
  }

  def emitAdaptor(adaptor: Adaptor, parents: Seq[String]): this.type = {
    containerHead(adaptor, "Adaptor")
    emitDefDoc(adaptor, parents)
    p(s"Applicable To: ${adaptor.ref.format}")
    toc("Adaptations", mkTocSeq(adaptor.adaptations))
  }

  def emitAdaptation(
    adaptation: Adaptation,
    parents: Seq[String]
  ): this.type = {
    leafHead(adaptation, 20)
    emitDefDoc(adaptation, parents)
    p(s"${italic(s"From event")}: ${adaptation.messageRef.format}\n")
    p(s"${italic(s"To command")}: ${adaptation.messageRef.format}\n")
    h2("Examples")
    adaptation.examples.foreach(emitExample(_, parents, 3))
    this
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

    Riddl.collectStats(root) match {
      case Right(stats: SortedMap[String, String]) =>
        emitTableHead(Seq("Measurement" -> 'R', "Value" -> 'L'))

        stats.foreach { case (k, v) => emitTableRow(k, v) }
        this
      case Left(messages) => list(messages.format)
    }
  }
}

case class GlossaryEntry(
  term: String,
  typ: String,
  brief: String,
  path: Seq[String],
  link: String = "",
  sourceLink: String = "")
