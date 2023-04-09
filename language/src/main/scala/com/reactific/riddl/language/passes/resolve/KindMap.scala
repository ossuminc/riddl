package com.reactific.riddl.language.passes.resolve

import com.reactific.riddl.language.AST.*

import scala.collection.mutable
import scala.reflect.{ClassTag, classTag}

/** Unit Tests For KindMap */
case class KindMap() {

  private val map: mutable.HashMap[Class[_], Seq[Definition]] = mutable.HashMap.empty

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
