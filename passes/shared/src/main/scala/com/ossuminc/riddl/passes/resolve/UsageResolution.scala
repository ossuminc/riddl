/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.resolve

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.{CommonOptions, Messages}

import scala.collection.mutable
import scala.reflect.ClassTag

trait UsageBase {

  type UseMap = mutable.HashMap[Definition, Seq[Definition]]
  type UsedByMap = mutable.HashMap[Definition, Seq[Definition]]

  protected val uses: UseMap = mutable.HashMap.empty[Definition, Seq[Definition]]
  protected val usedBy: UsedByMap = mutable.HashMap.empty[Definition, Seq[Definition]]
}

/** Validation State for Uses/UsedBy Tracking. During parsing, when usage is detected, call associateUsage. After
  * parsing ends, call checkUnused. Collects entities, types and functions too
  */
trait UsageResolution extends UsageBase {

  def commonOptions: CommonOptions

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

  def associateUsage[T <: Definition: ClassTag](user: Definition, resolution: Resolution[T]): Resolution[T] =
    resolution match
      case None => None
      case resolution @ Some((use: Definition, _)) => 
        associateUsage(user, use)
        resolution 
    end match
  end associateUsage
    
  def associateUsage(user: Definition, use: Definition): this.type =
    val used = uses.getOrElse(user, Seq.empty[Definition])
    if !used.contains(use) then
      uses.update(user, used :+ use)

    val usages = usedBy.getOrElse(use, Seq.empty[Definition])
    if !usages.contains(user) then
      usedBy.update(use, usages :+ user)
    this
  end associateUsage

  def checkUnused(): this.type = {
    if commonOptions.showUsageWarnings then {
      def hasUsages(definition: Definition): Boolean = {
        val result = usedBy.get(definition) match {
          case None        => false
          case Some(users) => users.nonEmpty
        }
        result
      }
      def checkList(definitions: Seq[Definition]): Unit = {
        for defn <- definitions if !hasUsages(defn) do {
          messages.addUsage(defn.loc, s"${defn.identify} is unused")
        }
      }
      checkList(entities)
      checkList(types)
      checkList(functions)
    }
    this
  }
}
