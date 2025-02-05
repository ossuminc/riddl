/*
 * Copyright 2019-2025 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.symbols

import com.ossuminc.riddl.language.AST.{Definition, Branch, Parents, WithIdentifier}
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

  extension(symTab: SymTab)
    def toPrettyString: String =
      val sb = new mutable.StringBuilder
      symTab.toSeq.sortBy(_._1).map {
        case (key: String, sti: SymTabItems) =>
          sb.append(key).append(" -> \n")
          for { item <- sti } yield {
            sb.append("  ")
              .append(item._1.kind).append(": ")
              .append(item._1.id.value).append(".")
              .append(item._2.map(_.id.value).mkString("."))
              .append("\n")
          }
      }
      sb.toString

}
