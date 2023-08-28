package com.reactific.riddl.language.ast

trait Values {
  this: Types with Definitions with Statements with AbstractDefinitions =>

  trait Value extends RiddlValue

  /** Represents an arbitrary value expressed as a quoted string
    *
    * @param loc
    *   The location of this expression
    * @param value
    *   The provided string value
    */
  case class ArbitraryValue(loc: At, value: LiteralString) extends Value {
    override def format: String = value.format
  }

  case class BooleanValue(loc: At, n: Boolean) extends Value {
    override def format: String = n.toString
  }

  /** An expression that is a literal constant integer value
    *
    * @param loc
    *   The location of the integer value
    * @param n
    *   The number to use as the value of the expression
    */
  case class IntegerValue(loc: At, n: BigInt) extends Value {
    override def format: String = n.toString()
  }

  /** An expression that is a liberal constant decimal value
    * @param loc
    *   The location of the decimal value
    * @param d
    *   The decimal number to use as the value of the expression
    */
  case class DecimalValue(loc: At, d: BigDecimal) extends Value {
    override def format: String = d.toString
  }

  case class ConstantValue(loc: At, pid: PathIdentifier) extends Value {
    override def format: String = "@" + pid.format
  }

  /** Represents an opoerator that is merely a reference to some value, presumably an entity state value but could also
    * be a projector or repository value.
    *
    * @param loc
    *   The location of this expression
    * @param path
    *   The path to the value for this expression
    */
  case class FieldValue(loc: At, path: PathIdentifier) extends Value {
    override def format: String = "@" + path.format
    def expressionType: TypeExpression = Abstract(loc)
  }

  /** The arguments of a [[FunctionCallValue]] and [[AggregateConstruction]] is a mapping between an argument name and
    * the expression that provides the value for that argument.
    *
    * @param args
    *   A mapping of Identifier to LiteralString to provide the arguments for the function call.
    */
  case class ParameterValues(
    loc: At,
    args: Map[Identifier, Value] = Map.empty[Identifier, Value]
  ) extends RiddlNode {
    override def format: String = args
      .map { case (id, str) =>
        id.format + "=" + str.format
      }
      .mkString("(", ", ", ")")
    override def isEmpty: Boolean = args.isEmpty
  }

  case class ComputedValue(
    loc: At,
    funcName: String,
    args: Seq[Value]
  ) extends Value {
    override def format: String =
      s"$funcName${args.map(_.format).mkString("(", ", ", ")")}"
  }

  /** Represents a literal string parsed between quote characters in the input
    *
    * @param loc
    *   The location in the input of the opening quote character
    * @param s
    *   The parsed value of the string content
    */
  case class StringValue(loc: At, s: String) extends Value {
    override def format = s"\"$s\""

    override def isEmpty: Boolean = s.isEmpty
  }

  case class FunctionCallValue(
    loc: At,
    function: FunctionRef,
    arguments: ParameterValues
  ) extends Value {
    def format: String = function.format + arguments.format
  }
}
