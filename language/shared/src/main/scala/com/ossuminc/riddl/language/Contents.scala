/*
 * Copyright 2019-2026 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language

import com.ossuminc.riddl.language.AST.{Branch, Definition, Definitions, Include, Processor, RiddlValue, VitalDefinition, WithIdentifier}

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

  extension [CV <: RiddlValue](container: Contents[CV])
    inline def apply(n: Int): CV = container.apply(n)
end Contents

extension [CV <: RiddlValue](sequence: Seq[CV])
  def toContents: Contents[CV] = Contents[CV](sequence: _*)
  def find(name: String): Option[CV] =
    sequence.find(d =>
      d.isInstanceOf[WithIdentifier] && d.asInstanceOf[WithIdentifier].id.value == name
    )

extension [CV <: RiddlValue](container: Contents[CV])
  inline def length: Int = container.length
  inline def size: Int = container.length
  inline def head: CV = container(0)
  inline def indexOf[B >: CV](elem: B): Int = container.indexOf[B](elem, 0)
  inline def splitAt(n: Int): (Contents[CV], Contents[CV]) = container.splitAt(n)
  inline def indices: Range = Range(0, container.length)
  inline def foreach[T](f: CV => T): Unit = container.foreach(f)
  inline def forall(p: CV => Boolean): Boolean = container.forall(p)
  inline def update(index: Int, elem: CV): Unit = container.update(index, elem)
  inline def foldLeft[B](z: B)(op: (B, CV) => B): B = container.foldLeft[B](z)(op)
  inline def isEmpty: Boolean = container.isEmpty
  inline def nonEmpty: Boolean = !isEmpty
  inline def mapValue[B <: RiddlValue](f: CV => B): Contents[B] = container.map[B](f)
  inline def flatMap[B <: RiddlValue](f: CV => IterableOnce[B]): Contents[B] =
    container.flatMap[B](f)
  inline def startsWith[B >: CV](that: IterableOnce[B], offset: Int = 0): Boolean =
    container.startsWith[B](that)
  def toSet[B >: CV <: RiddlValue]: immutable.Set[B] = immutable.Set.from(container)
  def toSeq: immutable.Seq[CV] = container.toSeq
  def toIterator: Iterator[CV] = container.toIterator
  inline def dropRight(howMany: Int): Contents[CV] = container.dropRight(howMany)
  inline def drop(howMany: Int): Contents[CV] = container.drop(howMany)
  inline def append(elem: CV): Unit = container.append(elem)
  inline def prepend(elem: CV): Unit = container.prepend(elem)
  inline def +=(elem: CV): Contents[CV] = { container.addOne(elem); container }
  inline def ++=(suffix: IterableOnce[CV]): Contents[CV] = { container.addAll(suffix); container }
  inline def ++(suffix: IterableOnce[CV]): Contents[CV] =
    container.concat[CV](suffix).asInstanceOf[Contents[CV]]
  private def identified: Contents[CV] = container.filter(_.isIdentified)
  def filter[T <: RiddlValue: ClassTag]: Seq[T] =
    val theClass = classTag[T].runtimeClass
    container.filter(x => theClass.isAssignableFrom(x.getClass)).map(_.asInstanceOf[T]).toSeq
  end filter
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
