package com.reactific.riddl.language.ast

trait Statements {
  this: AbstractDefinitions =>

  trait Statement extends Definition {
    def id: Identifier = Identifier.empty
    def kind: String = "Statement"
    def contents: Seq[Definition] = Seq.empty[Definition]
    def description: Option[Description] = None
    def brief: Option[LiteralString] = None
  }

  case class ArbitraryStatement(
    loc: At,
    what: LiteralString
  ) extends Statement {
    override def format: String = what.format
  }
}
