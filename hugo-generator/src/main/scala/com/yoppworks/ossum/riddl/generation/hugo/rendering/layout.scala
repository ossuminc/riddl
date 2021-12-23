package com.yoppworks.ossum.riddl.generation.hugo.rendering

import com.yoppworks.ossum.riddl.generation.hugo._

import scala.annotation.tailrec

sealed trait HugoContent {
  def outputFilePath: RelativePath
  def fileContents: String
}

final case class HugoTemplate(
  template: TemplateFile,
  outputFilePath: RelativePath,
  replacements: Map[String, String])
    extends HugoContent {
  def fileContents: String = template.replaceAll(replacements.toSeq: _*).toString
}

final case class TypesPlaceholder(relativePath: RelativePath) extends HugoContent {
  val outputFilePath = relativePath / "_index.md"
  def fileContents: String = PrinterEndo.empty.println("---").println("title: \"**Types**\"")
    .println("weight: -5").println("geekdocCollapseSection: true").println("---").toString
}

final case class ContextsPlaceholder(relativePath: RelativePath) extends HugoContent {
  val outputFilePath = relativePath / "_index.md"
  def fileContents: String = PrinterEndo.empty.println("---").println("title: \"**Contexts**\"")
    .println("weight: 0").println("---").toString
}

final case class EntitiesPlaceholder(relativePath: RelativePath) extends HugoContent {
  val outputFilePath = relativePath / "_index.md"
  def fileContents: String = PrinterEndo.empty.println("---").println("title: \"**Entities**\"")
    .println("weight: 0").println("geekdocCollapseSection: false").println("---").toString
}

/** [[HugoLayout]] creates the layout and contents for the Hugo `content` folder */
object HugoLayout {

  def apply(root: HugoRoot, projectName: String): List[HugoContent] = staticContent(projectName) ++
    gatherContent(root.contents.toSeq)

  private def staticContent(projectName: String): List[HugoContent] = List(HugoTemplate(
    Templates.forFile("config").fold(throw _, identity),
    RelativePath("../config.toml"),
    Map("projectName" -> projectName)
  ))

  @tailrec
  private def gatherContent(
    toLayout: Seq[HugoNode],
    acc: List[HugoContent] = List.empty
  ): List[HugoContent] = toLayout match {
    case (_: HugoRoot) +: tail => gatherContent(tail, acc)
    case (dom: HugoDomain) +: tail =>
      gatherContent(tail ++ dom.contents.toSeq, acc ++ layoutHugoDomain(dom))
    case (ctx: HugoContext) +: tail =>
      gatherContent(tail ++ ctx.contents.toSeq, acc ++ layoutHugoContext(ctx))
    case (ent: HugoEntity) +: tail =>
      gatherContent(tail ++ ent.contents.toSeq, acc ++ layoutHugoEntity(ent))
    case (tpe: HugoType) +: tail => gatherContent(tail, acc ++ layoutHugoType(tpe))
    case Nil                     => acc
  }

  // HugoTypes just generate a single '{typeName}.md' markdown file
  private def layoutHugoType(in: HugoType): List[HugoContent] = Renderer.render(in).toList

  // A HugoEntity generates a single '{entityName}.md' markdown file
  private def layoutHugoEntity(in: HugoEntity): List[HugoContent] = {
    val contextFolder = RelativePath.of(in).parent
    @inline
    def childFolder(name: String) = contextFolder / name

    val entityFile = Renderer.render(in).toList
    val typesMarker =
      oneOrEmpty { if (in.hasTypes) Some(TypesPlaceholder(childFolder("types"))) else None }

    entityFile ++ typesMarker
  }

  // A HugoContext can generate multiple files/folders
  private def layoutHugoContext(in: HugoContext): List[HugoContent] = {
    val contextFolder = RelativePath.of(in).parent
    @inline
    def childFolder(name: String) = contextFolder / name

    val contextFile = Renderer.render(in).toList
    val typesMarker =
      oneOrEmpty { if (in.hasTypes) Some(TypesPlaceholder(childFolder("types"))) else None }
    val entitiesMarker = oneOrEmpty {
      if (in.hasEntities) Some(EntitiesPlaceholder(childFolder("entities"))) else None
    }

    contextFile ++ typesMarker ++ entitiesMarker
  }

  // A HugoDomain can generate multiple files/folders
  private def layoutHugoDomain(in: HugoDomain): List[HugoContent] = {
    val contextFolder = RelativePath.of(in).parent
    @inline
    def childFolder(name: String) = contextFolder / name

    val domainFile = Renderer.render(in).toList
    val typesMarker =
      oneOrEmpty { if (in.hasTypes) Some(TypesPlaceholder(childFolder("types"))) else None }
    val contextsMarker = oneOrEmpty {
      if (in.hasContexts) Some(ContextsPlaceholder(childFolder("contexts"))) else None
    }

    domainFile ++ typesMarker ++ contextsMarker
  }

  @inline
  private final def oneOrEmpty[A](opt: Option[A]): List[A] = opt.toList
}
