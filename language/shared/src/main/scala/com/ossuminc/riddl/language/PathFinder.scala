/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language

import com.ossuminc.riddl.language.AST.*

import scala.collection.immutable.HashMap
import scala.collection.mutable 
// import scala.math.Ordered.orderingToOrdered

def findPaths (root: Root): HashMap[Definition, ParentStack] = {
  val map : mutable.HashMap[Definition,ParentStack] = mutable.HashMap.empty
  @scala.annotation.tailrec
  def traverse(
    toVisit: List[(Definition, ParentStack)]
  ): Unit = toVisit match {
    case Nil => map
    case (node: Definition, parentStack: ParentStack) :: rest =>
      val newStack = parentStack.clone()
      map += (node -> newStack)
      val newToVisit: List[(Definition,ParentStack)] =
        if node.hasDefinitions & node.isInstanceOf[Branch[RiddlValue]] then
          val branch = node.asInstanceOf[Branch[RiddlValue]]
          branch.contents.definitions.toList.map(definition => (definition, newStack)) ++ rest
        else
          List.empty[(Definition, ParentStack)]
        end if
      traverse(newToVisit)
  }
  traverse(List((root, ParentStack.empty)))
  map.toMap.asInstanceOf[HashMap[Definition, ParentStack]]
}
