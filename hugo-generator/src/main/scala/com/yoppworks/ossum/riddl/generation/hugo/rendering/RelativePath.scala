package com.yoppworks.ossum.riddl.generation.hugo.rendering

import com.yoppworks.ossum.riddl.generation.hugo._

import scala.annotation.tailrec

sealed trait RelativePath {
  def toString: String
  def /(name: String): RelativePath = resolve(name)
  def isFile: Boolean
  def isDirectory: Boolean = !isFile
  def parent: RelativePath
  def resolve(name: String): RelativePath
  def pathParts: Seq[String]
  def absParts: Seq[String]
}

object RelativePath {
  def apply(pathStr: String): RelativePath =
    if (pathStr.isBlank) { Root }
    else { Path(pathStr.split('/').reverse.toList) }

  def apply(parts: Seq[String]): RelativePath =
    if (parts.isEmpty) { Root }
    else { Path(parts.toList.reverse) }

  def of(node: HugoNode): RelativePath = {

    val filenameFor = node match {
      case _: HugoRoot    => "_index.md"
      case _: HugoDomain  => "_index.md"
      case _: HugoContext => "_index.md"
      case e: HugoEntity  => "_index.md"
      case t: HugoType    => t.name.toLowerCase + ".md"
    }

    @inline
    def nameOf(node: HugoNode): String = node.name.toLowerCase

    @tailrec
    def pathOf(node: HugoNode, acc: List[String]): RelativePath = node match {
      case _: HugoRoot      => apply(acc)
      case dom: HugoDomain  => pathOf(dom.parent, nameOf(dom) :: acc)
      case ctx: HugoContext => pathOf(ctx.parent, "contexts" :: nameOf(ctx) :: acc)
      case ent: HugoEntity  => pathOf(ent.parent, "entities" :: nameOf(ent) :: acc)
      case tpe: HugoType    => pathOf(tpe.parent, "types" :: acc)
    }

    pathOf(node, List.empty) / filenameFor
  }

  final case object Root extends RelativePath {
    override def toString: String = ""
    def isFile: Boolean = false
    def parent: RelativePath = this
    def resolve(name: String): RelativePath = Path(List(name))
    def pathParts: Seq[String] = Seq.empty
    def absParts: Seq[String] = Seq.empty
  }

  private final case class Path(parts: List[String]) extends RelativePath {
    override def toString: String = parts.reverse.mkString("/")
    def isFile: Boolean = parts.headOption.exists(_.contains('.'))
    def parent: RelativePath = parts.drop(1) match {
      case Nil  => Root
      case tail => Path(tail)
    }

    def resolve(name: String): RelativePath = Path(name :: parts)
    def pathParts: Seq[String] = parts.reverse
    def absParts: Seq[String] = parts match {
      case heads :+ last => s"/$last" :: heads.reverse
      case otherwise     => otherwise
    }
  }

}
