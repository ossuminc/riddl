package com.yoppworks.ossum.riddl.translator.hugo

import com.yoppworks.ossum.riddl.language.AST
import com.yoppworks.ossum.riddl.language.AST._

import java.io.PrintWriter
import java.nio.file.Path
import scala.collection.mutable

case class MarkdownWriter(filePath: Path) {

  private val sb: StringBuilder = new mutable.StringBuilder()

  override def toString: String = sb.toString

  private def mkDirs(): Unit = {
    val dirFile = filePath.getParent.toFile
    if (!dirFile.exists) {dirFile.mkdirs()}
  }

  def write(): Unit = {
    mkDirs()
    val printer = new PrintWriter(filePath.toFile)
    try {
      printer.write(sb.toString())
      printer.flush()
    } finally {printer.close()}
    sb.clear() // release memory because content written to file
  }

  def nl: this.type = {sb.append("\n"); this}

  def fileHead(name: String, weight: Int, desc: Option[String]): this
    .type = {
    val headTemplate =
      s"""---
         |title: "$name"
         |weight: $weight
         |description: "${desc.getOrElse("")}"
         |---
         |""".stripMargin
    sb.append(headTemplate)
    this
  }

  def containerWeight: Int = 2 * 5

  def fileHead(cont: Container[Definition]): this.type = {
    fileHead(cont.id.format, containerWeight,
      Option(cont.brief.fold(cont.id.format + " has no brief description.")(_.s)))
  }

  def fileHead(definition: Definition with BrieflyDescribedValue, weight: Int): this.type = {
    fileHead(definition.id.format, weight,
      Option(definition.brief.fold(definition.id.format + " has no brief description.")(_.s)))
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

  def title(title: String): this.type = {
    sb.append("\n<p style=\"text-align: center;\">\n")
      .append(s"# $title\n")
      .append("</p>\n")
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
      } else {
        sb.append(s"* _${prefix}_: $body\n")
      }
    }

    for {item <- items} {
      item match {
        case (prefix: String, definition: String, description: Option[Description]@unchecked) =>
          emitPair(prefix, definition)
          if (description.nonEmpty) {
            sb.append(description.get.lines.map(line => s"    * ${line.s}\n"))
          }
        case (prefix: String, body: String) =>
          emitPair(prefix, body)
        case (prefix: String, docBlock: Seq[String]@unchecked) =>
          sb.append(s"* $prefix\n")
          docBlock.foreach(s => sb.append(s"    * $s\n"))
        case body: String =>
          sb.append(s"* $body\n")
        case x: Any =>
          sb.append(s"* ${x.toString}\n")
      }
    }
    this
  }

  def list[T](typeOfThing: String, items: Seq[T], emptyMessage: String, level: Int = 2): this.type = {
    heading(typeOfThing, level)
    if (items.isEmpty) {
      p(emptyMessage)
    } else {
      list(items)
    }
    this
  }

  def toc(kindOfThing: String, contents: Map[String, Seq[String]]): this.type = {
    val items = contents.map { case (label, partial) =>
      s"[$label]" -> partial.mkString("(", "/", ")")
    }.toSeq
    list[(String, String)](kindOfThing, items, s"No $kindOfThing defined.")
  }

  private def mkTocSeq(
    list: Seq[Definition],
    prefix: Seq[String]
 ): Seq[(String, Seq[String])] = {
    list.map(c => (c.identify, prefix :+ c.id.format))
  }

  private def mkTocMap(list: Seq[Definition], prefix: Seq[String]): Map[String, Seq[String]] = {
    mkTocSeq(list, prefix).toMap
  }

  def emitFields(fields: Seq[Field]): this.type = {
    list(fields.map { field => (field.id.format, AST.kind(field.typeEx), field.description) })
  }

  def emitBriefly(
    d: Definition with BrieflyDescribedValue,
    parents: Seq[String],
    level: Int = 2
  ) : this.type = {
    if (d.brief.nonEmpty) {
      heading("Briefly", level)
      p(d.brief.fold("Brief description missing.\n")(_.s))
      val path = (parents :+ d.id.format).mkString(".")
      italic(s"Location").p(s": $path at ${d.loc}")
    }
    this
  }

  def emitDetails(d: Option[Description], level: Int = 2): this.type = {
    heading("Details", level)
    val description = d.fold(Seq("No description"))(_.lines.map(_.s))
    description.foreach(p)
    this
  }

  def emitOptions[OT <: OptionValue](options: Seq[OT]): this.type = {
    list("Options",
      options.map(_.format), "No options were defined."
    )
    this
  }

  def emitTypes(types: Seq[Type]): this.type = {
    list[(String, String)]("Types",
      types.map { t =>
        (t.id.format, AST.kind(t.typ) + t.description.fold(Seq.empty[String])(_.lines.map(_.s))
          .mkString("\n  ", "\n  ", ""))
      }, emptyMessage = "No types were defined."
    )
  }

  def emitDomain(cont: Domain, prefix: Seq[String]): this.type = {
    fileHead(cont)
    title(cont.id.format)
    emitBriefly(cont, prefix)
    if (cont.author.nonEmpty) {
      val a = cont.author.get
      val items = Seq(
        "Name" -> a.name.s,
        "Email" -> a.email.s,
      ) ++ a.organization.fold(Seq.empty[(String, String)])(ls => Seq("Organization" -> ls.s)) ++
        a.title.fold(Seq.empty[(String, String)])(ls => Seq("Title" -> ls.s))
      list("Author", items, "")
    }

    emitTypes(cont.types)
    toc("Contexts", mkTocMap(cont.contexts, prefix))
    toc("Stories", mkTocMap(cont.stories, prefix))
    toc("Plants", mkTocMap(cont.plants, prefix))
    toc("Subdomains", mkTocMap(cont.domains, prefix))
    h2("Details")
    val description = cont.description.fold(Seq("No description"))(_.lines.map(_.s))
    description.foreach(p)
    this
  }

  def emitExample(example: Example, parents: Seq[String], level: Int = 2): this.type = {
    val hLevel = if (example.isImplicit) {
      level
    } else {
      heading(example.id.format, level)
      level + 1
    }
    emitBriefly(example, parents, hLevel)
    heading("Gherkin", hLevel)
    if (example.givens.nonEmpty) {
      sb.append(example.givens.map { given =>
        given.scenario.map(_.s).mkString("    *", "\n    *", "\n")
      }.mkString("* GIVEN\n", "* AND\n", ""))
    }
    if (example.whens.nonEmpty) {
      sb.append(example
        .whens
        .map { when => when.condition.format }
        .mkString("* WHEN\n", "* AND\n", ""))
    }
    sb.append(example
      .thens
      .map { then_ => then_.action.format }
      .mkString("* THEN\n    * ", "\n    * ", "\n")
    )
    if (example.buts.nonEmpty) {
      sb.append(example.buts
        .map { but => but.action.format }
        .mkString("* BUT\n    * ", "\n    * ", "\n")
      )
    }
    emitDetails(example.description, hLevel)
    this
  }

  def emitInputOutput(input: Option[Aggregation], output: Option[Aggregation]): this.type = {
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
    emitInputOutput(function.input, function.output)
    h2("Examples")
    val functionExampleLevel = 3
    function.examples.foreach(emitExample(_, parents, functionExampleLevel))
    emitDetails(function.description)
  }

  def emitContext(cont: Context, parents: Seq[String]): this.type = {
    fileHead(cont)
    title(cont.id.format)
    emitBriefly(cont, parents)
    emitOptions(cont.options)
    emitTypes(cont.types)
    toc("Functions", mkTocMap(cont.functions, parents))
    toc("Adaptors", mkTocMap(cont.adaptors, parents))
    toc("Entities", mkTocMap(cont.entities, parents))
    toc("Sagas", mkTocMap(cont.sagas, parents))
    emitDetails(cont.description)
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
    h2("Invariants")
    if (invariants.isEmpty) {
      sb.append("No invariants defined. \n")
    } else {
      invariants.foreach { invariant =>
        h3(invariant.id.format)
        sb.append("* ").append(invariant.expression.format).append("\n")
        emitDetails(invariant.description)
      }
    }
    this
  }

  def emitHandlers(handlers: Seq[Handler], parents: Seq[String]): this.type = {
    h2("Handlers")
    if (handlers.isEmpty) {
      sb.append("No handlers defined.\n")
    } else {
      handlers.foreach { handler =>
        h3(handler.id.format)
        handler.clauses.foreach { clause =>
          h4("On " + clause.msg.format)
          val clauseParents = parents :+ handler.id.format
          emitBriefly(clause, clauseParents )
          h4("Examples")
          clause.examples.foreach(emitExample(_, clauseParents, 5))
          emitDetails(clause.description)
        }
        emitDetails(handler.description)
      }
    }
    this
  }

  def emitEntity(entity: Entity, parents: Seq[String]): this.type = {
    fileHead(entity)
    title(entity.id.format)
    emitBriefly(entity, parents)
    emitOptions(entity.options)
    emitTypes(entity.types)
    emitStates(entity.states,parents)
    emitInvariants(entity.invariants)
    toc("Functions", mkTocMap(entity.functions, parents))
    emitHandlers(entity.handlers, parents)
    emitDetails(entity.description)
  }

  def emitSagaActions(actions: Seq[SagaAction], parents: Seq[String]): this.type = {
    h2("Saga Actions")
    actions.foreach { action =>
      h3(action.id.format)
      emitBriefly(action, parents, 4)
      h4("Messaging")
      list(
        Seq(
          "Entity" -> action.entity.id.format,
          "Do" -> action.doCommand.format,
          "Undo" -> action.undoCommand.format
        )
      )
      p(action.format)
      h4("Examples")
      action.example.foreach(emitExample(_, parents, 5 ))
      emitDetails(action.description,4)
    }
    this
  }
  def emitSaga(saga: Saga, parents: Seq[String]): this.type = {
    fileHead(saga)
    title(saga.id.format)
    emitBriefly(saga, parents)
    emitOptions(saga.options)
    emitInputOutput(saga.input, saga.output)
    emitSagaActions(saga.sagaActions, parents)
    emitDetails(saga.description)
  }

  def emitStory(story: Story, prefix: Seq[String]): this.type = {
    fileHead(story)
    title(story.id.format)
    emitBriefly(story, prefix)
    h2("Story")
    p(s"I, as a ${story.role.s}, want ${story.capability.s}, so that ${story.benefit.s}.")
    list("Visualizations", story.shownBy.map(u => s"($u)[$u]"), "None")
    list("Implemented By", story.implementedBy.map(_.format), "None")
    emitDetails(story.description)
  }

  def emitPlant(plant: Plant, parents: Seq[String]): this.type = {
    fileHead(plant)
    title(plant.id.format)
    emitBriefly(plant, parents)
    // TODO: generate a diagram for the plant
    toc("Processors", mkTocMap(plant.processors, parents))
    list("Pipes", mkTocSeq(plant.pipes, parents), "No pipes defined")
    list("Input Joints", mkTocSeq(plant.inJoints, parents), "No input joints defined. ")
    list("Output Joints", mkTocSeq(plant.outJoints, parents), "No output joints defined.")
    emitDetails(plant.description)
  }

  def emitPipe(pipe: Pipe, parents: Seq[String]): this.type = {
    fileHead(pipe, weight = 20)
    title(pipe.id.format)
    emitBriefly(pipe, parents)
    if (pipe.transmitType.nonEmpty) {
      p(s"Transmission Type: ${pipe.transmitType.get.format} ")
    }
    emitDetails(pipe.description)
  }

  def emitProcessor(proc: Processor, parents: Seq[String]): this.type = {
    fileHead(proc, weight=30)
    title(proc.id.format)
    emitBriefly(proc, parents)
    h2("Inlets")
    proc.inlets.foreach { inlet =>
      h3(inlet.id.format + s": ${inlet.type_.format}")
      emitBriefly(inlet, parents, 4)
      emitDetails(inlet.description,4)
    }
    proc.outlets.foreach { outlet =>
      h3(outlet.id.format + s": ${outlet.type_.format}")
      emitBriefly(outlet, parents, 4)
      emitDetails(outlet.description,4)
    }
    emitDetails(proc.description)
  }

  def emitAdaptor(adaptor: Adaptor, parents: Seq[String]): this.type = {
    fileHead(adaptor)
    title(adaptor.id.format)
    emitBriefly(adaptor, parents)
    p(s"Applicable To: ${adaptor.ref.format}")
    if (adaptor.adaptations.nonEmpty) {
      val adaptations = adaptor.adaptations.map { adaptation =>
        adaptation.id.format -> parents.appended(adaptation.id.format)
      }.toMap
      toc("Adaptations", adaptations)
    }
    emitDetails(adaptor.description)
  }

  def emitAdaptation(adaptation: Adaptation, parents: Seq[String]): this.type = {
    fileHead(adaptation, 20)
    title(adaptation.id.format)
    emitBriefly(adaptation, parents)
    p(s"From event: ${adaptation.event.format}")
    p(s"To command: ${adaptation.command.format}")
    h2("Examples")
    adaptation.examples.foreach(emitExample(_,parents,3))
    emitDetails(adaptation.description)
  }
}

