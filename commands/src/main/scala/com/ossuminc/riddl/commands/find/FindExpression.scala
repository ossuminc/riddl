/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands.find

import com.ossuminc.riddl.commands.project.ProjectedNode
import com.ossuminc.riddl.language.AST.*

/** The `find` expression: Unix `find`'s shape, restricted to a subset that makes sense for RIDDL.
  *
  * Everything file-specific (`-size`, `-perm`, `-user`, `-newer`), platform-specific (`-xdev`,
  * `-mount`) and regex-flavour-selecting (`-regextype`) is deliberately absent — we use Java's regex
  * and only Java's.
  *
  * The expression is everything after `--` on the command line. That separator is required because
  * riddlc already owns `-d`, `-q`, `-v`, `-w`, `-s` and `-c` as global options, and `find` wants
  * single-dash names of its own; without it `-depth` would be ambiguous.
  */
sealed trait FindExpr {
  def matches(n: ProjectedNode, ctx: FindContext): Boolean
}

/** Facts a predicate needs that are not on the node itself. */
case class FindContext(depthOf: ProjectedNode => Int)

object FindExpr {
  case class And(left: FindExpr, right: FindExpr) extends FindExpr {
    def matches(n: ProjectedNode, c: FindContext): Boolean =
      left.matches(n, c) && right.matches(n, c)
  }
  case class Or(left: FindExpr, right: FindExpr) extends FindExpr {
    def matches(n: ProjectedNode, c: FindContext): Boolean =
      left.matches(n, c) || right.matches(n, c)
  }
  case class Not(inner: FindExpr) extends FindExpr {
    def matches(n: ProjectedNode, c: FindContext): Boolean = !inner.matches(n, c)
  }
  case object True extends FindExpr {
    def matches(n: ProjectedNode, c: FindContext): Boolean = true
  }
  case class Pred(name: String, test: (ProjectedNode, FindContext) => Boolean) extends FindExpr {
    def matches(n: ProjectedNode, c: FindContext): Boolean = test(n, c)
  }
}

/** Parses the argument list into an expression tree and the list of actions requested.
  *
  * Precedence follows find: `!` binds tightest, then implied/explicit `-a`, then `-o`. Parentheses
  * group, and need shell quoting exactly as they do with find.
  */
object FindExpression {

  final case class Parsed(expr: FindExpr, actions: Seq[FindAction], expectMin: Option[Int])

  def parse(args: Seq[String]): Either[String, Parsed] = {
    val actions = scala.collection.mutable.ListBuffer.empty[FindAction]
    var expectMin: Option[Int] = None

    // Actions and `-expect-min` are pulled out first: in find they sit in the expression and always
    // succeed, so treating them as terms would make `-type entity -print` mean `entity AND true`.
    // Extracting them keeps the expression purely about MATCHING, which is easier to reason about
    // and impossible to get subtly wrong with `-o`.
    val terms = scala.collection.mutable.ListBuffer.empty[String]
    var i = 0
    var error: Option[String] = None
    while i < args.length && error.isEmpty do
      args(i) match
        case "-print"    => actions.append(FindAction.Print); i += 1
        case "-location" => actions.append(FindAction.Location); i += 1
        case "-path0" | "-print0" => actions.append(FindAction.Print0); i += 1
        case "-list"     => actions.append(FindAction.ListTable); i += 1
        case "-quit"     => actions.append(FindAction.Quit); i += 1
        case "-printf" =>
          if i + 1 >= args.length then error = Some("-printf requires a format string")
          else { actions.append(FindAction.Printf(args(i + 1))); i += 2 }
        case "-expect-min" =>
          if i + 1 >= args.length then error = Some("-expect-min requires a number")
          else
            args(i + 1).toIntOption match
              case Some(v) => expectMin = Some(v); i += 2
              case None    => error = Some(s"-expect-min needs a number, got '${args(i + 1)}'")
        // `-path` is BOTH a test (glob against the dotted path) and, in find, nothing else. The
        // identity-only OUTPUT is `-printpath`, so that `-path '*.Order'` keeps its find meaning.
        case "-printpath" => actions.append(FindAction.PathOnly); i += 1
        case other        => terms.append(other); i += 1
      end match
    end while

    error match
      case Some(msg) => Left(msg)
      case None =>
        parseOr(terms.toList).flatMap { case (expr, rest) =>
          if rest.nonEmpty then Left(s"unexpected '${rest.head}'")
          else Right(Parsed(expr, actions.toSeq, expectMin))
        }
  }

  private def parseOr(toks: List[String]): Either[String, (FindExpr, List[String])] =
    parseAnd(toks).flatMap { case (left, rest) =>
      rest match
        case ("-o" | "-or") :: tail =>
          parseOr(tail).map { case (right, rest2) => (FindExpr.Or(left, right), rest2) }
        case _ => Right((left, rest))
    }

  private def parseAnd(toks: List[String]): Either[String, (FindExpr, List[String])] =
    parseUnary(toks).flatMap { case (left, rest) =>
      rest match
        case ("-a" | "-and") :: tail =>
          parseAnd(tail).map { case (right, rest2) => (FindExpr.And(left, right), rest2) }
        // Adjacency implies AND, exactly as in find. Anything that is not a closing paren or an
        // `-o` continues the conjunction.
        case head :: _ if head != ")" && head != "-o" && head != "-or" =>
          parseAnd(rest).map { case (right, rest2) => (FindExpr.And(left, right), rest2) }
        case _ => Right((left, rest))
    }

  private def parseUnary(toks: List[String]): Either[String, (FindExpr, List[String])] =
    toks match
      case ("!" | "-not") :: tail =>
        parseUnary(tail).map { case (e, rest) => (FindExpr.Not(e), rest) }
      case "(" :: tail =>
        parseOr(tail).flatMap { case (e, rest) =>
          rest match
            case ")" :: r => Right((e, r))
            case _        => Left("unbalanced '(' in expression")
        }
      case Nil => Right((FindExpr.True, Nil))
      case _   => FindPredicates.parse(toks)
}

/** What to do with a match. Phase 2 is READ-ONLY: `-exec`, `-replace` and `-delete` are phase 3, so
  * nothing here can alter a model.
  */
sealed trait FindAction
object FindAction {
  case object Print extends FindAction
  case object Location extends FindAction
  case object PathOnly extends FindAction
  case object Print0 extends FindAction
  case object ListTable extends FindAction
  case object Quit extends FindAction
  case class Printf(fmt: String) extends FindAction
}
