/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands.find

import com.ossuminc.riddl.commands.project.{ProjectedNode, ProjectionPass}
import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.parsing.Keyword
// The trailing `*` is required: `Contents` is opaque and its extension methods live at PACKAGE
// level, so importing only the object leaves `contents.isEmpty` unresolvable.
import com.ossuminc.riddl.language.{Contents, *}

/** The closed set of `find` tests.
  *
  * Closed on purpose: each predicate is a deliberate addition rather than a general
  * attribute-expression escape hatch, so the vocabulary stays documentable and cannot drift into
  * exposing AST internals as a user-facing surface.
  */
object FindPredicates {

  /** `-type` categories, alongside the concrete keywords.
    *
    * Structural nodes with no keyword of their own — on-clauses especially — deliberately get NO
    * `-type` name; the generalities below are what covers them.
    */
  /** Node kinds `-type` accepts, beyond the categories and RIDDL's own keywords.
    *
    * **Observed, not guessed**: this is every distinct `kind` the projection emits across both
    * corpora -- 190 riddl-models entry points and 9 riddl-examples -- which between them exercise
    * essentially every construct in the language. `ProjectionPass.kindOf` derives a kind from
    * `RiddlValue.kind` at runtime, so there is no static list to read it from, and a hand-guessed
    * one would be wrong in the direction that matters: rejecting a legitimate `-type`.
    *
    * `FindTypeVocabularyTest` re-derives it from the corpus and fails on drift, which keeps it
    * honest as the AST grows. Widening it is always safe; a MISSING entry turns a working query
    * into a parameter error, so add on drift rather than debating.
    */
  // `Set` is qualified because `AST.Set` shadows `scala.Set` under the wildcard import above
  // -- the gotcha recorded in CLAUDE.md.
  private[commands] val knownKinds: scala.collection.immutable.Set[String] =
    scala.collection.immutable.Set(
    "adaptor", "arbitrary-interaction", "author", "become-statement", "button", "command",
    "connector", "constant", "context", "correlation", "do-statement", "document", "domain",
    "entity", "enumerator", "epic", "error-statement", "event", "field", "flow",
    "focus-on-group", "foreach-statement", "form", "forward-statement", "function", "group",
    "handler", "inlet", "input", "invariant", "item", "let-statement", "linecomment", "list",
    "match-statement", "method", "methodargument", "module", "morph-statement", "on-event",
    "on-init", "on-other", "on-term", "onmessageclause", "optional-interaction", "outlet",
    "output", "parallel-interaction", "projector", "put-statement", "query", "record",
    "reply-statement", "repository", "repositoryref", "require-statement", "requires",
    "result", "return-statement", "returns", "router", "saga", "sagastep", "schema",
    "send-message-interaction", "send-statement", "sequential-interaction", "set-statement",
    "show-output-interaction", "shownby", "sink", "source", "split", "state", "table",
    "take-input-interaction", "tell-statement", "terminate-statement", "type", "usecase",
    "user", "vague-interaction", "value-reference", "version", "void", "when-statement",
    "yield-statement"
  )

  /** Everything `-type` accepts: the categories below, RIDDL's keywords, and the node kinds.
    *
    * Deliberately a UNION rather than the kinds alone. A `-type` value is documented as "a RIDDL
    * keyword where one exists, or a category", and `allKeywordsSet` covers spellings the corpus
    * happens not to contain.
    */
  private[commands] def typeVocabulary: scala.collection.immutable.Set[String] =
    categories.keySet ++ Keyword.allKeywordsSet ++ knownKinds

  /** Names the closest legal values rather than dumping all of them. */
  private def unknownTypeMessage(want: String): String =
    unknownValueMessage("-type", want, typeVocabulary)

