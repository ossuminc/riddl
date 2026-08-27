/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Messages.*
import com.ossuminc.riddl.language.{Contents, *}
import com.ossuminc.riddl.language.parsing.Keyword
import com.ossuminc.riddl.utils.PlatformContext

import java.util.regex.PatternSyntaxException

/** Unit Tests For TypeValidationState */
trait TypeValidation(using pc: PlatformContext) extends DefinitionValidation {

  def areSameType(typ1: Option[Type], typ2: Option[Type]): Boolean = {
    val result = for {
      t1 <- typ1
      t2 <- typ2
    } yield {
      t1 == t2
    }
    result.getOrElse(false)
  }

  private def checkPattern(p: Pattern): this.type = {
    try {
      val compound = p.pattern.map(_.s).fold("") { case (a: String, b: String) => a + b }
      java.util.regex.Pattern.compile(compound)
    } catch {
      case x: PatternSyntaxException =>
        messages.add(
          Message(
            p.loc,
            x.getMessage,
            suggestion =
              "Correct the regular-expression syntax in this pattern (RIDDL uses Java regex syntax)."
          )
        )
    }
    this
  }

  private def checkEnumeration(
    enumerators: Seq[Enumerator]
  ): this.type = {
    this.checkSequence(enumerators) { (enumerator: Enumerator) =>
      val id = enumerator.id
      checkIdentifierLength(enumerator)
        .check(
          id.value.head.isUpper,
          s"Enumerator '${id.value}' must start with upper case",
          StyleWarning,
          id.loc,
          suggestion =
            s"Start the enumerator name with an upper-case letter, e.g. '${id.value.capitalize}'.",
          ruleId = Some(RuleId.EnumeratorCapitalization)
        )
    }
    this
  }

  private def checkAlternation(
    alternation: Alternation,
    typeDef: Definition,
    parents: Parents
  ): this.type = {
    checkSequence(alternation.of.toSeq) { (typex: TypeExpression) =>
      checkTypeExpression(typex, typeDef, parents)
    }
    this
  }

  private def checkRangeType(rt: RangeType): this.type = {
    check(
      rt.min >= BigInt.long2bigInt(Long.MinValue),
      "Minimum value might be too small to store in a Long",
      Warning,
      rt.loc,
      suggestion =
        "Keep the minimum within the range of a 64-bit Long, or model the value with a different numeric type.",
      ruleId = Some(RuleId.RangeMinTooSmall)
    )
      .check(
        rt.max <= BigInt.long2bigInt(Long.MaxValue),
        "Maximum value might be too large to store in a Long",
        Warning,
        rt.loc,
        suggestion =
          "Keep the maximum within the range of a 64-bit Long, or model the value with a different numeric type.",
        ruleId = Some(RuleId.RangeMaxTooLarge)
      )
  }

  /** A field name may appear only ONCE in one aggregate.
    *
    * Reported by riddl-examples 2026-08-18: a duplicate survived validation AND an idempotent
    * prettify round trip, so their whole corpus read as zero messages of every kind while 13
    * ShopifyCart definitions carried the same name twice. They found it by parsing their own
    * output and counting names -- nothing riddlc said pointed at it.
    *
    * ERROR, not a style warning: a repeated name makes the aggregate's SHAPE ambiguous, and every
    * downstream consumer -- a generator, a BAST reader, riddlg -- has to pick one silently. That is
    * a contradiction rather than untidiness, which is the line this repo draws for Error.
    *
    * Both locations are named. The author is looking at one of them and needs the other; a message
    * that says only "declared twice" leaves them grepping.
    *
    * Called from BOTH aggregate sites -- plain `Aggregation` and `AggregateUseCaseTypeExpression`
    * -- because the defect is in the shape, not in one container. The reported repro was a
    * `command`, which is the second of those, so fixing only the first would have looked right and
    * closed nothing.
    */
  protected def checkDuplicateFieldNames(fields: Seq[Field], container: String): this.type = {
    fields
      .groupBy(_.id.value)
      .collect { case (name, fs) if fs.sizeIs > 1 => name -> fs.sortBy(_.loc.offset) }
      .toSeq
      .sortBy { case (_, fs) => fs.head.loc.offset } // stable output regardless of Map ordering
      .foreach { case (name, fs) =>
        val first = fs.head
        fs.tail.foreach { dupe =>
          messages.addError(
            dupe.loc,
            s"Field '$name' is declared more than once in $container; " +
              s"the first declaration is at ${first.loc.format}",
            suggestion = s"Rename one of them, or delete the redundant declaration. Two fields " +
              s"named '$name' leave the aggregate's shape ambiguous, and every consumer has to " +
              "choose one silently.",
            ruleId = Some(RuleId.FieldDuplicateName)
          )
        }
      }
    this
  }

