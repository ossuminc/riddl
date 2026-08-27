/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.utils

import scala.quoted.*

/** Expands a UNION TYPE into the runtime classes of its members, at compile time.
  *
  * RIDDL's containment rules are written down exactly once, as the `OccursInX` / `XContents` union
  * aliases in `AST.scala` that the parser's return types are checked against. Scala 3 ERASES
  * unions, so `isInstanceOf[OccursInContext]` does not exist, and every consumer that needs to ask
  * "may a Context hold this?" -- Synapify's drag-and-drop, the IDEA plugin, the VS Code extension
  * -- has had to keep a hand-maintained copy of those rules. Each copy drifts the moment the
  * grammar gains a construct; Synapify's was wrong in two ways before anyone noticed.
  *
  * This makes the union itself the answer, so the predicate cannot disagree with the type the
  * parser is checked against: add a member to `ContextContents` and the set gains it with no edit
  * anywhere else.
  *
  * ==Why this is a macro and not something simpler==
  *
  * Both simpler routes are dead ends in Scala 3.9, and each fails in a way that looks like
  * something else:
  *
  *   - `inline erasedValue[T] match { case _: (a | b) => … }` -- a NON-union `T` still matches
  *     `(a | b)` by taking `a := T, b := Nothing`, so the recursion never bottoms out. It surfaces
  *     as "Maximal number of successive inlines (32) exceeded", which reads like a depth limit and
  *     is really non-termination; raising the limit to 128 just moves the number in the message.
  *   - A match type `type F[T] = T match { case a | b => … }` is rejected outright: "The pattern
  *     contains an unaccounted type parameter `a`" (E191). Union patterns in match types were
  *     retired.
  *
  * A macro can inspect the `OrType` directly, which is what this does. It lives in `utils` rather
  * than beside its caller because Scala 3 cannot call a macro defined in the same compilation run.
  */
object UnionMembers:

  /** The runtime classes named by union type `T`, flattening nested unions and dealiasing on the
    * way, so `ContextContents` reaches through `OccursInProcessor` to `Type`, `Comment` and the
    * rest.
    *
    * Computed at COMPILE time. Bind the result to a `val` -- the returned `Set` is what makes a
    * membership test cheap enough to run per animation frame.
    */
  inline def unionClasses[T]: Set[Class[?]] = ${ unionClassesImpl[T] }

  private def unionClassesImpl[T: Type](using Quotes): Expr[Set[Class[?]]] =
    import quotes.reflect.*

    def flatten(tpe: TypeRepr): List[TypeRepr] =
      // `dealias` turns `ContextContents` into the union it names; `simplified` collapses the
      // leftovers. Without dealiasing, an alias-of-an-alias is a single opaque leaf and the set
      // comes back with one useless entry.
      tpe.dealias.simplified match
        case OrType(left, right) => flatten(left) ++ flatten(right)
        case AndType(left, _)    => flatten(left)
        case other               => List(other)
    end flatten

    val classExprs: List[Expr[Class[?]]] =
      flatten(TypeRepr.of[T]).distinct.map { t =>
        t.asType match
          case '[tpe] =>
            Expr.summon[reflect.ClassTag[tpe]] match
              case Some(ct) => '{ $ct.runtimeClass }
              case None =>
                report.errorAndAbort(
                  s"UnionMembers.unionClasses: no ClassTag for union member ${t.show}. " +
                    "Every member of a containment union must be a class or sealed trait."
                )
      }

    '{ Set(${ Varargs(classExprs) }*) }
  end unionClassesImpl

  /** A precomputed membership test for a union type.
    *
    * Built once per container kind and then asked per call: no parse, no pass, no IO, no allocation
    * -- a class walk over a small `Set`.
    */
  final class Contains(val classes: Set[Class[?]]):

    /** Is `value` an instance of one of the union's members?
      *
      * `isAssignableFrom`, not class equality, so a sealed trait named in the union (`Statement`,
      * `Comment`, `Interaction`) answers for its subtypes without enumerating them -- the same rule
      * `Contents.filter` uses.
      */
    def apply(value: Any): Boolean =
      val c = value.getClass
      classes.exists(_.isAssignableFrom(c))
    end apply

    /** By simple kind name, for a caller holding no instance -- a palette offering definitions the
      * user has not created yet. Case-insensitive.
      */
    def named(kind: String): Boolean =
      val k = kind.trim
      classes.exists(c => simpleName(c).equalsIgnoreCase(k))
    end named

    /** The member kind names, sorted -- for a palette or a diagnostic. */
    def kinds: Seq[String] = classes.map(simpleName).toSeq.sorted

    private def simpleName(c: Class[?]): String = c.getSimpleName.stripSuffix("$")
  end Contains

  inline def contains[T]: Contains = Contains(unionClasses[T])

end UnionMembers
