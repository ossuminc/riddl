package com.reactific.riddl.passes.validate

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.Messages.*

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
    typ1.nonEmpty && typ2.nonEmpty && (typ1.get == typ2.get)
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

  def getExpressionType(
    expr: Expression,
    parents: Seq[Definition]
  ): Option[TypeExpression] = {
    expr match {
      case NewEntityIdOperator(loc, pid)      => Some(UniqueId(loc, pid))
      case ValueOperator(_, path)             => getPathIdType(path, parents)
      case FunctionCallExpression(_, name, _) => getPathIdType(name, parents)
      case GroupExpression(loc, expressions)  =>
        // the type of a group is the last expression but it could be empty
        expressions.lastOption match {
          case None       => Some(Abstract(loc))
          case Some(expr) => getExpressionType(expr, parents)
        }
      case AggregateConstructionExpression(_, pid, _) =>
        getPathIdType(pid, parents)
      case Ternary(loc, _, expr1, expr2) =>
        val expr1Ty = getExpressionType(expr1, parents)
        val expr2Ty = getExpressionType(expr2, parents)
        if isAssignmentCompatible(expr1Ty, expr2Ty) then {
          expr1Ty
        } else {
          messages.addError(
            loc,
            s"""Ternary expressions must be assignment compatible but:
               |  ${expr1.format} and
               |  ${expr2.format}
               |are incompatible
               |""".stripMargin
          )
          None
        }
      case e: Expression => Some(e.expressionType)
    }
  }

  private def checkPattern(p: Pattern): this.type = {
    try {
      val compound = p.pattern.map(_.s).reduce(_ + _)
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
          s"Field names in ${mt.usecase.kind} should start with a lower case letter",
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

  def checkTypeExpression(
    typ: TypeExpression,
    defn: Definition,
    parents: Seq[Definition]
  ): this.type = {
    typ match {
      case AliasedTypeExpression(_, id: PathIdentifier) =>
        checkPathRef[Type](id, defn, parents)
      case mt: AggregateUseCaseTypeExpression =>
        checkAggregateUseCase(mt, defn, parents)
      case agg: Aggregation            => checkAggregation(agg)
      case alt: Alternation            => checkAlternation(alt, defn, parents)
      case set: Set                    => checkSet(set, defn, parents)
      case seq: Sequence               => checkSeq(seq, defn, parents)
      case mapping: Mapping            => checkMapping(mapping, defn, parents)
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
      case EntityReferenceTypeExpression(_, pid) =>
        checkPathRef[Entity](pid, defn, parents)
      case _: PredefinedType => this // nothing needed
      case _: TypeRef        => this // handled elsewhere
      case x =>
        require(requirement = false, s"Failed to match definition $x")
    }
    this
  }
}
