package com.reactific.riddl.language.validation

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.Messages.*

import scala.collection.mutable

/** Validation State for Uses/UsedBy Tracking. During parsing, when usage is
  * detected, call associateUsage. After parsing ends, call checkUnused.
  * Collects entities, types and functions too
  */
trait UsageValidationState extends DefinitionValidationState {

  type UseMap = mutable.HashMap[Definition, Seq[Definition]]
  private def emptyUseMap = mutable.HashMap.empty[Definition, Seq[Definition]]

  private val uses: UseMap = emptyUseMap

  def usesAsMap: Map[Definition, Seq[Definition]] = uses.toMap

  private val usedBy: UseMap = emptyUseMap

  def usedByAsMap: Map[Definition, Seq[Definition]] = usedBy.toMap

  private var entities: Seq[Entity] = Seq.empty[Entity]

  def addEntity(entity: Entity): this.type = {
    entities = entities :+ entity
    this
  }

  private var types: Seq[Type] = Seq.empty[Type]

  def addType(ty: Type): this.type = {
    types = types :+ ty
    this
  }

  private var functions: Seq[Function] = Seq.empty[Function]

  def addFunction(fun: Function): this.type = {
    functions = functions :+ fun
    this
  }

  def associateUsage(user: Definition, use: Definition): this.type = {

    val used = uses.getOrElse(user, Seq.empty[Definition])
    val new_used = used :+ use
    uses.update(user, new_used)

    val usages = usedBy.getOrElse(use, Seq.empty[Definition])
    val new_usages = usages :+ user
    usedBy.update(use, new_usages)

    this
  }

  def checkUnused(): this.type = {
    if (commonOptions.showUnusedWarnings) {
      def hasUsages(definition: Definition): Boolean = {
        val result = usedBy.get(definition) match {
          case None        => false
          case Some(users) => users.nonEmpty
        }
        result
      }
      def checkList(list: Seq[Definition]): Unit = {
        for { i <- list } {
          check(hasUsages(i), s"${i.identify} is unused", Warning, i.loc)
        }
      }
      checkList(entities)
      checkList(types)
      checkList(functions)
    }
    this
  }
}