  /** The general form of [[unknownTypeMessage]], for every selector with a closed vocabulary.
    *
    * **An unknown selector value is a PARAMETER ERROR, never zero matches.** `-type` learned this
    * at rc.26 and the reason generalizes exactly: `0 matched` for a typo is indistinguishable
    * from `0 matched` for a correct query with no hits, so the command answers confidently over
    * nothing — the failure `find` exists to end. riddl-generator hit it on `-shape alternation`,
    * which returned `0 matched` against a model holding 47 alternations, and reached a wrong
    * conclusion within minutes.
    *
    * Generalized 2026-08-31 rather than patching `-shape` alone: `-shape`, `-intention`,
    * `-cardinality` and `-option` ALL had the hole, and fixing the reported one would have left
    * three selectors failing the same way — the instance-fix reflex this repo keeps paying for.
    */
  private[commands] def unknownValueMessage(
    selector: String,
    want: String,
    vocabulary: scala.collection.immutable.Set[String]
  ): String =
    val near = vocabulary.toSeq
      .filter(v => v.startsWith(want.take(3)) || want.startsWith(v.take(3)) || v.contains(want))
      .sorted
      .take(6)
    val hint = if near.isEmpty then "" else s"; did you mean ${near.mkString(", ")}?"
    s"unknown $selector '$want'$hint"

  /** Every shape keyword `as <shape>` accepts, INCLUDING the deprecated synonyms.
    *
    * The synonyms are in deliberately: they still parse, so a model may contain them and a query
    * for one must not be rejected. Kept in step with `StreamletShape.fromKeyword` by hand — the
    * two cannot share a definition across the module boundary.
    */
  private[commands] val shapeVocabulary: scala.collection.immutable.Set[String] =
    // `Set` here is AST.Set unless qualified -- it shadows scala.Set in this file's scope.
    scala.collection.immutable.Set("void", "sink", "source", "flow", "merge", "split", "router",
      "cascade", "fanin", "broadcast", "fanout")

  /** Normalizes an intention for comparison by dropping hyphens.
    *
    * **The projection emits the enum NAME, not the keyword** — `ProjectionPass` writes
    * `i.toString`, so an event-sourced entity carries `EventSourced`, while the spelling in every
    * model is `event-sourced`. Comparing raw would mean `-intention event-sourced` validated and
    * then matched NOTHING: the same false zero this whole change exists to remove, one selector
    * over. Found 2026-08-31 when adding vocabulary checking broke an existing test that queried
    * `-intention EventSourced`; the test was right and the first vocabulary was wrong.
    *
    * Hyphens are the only difference between the two spellings across all three intention
    * families (`event-sourced`/`EventSourced`, `at-least-once`/`AtLeastOnce`), so dropping them
    * makes both work without a mapping table to keep in step.
    */
  private[commands] def normalizeIntention(s: String): String =
    s.toLowerCase.replace("-", "")

  /** Entity, context and connector intentions together — `-intention` does not distinguish them.
    *
    * Holds BOTH spellings: the keyword a modeller writes and the enum name the projection emits.
    * Membership is tested on the normalized form, so either is accepted and either matches.
    */
  private[commands] def intentionVocabulary: scala.collection.immutable.Set[String] =
    (AST.EntityIntention.keywords ++ AST.ConnectorIntention.keywords ++
      Seq("application", "external", "gateway", "service") ++
      AST.EntityIntention.values.toSeq.map(_.toString) ++
      AST.ConnectorIntention.values.toSeq.map(_.toString) ++
      AST.Intention.values.toSeq.map(_.toString)).map(_.toLowerCase).toSet

  /** What `ProjectionPass.cardinalityOf` can emit. `range(min,max)` is open-ended, so it is
    * matched by PREFIX below rather than listed.
    */
  private[commands] val cardinalityVocabulary: scala.collection.immutable.Set[String] =
    scala.collection.immutable
      .Set("optional", "zero-or-more", "one-or-more", "exactly-one", "range")

  /** Every option name the validator recognizes. */
  private[commands] def optionVocabulary: scala.collection.immutable.Set[String] =
    RecognizedOptions.registry.keySet

  private val categories: Map[String, ProjectedNode => Boolean] = Map(
    "statement" -> (_.value.isInstanceOf[Statement]),
    "processor" -> (_.value.isInstanceOf[Processor[?]]),
    "interaction" -> (_.value.isInstanceOf[Interaction]),
    "message" -> { n =>
      n.value match
        case t: Type =>
          t.typEx match
            case auc: AggregateUseCaseTypeExpression =>
              Seq("command", "event", "query", "result").contains(auc.usecase.useCase.toLowerCase)
            case _ => false
        case _ => false
    }
  )

