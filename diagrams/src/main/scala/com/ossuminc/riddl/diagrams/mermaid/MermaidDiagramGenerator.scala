package com.ossuminc.riddl.diagrams.mermaid

import com.ossuminc.riddl.utils.FileBuilder
import com.ossuminc.riddl.language.AST.*

/** Common trait for things that generate mermaid diagrams */
trait MermaidDiagramGenerator extends FileBuilder {

  def generate: Seq[String] = toLines

  def title: String

  def kind: String

  protected def frontMatterItems: Map[String, String]

  final protected def frontMatter(): Unit = {
    addLine("---")
    addLine(s"title: $title")
    addLine("init:")
    addLine("    theme: dark")
    addLine(s"$kind:")
    append(frontMatterItems.map(x => x._1 + ": " + x._2).mkString("    ", "\n    ", "\n"))
    addLine("---\n")
  }

  protected def getCssFor(definition: Definition): String = {
    val maybeStrings: Option[Seq[String]] = definition match {
      case a: Adaptor     => a.getOptionValue[AdaptorCssOption].map(l => l.map(_.s))
      case p: Application => p.getOptionValue[ApplicationCssOption].map(list => list.map(_.s))
      case p: Connector   => p.getOptionValue[ConnectorCssOption].map(list => list.map(_.s))
      case c: Context     => c.getOptionValue[ContextCssOption].map(list => list.map(_.s))
      case d: Domain      => d.getOptionValue[DomainCssOption].map(list => list.map(_.s))
      case e: Entity      => e.getOptionValue[EntityCssOption].map(list => list.map(_.s))
      case p: Epic        => p.getOptionValue[EpicCssOption].map(list => list.map(_.s))
      case p: Projector   => p.getOptionValue[ProjectorCssOption].map(list => list.map(_.s))
      case p: Repository  => p.getOptionValue[RepositoryCssOption].map(list => list.map(_.s))
      case p: Saga        => p.getOptionValue[SagaCssOption].map(list => list.map(_.s))
      case p: Streamlet   => p.getOptionValue[StreamletCssOption].map(list => list.map(_.s))
      case _              => Option.empty[Seq[String]]
    }
    maybeStrings.map(_.mkString(",")).getOrElse("")

  }

  protected def getIconFor(definition: Definition): String = {
    val maybeStrings: Option[Seq[String]] = definition match {
      case a: Adaptor     => a.getOptionValue[AdaptorIconOption].map(l => l.map(_.s))
      case p: Application => p.getOptionValue[ApplicationIconOption].map(list => list.map(_.s))
      case p: Connector   => p.getOptionValue[ConnectorIconOption].map(list => list.map(_.s))
      case c: Context     => c.getOptionValue[ContextIconOption].map(list => list.map(_.s))
      case d: Domain      => d.getOptionValue[DomainIconOption].map(list => list.map(_.s))
      case e: Entity      => e.getOptionValue[EntityIconOption].map(list => list.map(_.s))
      case p: Epic        => p.getOptionValue[EpicIconOption].map(list => list.map(_.s))
      case p: Projector   => p.getOptionValue[ProjectorIconOption].map(list => list.map(_.s))
      case p: Repository  => p.getOptionValue[RepositoryIconOption].map(list => list.map(_.s))
      case p: Saga        => p.getOptionValue[SagaIconOption].map(list => list.map(_.s))
      case p: Streamlet   => p.getOptionValue[StreamletIconOption].map(list => list.map(_.s))
      case _              => Option.empty[Seq[String]]
    }
    maybeStrings.map(_.mkString(",")).getOrElse("")
  }

  protected def getTechnology(definition: Definition): String = {
    val maybeStrings: Option[Seq[String]] = definition match {
      case a: Adaptor     => a.getOptionValue[AdaptorTechnologyOption].map(l => l.map(_.s))
      case p: Application => p.getOptionValue[ApplicationTechnologyOption].map(list => list.map(_.s))
      case p: Connector   => p.getOptionValue[ConnectorTechnologyOption].map(list => list.map(_.s))
      case c: Context     => c.getOptionValue[ContextTechnologyOption].map(list => list.map(_.s))
      case d: Domain      => d.getOptionValue[DomainTechnologyOption].map(list => list.map(_.s))
      case e: Entity      => e.getOptionValue[EntityTechnologyOption].map(list => list.map(_.s))
      case p: Epic        => p.getOptionValue[EpicTechnologyOption].map(list => list.map(_.s))
      case p: Projector   => p.getOptionValue[ProjectorTechnologyOption].map(list => list.map(_.s))
      case p: Repository  => p.getOptionValue[RepositoryTechnologyOption].map(list => list.map(_.s))
      case p: Saga        => p.getOptionValue[SagaTechnologyOption].map(list => list.map(_.s))
      case p: Streamlet   => p.getOptionValue[StreamletTechnologyOption].map(list => list.map(_.s))
      case _              => Option.empty[Seq[String]]
    }
    maybeStrings.map(_.mkString(", ")).getOrElse("Arbitrary Technology")
  }
}
