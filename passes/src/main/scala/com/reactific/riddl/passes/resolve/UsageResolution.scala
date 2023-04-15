package com.reactific.riddl.passes.resolve

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.{CommonOptions, Messages}

import scala.collection.mutable

trait UsageBase {

  type UseMap = mutable.HashMap[Definition, Seq[Definition]]

  private def emptyUseMap = mutable.HashMap.empty[Definition, Seq[Definition]]

  protected val uses: UseMap = emptyUseMap
  protected val usedBy: UseMap = emptyUseMap
}

/** Validation State for Uses/UsedBy Tracking. During parsing, when usage is
  * detected, call associateUsage. After parsing ends, call checkUnused.
  * Collects entities, types and functions too
  */
trait UsageResolution extends UsageBase {

  def commonOptions: CommonOptions

  def messages: Messages.Accumulator

  def usesAsMap: Map[Definition, Seq[Definition]] = uses.toMap

  def usedByAsMap: Map[Definition, Seq[Definition]] = usedBy.toMap

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

  def associateUsage(user: Definition, use: Definition): this.type = {

    val used = uses.getOrElse(user, Seq.empty[Definition])
    if !used.contains(use) then {
      uses.update(user,used :+ use)
    }

    val usages = usedBy.getOrElse(use, Seq.empty[Definition])
    if !usages.contains(user) then {
      usedBy.update(use, usages :+ user)
    }
    this
  }

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
        for  defn <- definitions if !hasUsages(defn)  do {
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
