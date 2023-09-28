/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.passes.resolve

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.Messages
import com.reactific.riddl.utils.StringHelpers

import scala.collection.mutable
import scala.reflect.{ClassTag, classTag}

/** A mapping from reference to definition */
case class ReferenceMap(messages: Messages.Accumulator) {

  private case class Key(path: String, in: Definition) {
    override def toString: String = s"(k=$path,v=${in.identify})"
  }

  private val map: mutable.HashMap[Key, Definition] = mutable.HashMap.empty

  override def toString: String = {
    StringHelpers.toPrettyString(this)
  }

  def size: Int = map.size

  def add[T <: Definition: ClassTag](ref: Reference[T], parent: Definition, definition: T): Unit = {
    add(ref.pathId.format, parent, definition)
  }

  def add[T <: Definition: ClassTag](pathId: PathIdentifier, parent: Definition, definition: T): Unit = {
    add(pathId.format, parent, definition)
  }

  private def add[T <: Definition: ClassTag](pathId: String, parent: Definition, definition: T): Unit = {
    val key = Key(pathId, parent)
    val expected = classTag[T].runtimeClass
    val actual = definition.getClass
    require(
      expected.isAssignableFrom(actual),
      s"referenced ${actual.getSimpleName} is not assignable to expected ${expected.getSimpleName}"
    )
    map.update(key, definition)
  }

  def definitionOf[T <: Definition: ClassTag](pid: PathIdentifier, parent: Definition): Option[T] = {
    val key = Key(pid.format, parent)
    val value = map.get(key)
    value match
      case None =>
        None
      case Some(x: T) =>
        Some(x)
      case Some(x) =>
        val className = classTag[T].runtimeClass.getSimpleName
        messages.addError(pid.loc, s"Path Id '${pid.format} found ${x.identify} but a ${className} was expected")
        None
  }
}

object ReferenceMap {
  val empty: ReferenceMap = ReferenceMap(Messages.Accumulator.empty)
}