  def parse(toks: List[String]): Either[String, (FindExpr, List[String])] = {
    def arg(name: String, rest: List[String])(
      f: String => Either[String, FindExpr]
    ): Either[String, (FindExpr, List[String])] =
      rest match
        case v :: tail => f(v).map(e => (e, tail))
        case Nil       => Left(s"$name requires an argument")

    toks match
      case "-type" :: rest =>
        arg("-type", rest) { v =>
          val want = v.toLowerCase
          // An unknown `-type` is a PARAMETER ERROR, not zero matches. A typo used to yield
          // `0 matched` and exit 0 -- indistinguishable from a correct query with no hits, which
          // is the confident-answer-over-nothing failure this command exists to end.
          if !typeVocabulary.contains(want) then Left(unknownTypeMessage(want))
          else Right(FindExpr.Pred(s"-type $v", (n, _) => matchesType(n, want)))
        }
      case "-name" :: rest =>
        arg("-name", rest)(v => Right(globPred(s"-name $v", v, idOf, ci = false)))
      case "-iname" :: rest =>
        arg("-iname", rest)(v => Right(globPred(s"-iname $v", v, idOf, ci = true)))
      case "-path" :: rest =>
        arg("-path", rest)(v => Right(globPred(s"-path $v", v, pathOf, ci = false)))
      case "-ipath" :: rest =>
        arg("-ipath", rest)(v => Right(globPred(s"-ipath $v", v, pathOf, ci = true)))
      case "-regex" :: rest =>
        arg("-regex", rest)(v => regexPred(s"-regex $v", v, ci = false))
      case "-iregex" :: rest =>
        arg("-iregex", rest)(v => regexPred(s"-iregex $v", v, ci = true))

      case "-in" :: rest =>
        arg("-in", rest) { v =>
          // The parent chain named top-down. Matches anything at any depth beneath it, which is
          // what "in this context" means to a modeller.
          Right(FindExpr.Pred(s"-in $v", (n, _) => ancestorPaths(n).contains(v)))
        }
      case "-under-a" :: rest =>
        arg("-under-a", rest) { v =>
          val want = v.toLowerCase
          Right(
            FindExpr.Pred(
              s"-under-a $v",
              (n, _) => n.parents.exists(p => ProjectionPass.kindOf(p) == want)
            )
          )
        }
      case "-under-name" :: rest =>
        arg("-under-name", rest) { v =>
          val rx = Glob.toRegex(v, ci = false)
          Right(
            FindExpr.Pred(
              s"-under-name $v",
              (n, _) => n.parents.exists(p => rx.matches(p.id.value))
            )
          )
        }

      case "-maxdepth" :: rest =>
        arg("-maxdepth", rest) { v =>
          v.toIntOption.toRight(s"-maxdepth needs a number, got '$v'").map { d =>
            FindExpr.Pred(s"-maxdepth $v", (n, c) => c.depthOf(n) <= d)
          }
        }
      case "-mindepth" :: rest =>
        arg("-mindepth", rest) { v =>
          v.toIntOption.toRight(s"-mindepth needs a number, got '$v'").map { d =>
            FindExpr.Pred(s"-mindepth $v", (n, c) => c.depthOf(n) >= d)
          }
        }

      case "-empty" :: rest =>
        // `Container.isEmpty` is COMMENT-TOLERANT: a body holding only a comment reports empty.
        // That is the AST's contract, kept rather than second-guessed, and it is why `-stub` is a
        // separate predicate instead of a synonym.
        Right((FindExpr.Pred("-empty", (n, _) => isEmptyNode(n)), rest))
      case "-stub" :: rest =>
        Right((FindExpr.Pred("-stub", (n, _) => isStub(n)), rest))
      case "-unresolved" :: rest =>
        Right((FindExpr.Pred("-unresolved", (n, _) => hasUnresolvedRef(n)), rest))

      case "-option" :: rest =>
        arg("-option", rest) { v =>
          if !optionVocabulary.contains(v) then
            Left(unknownValueMessage("-option", v, optionVocabulary))
          else Right(FindExpr.Pred(s"-option $v", (n, _) => strings(n, "options").contains(v)))
        }
      case "-intention" :: rest =>
        arg("-intention", rest) { v =>
          val want = v.toLowerCase
          if !intentionVocabulary.map(normalizeIntention).contains(normalizeIntention(want)) then
            Left(unknownValueMessage("-intention", want, intentionVocabulary))
          else
          Right(
            FindExpr.Pred(
              s"-intention $v",
              (n, _) =>
                (strings(n, "intentions") :+ str(n, "intention").getOrElse(""))
                  .map(normalizeIntention)
                  .contains(normalizeIntention(want))
            )
          )
        }
      case "-shape" :: rest =>
        arg("-shape", rest) { v =>
          val want = v.toLowerCase
          if !shapeVocabulary.contains(want) then
            Left(unknownValueMessage("-shape", want, shapeVocabulary))
          else
            Right(
              FindExpr
                .Pred(s"-shape $v", (n, _) => str(n, "shape").map(_.toLowerCase).contains(want))
            )
        }
      case "-carries" :: rest =>
        arg("-carries", rest) { v =>
          val want = v.toLowerCase
          Right(FindExpr.Pred(s"-carries $v", (n, _) => carries(n, want)))
        }
      case "-cardinality" :: rest =>
        arg("-cardinality", rest) { v =>
          val want = v.toLowerCase
          // The predicate is a PREFIX match, so a legal argument is any prefix of a legal value
          // ("one-" is meaningful). Validate on that basis rather than exact membership, or the
          // check would reject queries the predicate handles correctly.
          if !cardinalityVocabulary.exists(_.startsWith(want)) then
            Left(unknownValueMessage("-cardinality", want, cardinalityVocabulary))
          else Right(
            FindExpr.Pred(
              s"-cardinality $v",
              (n, _) => str(n, "cardinality").map(_.toLowerCase).exists(_.startsWith(want))
            )
          )
        }
      // Statement CONTENT selectors (riddl-models, 2026-08-25). `-regex` matches a definition's
      // name or path, and a statement has neither -- so `-type morph-statement -regex '.*Data.*'`
      // matched 0 and the selection fell back to Python over text, which is what `find` exists to
      // stop.
      case "-operand-kind" :: rest =>
        arg("-operand-kind", rest) { v =>
          val want = v.toLowerCase
          // Keyed off the SAME classification `dump --json` emits, so selection and projection
          // share one vocabulary rather than drifting into two.
          Right(
            FindExpr.Pred(
              s"-operand-kind $v",
              (n, c) => operandKinds(n, c).contains(want)
            )
          )
        }
      case "-source-regex" :: rest =>
        arg("-source-regex", rest) { v =>
          try
            val rx = v.r
            Right(FindExpr.Pred(s"-source-regex $v", (n, _) =>
              FindEditor.spanText(n).exists(t => rx.findFirstIn(t).isDefined)))
          catch case e: Exception => Left(s"bad regex '$v': ${e.getMessage}")
        }
      case "-reads-state" :: rest =>
        // The common case, spelled directly: an operand resolving to a field of an entity state.
        Right((FindExpr.Pred("-reads-state", (n, c) => operandKinds(n, c).contains("state-field")), rest))

      case "-arity" :: rest =>
        arg("-arity", rest)(v => arityPred(v))

      case other :: _ => Left(s"unknown test '$other'")
      case Nil        => Right((FindExpr.True, Nil))
  }

