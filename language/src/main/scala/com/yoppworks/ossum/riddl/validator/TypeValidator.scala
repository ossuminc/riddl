package com.yoppworks.ossum.riddl.validator

import com.yoppworks.ossum.riddl.parser.AST._
import com.yoppworks.ossum.riddl.parser.Traversal
import Validation._

case class TypeValidator(
  typeDef: TypeDef,
  payload: ValidationState
) extends ValidatorBase[TypeDef](typeDef)
    with Traversal.TypeTraveler[ValidationState] {

  override def open(): Unit = {
    super.open()
    check(
      typeDef.id.value.head.isUpper,
      s"Type name ${typeDef.id.value} must start with a capital letter",
      StyleWarning
    )
  }

  def visitType(ty: Type): Unit = {
    ty match {
      case _: PredefinedType =>
      case Optional(_: Location, typeName: Identifier) =>
        checkRef[TypeDefinition](typeName)
      case ZeroOrMore(_: Location, typeName: Identifier) =>
        checkRef[TypeDefinition](typeName)
      case OneOrMore(_: Location, typeName: Identifier) =>
        checkRef[TypeDefinition](typeName)
      case TypeRef(_, typeName) =>
        checkRef[TypeDefinition](typeName)
      case Aggregation(_, of) =>
        of.foreach {
          case (id: Identifier, typEx: TypeExpression) =>
            checkRef[TypeDefinition](typEx.id)
            check(
              id.value.head.isLower,
              "Field names should start with lower case",
              StyleWarning,
              typEx.loc
            )
        }
      case Alternation(_, of) =>
        of.foreach { typEx: TypeExpression =>
          checkRef[TypeDefinition](typEx.id)
        }
      case Enumeration(_, of) =>
        if (payload.isReportStyleWarnings) {
          of.foreach { id =>
            check(
              id.value.head.isUpper,
              s"Enumerator '${id.value}' must start with lower case",
              StyleWarning
            )
          }
        }
    }
  }
}