  private def checkAggregation(agg: Aggregation): this.type = {
    checkDuplicateFieldNames(agg.fields, "this aggregation")
    checkSequence(agg.fields) { (field: Field) =>
      checkIdentifierLength(field)
        .check(
          field.id.value.head.isLower,
          "Field names in aggregates should start with a lower case letter",
          StyleWarning,
          field.loc,
          suggestion =
            s"Start the field name with a lower-case letter, e.g. '${field.id.value.take(1).toLowerCase + field.id.value.drop(1)}'.",
          ruleId = Some(RuleId.FieldShouldBeLowercase)
        )
        .checkMetadata(field)
    }
    this
  }

  private def checkAggregateUseCase(
    mt: AggregateUseCaseTypeExpression,
    typeDef: Definition,
    parents: Parents
  ): this.type = {
    checkDuplicateFieldNames(mt.fields, s"this ${mt.usecase.useCase}")
    checkSequence(mt.fields) { (field: Field) =>
      checkIdentifierLength(field)
        .check(
          field.id.value.head.isLower,
          s"Field names in ${mt.usecase.useCase} should start with a lower case letter",
          StyleWarning,
          field.loc,
          suggestion =
            s"Start the field name with a lower-case letter, e.g. '${field.id.value.take(1).toLowerCase + field.id.value.drop(1)}'.",
          ruleId = Some(RuleId.MessageFieldShouldBeLowercase)
        )
        .checkTypeExpression(field.typeEx, typeDef, parents)
        .checkMetadata(field)
    }
    checkUseCaseYields(mt, parents)
  }

  /** A19: a `yields` clause is only valid on a command (yielding an event) or a query (yielding a
    * result). Anything else is an error. Invoked from `checkAggregateUseCase` (nested AUCTEs) and
    * directly from `ValidationPass.validateType` (top-level message types, which skip
    * `checkTypeExpression`).
    */
  protected def checkUseCaseYields(
    mt: AggregateUseCaseTypeExpression,
    parents: Parents
  ): this.type = {
    mt.yields.foreach { yieldRef =>
      mt.usecase match {
        case AggregateUseCase.CommandCase =>
          checkMessageRef(yieldRef, parents, Seq(AggregateUseCase.EventCase))
        case AggregateUseCase.QueryCase =>
          checkMessageRef(yieldRef, parents, Seq(AggregateUseCase.ResultCase))
        case other =>
          messages.addError(
            yieldRef.pathId.loc,
            s"Only command and query types may declare `yields`, but ${other.useCase} does not",
            suggestion = "Remove the `yields` clause, or declare the type as a command or query.",
            ruleId = Some(RuleId.YieldsOnlyCommandQuery)
          )
      }
    }
    this
  }

  private def checkSet(set: Set, definition: Definition, parents: Parents): Unit = {
    checkTypeExpression(set.of, definition, parents)
  }

  private def checkSeq(sequence: Sequence, definition: Definition, parents: Parents): Unit = {
    checkTypeExpression(sequence.of, definition, parents)
  }

  private def checkMapping(
    mapping: Mapping,
    typeDef: Definition,
    parents: Parents
  ): this.type = {
    this
      .checkTypeExpression(mapping.from, typeDef, parents)
      .checkTypeExpression(mapping.to, typeDef, parents)
  }

  private def checkGraph(
    graph: Graph,
    typeDef: Definition,
    parents: Parents
  ): this.type = {
    this.checkTypeExpression(graph.of, typeDef, parents)
  }

  private def checkTable(
    table: Table,
    typeDef: Definition,
    parents: Parents
  ): this.type = {
    this.checkTypeExpression(table.of, typeDef, parents)
  }

  private def checkReplica(
    replica: Replica,
    typeDef: Definition,
    parents: Parents
  ): Unit = {
    checkTypeExpression(replica.of, typeDef, parents)
    replica.of match {
      case _: Mapping | _: Sequence | _: Set | _: IntegerTypeExpression => // these are okay
      case _: Cardinality =>
        messages.addError(
          replica.loc,
          s"Replica type expressions may not have cardinality",
          suggestion =
            "Remove the cardinality from the replica's element type; a replica wraps a single replicable type.",
          ruleId = Some(RuleId.ReplicaHasCardinality)
        )
      case _: TypeExpression =>
        messages.addError(
          replica.loc,
          s"Type expression in Replica is not a replicable type",
          suggestion =
            "Use a replicable element type for the replica: a mapping, sequence, set, or integer type.",
          ruleId = Some(RuleId.ReplicaNotReplicable)
        )
    }
  }