  // -----------------------------------------------------------------------------------------------
  // Record accessors — the projection is the single source of these facts, so `find` and
  // `dump --json` can never disagree about what a node is.
  // -----------------------------------------------------------------------------------------------

  /** The operand kinds in play at this node.
    *
    * A `value-reference` node carries its own `resolvedKind`. A STATEMENT carries none — its
    * operands are separate nodes — so a statement matches when a value reference INSIDE ITS SPAN
    * does. That containment is what makes `-type morph-statement -reads-state` select the four
    * statements riddl-models wanted rather than all fifty-nine.
    */
  private def operandKinds(n: ProjectedNode, c: FindContext): Seq[String] = {
    val own = str(n, "resolvedKind").toSeq.map(_.toLowerCase)
    if own.nonEmpty then own else c.operandKindsOf(n)
  }

  private def str(n: ProjectedNode, key: String): Option[String] =
    n.record.value.get(key).collect { case s: ujson.Str => s.str }

  private def strings(n: ProjectedNode, key: String): Seq[String] =
    n.record.value.get(key) match
      case Some(a: ujson.Arr) => a.arr.collect { case s: ujson.Str => s.str }.toSeq
      case _                  => Nil

  private def idOf(n: ProjectedNode): Option[String] = str(n, "id")
  private def pathOf(n: ProjectedNode): Option[String] = str(n, "path")

