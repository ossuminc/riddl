package com.yoppworks.ossum.riddl.generation.hugo

sealed trait Namespace extends Nameable {
  def name: String
  def fullName: String

  def parent: Namespace
  def isRoot: Boolean

  def get(fullName: String): Option[Namespace]
  def get(pathParts: Seq[String]): Option[Namespace]
  def resolveName(name: String): String

  final def toNode: HugoNode = this.asInstanceOf[HugoNode]
}

object Namespace {
  def emptyRoot: Namespace = HugoRoot(_ => Seq.empty[HugoNode])
}

sealed trait HugoNode extends Namespace {
  type Self <: HugoNode

  def name: String
  def fullName: String
  def description: Option[HugoDescription]
  def hasDescription: Boolean = description.nonEmpty

  def parent: HugoNode
  def isRoot: Boolean

  def children: Map[String, HugoNode]
  def hasChildren: Boolean = children.nonEmpty

  def contents: Iterable[HugoNode] = children.values
  def allContents: Iterable[HugoNode] = children.values
    .flatMap(child => Iterable(child) ++ child.allContents)

  def get(fullName: String): Option[HugoNode]
  def get(pathParts: Seq[String]): Option[HugoNode]
  final def resolveName(name: String): String = if (name.isEmpty) fullName else s"$fullName.$name"
}

sealed abstract class NodeBase[Node <: NodeBase[Node]] private[hugo] extends HugoNode {
  type Self = Node

  def get(fullName: String): Option[HugoNode] = get(pathParts(relativize(fullName)))

  def get(pathParts: Seq[String]): Option[HugoNode] = pathParts.foldLeft(Option[HugoNode](this)) {
    case (Some(node), part) => node.children.get(part)
    case (None, _)          => None
  }

  @inline
  private final def relativize(path: String) = path.stripPrefix(fullName)

  @inline
  private final def pathParts(path: String) = path.split('.').toSeq

}

sealed trait RiddlNoDescription extends HugoNode {
  final val description = None
  override final val hasDescription = false
}

case class HugoRoot(builder: HugoNode => Seq[HugoNode])
    extends NodeBase[HugoRoot] with RiddlNoDescription {
  lazy val children = builder(this).map(n => (n.name, n)).toMap
  override val isRoot = true
  override val name = "_root_"
  override val fullName = "_root_"
  override val parent = this
}

sealed abstract class HugoNodeBase[Node <: HugoNodeBase[Node]] private[hugo] (
  name: String,
  builder: HugoNode => Seq[HugoNode])
    extends NodeBase[Node] with HugoNode {
  private lazy val selfTypeName = this.getClass.getSimpleName.stripSuffix("$")
  override def toString: String = s"$selfTypeName($fullName)"

  lazy val children = builder(this).map(n => (n.name, n)).toMap

  def isRoot: Boolean = false

  def fullName: String = if (parent.isRoot) name else s"${parent.fullName}.$name"
}

sealed trait NoChildrenNode[Node <: HugoNodeBase[Node]] {
  self: HugoNodeBase[Node] =>
  override val hasChildren = false
  override lazy val children = Map.empty[String, Node]
  override val allContents = Iterable.empty[Node]
}

final case class HugoDomain(
  name: String,
  parent: HugoNode,
  description: Option[HugoDescription] = None
)(builder: HugoNode => Seq[HugoNode])
    extends HugoNodeBase[HugoDomain](name, builder) {
  def contexts: Iterable[HugoContext] = contents.collect { case hc: HugoContext => hc }
  def domains: Iterable[HugoDomain] = contents.collect { case hd: HugoDomain => hd }
  def types: Iterable[HugoType] = contents.collect { case ht: HugoType => ht }
}

object HugoDomain {
  def empty(
    name: String,
    parent: HugoNode,
    description: Option[HugoDescription]
  ): HugoDomain = HugoDomain(name, parent, description)(_ => Seq.empty)
}

final case class HugoContext(
  name: String,
  parent: HugoNode,
  description: Option[HugoDescription] = None
)(builder: HugoNode => Seq[HugoNode])
    extends HugoNodeBase[HugoContext](name, builder) {
  def entities: Iterable[HugoEntity] = contents.collect { case he: HugoEntity => he }
  def types: Iterable[HugoType] = contents.collect { case ht: HugoType => ht }
}

object HugoContext {
  def empty(
    name: String,
    parent: HugoNode,
    description: Option[HugoDescription]
  ): HugoContext = HugoContext(name, parent, description)(_ => Seq.empty)
}

final case class HugoEntity(
  name: String,
  parent: HugoNode,
  options: HugoEntity.Options,
  states: Set[HugoEntity.State],
  handlers: Set[HugoEntity.Handler],
  functions: Set[HugoEntity.Function],
  invariants: Set[HugoEntity.Invariant],
  description: Option[HugoDescription] = None
)(builder: HugoNode => Seq[HugoNode])
    extends HugoNodeBase[HugoEntity](name, builder) {
  def types: Iterable[HugoType] = contents.collect { case ht: HugoType => ht }
}

object HugoEntity {

  def empty(
    name: String,
    parent: HugoNode,
    options: HugoEntity.Options,
    states: Set[HugoEntity.State],
    handlers: Set[HugoEntity.Handler],
    functions: Set[HugoEntity.Function],
    invariants: Set[HugoEntity.Invariant],
    description: Option[HugoDescription] = None
  ): HugoEntity =
    HugoEntity(name, parent, options, states, handlers, functions, invariants, description)(_ =>
      Seq.empty
    )

  type Options = EntityOption.ValueSet
  object EntityOption extends Enumeration {
    val none: Options = ValueSet.empty
    val EventSourced, ValueType, Aggregate, Persistent, Consistent, Available, FiniteStateMachine =
      Value
  }

  case class State(fullName: String, fields: Set[HugoField]) extends NameableFromFullName

  case class Handler(fullName: String, clauses: Seq[OnClause]) extends NameableFromFullName

  sealed trait OnClause {
    def onType: RiddlType
  }
  object OnClause {
    case class Command(onType: RiddlType) extends OnClause
    case class Event(onType: RiddlType) extends OnClause
    case class Query(onType: RiddlType) extends OnClause
    case class Action(onType: RiddlType) extends OnClause
  }

  case class Function(fullName: String, inputs: Set[HugoField], output: RiddlType)
      extends NameableFromFullName

  case class Invariant(fullName: String, expression: List[String]) extends NameableFromFullName
}

case class HugoType(
  name: String,
  parent: HugoNode,
  typeDef: RiddlType,
  description: Option[HugoDescription] = None)
    extends HugoNodeBase[HugoType](name, _ => Seq.empty) with NoChildrenNode[HugoType]
