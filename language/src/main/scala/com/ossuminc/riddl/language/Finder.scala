/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language

import com.ossuminc.riddl.language.AST.*
import scala.collection.immutable.HashMap
import scala.collection.mutable

import scala.reflect.{ClassTag, classTag}
import scalajs.js.annotation.*

/** The referent for finding things within a given [[com.ossuminc.riddl.language.AST.Container]] of
  * [[com.ossuminc.riddl.language.AST.RiddlValue]] as found in the AST model. This provides the
  * ability to find values in the model by traversing it and looking for the matching condition.
  * @param root
  *   The container of RiddlValues to traverse for the sought condition
  */
@JSExportTopLevel("Finder")
case class Finder[CV <: RiddlValue](root: Container[CV]) {

  import scala.reflect.ClassTag

  // Cache for findByType results - maps Class to previously found Seq of that type
  // This avoids repeated full tree traversals for the same type
  private val findByTypeCache: mutable.Map[Class[?], Seq[RiddlValue]] = mutable.Map.empty

  // Cache for recursiveFindByType results - same pattern as findByTypeCache
  // but for the recursive variant that descends into nested statements
  private val recursiveFindByTypeCache: mutable.Map[Class[?], Seq[RiddlValue]] = mutable.Map.empty

  /** Search the `root` for a [[AST.RiddlValue]] that matches the boolean expression
    *
    * @param select
    *   The boolean expression to search for
    *
    * @return
    *   A [[scala.Seq]] of the matching [[AST.RiddlValue]]
    */
  @JSExport
  def find(select: CV => Boolean): Seq[CV] =
    Folding.foldEachDefinition[Seq[CV], CV](root, Seq.empty[CV]) {
      case (state: Seq[CV], value: CV) =>
        if select(value) then state :+ value else state
    }
  end find

  /** Search the [[root]] for a certain kind of [[AST.RiddlValue]] and return those */
  @JSExport
  def findByType[T <: AST.RiddlValue: ClassTag]: Seq[T] =
    import scala.reflect.classTag
    val lookingFor = classTag[T].runtimeClass

    // Check cache first
    findByTypeCache.get(lookingFor) match {
      case Some(cached) =>
        // Return cached result
        cached.asInstanceOf[Seq[T]]
      case None =>
        // Cache miss - compute and store result
        val result = find { (value: RiddlValue) => lookingFor.isAssignableFrom(value.getClass) }
        findByTypeCache(lookingFor) = result
        result.asInstanceOf[Seq[T]]
    }
  end findByType

  /** The direct, non-`contents` children of a [[RiddlValue]] that hold nested statements or values
    * in a FIELD rather than in a `Contents[?]` — e.g. `WhenStatement.condition`,
    * `Correlation.timeoutStatements`, `RequireStatement.argument`.
    *
    * `recursiveFindByType`'s traversal was, until 2026-08-15, total over a handful of container-
    * shaped statement kinds (When/Match/Foreach/SagaStep) but blind to every FIELD any node holds a
    * nested value or statement block in — the same shape of defect `statementValues` had in
    * `ValidationPass` (see CLAUDE.md "Total Dispatch"). This function is the fix, factored out so a
    * new field-held site is added in ONE place rather than patched into a growing `consider` match.
    *
    * Deliberately excludes plain structural [[Reference]] fields (`processorRef`, `entity`,
    * `handler`, `output`, `portlet`, `collection`, …) — those are leaf path identifiers already
    * resolved via `ResolutionPass`'s refMap, not nested statements/values, and including every
    * reference field on every statement would broaden this well past the defect's shape without a
    * concrete use found for it.
    */
  private def fieldChildren(v: RiddlValue): Seq[RiddlValue] = v match
    // Statement blocks held in fields, not in `contents`
    case ws: WhenStatement =>
      Seq(ws.condition) ++ ws.thenStatements.toSeq ++ ws.elseStatements.toSeq
    case ms: MatchStatement    => Seq(ms.expression) ++ ms.cases ++ ms.default.toSeq
    case mc: MatchCase         => Seq(mc.pattern) ++ mc.guard.toSeq ++ mc.statements.toSeq
    case cp: ComparisonPattern => Seq(cp.comparand)
    case lp: LiteralPattern    => Seq(lp.literal)
    // Same-shaped bare-LiteralString fields as `lp.literal` immediately above — added for
    // audit consistency (review round 1, fix 2). A LiteralString is a leaf so these add no
    // actual reachability, but a table claiming to be exhaustive should not have unexplained
    // gaps next to a sibling it does cover.
    case ps: DoStatement  => Seq(ps.what)
    case es: ErrorStatement   => Seq(es.message)
    case cs: CodeStatement    => Seq(cs.language)
    case fe: ForeachStatement => fe.doStatements.toSeq
    case ss: SagaStep         => ss.doStatements.toSeq ++ ss.undoStatements.toSeq
    case cr: Correlation => cr.timeoutStatements.toSeq // `.contents` already walked as a Container
    case iv: Invariant   => iv.condition.toSeq
    case ib: InvariantBlock => ib.statements.toSeq :+ ib.predicate

    // Statement leaves holding a single Value/BooleanExpression/message operand in a field
    case rq: RequireStatement => Seq(rq.condition) ++ rq.argument.toSeq
    case st: SetStatement     => Seq(st.value)
    case lt: LetStatement     => Seq(lt.expression)
    case pt: PutStatement     => Seq(pt.value)
    case rt: ReturnStatement  => Seq(rt.value)
    case sn: SendStatement    => Seq(sn.msg)
    case tl: TellStatement    => Seq(tl.msg)
    case yl: YieldStatement   => Seq(yl.msg)
    case rp: ReplyStatement   => Seq(rp.msg)
    case mo: MorphStatement   => Seq(mo.value)
    // `target` is the instance address (a `Value`, since 2026-08-15) and must be walked with the
    // args, or every `Finder` consumer goes blind to whatever the address is built from.
    case tm: TerminateStatement => tm.target +: tm.args

    // Value/BooleanExpression composition — needed so a condition tree's leaves (a NumericLiteral,
    // a ValueRef, …) are reachable, not just the top-level condition node itself.
    case ic: InvariantCondition   => ic.argument.toSeq
    case ce: ComparisonExpression => Seq(ce.left, ce.right)
    case le: LogicalExpression    => Seq(le.left, le.right)
    case ne: NotExpression        => Seq(ne.expr)
    case ct: Constructor          => ct.args
    case cl: Call                 => cl.args
    case in: Initiate             => in.args
    case ca: ConstructorArg       => Seq(ca.value)
    case gv: GetValue             => Seq(gv.source)

    // Review round 1, fix 1: `PromptValue` (A20's `prompt("…") as T` typed hole) was the ONLY
    // arm of `Value` holding a non-trivial nested structure and had NO case at all — not the
    // `prompt` text, not the `as T` ascription. `PromptValue` legally sits in
    // `WhenStatement.condition`, `LetStatement.expression`, `SetStatement.value`,
    // `PutStatement.value`, `ReturnStatement.value` and `ConstantValue`, so a search for a
    // `NumericLiteral`/`AliasedTypeExpression`/etc. reached through any of those missed it. The
    // ascription's `TypeExpression` is surfaced here too (an `AliasedTypeExpression` — a NAMED
    // type ascription — recurses one further level into its `PathIdentifier`, so `prompt("…") as
    // SomeType` makes both the ascription node AND the path it names reachable).
    case pv: PromptValue            => Seq(pv.prompt) ++ pv.typeEx.toSeq
    case ate: AliasedTypeExpression => Seq(ate.pathId)

    case _ => Seq.empty
  end fieldChildren

  def recursiveFindByType[T <: AST.RiddlValue: ClassTag]: Seq[T] =
    import scala.reflect.classTag
    val lookingFor = classTag[T].runtimeClass

    // Check cache first
    recursiveFindByTypeCache.get(lookingFor) match
      case Some(cached) =>
        cached.asInstanceOf[Seq[T]]
      case None =>
        def consider(list: Seq[T], child: RiddlValue): Seq[T] =
          val afterContents =
            child match
              case c: Container[?] =>
                c.contents.foldLeft(list) { case (next, child) => consider(next, child) }
              case _ => list
          val afterFields =
            fieldChildren(child).foldLeft(afterContents) { case (next, child) =>
              consider(next, child)
            }
          if lookingFor.isAssignableFrom(child.getClass) then afterFields :+ child.asInstanceOf[T]
          else afterFields
        end consider
        val result = root.contents.foldLeft(Seq.empty[T]) { case (list, child) =>
          consider(list, child)
        }
        recursiveFindByTypeCache(lookingFor) = result
        result
    end match
  end recursiveFindByType

  /** The return value for the [[Finder.findWithParents()]] function */
  type DefWithParents[T <: RiddlValue] = Seq[(T, Parents)]

  /** Find a matching set of [[AST.RiddlValue]] but return them with their parents
    *
    * @param select
    *   The boolean expression derived from a candidate [[AST.RiddlValue]] that selects it to the
    *   result set
    * @return
    *   A [[Finder#DefWithParents]] that returns a [[scala.Seq]] of two-tuples with the
    *   [[AST.RiddlValue]] a a [[scala.Seq]] of the parents of that value.
    */
  @JSExport
  def findWithParents[T <: RiddlValue: ClassTag](
    select: T => Boolean
  ): DefWithParents[T] =
    import scala.collection.mutable
    val lookingFor = classTag[T].runtimeClass
    // Use ArrayBuffer for O(1) amortized append instead of O(n) :+ operator
    val buffer = mutable.ArrayBuffer.empty[(T, Parents)]
    Folding.foldLeftWithStack[mutable.ArrayBuffer[(T, Parents)], CV](
      buffer,
      root,
      ParentStack.empty
    ) { case (state, definition: CV, parents) =>
      if lookingFor.isAssignableFrom(definition.getClass) then
        val value: T = definition.asInstanceOf[T]
        if select(value) then
          state += (value -> parents) // O(1) amortized instead of O(n)
          state
        else state
      else state
    }
    buffer.toSeq // Convert to immutable Seq at the end
  end findWithParents

  /** Find the Parents for a given node in the root
    * @param node
    *   The node for which the Parents are sought.
    * @return
    *   Parents - A Sequence of Branch nodes from nearest parent towards the Root.
    */
  @JSExport
  def findParents(node: Definition): Parents = {
    val result = findWithParents[Definition](_ == node)
    result.headOption.map(_._2).getOrElse(Parents.empty)
  }

  /** Start from the root Container and for every definition it contains, compute the Parents (path
    * to that definition).
    * @return
    *   A HashMap[Definition,Parents] that provides the path to every definition in a fast-access
    *   data structure
    */
  @JSExport
  def findAllPaths: HashMap[Definition, Parents] = {
    val stack = ParentStack.empty[Branch[?]]
    val result: mutable.HashMap[Definition, Parents] = mutable.HashMap.empty
    Folding.foldLeftWithStack(result, root, stack) { case (map, definition: Definition, parents) =>
      map.addOne((definition, parents))
      map
    }
    result.toMap[Definition, Parents].asInstanceOf[HashMap[Definition, Parents]]
  }

  /** Run a transformation function on the [[Finder]] contents. They type parameter specifies what
    * kind of thing should be found, the `select` argument provides further refinement of which
    * things of that type should be selected. The transformation function, `transformF` does the
    * transformation, probably by using the Scala `.copy` method.
    *
    * @tparam TT
    *   The transform type. This narrows the search to just the contents that have the base type TT.
    * @param select
    *   The function to select which values should be operated on. It should return true if the
    *   transformation function should be executed on the element passed to it.
    * @param transformF
    *   The transformation function to convert one value to another. The returned value will replace
    *   the passed value in the [[Finder]]'s container.
    */
  @JSExport
  def transform[TT <: RiddlValue: ClassTag](select: TT => Boolean)(transformF: CV => CV): Unit =
    val clazz = classTag[TT].runtimeClass
    for { i <- root.contents.indices } do {
      val item: CV = root.contents(i)
      if clazz.isAssignableFrom(item.getClass) then
        if select(item.asInstanceOf[TT]) then root.contents(i) = transformF(item)
        end if
      end if
    }
  end transform

  /** Find definitions that are empty
    *
    * @return
    *   A [[scala.Seq]] of [[AST.RiddlValue]], along with their parents that are empty
    */
  @JSExport def findEmpty: DefWithParents[Definition] = findWithParents[Definition](_.isEmpty)
}

object Finder:
  def apply[CV <: RiddlValue](contents: Contents[CV]): Finder[CV] =
    val container = SimpleContainer[CV](contents)
    Finder[CV](container)
  end apply

  /** Search the contents of each parent in a hierarchy chain for definitions of type `T`. Walks
    * from nearest parent to furthest (e.g., Entity → Context → Domain).
    *
    * @tparam T
    *   The type of definition to find
    * @param parents
    *   The parent chain to search, ordered nearest to furthest
    * @return
    *   Each matching definition paired with the parents of the container it was found in, suitable
    *   for constructing a [[AST.PathIdentifier]]
    */
  def findInParents[T <: RiddlValue: ClassTag](
    parents: Parents
  ): Seq[(T, Parents)] =
    val lookingFor = classTag[T].runtimeClass
    val buffer = mutable.ArrayBuffer.empty[(T, Parents)]
    parents.zipWithIndex.foreach { case (parent, idx) =>
      val parentsOfParent = parents.drop(idx + 1)
      parent.contents.toSeq.foreach { child =>
        if lookingFor.isAssignableFrom(child.getClass) then
          buffer += (child.asInstanceOf[T] -> parentsOfParent)
      }
    }
    buffer.toSeq
  end findInParents
end Finder
