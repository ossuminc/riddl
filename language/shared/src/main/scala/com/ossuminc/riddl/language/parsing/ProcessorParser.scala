package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.*
import fastparse.*
import fastparse.MultiLineWhitespace.*

trait ProcessorParser
    extends VitalDefinitionParser
    with FunctionParser
    with HandlerParser
    with StreamingParser
    with CommonParser {

  def option[u: P]: P[OptionValue] =
    P(
      Keywords.option ~/ is.? ~
        location ~ CharsWhile(ch => ch.isLower | ch.isDigit | ch == '_' | ch == '-').! ~
        (Punctuation.roundOpen ~ literalString.rep(0, Punctuation.comma) ~ Punctuation.roundClose).?
    ).map { case (loc, option, params) =>
      OptionValue(loc, option, params.getOrElse(Seq.empty[LiteralString]))
    }

  def relationshipCardinality[u: P]: P[RelationshipCardinality] =
    P(StringIn("1:1", "1:N", "N:1", "N:N").!).map {
      case s: String if s == "1:1" => RelationshipCardinality.OneToOne
      case s: String if s == "1:N" => RelationshipCardinality.OneToMany
      case s: String if s == "N:1" => RelationshipCardinality.ManyToOne
      case s: String if s == "N:N" => RelationshipCardinality.ManyToMany
    }

  def relationship[u: P]: P[Relationship] =
    P(
      location ~ Keywords.relationship ~ identifier ~/ to ~ processorRef ~ as ~ relationshipCardinality ~
        (Keywords.label ~ as ~ literalString).? ~ withMetaData
    ).map { case (loc, id, procRef, cardinality, label, descriptives) =>
      Relationship(loc, id, procRef, cardinality, label, descriptives.toContents)
    }

  def processorDefinitionContents[u: P](statementsSet: StatementsSet): P[OccursInProcessor] =
    P(
      vitalDefinitionContents | constant | invariant | function | handler(statementsSet) | option |
        streamlet | connector | relationship
    )./.asInstanceOf[P[OccursInProcessor]]
}
