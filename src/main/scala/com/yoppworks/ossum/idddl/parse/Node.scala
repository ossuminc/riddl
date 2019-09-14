package com.yoppworks.ossum.idddl.parse

sealed trait Node

object Node {

  case class Error(message: String) extends Node
  sealed trait Terminal extends Node
  sealed trait Type extends Node

  case object `type` extends Terminal
  case object domain extends Terminal
  case object command extends Terminal
  case object event extends Terminal
  case object query extends Terminal
  case object result extends Terminal
  case object lcb extends Terminal
  case object rcb extends Terminal
  case object eq extends Terminal

  case class TypeError(message: String) extends Type
  case object String extends Type
  case object Boolean extends Type
  case object Number extends Type
  case object Id extends Type
  case object Date extends Type
  case object Time extends Type
  case object TimeStamp extends Type
  case object URL extends Type
  case class Rename(to: Type) extends Type
  case class Enumeration(of: Seq[String]) extends Type
  case class Alternation(of: Seq[Type]) extends Type
  case class Aggregation(of: Map[String, Type]) extends Type
  case class Optional(element: Type) extends Type
  case class Required(element: Type) extends Type
  case class Tuple(elements: Seq[Type]) extends Type
  case class ZeroOrMore(of: Type) extends Type
  case class OneOrMore(of: Type) extends Type
}
