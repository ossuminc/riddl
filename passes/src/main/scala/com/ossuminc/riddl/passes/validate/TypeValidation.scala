/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Messages.*

import java.util.regex.PatternSyntaxException

/** Unit Tests For TypeValidationState */
trait TypeValidation extends DefinitionValidation {

  def areSameType(
    tr1: Reference[Type],
    tr2: Reference[Type],
    parents: Seq[Definition]
  ): Boolean = {
    val pid1 = tr1.pathId
    val pid2 = tr2.pathId
    val typeDef1 = resolvePath[Type](pid1, parents)
    val typeDef2 = resolvePath[Type](pid2, parents)
    areSameType(typeDef1, typeDef2)
  }

  def areSameType(typ1: Option[Type], typ2: Option[Type]): Boolean = {
    val result = for {
      t1 <- typ1
      t2 <- typ2
    } yield {
      t1 == t2
    }
    result.getOrElse(false)
  }

  def isAssignmentCompatible(
    typeEx1: Option[TypeExpression],
    typeEx2: Option[TypeExpression]
  ): Boolean = {
    typeEx1 match {
      case None => false
      case Some(ty1) =>
        typeEx2 match {
          case None      => false
          case Some(ty2) => ty1.isAssignmentCompatible(ty2)
        }
    }
  }

  private def checkPattern(p: Pattern): this.type = {
    try {
      val compound = p.pattern.map(_.s).fold("") { case (a: String, b: String) => a + b }
      java.util.regex.Pattern.compile(compound)
    } catch {
      case x: PatternSyntaxException =>
        messages.add(Message(p.loc, x.getMessage))
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
          s"Enumerator '${id.format}' must start with upper case",
          StyleWarning,
          id.loc
        )
        .checkDescription[Enumerator](enumerator)
    }
    this
  }

  private def checkAlternation(
    alternation: Alternation,
    typeDef: Definition,
    parents: Seq[Definition]
  ): this.type = {
    checkSequence(alternation.of) { (typex: TypeExpression) =>
      checkTypeExpression(typex, typeDef, parents)
    }
    this
  }

  private def checkRangeType(rt: RangeType): this.type = {
    check(
      rt.min >= BigInt.long2bigInt(Long.MinValue),
      "Minimum value might be too small to store in a Long",
      Warning,
      rt.loc
    )
      .check(
        rt.max <= BigInt.long2bigInt(Long.MaxValue),
        "Maximum value might be too large to store in a Long",
        Warning,
        rt.loc
      )
  }

  private def checkAggregation(agg: Aggregation): this.type = {
    checkSequence(agg.fields) { (field: Field) =>
      checkIdentifierLength(field)
        .check(
          field.id.value.head.isLower,
          "Field names in aggregates should start with a lower case letter",
          StyleWarning,
          field.loc
        )
        .checkDescription(field)
    }
    this
  }

  private def checkAggregateUseCase(
    mt: AggregateUseCaseTypeExpression,
    typeDef: Definition,
    parents: Seq[Definition]
  ): this.type = {
    checkSequence(mt.fields) { (field: Field) =>
      checkIdentifierLength(field)
        .check(
          field.id.value.head.isLower,
          s"Field names in ${mt.usecase.useCase} should start with a lower case letter",
          StyleWarning,
          field.loc
        )
        .checkTypeExpression(field.typeEx, typeDef, parents)
        .checkDescription(field)
    }
    this
  }

  private def checkSet(set: Set, definition: Definition, parents: Seq[Definition]): Unit = {
    checkTypeExpression(set.of, definition, parents)
  }

  private def checkSeq(sequence: Sequence, definition: Definition, parents: Seq[Definition]): Unit = {
    checkTypeExpression(sequence.of, definition, parents)
  }

  private def checkMapping(
    mapping: Mapping,
    typeDef: Definition,
    parents: Seq[Definition]
  ): this.type = {
    this
      .checkTypeExpression(mapping.from, typeDef, parents)
      .checkTypeExpression(mapping.to, typeDef, parents)
  }

  private def checkGraph(
    graph: Graph,
    typeDef: Definition,
    parents: Seq[Definition]
  ): this.type = {
    this.checkTypeExpression(graph.of, typeDef, parents)
  }

  private def checkTable(
    table: Table,
    typeDef: Definition,
    parents: Seq[Definition]
  ): this.type = {
    this.checkTypeExpression(table.of, typeDef, parents)
  }

  def checkTypeExpression(
    typ: TypeExpression,
    defn: Definition,
    parents: Seq[Definition]
  ): this.type = {
    typ match {
      case AliasedTypeExpression(_, _, id: PathIdentifier) =>
        checkPathRef[Type](id, defn, parents)
      case mt: AggregateUseCaseTypeExpression =>
        checkAggregateUseCase(mt, defn, parents)
      case agg: Aggregation            => checkAggregation(agg)
      case alt: Alternation            => checkAlternation(alt, defn, parents)
      case set: Set                    => checkSet(set, defn, parents)
      case seq: Sequence               => checkSeq(seq, defn, parents)
      case mapping: Mapping            => checkMapping(mapping, defn, parents)
      case graph: Graph                => checkGraph(graph, defn, parents)
      case table: Table                => checkTable(table, defn, parents)
      case rt: RangeType               => checkRangeType(rt)
      case p: Pattern                  => checkPattern(p)
      case Enumeration(_, enumerators) => checkEnumeration(enumerators)
      case Optional(_, tye)            => checkTypeExpression(tye, defn, parents)
      case OneOrMore(_, tye)           => checkTypeExpression(tye, defn, parents)
      case ZeroOrMore(_, tye)          => checkTypeExpression(tye, defn, parents)
      case SpecificRange(_, typex: TypeExpression, min, max) =>
        checkTypeExpression(typex, defn, parents)
        check(
          min >= 0,
          "Minimum cardinality must be non-negative",
          Error,
          typ.loc
        )
        check(
          max >= 0,
          "Maximum cardinality must be non-negative",
          Error,
          typ.loc
        )
        check(
          min < max,
          "Minimum cardinality must be less than maximum cardinality",
          Error,
          typ.loc
        )
      case UniqueId(_, pid) => checkPathRef[Entity](pid, defn, parents)
      case Decimal(loc, whole, fractional) =>
        check(whole >= 1, "The whole number part must be positive", Error, loc)
        check(fractional >= 1, "The fractional part must be positive", Error, loc)
      case EntityReferenceTypeExpression(_, pid) =>
        checkPathRef[Entity](pid, defn, parents)
      case _: PredefinedType => () // nothing needed
    }
    this
  }
}
