package com.reactific.riddl.passes.resolve
import scala.collection.mutable

import com.reactific.riddl.language.AST.*

case class TypeMap() {

  private val map: mutable.HashMap[Value, TypeExpression] = mutable.HashMap.empty
}
