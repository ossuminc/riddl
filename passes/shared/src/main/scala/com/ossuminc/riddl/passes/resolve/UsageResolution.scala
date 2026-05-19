/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.resolve

import com.ossuminc.riddl.utils.{CommonOptions, PlatformContext}
import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Messages

import scala.collection.mutable
import scala.reflect.ClassTag

trait UsageBase {

  type UseMap = mutable.HashMap[Definition, mutable.Set[Definition]]
  type UsedByMap = mutable.HashMap[Definition, mutable.Set[Definition]]
  type UseInPathMap = mutable.HashMap[Definition, mutable.Set[Definition]]
  type UsedInPathByMap = mutable.HashMap[Definition, mutable.Set[Definition]]

  protected val uses: UseMap = mutable.HashMap.empty[Definition, mutable.Set[Definition]]
  protected val usedBy: UsedByMap = mutable.HashMap.empty[Definition, mutable.Set[Definition]]

  /** Tracks references that appear as intermediate components in a
    * [[com.ossuminc.riddl.language.AST.PathIdentifier]] — e.g.,
    * `R` in `set field R.f to "1"`. Distinct from
    * `uses`/`usedBy` (which record the final resolved target and
    * direct type references). Used by `checkUnused` to suppress
    * the "is unused" warning and by the new path-only completeness
    * check.
    */
  protected val usesInPath: UseInPathMap =
    mutable.HashMap.empty[Definition, mutable.Set[Definition]]
  protected val usedInPathBy: UsedInPathByMap =
    mutable.HashMap.empty[Definition, mutable.Set[Definition]]
}

/** Validation State for Uses/UsedBy Tracking. During parsing, when usage is detected, call associateUsage. After
  * parsing ends, call checkUnused. Collects entities, types and functions too
  */
trait UsageResolution(using io: PlatformContext) extends UsageBase {

  protected def messages: Messages.Accumulator

  protected var entities: Seq[Entity] = Seq.empty[Entity]

  def addEntity(entity: Entity): this.type = {
    entities = entities :+ entity
    this
  }

  protected var types: Seq[Type] = Seq.empty[Type]

  def addType(ty: Type): this.type = {
    types = types :+ ty
    this
  }

  protected var functions: Seq[Function] = Seq.empty[Function]

  def addFunction(fun: Function): this.type = {
    functions = functions :+ fun
    this
  }

  protected var repositories: Seq[Repository] = Seq.empty[Repository]

  def addRepository(repo: Repository): this.type = {
    repositories = repositories :+ repo
    this
  }

  def associateUsage[T <: Definition: ClassTag](user: Definition, resolution: Resolution[T]): Resolution[T] =
    resolution match
      case None => None
      case resolution @ Some((use: Definition, _)) =>
        associateUsage(user, use)
        resolution
    end match
  end associateUsage

  def associateUsage(user: Definition, use: Definition): this.type =
    uses.getOrElseUpdate(user, mutable.Set.empty[Definition]).add(use)
    usedBy.getOrElseUpdate(use, mutable.Set.empty[Definition]).add(user)
    this
  end associateUsage

  /** Record `use` as appearing in a path identifier authored within `user`
    * (i.e., an anchor or intermediate component, not the final resolved
    * target). Mirrors [[associateUsage]] but writes to the separate
    * `usesInPath` / `usedInPathBy` maps.
    *
    * Self-references are skipped: a definition referencing itself via its
    * own name (e.g., `state AState of fooBar.fields` inside `entity fooBar`)
    * does not count as external usage.
    */
  def associatePathUsage(user: Definition, use: Definition): this.type =
    if user ne use then
      usesInPath.getOrElseUpdate(user, mutable.Set.empty[Definition]).add(use)
      usedInPathBy.getOrElseUpdate(use, mutable.Set.empty[Definition]).add(user)
    this
  end associatePathUsage

  def checkUnused(): this.type = {
    if io.options.showUsageWarnings then {
      def hasDirectUsage(d: Definition): Boolean =
        usedBy.get(d).exists(_.nonEmpty)
      def hasPathUsage(d: Definition): Boolean =
        usedInPathBy.get(d).exists(_.nonEmpty)
      def checkList(definitions: Seq[Definition]): Unit = {
        for defn <- definitions if !hasDirectUsage(defn) && !hasPathUsage(defn) do {
          messages.addUsage(defn.errorLoc, s"${defn.identify} is unused")
        }
      }
      checkList(entities)
      checkList(types)
      checkList(functions)
      checkList(repositories)
    }

    // Path-only-used types: directly addressable by path but never declared
    // as the type of a field or state. Emit a CompletenessWarning so the
    // author knows the type can't actually carry data.
    if io.options.showCompletenessWarnings then {
      for ty <- types
          if usedBy.get(ty).forall(_.isEmpty)
          if usedInPathBy.get(ty).exists(_.nonEmpty)
      do {
        messages.addCompleteness(
          ty.errorLoc,
          s"${ty.identify} is only referenced in path identifiers; " +
            "for it to be a useful addressable type, it should be used " +
            "as the type of a field or state."
        )
      }
    }
    this
  }
}