  private def ancestorPaths(n: ProjectedNode): Seq[String] = strings(n, "ancestors")

  private def matchesType(n: ProjectedNode, want: String): Boolean =
    categories.get(want) match
      case Some(test) => test(n)
      case None       => ProjectionPass.kindOf(n.value) == want

  private def isEmptyNode(n: ProjectedNode): Boolean = n.value match
    case c: Container[?] => c.isEmpty
    case _               => false

  /** A `???` body. `Container.isEmpty` cannot distinguish it from a body with only a comment, so
    * this asks the AST directly rather than reusing `-empty`.
    */
  private def isStub(n: ProjectedNode): Boolean = n.value match
    case c: Container[?] => c.contents.isEmpty
    case _               => false

  /** Any reference on this node that did not resolve. The projection emits `resolved: null`
    * explicitly for exactly this purpose — "absent" and "did not resolve" are different facts.
    */
  private def hasUnresolvedRef(n: ProjectedNode): Boolean = {
    def scan(v: ujson.Value): Boolean = v match
      case o: ujson.Obj =>
        (o.value.contains("ref") && o.value.get("resolved").contains(ujson.Null)) ||
        o.value.values.exists(scan)
      case a: ujson.Arr => a.arr.exists(scan)
      case _            => false
    scan(n.record)
  }

  private def carries(n: ProjectedNode, want: String): Boolean = {
    def scan(v: ujson.Value): Boolean = v match
      case o: ujson.Obj =>
        o.value.get("carries").exists {
          case s: ujson.Str => s.str.toLowerCase == want
          case _            => false
        } || o.value.values.exists(scan)
      case a: ujson.Arr => a.arr.exists(scan)
      case _            => false
    scan(n.record) || str(n, "messageKind").map(_.toLowerCase).contains(want)
  }

  /** `-arity <in>,<out>`, each `N`, `N+` or `*`. */
  private def arityPred(spec: String): Either[String, FindExpr] = {
    val parts = spec.split(",", -1)
    if parts.length != 2 then Left(s"-arity needs '<inlets>,<outlets>', got '$spec'")
    else
      def check(pat: String, actual: Int): Boolean = pat.trim match
        case "*"                     => true
        case p if p.endsWith("+")    => p.dropRight(1).toIntOption.exists(actual >= _)
        case p                       => p.toIntOption.contains(actual)
      Right(
        FindExpr.Pred(
          s"-arity $spec",
          (n, _) =>
            n.record.value.get("arity") match
              case Some(o: ujson.Obj) =>
                check(parts(0), o("inlets").num.toInt) && check(parts(1), o("outlets").num.toInt)
              case _ => false
        )
      )
  }

  private def globPred(
    label: String,
    pattern: String,
    field: ProjectedNode => Option[String],
    ci: Boolean
  ): FindExpr = {
    val rx = Glob.toRegex(pattern, ci)
    FindExpr.Pred(label, (n, _) => field(n).exists(rx.matches))
  }

  private def regexPred(label: String, pattern: String, ci: Boolean): Either[String, FindExpr] =
    try
      val rx = if ci then s"(?i)$pattern".r else pattern.r
      Right(FindExpr.Pred(label, (n, _) => pathOf(n).exists(p => rx.matches(p))))
    catch case e: Exception => Left(s"bad regex '$pattern': ${e.getMessage}")
}

/** Shell-style globs, translated to Java regex.
  *
  * Hand-rolled because nothing in the codebase does globbing and `java.nio.file.FileSystems`
  * is unavailable on Scala.js and Native, where these modules also build.
  */
object Glob {
  def toRegex(pattern: String, ci: Boolean): scala.util.matching.Regex = {
    val sb = new StringBuilder(if ci then "(?i)" else "")
    pattern.foreach {
      case '*'                          => sb.append(".*")
      case '?'                          => sb.append(".")
      case c if "\\^$.|+()[]{}".contains(c) => sb.append('\\').append(c)
      case c                            => sb.append(c)
    }
    sb.toString.r
  }
}
