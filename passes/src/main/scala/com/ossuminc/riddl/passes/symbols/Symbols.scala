/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.ossuminc.riddl.passes.symbols

import com.ossuminc.riddl.language.AST.{Definition, NamedValue}

import scala.collection.mutable

/** Some common types associated with the AST names of things for brevity; and, used widely*/
object Symbols {
  type Parent = Definition
  type Parents = Seq[Parent]
  type Parentage = mutable.HashMap[NamedValue, Parents]
  type ParentStack = mutable.Stack[Definition]
  type PathNames = Seq[String]
  type SymTabItem = (NamedValue, Parents)
  type SymTabItems = Seq[SymTabItem]
  type SymTab = mutable.HashMap[String, SymTabItems]

  val emptySymTab = mutable.HashMap.empty[String, SymTabItems]
  val emptyParentage = mutable.HashMap.empty[NamedValue, Parents]
}
