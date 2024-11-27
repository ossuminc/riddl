/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.hugo.writers

import com.ossuminc.riddl.diagrams.mermaid
import com.ossuminc.riddl.language.AST
import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.parsing.{Keyword, Keywords}
import com.ossuminc.riddl.hugo.themes.ThemeGenerator
import com.ossuminc.riddl.language.parsing.Keywords.*
import com.ossuminc.riddl.utils.{PlatformContext, TextFileWriter}

import scala.annotation.unused
import scala.collection.immutable.Seq

/** Base */
trait MarkdownWriter(using pc: PlatformContext)
    extends MarkdownBasics
    with AdaptorWriter
    with ContextWriter
    with DomainWriter
    with EntityWriter
    with EpicWriter
    with FunctionWriter
    with ProjectorWriter
    with RepositoryWriter
    with SagaWriter
    with StreamletWriter
    with ModuleWriter
    with SummariesWriter {

  def generator: ThemeGenerator

  private case class Level(name: String, href: String, children: Seq[Level]):
    override def toString: String =
      s"{name:\"$name\",href:\"$href\",children:[${children.map(_.toString).mkString(",")}]}"

  def makeRootIndex(root: Root, indent: Int = 0): Unit =
    for { topLevelDomain <- root.domains.sortBy(_.id.value) } do makeDomainIndex(topLevelDomain, indent)
  end makeRootIndex

  private def makeDomainIndex(domain: Domain, indent: Int = 0): Unit =
    val link = generator.makeDocLink(domain)
    val spaces = " ".repeat(indent)
    p(s"$spaces* [${domain.identify}]($link)")
    for { nestedDomain <- AST.getDomains(domain).sortBy(_.id.value) } do makeDomainIndex(nestedDomain, indent + 2)
    for { epic <- AST.getEpics(domain).sortBy(_.id.value) } do {
      val link = generator.makeDocLink(epic)
      val spaces = " ".repeat(indent + 2)
      p(s"$spaces* [${epic.identify}]($link)")
    }
    for { context <- AST.getContexts(domain).sortBy(_.id.value) } do {
      val link = generator.makeDocLink(context)
      val spaces = " ".repeat(indent + 2)
      p(s"$spaces* [${context.identify}]($link)")
      for { entity <- AST.getEntities(context).sortBy(_.id.value) } do {
        val link = generator.makeDocLink(entity)
        val spaces = " ".repeat(indent + 4)
        p(s"$spaces* [${entity.identify}]($link)")
      }
    }
  end makeDomainIndex

  private def makeData(container: Parent, parents: Seq[String]): Level =
    Level(
      container.identify,
      generator.makeDocLink(container, parents),
      children = {
        val newParents = container.id.value +: parents
        container.contents.parents
          .filter(d => d.nonEmpty && !d.isInstanceOf[OnMessageClause])
          .map(makeData(_, newParents))
      }
    )
  end makeData

  def emitC4ContainerDiagram(
    definition: Context,
    parents: Parents
  ): Unit = {
    val name = definition.identify
    val brief = { (defn: Definition) => toBriefString(defn) }

    val heading =
      s"""C4Context
         |  title C4 Containment Diagram for [$name]
         |""".stripMargin.split('\n').toSeq

    val containers = parents.filter(_.isContainer).reverse
    val systemBoundaries = containers.zipWithIndex
    val openedBoundaries = systemBoundaries.map { case (dom: Definition, n) =>
      val nm = dom.id.format
      val keyword = if n == 0 then "Enterprise_Boundary" else "System_Boundary"
      " ".repeat((n + 1) * 2) + s"$keyword($nm,$nm,\"${brief(dom)}\") {"
    }
    val closedBoundaries = systemBoundaries.reverse.map { case (_, n) =>
      " ".repeat((n + 1) * 2) + "}"
    }
    val prefix = " ".repeat(parents.size * 2)
    val context_head = prefix +
      s"Boundary($name, $name, \"${brief(definition)}\") {"
    val context_foot = prefix + "}"

    val body = definition.entities.map(e => prefix + s"  System(${e.id.format}, ${e.id.format}, \"${brief(e)}\")")
    val lines: Seq[String] = heading ++ openedBoundaries ++ Seq(context_head) ++
      body ++ Seq(context_foot) ++ closedBoundaries
    emitMermaidDiagram(lines)
  }

  def emitTerms(terms: Seq[Term]): Unit = {
    list(
      "Terms",
      terms.map(t => (t.id.format, t.briefString, t.descriptions.headOption.getOrElse("No description")))
    )
  }

  protected def emitFields(fields: Seq[Field]): Unit = {
    list(fields.map { field =>
      (field.id.format, field.typeEx.format, field.briefString, field.descriptionString)
    })
  }

  private def toBriefString(definition: Definition): String =
    definition match
      case wab: WithMetaData => wab.briefString
      case _                     => "Brief description missing."
    end match
  end toBriefString

  private def emitBriefParagraph(definition: Definition): Unit =
    definition match
      case wab: WithMetaData => p(italic(wab.briefString))
      case _                     => p("Brief description missing.")
    end match
  end emitBriefParagraph

  private def emitDescriptionParagraphs(definition: Definition): Unit =
    definition match
      case wad: WithMetaData => wad.descriptions.foreach(d => p(d.lines.mkString("\n")))
      case _                     => ()
    end match
  end emitDescriptionParagraphs

  protected def emitVitalDefTable(
    definition: Definition,
    parents: Parents
  ): Unit = {
    emitTableHead(Seq("Item" -> 'C', "Value" -> 'L'))

    val brief: String = toBriefString(definition).trim
    emitTableRow(italic("Briefly"), brief)
    if definition.isVital then {
      val parent = definition.asInstanceOf[VitalDefinition[?]]
      val authors: Seq[Author] = parent.authorRefs.flatMap { (authorRef: AuthorRef) =>
        generator.refMap.definitionOf[Author](authorRef.pathId, parent)
      }
      emitTableRow(italic("Authors"), authors.map(_.name.format).mkString(", "))
    }
    val path = (parents.map(_.id.value) :+ definition.id.value).mkString(".")
    emitTableRow(italic("Definition Path"), path)
    val link = generator.makeSourceLink(definition)
    emitTableRow(italic("View Source Link"), s"[${definition.loc}]($link)")

    val users: String = generator.usage.getUsers(definition) match {
      case users: Seq[Definition] if users.nonEmpty => users.map(_.identify).mkString(", ")
      case _                                        => "None"
    }
    emitTableRow(italic("Used By"), users)
    val uses = generator.usage.getUses(definition) match {
      case uses: Seq[Definition] if uses.nonEmpty => uses.map(_.identify).mkString(", ")
      case _                                      => "None"
    }
    emitTableRow(italic("Uses"), uses)
  }

  // This substitutions domain contains referent referenced

  private final val definition_keywords: Seq[String] = Seq(
    Keyword.adaptor,
    Keyword.author,
    Keyword.case_,
    Keyword.command,
    Keyword.connector,
    Keyword.constant,
    Keyword.context,
    Keyword.entity,
    Keyword.epic,
    Keyword.field,
    Keyword.flow,
    Keyword.function,
    Keyword.group,
    Keyword.handler,
    Keyword.inlet,
    Keyword.input,
    Keyword.invariant,
    Keyword.outlet,
    Keyword.output,
    Keyword.pipe,
    Keyword.projector,
    Keyword.query,
    Keyword.replica,
    Keyword.reply,
    Keyword.repository,
    Keyword.record,
    Keyword.result,
    Keyword.saga,
    Keyword.sink,
    Keyword.source,
    Keyword.state,
    Keyword.streamlet,
    Keyword.term,
    Keyword.user
  )

  private val keywords: String = definition_keywords.mkString("(", "|", ")")
  private val pathIdRegex = s" ($keywords) (\\w+(\\.\\w+)*)".r

  private def substituteIn(lineToReplace: String): String = {
    val matches = pathIdRegex.findAllMatchIn(lineToReplace).toSeq.reverse
    matches.foldLeft(lineToReplace) { case (line, rMatch) =>
      val kind = rMatch.group(1)
      val pathId = rMatch.group(3)

      def doSub(line: String, definition: Definition, isAmbiguous: Boolean = false): String = {
        val docLink = generator.makeDocLink(definition)
        val substitution =
          if isAmbiguous then s"($kind $pathId (ambiguous))[$docLink]"
          else s" ($kind $pathId)[$docLink]"
        line.substring(0, rMatch.start) + substitution + line.substring(rMatch.end)
      }

      generator.refMap.definitionOf[Definition](pathId) match {
        case Some(definition) => doSub(line, definition)
        case None =>
          val names = pathId.split('.').toSeq
          generator.symbolsOutput.lookupSymbol[Definition](names) match
            case Nil                => line
            case ::((head, _), Nil) => doSub(line, definition = head)
            case ::((head, _), _)   => doSub(line, definition = head, isAmbiguous = true)
      }
    }
  }

  def emitDescriptions(descriptions: Seq[Description], level: Int = 2): this.type =
    heading("Description", level)
    val substitutedDescription: Seq[String] = for {
      desc <- descriptions
      line <- desc.lines.map(_.s)
      newLine = substituteIn(line)
    } yield {
      newLine
    }
    substitutedDescription.foreach(p)
    this
  end emitDescriptions

  protected def emitOptions[OT <: OptionValue](
    options: Seq[OT],
    level: Int = 2
  ): this.type = {
    list("RiddlOptions", options.map(_.format), level)
    this
  }

  protected def emitDefDoc(
    definition: Definition,
    parents: Parents,
    level: Int = 2
  ): this.type = {
    emitVitalDefTable(definition, parents)
    definition match
      case wad: WithMetaData => emitDescriptions(wad.descriptions, level)
      case _                     => this
  }

  protected def emitShortDefDoc(
    definition: Definition
  ): this.type = {
    emitBriefParagraph(definition)
    emitDescriptionParagraphs(definition)
    this
  }

  private def makePathIdRef(
    pid: PathIdentifier,
    parents: Parents
  ): String = {
    parents.headOption match
      case None => ""
      case Some(parent) =>
        val resolved = generator.refMap.definitionOf[Definition](pid, parent)
        resolved match
          case None => s"unresolved path: ${pid.format}"
          case Some(res) =>
            val slink = generator.makeSourceLink(res)
            resolved match
              case None => s"unresolved path: ${pid.format}"
              case Some(definition) =>
                val pars = generator.makeStringParents(parents.drop(1))
                val link = generator.makeDocLink(definition, pars)
                s"[${resolved.head.identify}]($link) [{{< icon \"gdoc_code\" >}}]($slink)"
  }

  private def makeTypeName(
    pid: PathIdentifier,
    parents: Parents
  ): String = {
    parents.headOption match
      case None => s"unresolved path: ${pid.format}"
      case Some(parent) =>
        generator.refMap.definitionOf[Definition](pid, parent) match {
          case None                         => s"unresolved path: ${pid.format}"
          case Some(definition: Definition) => definition.id.format
        }
  }

  protected def makeTypeName(
    typeEx: TypeExpression,
    parents: Parents
  ): String = {
    val name = typeEx match {
      case AliasedTypeExpression(_, _, pid)      => makeTypeName(pid, parents)
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
    parents: Parents
  ): String = {
    typeEx match {
      case a: AliasedTypeExpression =>
        s"Alias of ${makePathIdRef(a.pathId, parents)}"
      case er: EntityReferenceTypeExpression =>
        s"Entity reference to ${makePathIdRef(er.entity, parents)}"
      case uid: UniqueId =>
        s"Unique identifier for entity ${makePathIdRef(uid.entityPath, parents)}"
      case alt: Alternation =>
        val data = alt.of.map { (te: AliasedTypeExpression) =>
          makePathIdRef(te.pathId, parents)
        }
        s"Alternation of: " + data.mkString(", ")
      case agg: Aggregation =>
        val data = agg.fields.map { (f: Field) =>
          (f.id.format, resolveTypeExpression(f.typeEx, parents))
        }
        "Aggregation of:" + data.mkString(", ")
      case mt: AggregateUseCaseTypeExpression =>
        val data = mt.fields.map { (f: Field) =>
          (f.id.format, resolveTypeExpression(f.typeEx, parents))
        }
        s"${mt.usecase.useCase} message of: " + data.mkString(", ")
      case _ => typeEx.format
    }
  }

  private def emitAggregateMembers(agg: AggregateTypeExpression, parents: Parents): this.type = {
    val data = agg.contents.map {
      case f: AggregateValue => (f.id.format, resolveTypeExpression(f.typeEx, parents))
    }.toSeq
    list(data.filterNot(t => t._1.isEmpty && t._2.isEmpty))
    this
  }

  private def emitTypeExpression(
    typeEx: TypeExpression,
    parents: Parents,
    headLevel: Int = 2
  ): Unit = {
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
        val data = alt.of.toSeq.map { (te: AliasedTypeExpression) =>
          makePathIdRef(te.pathId, parents)
        }
        list(data)
      case agg: Aggregation =>
        heading("Aggregation Of", headLevel)
        emitAggregateMembers(agg, parents)
      case mt: AggregateUseCaseTypeExpression =>
        heading(s"${mt.usecase} Of", headLevel)
        emitAggregateMembers(mt, parents)
      case map: Mapping =>
        heading("Mapping Of", headLevel)
        val from = resolveTypeExpression(map.from, parents)
        val to = resolveTypeExpression(map.to, parents)
        p(s"From:\n: $from").nl
        p(s"To:\n: $to")
      case en: Enumeration =>
        heading("Enumeration Of", headLevel)
        list(en.enumerators.toSeq.map(_.id.format))
      case Pattern(_, strs) =>
        heading("Pattern Of", headLevel)
        list(strs.map("`" + _.s + "`"))
      case _ =>
        heading("Type", headLevel)
        p(resolveTypeExpression(typeEx, parents))
    }
  }

  private def emitType(typ: Type, parents: Parents): Unit = {
    h4(typ.identify)
    emitDefDoc(typ, parents)
    emitTypeExpression(typ.typEx, typ +: parents)
  }

  protected def emitTypes(types: Seq[Type], parents: Parents, level: Int = 2): Unit = {
    val groups = types
      .groupBy { typ =>
        typ.typEx match
          case mt: AggregateUseCaseTypeExpression  => mt.usecase.toString + " "
          case AliasedTypeExpression(_, _, _)      => "Alias "
          case EntityReferenceTypeExpression(_, _) => "Reference "
          case _: NumericType                      => "Numeric "
          case PredefinedType(_)                   => "Predefined "
          case _                                   => "Structural"
        end match
      }
      .toSeq
      .sortBy(_._2.size)
    heading("Types", level)
    for {
      (label, list) <- groups
    } do {
      heading(label + " Types", level + 1)
      for typ <- list do emitType(typ, parents)
    }
  }

  private def emitConstants(constants: Seq[Constant], parents: Parents): Unit = {
    h2("Constants")
    for { c <- constants } do
      emitDefDoc(c, parents)
      p(s"* type:  ${c.typeEx.format}")
      p(s"* value: ${c.value.format}")
  }

  protected def emitAuthorInfo(authors: Seq[Author], level: Int = 2): this.type = {
    for a <- authors do {
      val items = Seq("Name" -> a.name.s, "Email" -> a.email.s) ++
        a.organization.fold(Seq.empty[(String, String)])(ls => Seq("Organization" -> ls.s)) ++
        a.title.fold(Seq.empty[(String, String)])(ls => Seq("Title" -> ls.s))
      list("Author", items, level)
    }
    this
  }

  protected def emitInputOutput(
    input: Option[Aggregation],
    output: Option[Aggregation]
  ): this.type = {
    if input.nonEmpty then
      h4("Requires (Input)")
      emitFields(input.get.fields)
    if output.nonEmpty then
      h4("Returns (Output)")
      output match
        case None      =>
        case Some(agg) => emitFields(agg.fields)
    this
  }

  private def emitFunctions(functions: Seq[Function], parents: Parents): Unit = {
    h2("Functions")
    for { f <- functions } do {
      emitFunction(f, parents)
    }
  }

  protected def emitInvariants(invariants: Seq[Invariant]): this.type = {
    if invariants.nonEmpty then {
      h2("Invariants")
      invariants.foreach { invariant =>
        h3(invariant.id.format)
        list(invariant.condition.map(_.format).toSeq)
        emitDescriptions(invariant.descriptions, level = 4)
      }
    }
    this
  }

  private def emitHandlers(
    handlers: Seq[Handler],
    parents: Parents
  ): Unit = {
    h3("Handlers")
    for { h <- handlers } do {
      emitHandler(h, parents)
    }
  }

  private def emitInlet(inlet: Inlet, parents: Parents): Unit = {
    emitDefDoc(inlet, parents, 3)
    val typeRef = makePathIdRef(inlet.type_.pathId, parents)
    p(s"Receives type $typeRef")
  }

  protected def emitInlets(inlets: Seq[Inlet], parents: Parents): Unit = {
    h2("Inlets")
    for { i <- inlets } do {
      emitInlet(i, parents)
    }
  }

  private def emitOutlet(outlet: Outlet, parents: Parents): Unit = {
    emitDefDoc(outlet, parents, 3)
    val typeRef = makePathIdRef(outlet.type_.pathId, parents)
    p(s"Transmits type $typeRef")
  }

  protected def emitOutlets(outlets: Seq[Outlet], parents: Parents): Unit = {
    h2("Outlets")
    for { o <- outlets } do {
      emitOutlet(o, parents)
    }
  }

  protected def emitVitalDefinitionDetails[CT <: RiddlValue](vd: VitalDefinition[CT], stack: Parents): Unit = {
    h2(vd.identify)
    emitDefDoc(vd, stack)
    emitOptions(vd.options)
    emitTerms(vd.terms)
  }

  protected def emitProcessorDetails[CT <: RiddlValue](processor: Processor[CT], stack: Parents): Unit = {
    val parents: Parents = processor +: stack
    if processor.types.nonEmpty then emitTypes(processor.types, parents)
    if processor.constants.nonEmpty then emitConstants(processor.constants, parents)
    if processor.functions.nonEmpty then emitFunctions(processor.functions, parents)
    if processor.invariants.nonEmpty then emitInvariants(processor.invariants)
    if processor.handlers.nonEmpty then emitHandlers(processor.handlers, parents)
    if processor.streamlets.nonEmpty then processor.streamlets.foreach(emitStreamlet(_, parents))
  }
}
