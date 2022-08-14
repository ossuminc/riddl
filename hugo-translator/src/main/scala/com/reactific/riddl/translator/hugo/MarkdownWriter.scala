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

import java.io.PrintWriter
import java.nio.file.Path
import scala.collection.mutable

case class MarkdownWriter(filePath: Path) {

  private val sb: StringBuilder = new mutable.StringBuilder()

  override def toString: String = sb.toString

  private def mkDirs(): Unit = {
    val dirFile = filePath.getParent.toFile
    if (!dirFile.exists) { dirFile.mkdirs() }
  }

  def write(writer: PrintWriter): Unit = {
    try {
      writer.write(sb.toString())
      writer.flush()
    } finally { writer.close() }
    sb.clear() // release memory because content written to file
  }

  def write(): Unit = {
    mkDirs()
    val writer = new PrintWriter(filePath.toFile)
    write(writer)
  }

  def nl: this.type = { sb.append("\n"); this }

  def fileHead(
    name: String,
    weight: Int,
    desc: Option[String],
    extras: Map[String, String] = Map.empty[String, String]
  ): this.type = {
    val adds: String = extras.map { case (k: String, v: String) => s"$k: $v" }
      .mkString("\n")
    val headTemplate = s"""---
                          |title: "$name"
                          |weight: $weight
                          |description: "${desc.getOrElse("")}"
                          |geekdocAnchor: true
                          |$adds
                          |---
                          |""".stripMargin
    sb.append(headTemplate)
    this
  }

  def containerWeight: Int = 2 * 5

  def fileHead(cont: ParentDefOf[Definition]): this.type = {
    fileHead(
      cont.id.format,
      containerWeight,
      Option(
        cont.brief.fold(cont.id.format + " has no brief description.")(_.s)
      ),
      Map("geekdocCollapseSection" -> "true")
    )
  }

  def fileHead(definition: Definition, weight: Int): this.type = {
    fileHead(
      definition.id.format,
      weight,
      Option(
        definition.brief
          .fold(definition.id.format + " has no brief description.")(_.s)
      )
    )
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
      italic("Path").p(s": $path")
      italic("Defined At").p(s": ${d.loc}")
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

  def emitOptions[OT <: OptionValue](options: Seq[OT]): this.type = {
    list("Options", options.map(_.format))
    this
  }

  def emitTypes(types: Seq[Type]): this.type = {
    list[(String, String)](
      "Types",
      types.map { t =>
        (
          t.id.format,
          AST.kind(t.typ) +
            t.description.fold(Seq.empty[String])(_.lines.map(_.s))
              .mkString("\n  ", "\n  ", "")
        )
      }
    )
  }

  def emitDomain(domain: Domain, parents: Seq[String]): this.type = {
    fileHead(domain)
    title(domain)
    for (a <- domain.authors) {
      val items = Seq("Name" -> a.name.s, "Email" -> a.email.s) ++
        a.organization.fold(Seq.empty[(String, String)])(ls =>
          Seq("Organization" -> ls.s)
        ) ++
        a.title.fold(Seq.empty[(String, String)])(ls => Seq("Title" -> ls.s))
      list("Author", items)
    }
    emitBriefly(domain, parents)
    emitDetails(domain.description)
    emitTypes(domain.types)
    toc("Contexts", mkTocSeq(domain.contexts))
    toc("Stories", mkTocSeq(domain.stories))
    toc("Plants", mkTocSeq(domain.plants))
    toc("Subdomains", mkTocSeq(domain.domains))
    this
  }

  def emitExample(
    example: Example,
    parents: Seq[String],
    level: Int = 2
  ): this.type = {
    val hLevel =
      if (example.isImplicit) { level }
      else {
        heading(example.id.format, level)
        level + 1
      }
    emitBriefly(example, parents, hLevel)
    emitDetails(example.description, hLevel)
    if (example.givens.nonEmpty) {
      sb.append(
        example.givens.map { given =>
          given.scenario.map(_.s).mkString("    *", "\n    *", "\n")
        }.mkString("* GIVEN\n", "* AND\n", "\n")
      )
    }
    if (example.whens.nonEmpty) {
      sb.append(
        example.whens.map { when => when.condition.format }
          .mkString("* WHEN\n", "\n    * AND ", "\n")
      )
    }
    sb.append(
      example.thens.map { then_ => then_.action.format }
        .mkString("* THEN\n    * ", "\n    * ", "\n")
    )
    if (example.buts.nonEmpty) {
      sb.append(
        example.buts.map { but => but.action.format }
          .mkString("* BUT\n    * ", "\n    * ", "\n")
      )
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
    fileHead(function)
    h2(function.id.format)
    emitBriefly(function, parents)
    emitDetails(function.description)
    emitInputOutput(function.input, function.output)
    h2("Examples")
    val functionExampleLevel = 3
    function.examples.foreach(emitExample(_, parents, functionExampleLevel))
    this
  }

  def emitContext(cont: Context, parents: Seq[String]): this.type = {
    fileHead(cont)
    title(cont)
    emitBriefly(cont, parents)
    emitDetails(cont.description)
    emitOptions(cont.options)
    emitTypes(cont.types)
    toc("Functions", mkTocSeq(cont.functions))
    toc("Adaptors", mkTocSeq(cont.adaptors))
    toc("Entities", mkTocSeq(cont.entities))
    toc("Sagas", mkTocSeq(cont.sagas))
    this
  }

  def emitStates(states: Seq[State], parents: Seq[String]): this.type = {
    h2("States")
    states.foreach { state =>
      h3(state.id.format)
      emitBriefly(state, parents, 4)
      h4("Fields")
      emitFields(state.typeEx.fields)
      emitDetails(state.description, 4)
    }
    this
  }

  def emitInvariants(invariants: Seq[Invariant]): this.type = {
    if (invariants.nonEmpty) {
      h2("Invariants")
      invariants.foreach { invariant =>
        h3(invariant.id.format)
        sb.append("* ").append(invariant.expression.format).append("\n")
        emitDetails(invariant.description, 4)
      }
    }
    this
  }

  def emitHandlers(handlers: Seq[Handler], parents: Seq[String]): this.type = {
    if (handlers.nonEmpty) {
      h2("Handlers")
      handlers.foreach { handler =>
        h3(handler.id.format)
        emitBriefly(handler, parents, 4)
        emitDetails(handler.description, 4)
        handler.clauses.foreach { clause =>
          h4("On " + clause.msg.format)
          emitDetails(clause.description, level = 5)
          val clauseParents = parents :+ handler.id.format
          emitBriefly(clause, clauseParents)
          h5("Examples")
          clause.examples.foreach(emitExample(_, clauseParents, 6))
        }
      }
    }
    this
  }

  def emitEntity(entity: Entity, parents: Seq[String]): this.type = {
    fileHead(entity)
    title(entity)
    emitBriefly(entity, parents)
    emitDetails(entity.description)
    emitOptions(entity.options)
    emitTypes(entity.types)
    emitStates(entity.states, parents)
    emitInvariants(entity.invariants)
    toc("Functions", mkTocSeq(entity.functions))
    emitHandlers(entity.handlers, parents)
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
    fileHead(saga)
    title(saga)
    emitBriefly(saga, parents)
    emitDetails(saga.description)
    emitOptions(saga.options)
    emitInputOutput(saga.input, saga.output)
    emitSagaSteps(saga.sagaSteps, parents)
  }

  def emitStory(story: Story, prefix: Seq[String]): this.type = {
    fileHead(story)
    title(story)
    emitBriefly(story, prefix)
    emitDetails(story.description)
    h2("Story")
    p(
      s"I, as a ${story.role.s}, want ${story.capability.s}, so that ${story.benefit.s}."
    )
    list("Visualizations", story.shownBy.map(u => s"($u)[$u]"))
    list("Implemented By", story.implementedBy.map(_.format))
  }

  def emitPlant(plant: Plant, parents: Seq[String]): this.type = {
    fileHead(plant)
    title(plant)
    emitBriefly(plant, parents)
    emitDetails(plant.description)
    // TODO: generate a diagram for the plant
    toc("Processors", mkTocSeq(plant.processors))
    list("Pipes", mkTocSeq(plant.pipes))
    list("Input Joints", mkTocSeq(plant.inJoints))
    list("Output Joints", mkTocSeq(plant.outJoints))
  }

  def emitPipe(pipe: Pipe, parents: Seq[String]): this.type = {
    fileHead(pipe, weight = 20)
    title(pipe)
    emitBriefly(pipe, parents)
    emitDetails(pipe.description)
    if (pipe.transmitType.nonEmpty) {
      p(s"Transmission Type: ${pipe.transmitType.get.format} ")
    }
    this
  }

  def emitProcessor(proc: Processor, parents: Seq[String]): this.type = {
    fileHead(proc, weight = 30)
    title(proc)
    emitBriefly(proc, parents)
    emitDetails(proc.description)
    h2("Inlets")
    proc.inlets.foreach { inlet =>
      h3(inlet.id.format + s": ${inlet.type_.format}")
      emitBriefly(inlet, parents, 4)
      emitDetails(inlet.description, 4)
    }
    proc.outlets.foreach { outlet =>
      h3(outlet.id.format + s": ${outlet.type_.format}")
      emitBriefly(outlet, parents, 4)
      emitDetails(outlet.description, 4)
    }
    this
  }

  def emitAdaptor(adaptor: Adaptor, parents: Seq[String]): this.type = {
    fileHead(adaptor)
    title(adaptor)
    emitBriefly(adaptor, parents)
    emitDetails(adaptor.description)
    p(s"Applicable To: ${adaptor.ref.format}")
    toc("Adaptations", mkTocSeq(adaptor.adaptations))
  }

  def emitAdaptation(
    adaptation: Adaptation,
    parents: Seq[String]
  ): this.type = {
    fileHead(adaptation, 20)
    title(adaptation)
    emitBriefly(adaptation, parents)
    emitDetails(adaptation.description)
    italic(s"From event").p(s": ${adaptation.messageRef.format}\n")
    italic(s"To command").p(s": ${adaptation.messageRef.format}\n")
    h2("Examples")
    adaptation.examples.foreach(emitExample(_, parents, 3))
    this
  }

  def emitTableHead(columnTitles: Seq[String]): this.type = {
    sb.append(columnTitles.mkString("| ", " | ", " |\n"))
    val dashes = columnTitles.map(s => "-".repeat(s.length))
    sb.append(dashes.mkString("| ", " | ", " |\n"))
    this
  }

  def emitTableRow(firstCol: String, remainingCols: String*): this.type = {
    val row = firstCol +: remainingCols
    sb.append(row.mkString("| ", " | ", " |\n"))
    this
  }

  def emitGlossary(
    weight: Int,
    terms: Seq[GlossaryEntry]
  ): this.type = {
    fileHead("Glossary Of Terms", weight, Some("A generated glossary of terms"))
    emitTableHead(Seq("Term", "Type", "Brief", "Path"))
    terms.sortBy(_.term).foreach { entry =>
      val linkPath = entry.path :+ entry.term
      val termLink = s"[${entry.term}](${linkPath.mkString("/")})"
      emitTableRow(termLink, entry.typ, entry.brief, entry.path.mkString("."))
    }
    this
  }
}

case class GlossaryEntry(
  term: String,
  typ: String,
  brief: String,
  path: Seq[String])
