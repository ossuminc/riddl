/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language

import com.ossuminc.riddl.language.AST.{
  BASTImport,
  Branch,
  Container,
  Definition,
  Definitions,
  Include,
  Processor,
  RiddlValue,
  VitalDefinition,
  WithIdentifier
}

import scala.collection.{SeqFactory, immutable, mutable}
import scala.reflect.{ClassTag, classTag}

/** A representation of the editable contents of a definition
  * @tparam CV
  *   The upper bound of the values that can be contained (RiddlValue)
  */
opaque type Contents[CV <: RiddlValue] = mutable.ArrayBuffer[CV]

/** Companion object for Contents opaque type. */
object Contents:
  def dempty[T <: RiddlValue]: Contents[T] = new mutable.ArrayBuffer[T](2)
  def empty[T <: RiddlValue](
    initialSize: Int = mutable.ArrayBuffer.DefaultInitialSize
  ): Contents[T] =
    new mutable.ArrayBuffer[T](initialSize)
  def apply[T <: RiddlValue](items: T*): Contents[T] = mutable.ArrayBuffer[T](items: _*)
  def unapply[T <: RiddlValue](contents: Contents[T]): SeqFactory.UnapplySeqWrapper[T] =
    mutable.ArrayBuffer.unapplySeq[T](contents)

  extension [CV <: RiddlValue](container: Contents[CV]) def apply(n: Int): CV = container.apply(n)
end Contents

extension [CV <: RiddlValue](sequence: Seq[CV])
  def toContents: Contents[CV] = Contents[CV](sequence: _*)
  def find(name: String): Option[CV] =
    sequence.find(d =>
      d.isInstanceOf[WithIdentifier] && d.asInstanceOf[WithIdentifier].id.value == name
    )