  /** The `Id(<keyword> …)` spelling that names Processor `p`'s kind.
    *
    * Written out rather than derived, because both derivations are wrong. `getClass.getSimpleName`
    * (which this used until the final review of the instance-identity branch) couples a user-facing
    * diagnostic to a JVM class name and works only by the accident that all six Processor class
    * names lowercase to their keyword. `Definition.kind` is riddl's own answer everywhere else, but
    * a [[Streamlet]] OVERRIDES it to its SHAPE (`"Sink"`, `"Flow"`, …), so `Id(streamlet Feed)`
    * would be reported as a lie.
    *
    * Total over `Processor`'s six concrete kinds — matching the six keywords `TypeParser`'s
    * `uniqueIdType` accepts — so a seventh kind is a compile error here rather than a silent
    * fall-through that reports a legal keyword as wrong.
    */
  protected def processorKeywordOf(p: Processor[?]): String = p match
    case _: Adaptor    => Keyword.adaptor
    case _: Context    => Keyword.context
    case _: Entity     => Keyword.entity
    case _: Projector  => Keyword.projector
    case _: Repository => Keyword.repository
    case _: Streamlet  => Keyword.streamlet
  end processorKeywordOf

  /** The [[Processor]] an `Id(…)` names, found INDEPENDENTLY of which parent [[ResolutionPass]]
    * happened to key it under.
    *
    * This exists because the obvious `refMap.definitionOf[Processor[?]](pid, parents.head)` is
    * right in exactly one position and silently wrong in the others, and `.foreach` turned the miss
    * into "skip the check". The keys differ because `ResolutionPass.process` PREPENDS a `Branch` to
    * its own parents before resolving it:
    *   - a Field's `Id(…)` is keyed under the owning `Type` (validation's `parents.head` there is
    *     also the Type — the one position that matched, which is why the check appeared to work);
    *   - a type ALIAS's `Id(…)` is keyed under the `Type` ITSELF, while validation's `parents.head`
    *     is the enclosing Context — so the lookup missed and the check never fired. riddl-models
    *     holds 232 `type X is Id(…)` aliases against 7 field-position uses, so the check was silent
    *     in 97% of real usage;
    *   - an `on init`/`on term` PARAMETER's `Id(…)` is keyed under the on-clause.
    *
    * `anyDefinitionOf` is used rather than the typed `definitionOf`, because the typed overload
    * REPORTS an Error when a key exists holding some other kind — which, probing several parents,
    * would turn a near-miss into a spurious diagnostic.
    *
    * There is deliberately NO symbol-table fallback for a single-name path. `resolveAPathId` sends
    * one through `searchSymbolTable`, which calls `resolved(pathId, parent, d)` — so a single name
    * IS in the refMap, under the same parent as everything else. A symbol-table probe would only
    * add a way to pick an ARBITRARY same-named definition where the resolver correctly reported an
    * ambiguity. So a miss here means the path did not resolve AT ALL, which [[ResolutionPass]] has
    * already reported: silent is the right behaviour for the miss, and the defect was that the
    * lookup could miss for a path that HAD resolved.
    */
  protected def uniqueIdReferent(
    pid: PathIdentifier,
    defn: Definition,
    parents: Parents
  ): Option[Processor[?]] =
    (defn +: parents.headOption.toSeq)
      .flatMap(parent => resolution.refMap.anyDefinitionOf(pid, parent))
      .collectFirst { case p: Processor[?] => p }
  end uniqueIdReferent

  /** [1.10]: a prefix that IS written on an aliased type must be true. It is never DEMANDED.
    *
    * Ruled by Reid, 2026-08-26, and the two halves are separate decisions:
    *
    *   - **Not demanded.** `TypeRef`'s rule holds a BARE reference to the standard, because the
    *     positions it covers (a portlet's type, a function's `requires`/`returns`) admit several
    *     KINDS and the prefix is what disambiguates them. A field's type admits only a type, so a
    *     prefix there removes no ambiguity -- it is decoration, and demanding it would cost 542
    *     sites in reactive-bbq alone for no reader benefit.
    *   - **Checked when written.** A prefix that lies is worse than one that is absent, because a
    *     reader believes it. That is the same reasoning behind `UniqueId.kindKeyword`.
    *
    * **Only a NON-DEFAULT keyword can be checked, and that is a hard limit rather than a choice.**
    * `TypeParser` builds `AliasedTypeExpression(loc, "type", pid)` when no keyword is written, so
    * the AST cannot tell `Ctx.Rec` from `type Ctx.Rec`. Reporting on `"type"` would therefore fire
    * on every bare field type in the corpus -- exactly the demand this ruling declined.
    */
  private def checkAliasKeyword(ate: AliasedTypeExpression, target: Type): Unit =
    val written = ate.keyword.toLowerCase
    if written.nonEmpty && written != "type" then
      val declared: String = target.typEx match
        case auc: AggregateUseCaseTypeExpression => auc.usecase.useCase.toLowerCase
        case _                                   => "type"
      if written != declared then
        val name = ate.pathId.format
        messages.addError(
          ate.loc,
          s"'$name' is declared ${article(declared)}, but this reference names it as " +
            s"${article(written)}",
          suggestion = s"Write '$declared $name', or drop the prefix -- it is optional here, but " +
            "a prefix that is written must name what the target was declared as.",
          ruleId = Some(RuleId.WrongKeyword)
        )
  end checkAliasKeyword

