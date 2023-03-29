package com.reactific.riddl.language.passes.resolution

import scala.collection.mutable
import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.Messages

import scala.reflect.{ClassTag, classTag}

/** A mapping from reference to definition */
case class ReferenceMap(messages: Messages.Accumulator) {

  private val map: mutable.HashMap[(String,Definition), Definition] = mutable.HashMap.empty

  def add[T <: Definition: ClassTag](ref: Reference[T], parent: Definition, definition: T): Unit = {
    add(ref.pathId, parent, definition)
  }

  def add[T <: Definition : ClassTag](pathId: PathIdentifier, parent: Definition, definition: T): Unit = {
    add(pathId.value.mkString, parent, definition)
  }

  def add[T <: Definition : ClassTag]( pathId: String, parent: Definition, definition: T ): Unit = {
    val expected = classTag[T].runtimeClass
    val actual = definition.getClass
    require(expected.isAssignableFrom(actual),
      s"referenced ${actual.getSimpleName} is not assignable to expected ${expected.getSimpleName}")
    map.update(pathId -> parent, definition)
  }

  def definitionOf[T <: Definition](pid: PathIdentifier, parent: Definition): Option[T] = {
    map.get(pid.value.mkString -> parent).map(_.asInstanceOf[T])
  }
}
