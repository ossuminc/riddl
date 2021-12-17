package com.yoppworks.ossum.riddl.generation.hugo

sealed trait Namespace {
  def name: String
  def fullName: String
  def parent: Namespace
  def isRoot: Boolean
  def directChildren: Iterable[Namespace]
  def allChildren: Iterable[Namespace]
  def node: Option[HugoRepr]
  private[hugo] def setNode(value: HugoRepr): Namespace
  def get(name: String): Option[Namespace]
  def getOrCreate(name: String): Namespace
  // Force implementations to handle `toString`
  def toString: String
  // Same impl
  final def resolve(name: String): String = s"$fullName.$name"
  final def hugoNodes: Iterable[HugoRepr] = directChildren.view.map(_.node)
    .collect { case Some(hugoRepr) => hugoRepr }

}

object Namespace {
  final def emptyRoot: Namespace = new NodeImpl("_root_")

  final def unapply(ns: Namespace): Option[(Option[HugoRepr], Namespace, String)] = {
    val ni = ns.asInstanceOf[NodeImpl]
    Some((ni.node, ni.parent, ni.name))
  }

  private final class NodeImpl(
    val name: String,
    var node: Option[HugoRepr],
    _parent: Option[NodeImpl])
      extends Namespace {
    def this(_name: String) = this(_name, None, None)
    def this(name: String, parent: NodeImpl) = this(name, None, Some(parent))
    val parent = _parent.fold[Namespace](this)(identity)
    def setNode(value: HugoRepr): Namespace = {
      this.node = Some(value)
      this
    }
    private val childrenInternal = scala.collection.mutable.Map.empty[String, NodeImpl]
    def directChildren: Iterable[Namespace] = childrenInternal.values
    override def allChildren: Iterable[Namespace] = childrenInternal.values.flatMap { child =>
      Iterable(child) ++ child.allChildren
    }

    override def toString: String =
      if (childrenInternal.isEmpty) { s"$name" }
      else { s"$name.{${directChildren.mkString(", ")}}" }

    val isRoot: Boolean = _parent.isEmpty
    def fullName: String =
      if (parent.isRoot) { s"$name" }
      else { s"${parent.fullName}.$name" }

    def get(name: String): Option[Namespace] = pathParts(relativize(name)).foldLeft(Option(this)) {
      case (Some(node), part) => node.childrenInternal.get(part)
      case (None, _)          => None
    }

    def getOrCreate(name: String): Namespace = {
      assert(name.nonEmpty, "name cannot be empty")
      val nameParts = pathParts(relativize(name))
      val (heads, last) = (nameParts.dropRight(1), nameParts.last)
      val insertionParent = heads.foldLeft(this) { case (node, part) =>
        node.childrenInternal.get(part) match {
          case Some(child) => child
          case None        => addChildNode(node, part)
        }
      }
      insertionParent.childrenInternal.get(last) match {
        case Some(node) => node
        case None =>
          val child = new NodeImpl(last, insertionParent)
          insertionParent.childrenInternal.addOne(last -> child)
          child
      }
    }

    @inline
    private def relativize(path: String) = path.stripPrefix(fullName)

    @inline
    private def pathParts(path: String) = path.split('.').toSeq

    private def addChildNode(node: NodeImpl, childName: String) = {
      val child = new NodeImpl(childName, node)
      node.childrenInternal.addOne(childName -> child)
      child
    }
  }

}
