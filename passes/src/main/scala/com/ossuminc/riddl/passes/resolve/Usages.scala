/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.resolve

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.utils

import scala.collection.mutable

/** The data generated from the [[ResolutionPass]] that provides usage information about which
  * definitions use others and vice versa. The data collected
  *
  * @param uses
  *   A hashmap with a [[com.ossuminc.riddl.language.AST.Definition]] as the key and the list of
  *   [[com.ossuminc.riddl.language.AST.Definition]]s it uses
  * @param usedBy
  *   A hashmap with a [[com.ossuminc.riddl.language.AST.Definition]] as the key and the list of
  *   [[com.ossuminc.riddl.language.AST.Definition]] used by it
  */
case class Usages(
  override protected val uses: UsageBase#UseMap,
  override protected val usedBy: UsageBase#UsedByMap,
  override protected val usesInPath: UsageBase#UseInPathMap,
  override protected val usedInPathBy: UsageBase#UsedInPathByMap
) extends UsageBase {

  def usesSize: Int = uses.size
  def usedBySize: Int = usedBy.size

  /** Determine if a definition is used or not */
  def isUsed(definition: Definition): Boolean = {
    uses.contains(definition)
  }

  /** True iff `definition` appears as an anchor or intermediate component in at least one path
    * identifier.
    */
  def isUsedInPath(definition: Definition): Boolean =
    usedInPathBy.get(definition).exists(_.nonEmpty)

  /** Definitions whose body contains a path identifier that references `used` as an anchor or
    * intermediate component (not as a final resolved target).
    */
  def getPathUsers(used: Definition): Seq[Definition] =
    usedInPathBy.getOrElse(used, mutable.Set.empty).toSeq

  /** Determine if one definition is used by another
    *
    * @param used
    *   The definition that is used
    * @param user
    *   The definition that does the using
    * @return
    *   True iff `user` uses `used`
    */
  def isUsedBy(used: Definition, user: Definition): Boolean = {
    usedBy.get(used) match {
      case Some(list) if list.contains(user) => true
      case _                                 => false
    }
  }

  /** Determine if one definition is using another
    *
    * @param user
    *   The definition that uses
    * @param used
    *   The definition that is used
    * @return
    *   True iff `user` uses `used`
    */
  def uses(user: Definition, used: Definition): Boolean = {
    val usage = this.uses.get(user)
    usage.exists(list => list.contains(used))
  }

  /** Retrieve the list of users that use a [[com.ossuminc.riddl.language.AST.Definition]]
    *
    * @param used
    *   The [[com.ossuminc.riddl.language.AST.Definition]] being used
    * @return
    *   The [[scala.Seq]] of [[com.ossuminc.riddl.language.AST.Definition]] that are using `used`
    */
  def getUsers(used: Definition): Seq[Definition] = {
    usedBy.getOrElse(used, mutable.Set.empty).toSeq
  }

  /** Retrieve the uses of a given user
    *
    * @param user
    *   The [[com.ossuminc.riddl.language.AST.Definition]] that is the user
    * @return
    *   The [[scala.Seq]] of [[com.ossuminc.riddl.language.AST.Definition]] that are used by `user`
    */
  def getUses(user: Definition): Seq[Definition] = {
    uses.getOrElse(user, mutable.Set.empty).toSeq
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

  /** Used for validity checks to make sure that the users are used by the usages */
  def verifyReflective: Boolean = {
    def reflective(
      forward: mutable.HashMap[Definition, mutable.Set[Definition]],
      reverse: mutable.HashMap[Definition, mutable.Set[Definition]]
    ): Boolean =
      (for
        (user, user_uses) <- forward
        use <- user_uses
      yield reverse.keySet.contains(use) && reverse(use).contains(user))
        .forall(identity)

    reflective(uses, usedBy) && reflective(usesInPath, usedInPathBy)
  }
}

object Usages {
  val empty: Usages = Usages(
    mutable.HashMap.empty,
    mutable.HashMap.empty,
    mutable.HashMap.empty,
    mutable.HashMap.empty
  )
}
