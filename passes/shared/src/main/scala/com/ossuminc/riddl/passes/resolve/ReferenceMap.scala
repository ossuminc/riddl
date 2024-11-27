/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.resolve

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.utils.{PlatformContext, StringHelpers}

import scala.collection.mutable
import scala.reflect.{ClassTag, classTag}

/** The primary output of the [[ResolutionPass]]. It provides a mapping from a reference to the referenced definition.
  * This is useful for fast resolution during validation and other Passes because the resolution logic doesn't need to
  * be exercised again.
  * @param messages
  *   A message accumulator for collecting messages when member functions are invoked
  */
case class ReferenceMap(messages: Messages.Accumulator) {

  private case class Key(path: String, in: Definition) {
    override def toString: String = s"(k=$path,v=${in.identify})"
  }

  private val map: mutable.HashMap[Key, Definition] = mutable.HashMap.empty

  override def toString: String = {
    StringHelpers.toPrettyString(this) ++ StringHelpers.toPrettyString(map)
  }

  def size: Int = map.size

  def add[T <: Definition: ClassTag](ref: Reference[T], parent: Parent, definition: T): Unit = {
    add(ref.pathId.format, parent, definition)
  }

  def add[T <: Definition: ClassTag](pathId: PathIdentifier, parent: Parent, definition: T): Unit = {
    add(pathId.format, parent, definition)
  }

  private def add[T <: Definition: ClassTag](pathId: String, parent: Parent, definition: T): Unit = {
    val key = Key(pathId, parent)
    val expected = classTag[T].runtimeClass
    val actual = definition.getClass
    require(
      expected.isAssignableFrom(actual),
      s"referenced ${actual.getSimpleName} is not assignable to expected ${expected.getSimpleName}"
    )
    map.update(key, definition)
  }

  def definitionOf[T <: Definition: ClassTag](pathId: PathIdentifier)(using PlatformContext): Option[T] = {
    definitionOf[T](pathId.format)
  }

  def definitionOf[T <: Definition: ClassTag](pathId: String): Option[T] = {
    val potentials = map.find(key => key._1.path == pathId)
    potentials match
      case None => Option.empty[T]
      case Some((_, definition)) =>
        val klass = classTag[T].runtimeClass
        if definition.getClass == klass then Some(definition.asInstanceOf[T]) else Option.empty[T]
  }

  def definitionOf[T <: Definition: ClassTag](pid: PathIdentifier, parent: Definition)(using PlatformContext): Option[T] = {
    val key = Key(pid.format, parent)
    val value = map.get(key)
    value match
      case None =>
        None
      case Some(x: T) =>
        Some(x)
      case Some(x) =>
        val className = classTag[T].runtimeClass.getSimpleName
        messages.addError(pid.loc, s"Path Id '${pid.format} found ${x.identify} but a $className was expected")
        None
  }

  def definitionOf[T <: Definition: ClassTag](ref: Reference[T], parent: Parent)(using PlatformContext): Option[T] = {
    definitionOf[T](ref.pathId, parent)
  }
}

object ReferenceMap {
  def empty: ReferenceMap = ReferenceMap(Messages.Accumulator.empty)
}
