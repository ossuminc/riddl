/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.resolve

import com.ossuminc.riddl.language.AST.*

import scala.collection.mutable
import scala.reflect.{ClassTag, classTag}

/** Mapping from implementation class to the instances of definitions */
case class KindMap() {

  private val map: mutable.HashMap[Class[?], Seq[Definition]] = mutable.HashMap.empty

  def size: Int = map.size

  def add(definition: Definition): Unit = {
    val clazz = definition.getClass
    val existing = map.getOrElse(clazz, Seq.empty)
    map.update(clazz, existing :+ definition)
  }

  def definitionsOfKind[T <: Definition : ClassTag]: Seq[T] = {
    val TClass = classTag[T].runtimeClass
    map.getOrElse(TClass, Seq.empty[T]).map(_.asInstanceOf[T])
  }
}

object KindMap {
  val empty: KindMap = KindMap()
}
