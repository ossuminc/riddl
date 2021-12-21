package com.yoppworks.ossum.riddl.generation.hugo.rendering

import com.yoppworks.ossum.riddl.generation.hugo._

sealed trait RelativePath {
  def toString: String
  def /(name: String): RelativePath = resolve(name)
  def resolve(name: String): RelativePath
}

object RelativePath {
  def apply(pathStr: String): RelativePath =
    if (pathStr.isEmpty) { Root }
    else { Path(pathStr.split('/').reverse.toList) }

  def apply(parts: Seq[String]): RelativePath =
    if (parts.isEmpty) { Root }
    else { Path(parts.toList.reverse) }

  def of(repr: HugoNode): RelativePath = {

    val filenameFor = repr match {
      case _: HugoDomain  => "_index.md"
      case _: HugoContext => "_index.md"
      case e: HugoEntity  => e.name.toLowerCase + ".md"
      case t: HugoType    => t.name.toLowerCase + ".md"
    }

    /*
    def pathOf(cont: RiddlContainer, acc: List[String]): RelativePath = cont match {
      case _: HugoRoot => apply(acc)
      case dom: HugoDomain => dom.namespace.parent.node match {
          case Some(parent: HugoDomain) => pathOf(parent, dom.name :: acc)
          case _                        => apply(dom.name :: acc)
        }
      case ctx: HugoContext => ctx.namespace.parent.node match {
          case Some(parent: RiddlContainer) => pathOf(parent, ctx.name :: "contexts" :: acc)
          case _                            => apply(ctx.name :: "contexts" :: acc)
        }
      case ent: HugoEntity => ent.namespace.parent.node match {
          case Some(parent: RiddlContainer) => pathOf(parent, "entities" :: acc)
          case _                            => apply("entities" :: acc)
        }
    }

     */

    ???
  }

  final case object Root extends RelativePath {
    override def toString: String = ""
    override def resolve(name: String): RelativePath = Path(List(name))
  }

  private case class Path(parts: List[String]) extends RelativePath {
    override def toString: String = parts.reverse.mkString("/")
    override def resolve(name: String): RelativePath = Path(name :: parts)
  }

}

object PathUtils {}