  def checkTypeExpression(
    typ: TypeExpression,
    defn: Definition,
    parents: Parents
  ): this.type = {
    typ match {
      case ate @ AliasedTypeExpression(_, _, id: PathIdentifier) =>
        checkPathRef[Type](id, parents).foreach(checkAliasKeyword(ate, _))
      case mt: AggregateUseCaseTypeExpression =>
        checkAggregateUseCase(mt, defn, parents)
      case agg: Aggregation            => checkAggregation(agg)
      case alt: Alternation            => checkAlternation(alt, defn, parents)
      case set: Set                    => checkSet(set, defn, parents)
      case seq: Sequence               => checkSeq(seq, defn, parents)
      case mapping: Mapping            => checkMapping(mapping, defn, parents)
      case graph: Graph                => checkGraph(graph, defn, parents)
      case table: Table                => checkTable(table, defn, parents)
      case replica: Replica            => checkReplica(replica, defn, parents)
      case rt: RangeType               => checkRangeType(rt)
      case p: Pattern                  => checkPattern(p)
      case Enumeration(_, enumerators) => checkEnumeration(enumerators.toSeq)
      case Optional(_, tye)            => checkTypeExpression(tye, defn, parents)
      case OneOrMore(_, tye)           => checkTypeExpression(tye, defn, parents)
      case ZeroOrMore(_, tye)          => checkTypeExpression(tye, defn, parents)
      case SpecificRange(_, typex: TypeExpression, min, max) =>
        checkTypeExpression(typex, defn, parents)
        check(
          min >= 0,
          "Minimum cardinality must be non-negative",
          Error,
          typ.loc,
          suggestion = "Use a minimum cardinality of 0 or greater.",
          ruleId = Some(RuleId.NegativeMinCardinality)
        )
        check(
          max >= 0,
          "Maximum cardinality must be non-negative",
          Error,
          typ.loc,
          suggestion = "Use a maximum cardinality of 0 or greater.",
          ruleId = Some(RuleId.NegativeMaxCardinality)
        )
        check(
          min < max,
          "Minimum cardinality must be less than maximum cardinality",
          Error,
          typ.loc,
          suggestion = "Make the minimum cardinality strictly less than the maximum (e.g. {1..5}).",
          ruleId = Some(RuleId.MinExceedsMax)
        )
      case UniqueId(loc, pid, kindKeyword) =>
        checkPathRef[Processor[?]](pid, parents)
        // The keyword must TELL THE TRUTH. Resolution has already run, so the referent's
        // real kind is available; a keyword that contradicts it is worse than no keyword,
        // because a reader believes it.
        kindKeyword.foreach { kw =>
          uniqueIdReferent(pid, defn, parents).foreach { referent =>
            val actual = processorKeywordOf(referent)
            check(
              actual == kw,
              s"Id names ${referent.identify}, which is a $actual, but it is declared as '$kw'",
              Error,
              loc,
              suggestion = s"Write 'Id($actual ${pid.format})' or drop the keyword.",
              ruleId = Some(RuleId.IdNamesNonProcessor)
            )
          }
        }
      case Decimal(loc, whole, fractional) =>
        check(
          whole >= 1,
          "The whole number part must be positive",
          Error,
          loc,
          suggestion =
            "Specify a whole-number part of at least 1 for the Decimal, e.g. 'Decimal(10,2)'.",
          ruleId = Some(RuleId.WholePartPositive)
        )
        check(
          fractional >= 1,
          "The fractional part must be positive",
          Error,
          loc,
          suggestion =
            "Specify a fractional part of at least 1 for the Decimal, e.g. 'Decimal(10,2)'.",
          ruleId = Some(RuleId.FractionPartPositive)
        )
      case EntityReferenceTypeExpression(_, pid) =>
        checkPathRef[Entity](pid, parents)
      case _: PredefinedType => () // nothing needed
    }
    this
  }
}