extension [CV <: RiddlValue](container: Contents[CV])
  def length: Int = container.length
  def size: Int = container.length
  def head: CV = container(0)
  def indexOf[B >: CV](elem: B): Int = container.indexOf[B](elem, 0)
  def splitAt(n: Int): (Contents[CV], Contents[CV]) = container.splitAt(n)
  def indices: Range = Range(0, container.length)
  def foreach[T](f: CV => T): Unit = container.foreach(f)
  def forall(p: CV => Boolean): Boolean = container.forall(p)
  def update(index: Int, elem: CV): Unit = container.update(index, elem)
  def foldLeft[B](z: B)(op: (B, CV) => B): B = container.foldLeft[B](z)(op)
  def isEmpty: Boolean = container.isEmpty
  def nonEmpty: Boolean = !isEmpty
  def mapValue[B <: RiddlValue](f: CV => B): Contents[B] = container.map[B](f)
  def flatMap[B <: RiddlValue](f: CV => IterableOnce[B]): Contents[B] =
    container.flatMap[B](f)
  def startsWith[B >: CV](that: IterableOnce[B], offset: Int = 0): Boolean =
    container.startsWith[B](that)
  def toSet[B >: CV <: RiddlValue]: immutable.Set[B] = immutable.Set.from(container)
  def toSeq: immutable.Seq[CV] = container.toSeq
  def toIterator: Iterator[CV] = container.toIterator
  def dropRight(howMany: Int): Contents[CV] = container.dropRight(howMany)
  def drop(howMany: Int): Contents[CV] = container.drop(howMany)
  def clear(): Unit = container.clear()
  def remove(index: Int): CV = container.remove(index)
  def append(elem: CV): Unit = container.append(elem)
  def prepend(elem: CV): Unit = container.prepend(elem)
  def +=(elem: CV): Contents[CV] = { container.addOne(elem); container }
  def ++=(suffix: IterableOnce[CV]): Contents[CV] = { container.addAll(suffix); container }
  def ++(suffix: IterableOnce[CV]): Contents[CV] =
    container.concat[CV](suffix).asInstanceOf[Contents[CV]]
  private def identified: Contents[CV] = container.filter(_.isIdentified)
  def filter[T <: RiddlValue: ClassTag]: Seq[T] =
    val theClass = classTag[T].runtimeClass
    container.filter(x => theClass.isAssignableFrom(x.getClass)).map(_.asInstanceOf[T]).toSeq
  end filter

  /** Like [[filter]], but descends through the provenance wrappers -- `Include` and `BASTImport`
    * -- before matching. The same two [[flatten]] removes.
    *
    * HOW a definition got into a container is riddl's business, not its reader's. An author who
    * writes `include "Campaign.riddl"` or `import domain X from "lib.bast"` is saying where the
    * text or artifact lives, not that `Campaign` is one level further from the context than its
    * siblings. A tool asking "what is in this context" wants the whole list; asking it to
    * distinguish direct from included from imported is asking it to care about a distinction it
    * has no stake in.
    *
    * It descends through NOTHING ELSE. An entity of a nested context is not an entity of THIS
    * context, which is why this is not `Finder.recursiveFindByType` -- that walks every
    * `Container` and would over-report where this used to under-report.
    *
    * The tree is never mutated, so the wrappers survive for the tooling that DOES have a stake in
    * provenance: PrettifyPass multi-file mode writes definitions back to the files they came from,
    * and `BASTLoader.getImports` still finds imports in their wrapper. Reporting and structure are
    * separate concerns -- this changes what is reported, [[flatten]] changes what is there.
    *
    * NOTE: a wrapper is matched BEFORE the type test, so this is the wrong tool for finding the
    * wrappers themselves -- [[includes]] deliberately stays on [[filter]].
    */
  def filterThroughWrappers[T <: RiddlValue: ClassTag]: Seq[T] =
    val theClass = classTag[T].runtimeClass
    def loop(items: Seq[RiddlValue]): Seq[T] =
      items.flatMap {
        case inc: Include[?]                            => loop(inc.contents.toSeq)
        case bi: BASTImport                             => loop(bi.contents.toSeq)
        case x if theClass.isAssignableFrom(x.getClass) => Seq(x.asInstanceOf[T])
        case _                                          => Seq.empty
      }
    end loop
    loop(container.toSeq)
  end filterThroughWrappers

  // The next three stay on the LITERAL `filter` deliberately, unlike the 35 named accessors in
  // AST.scala. They are consumed by riddl's own passes rather than by tools reading a model, and
  // those callers already reach included definitions by another route -- so making these
  // include-transparent would double count, not fix anything:
  //   - `processors`  -> DiagramsPass adds `processor.includes.toContents.processors` itself
  //   - `definitions` -> ResolutionPass feeds `include.contents.definitions` in as separate
  //                      candidates, alongside the enclosing scope's own
  //   - `vitals`      -> StatsPass counts while traversing, which already visits include contents
  // If one of these ever needs to answer a consumer's question, give it the transparent
  // treatment AND remove the caller's manual walk in the same change.
  def vitals: Seq[VitalDefinition[?]] = container.filter[VitalDefinition[?]]
  def processors: Seq[Processor[?]] = container.filter[Processor[?]]
  def find(name: String): Option[CV] =
    identified.find(d =>
      d.isInstanceOf[WithIdentifier] && d.asInstanceOf[WithIdentifier].id.value == name
    )
  def identifiedValues: Seq[WithIdentifier] =
    container
      .filter(d => d.isInstanceOf[WithIdentifier])
      .map(_.asInstanceOf[WithIdentifier])
      .toSeq
  def includes: Seq[Include[?]] = container.filter[Include[?]].map(_.asInstanceOf[Include[?]])
  def definitions: Definitions = container.filter[Definition].map(_.asInstanceOf[Definition])
  def parents: Seq[Branch[CV]] = container.filter[Branch[CV]]
end extension

extension [CV <: RiddlValue, CV2 <: RiddlValue](container: Contents[CV])
  def merge(other: Contents[CV2]): Contents[RiddlValue] =
    val result = Contents.empty[RiddlValue](container.size + other.size)
    result ++= container
    result ++= other
    result
  end merge
end extension

extension [CV <: RiddlValue](container: Container[CV])
  /** Recursively flatten Include and BASTImport nodes, promoting their children to the parent
    * container. This is a one-way, irreversible, in-place operation.
    */
  def flatten(): Unit =
    val items = container.contents
    val flattened = mutable.ArrayBuffer.empty[RiddlValue]
    items.foreach:
      case include: Include[?] =>
        include.contents.foreach(child => flattened += child)
      case bi: BASTImport =>
        bi.contents.foreach(child => flattened += child)
      case other =>
        flattened += other
    items.clear()
    flattened.foreach: item =>
      items.asInstanceOf[mutable.ArrayBuffer[RiddlValue]] += item
    // Recurse into child containers
    items.foreach:
      case child: Container[?] => child.flatten()
      case _                   => ()
  end flatten
end extension
