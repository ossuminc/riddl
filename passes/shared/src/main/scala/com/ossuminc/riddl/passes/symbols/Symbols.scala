/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.ossuminc.riddl.passes.symbols

import com.ossuminc.riddl.language.AST.{Definition, Parent, Parents, WithIdentifier}
import com.ossuminc.riddl.utils

import scala.collection.mutable

/** Some common types associated with the AST names of things for brevity; and, used widely */
object Symbols {
  type Parentage = mutable.HashMap[Definition, Parents]
  type PathNames = Seq[String]
  type SymTabItem = (Definition, Parents)
  type SymTabItems = Seq[SymTabItem]
  type SymTab = mutable.HashMap[String, SymTabItems]

  val emptySymTab = mutable.HashMap.empty[String, SymTabItems]
  val emptyParentage = mutable.HashMap.empty[Definition, Parents]
}
