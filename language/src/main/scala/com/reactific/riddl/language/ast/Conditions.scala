package com.reactific.riddl.language.ast

trait Conditions {
  this: Types with Values with Definitions with AbstractDefinitions =>

  /** Base trait for conditions */
  sealed abstract class Condition(loc: At) extends Value {
    @inline override def isEmpty: Boolean = true
    final def valueType: TypeExpression = Bool(loc)
  }

  /** A condition value for "true"
    * @param loc
    *   The location of this expression value
    */
  case class True(loc: At) extends Condition(loc) {
    override def format: String = "true"
  }

  /** A condition value for "false"
    * @param loc
    *   The location of this expression value
    */
  case class False(loc: At) extends Condition(loc) {
    override def format: String = "false"
  }

  /** Represents an arbitrary condition that is specified merely with a literal string. This can't be easily processed
    * downstream but provides the author with the ability to include arbitrary ideas/concepts into an condition
    * expression. For example in a when condition:
    * {{{
    *   example foo { when "the timer has expired" }
    * }}}
    * shows the use of an arbitrary condition for the "when" part of a Gherkin example.
    *
    * @param cond
    *   The arbitrary condition provided as a quoted string
    */
  case class ArbitraryCondition(loc: At, cond: LiteralString) extends Condition(loc) {
    override def format: String = cond.format
  }

  /** Represents a condition that is merely a reference to some Boolean value, presumably an entity state value or
    * parameter.
    *
    * @param loc
    *   The location of this condition
    * @param path
    *   The path to the value for this condition
    */
  case class ValueCondition(loc: At, path: PathIdentifier) extends Condition(loc) {
    override def format: String = "field " + path.format
  }

  /** A RIDDL Function call to the function identified by its path identifier
    * with a matching set of arguments. This
    * function must return a boolean since it is defined as a Condition.
    *
    * @param loc
    *   The location of the function call expression
    * @param name
    *   The path identifier of the RIDDL Function being called
    * @param arguments
    *   An [[ArgList]] to pass to the function.
    */
  case class FunctionCallCondition(
    loc: At,
    name: FunctionRef,
    arguments: ArgumentValues
  ) extends Condition(loc) {
    override def format: String = name.format + arguments.format
  }

  sealed trait Comparator extends RiddlNode

  case object lt extends Comparator {
    override def format: String = "<"
  }

  case object gt extends Comparator {
    override def format: String = ">"
  }

  case object le extends Comparator {
    override def format: String = "<="
  }

  case object ge extends Comparator {
    override def format: String = ">="
  }

  case object eq extends Comparator {
    override def format: String = "=="
  }

  case object ne extends Comparator {
    override def format: String = "!="
  }

  /** Represents one of the six comparison operators
    *
    * @param loc
    *   Location of the comparison
    * @param op
    *   The comparison operator
    * @param expr1
    *   The first operand in the comparison
    * @param expr2
    *   The second operand in the comparison
    */
  case class Comparison(
    loc: At,
    op: Comparator,
    val1: Value,
    val2: Value
  ) extends Condition(loc) {
    override def format: String = op.format + Seq(val1.format, val2.format)
      .mkString("(", ", ", ")")
  }

  /** Not condition
    *
    * @param loc
    *   Location of the not condition
    * @param cond1
    *   The condition being negated
    */
  case class NotCondition(loc: At, cond1: Condition) extends Condition(loc) {
    override def format: String = "not(" + cond1.format + ")"
  }

  /** Base class for conditions with two operands
    */
  abstract class MultiCondition(loc: At) extends Condition(loc) {
    def conditions: Seq[Condition]

    override def format: String = conditions
      .map(_.format)
      .mkString("(", ", ", ")")
  }

  /** And condition
    *
    * @param loc
    *   Location of the and condition
    * @param conditions
    *   The conditions (minimum 2) that must all be true for "and" to be true
    */
  case class AndCondition(loc: At, conditions: Seq[Condition]) extends MultiCondition(loc) {
    override def format: String = "and" + super.format
  }

  /** Or condition
    *
    * @param loc
    *   Location of the `or` condition
    * @param conditions
    *   The conditions (minimum 2), any one of which must be true for "Or" to be true
    */
  case class OrCondition(loc: At, conditions: Seq[Condition]) extends MultiCondition(loc) {
    override def format: String = "or" + super.format
  }

  /** Xor condition
    * @param loc
    *   Location of the `xor` condition
    * @param conditions
    *   The conditions (minimum 2), only one of which may be true for "xor" to be true.
    */
  case class XorCondition(loc: At, conditions: Seq[Condition]) extends MultiCondition(loc) {
    override def format: String = "xor" + super.format
  }

}
