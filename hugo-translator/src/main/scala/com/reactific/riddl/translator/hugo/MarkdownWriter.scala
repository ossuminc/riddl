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

package com.reactific.riddl.translator.hugo

import com.reactific.riddl.language.AST
import com.reactific.riddl.language.AST._
import com.reactific.riddl.utils.TextFileWriter
import java.nio.file.Path

case class MarkdownWriter(
  filePath: Path,
  state: HugoTranslatorState
) extends TextFileWriter {

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
        "geekdocFilePath" ->s"${state.makeFilePath(cont).getOrElse("no-such-file")}"
      )
    )
    tbd(cont)
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
    sb.append(s"\n# $heading\n")
    this
  }

  def h2(heading: String): this.type = {
    sb.append(s"\n## $heading\n")
    this
  }

  def h3(heading: String): this.type = {
    sb.append(s"\n### $heading\n")
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

  def title(definition: Definition): this.type = {
    sb.append(s"# ${definition.identify}\n")
    this
  }

  def italic(phrase: String): this.type = {
    sb.append(s"_${phrase}_")
    this
  }

  def bold(phrase: String): this.type = {
    sb.append(s"*$phrase*")
    this
  }

  def list[T](items: Seq[T]): this.type = {
    def emitPair(prefix: String, body: String): Unit = {
      if (prefix.startsWith("[") && body.startsWith("(")) {
        sb.append(s"* $prefix$body\n")
      } else { sb.append(s"* _${prefix}_: $body\n") }
    }

    for { item <- items } {
      item match {
        case (
          prefix: String,
          description: String,
          sublist: Seq[String] @unchecked,
          desc: Option[Description] @unchecked) =>
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
        case (prefix: String, body: String) => emitPair(prefix, body)
        case (prefix: String, docBlock: Seq[String] @unchecked) =>
          sb.append(s"* $prefix\n")
          docBlock.foreach(s => sb.append(s"    * $s\n"))
        case body: String => sb.append(s"* $body\n")
        case rnod: RiddlNode => sb.append(s"* ${rnod.format}")
        case x: Any       => sb.append(s"* ${x.toString}\n")
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

  def toc(kindOfThing: String, contents: Seq[String]): this.type = {
    if (contents.nonEmpty) {
      val items = contents.map { name =>
        s"[$name]" -> s"(${name.toLowerCase})"
      }
      list[(String, String)](kindOfThing, items)
    }
    this
  }

  private def mkTocSeq(
    list: Seq[Definition]
  ): Seq[String] = {
    val result = list.map(c => c.id.value)
    result
  }

  /** Generate a string that contains the name of a definition that is
   * markdown linked to the definition in its source.
   * For example, given sourceURL option of https://github.com/a/b
   * and for an editPath option of src/main/riddl and for a
   * Location that has Org/org.riddl at line 30, we would generate this
   * URL:
   * `https://github.com/a/b/blob/main/src/main/riddl/Org/org.riddl#L30`
   * Note that that this works through recursive path identifiers to find
   * the first type that is not a reference
   * Note: this only works for github sources
   * */
  def makeTypeExpressionSourceLink(
    typeEx: TypeExpression,
    parents: Seq[Definition]
  ): String = {
    state.options.sourceURL match {
      case Some(url) =>
        state.options.editPath match {
          case Some(strPath) =>
            val prefix = url + "/blob/" + strPath
            typeEx match {
              case EntityReferenceTypeExpression(_, pathId) =>
                val resolved = state.resolvePath(pathId, parents)
                if (resolved.nonEmpty) {
                  val loc = resolved.head.loc
                  val suffix = resolved.reverse.map(_.id.value).mkString("/")
                  val line = s"#${loc.line}"
                  s"[${resolved.head.identify}]($prefix/$suffix$line)"
                } else {
                  typeEx.format
                }
              case AliasedTypeExpression(_, pathId) =>
                val resolved = state.resolvePath(pathId, parents)
                if (resolved.nonEmpty) {
                  val loc = resolved.head.loc
                  val suffix = resolved.reverse.map(_.id.value).mkString("/")
                  val line = s"#${loc.line}"
                  s"[${resolved.head.identify}]($prefix/$suffix$line)"
                } else {
                  typeEx.format
                }
              case _ => typeEx.format
            }
          case _ => typeEx.format
        }
      case _ => typeEx.format
    }
  }

  def emitIndex(kind: String): this.type = {
    if (state.options.withGraphicalTOC) {
      h2("Graphical TOC Not Implemented Yet")
    } else {
      h2(s"$kind Index")
      p("{{< toc-tree >}}")
    }
  }

  def emitTerms(terms: Seq[Term]): this.type = {
    list("Terms", terms.map(t =>
      (t.id.format, t.brief.map(_.s).getOrElse("{no brief}"), t.description)
    ))
    this
  }

  def emitFields(fields: Seq[Field]): this.type = {
    list(fields.map { field =>
      (field.id.format, AST.kind(field.typeEx), field.description)
    })
  }

  def emitBriefly(
    d: Definition,
    parents: Seq[String],
    level: Int = 2
  ): this.type = {
    if (d.brief.nonEmpty) {
      heading("Briefly", level)
      p(d.brief.fold("Brief description missing.\n")(_.s))
      val path = (parents :+ d.id.format).mkString(".")
      italic("Definition Path").p(s": $path")
      val link = state.makeSourceLink(d)
      italic("Source Location").p(s": [${d.loc}]($link)")
    }
    this
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
    emitBriefly(definition,parents, level)
    emitDetails(definition.description, level)
    if (definition.hasAuthors) {
      emitAuthorInfo(definition.asInstanceOf[WithAuthors].authors)
    }
    this
  }

  def makePathIdRef(
    pid: PathIdentifier,
    parents: Seq[Definition]
  ): String = {
    val resolved = state.resolvePath(pid, parents)
    if (resolved.isEmpty) {
      s"unresolved path: ${pid.format}"
    } else {
      val slink = state.makeSourceLink(resolved.head)
      val link = state.makeDocLink(resolved.head, state.makeParents(resolved.tail))
      s"[${resolved.head.identify}]($link) at [source]($slink)"
    }
  }

  def resolveTypeExpression(
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
      case _ =>
        typeEx.format
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
        heading("Alternation Of", headLevel )
        val data = alt.of.map { te: AliasedTypeExpression =>
          makePathIdRef(te.pid, parents)
        }
        list(data)
      case agg: Aggregation =>
        heading("Aggregation Of", headLevel)
        val data = agg.fields.map { f: Field =>
          val pars = f +: parents
          (f.id.format, resolveTypeExpression(f.typeEx,pars))
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
        list( data )
      case Pattern(_,strs) =>
        heading("Pattern Of", headLevel)
        list(strs.map("`" + _.s + "`"))
      case _ =>
        heading("Type", headLevel)
        p(resolveTypeExpression(typeEx,parents))
    }
  }

  def emitType(typ: Type, stack: Seq[Definition]): this.type = {
    val suffix = typ.typ match {
      case mt: MessageType => mt.messageKind.kind.capitalize
      case _ => "Type"
    }
    containerHead(typ, suffix)
    emitDefDoc(typ, state.makeParents(stack))
    emitTypeExpression(typ.typ, typ+: stack)
    val link = state.makeSourceLink(typ)
    if (link.nonEmpty) {
      h3("Source Link")
      p(s"[${typ.identify}]($link)")
    }
    this
  }

  def emitAuthorInfo(authors: Seq[AuthorInfo], level: Int = 2): this.type = {
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
    containerHead(domain,"Domain")
    emitDefDoc(domain, parents)
    toc("Subdomains", mkTocSeq(domain.domains))
    toc("Types", mkTocSeq(domain.types))
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
        }
    )
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
    containerHead(function,"Function")
    h2(function.id.format)
    emitDefDoc(function, parents)
    emitInputOutput(function.input, function.output)
    h2("Examples")
    val functionExampleLevel = 3
    function.examples.foreach(emitExample(_, parents, functionExampleLevel))
    this
  }

  def emitContextMap(root: Definition, focus: Context): this.type = {
    h2("Context Map")
  }

  def emitContext(context: Context, stack: Seq[Definition]): this.type = {
    containerHead(context,"Context")
    val parents = state.makeParents(stack)
    emitDefDoc(context, parents)
    emitContextMap(stack.last, context)
    emitOptions(context.options)
    toc("Types", mkTocSeq(context.types))
    toc("Functions", mkTocSeq(context.functions))
    toc("Adaptors", mkTocSeq(context.adaptors))
    toc("Entities", mkTocSeq(context.entities))
    toc("Sagas", mkTocSeq(context.sagas))
    emitTerms(context.terms)
    emitIndex("Context")
    this
  }

  def emitState(state: State, parents: Seq[String]): this.type = {
    containerHead(state,"State")
    emitDefDoc(state, parents)
    h2("Fields")
    emitFields(state.typeEx.fields)
    emitDetails(state.description)
  }

  def emitInvariants(invariants: Seq[Invariant]): this.type = {
    if (invariants.nonEmpty) {
      h2("Invariants")
      invariants.foreach { invariant =>
        h3(invariant.id.format)
        val expr = invariant.expression.map(_.format).getOrElse("<not specified>")
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

  def emitEntity(entity: Entity, parents: Seq[String]): this.type = {
    containerHead(entity,"Entity")
    emitDefDoc(entity, parents)
    emitOptions(entity.options)
    emitInvariants(entity.invariants)
    toc("Types", mkTocSeq(entity.types))
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

  def emitStory(story: Story, parents: Seq[String]): this.type = {
    containerHead(story, "Story")
    emitDefDoc(story, parents)
    h2("Story")
    p(
      s"I, as a ${story.role.s}, want ${story.capability.s}, so that ${story.benefit.s}."
    )
    list("Visualizations", story.shownBy.map(u => s"($u)[$u]"))
    list("Implemented By", story.implementedBy.map(_.format))
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

  def emitProjection(projection: Projection, parents: Seq[String]): this.type = {
    containerHead(projection, "Projection")
    emitDefDoc(projection, parents)
    emitFields(projection.fields)
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
    italic(s"From event").p(s": ${adaptation.messageRef.format}\n")
    italic(s"To command").p(s": ${adaptation.messageRef.format}\n")
    h2("Examples")
    adaptation.examples.foreach(emitExample(_, parents, 3))
    this
  }

  def emitTableHead(columnTitles: Seq[(String,Char)]): this.type = {
    sb.append(columnTitles.map(_._1).mkString("| ", " | ", " |\n"))
    val dashes = columnTitles.map{
      case (s,c) =>
        c match {
          case 'C' =>
            ":---:" ++ " ".repeat(Math.max(s.length - 5, 0))
          case 'L' =>
            ":---" ++ " ".repeat(Math.max(s.length-4, 0))
          case 'R' =>
            " ".repeat(Math.max(s.length-4, 0)) ++ "---:"
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
    if (link.nonEmpty) {
      s"[{{< icon \"$id\" >}}]($link \"$title\")"
    } else { "" }
  }

  def emitTermRow(term: GlossaryEntry): Unit = {
    val slink =
      makeIconLink("gdoc_github", "GitHub Link", term.sourceLink)
    val trm = s"[${term.term}](${term.link})$slink"
    val typ = s"[${term.typ}](https://riddl.tech/concepts/${
      term.typ.toLowerCase})"
    emitTableRow(trm, typ, term.brief)
  }

  def emitGlossary(
    weight: Int,
    terms: Seq[GlossaryEntry]
  ): this.type = {
    fileHead("Glossary Of Terms", weight, Some("A generated glossary of terms"))

    emitTableHead(Seq(
      "Term" -> 'C',
      "Type" -> 'C',
      "Brief Description" -> 'L'
    ))

    terms.sortBy(_.term).foreach { entry =>
      emitTermRow(entry)
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
