/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.resolve

import com.ossuminc.riddl.language.AST.*

import scala.collection.mutable

/** Unit Tests For Usages */
case class Usages(
  override protected val uses: UsageBase#UseMap,
  override protected val usedBy: UsageBase#UsedByMap
) extends UsageBase {

  def usesSize: Int = uses.size
  def usedBySize: Int = usedBy.size

  def isUsed(definition: Definition): Boolean = {
    uses.keys.exists(_ == definition)
  }

  def isUsedBy(used: Definition, user: Definition): Boolean = {
    usedBy.get(used) match {
      case Some(list) if list.contains(user) => true
      case _                                 => false
    }
  }

  def uses(user: Definition, used: NamedValue): Boolean = {
    this.uses.get(user).exists(list => list.contains(used))
  }

  def getUsers(used: NamedValue): Seq[Definition] = {
    usedBy.getOrElse(used, Seq.empty)
  }

  def getUses(user: Definition): Seq[NamedValue] = {
    uses.getOrElse(user, Seq.empty)
  }

  def usesAsString: String = {
    uses
      .map { case (key, value) =>
        s"${key.identify} => ${value.map(_.identify).mkString(",")}"
      }
      .mkString("\n")
  }

  def usedByAsString: String = {
    usedBy
      .map { case (key, value) =>
        s"${key.identify} <= ${value.map(_.identify).mkString(",")}"
      }
      .mkString("\n")
  }

  def verifyReflective: Boolean = {
    // ensure usedBy and uses are reflective
    (for
      (user, user_uses) <- uses
      use <- user_uses
    yield {
      usedBy.keySet.contains(use) && usedBy(use).contains(user)
    }).forall { identity }
  }
}

object Usages {
  val empty: Usages = Usages(mutable.HashMap.empty, mutable.HashMap.empty)
}
