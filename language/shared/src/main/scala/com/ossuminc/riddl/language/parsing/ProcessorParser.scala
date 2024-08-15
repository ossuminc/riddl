package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.*
import fastparse.*
import fastparse.MultiLineWhitespace.*

trait ProcessorParser extends VitalDefinitionParser with FunctionParser with HandlerParser {

  def inlet[u: P]: P[Inlet] = {
    P(
      location ~ Keywords.inlet ~ identifier ~ is ~
        typeRef ~/ briefly ~ description
    )./.map { tpl => Inlet.apply.tupled(tpl) }
  }

  def outlet[u: P]: P[Outlet] = {
    P(
      location ~ Keywords.outlet ~ identifier ~ is ~
        typeRef ~/ briefly ~ description
    )./.map { tpl => Outlet.apply.tupled(tpl) }
  }

  def option[u: P]: P[OptionValue] = {
    P(
      Keywords.option ~/ is.? ~
        location ~ CharsWhile(ch => ch.isLower | ch.isDigit | ch == '_' | ch == '-').! ~
        (Punctuation.roundOpen ~ literalString.rep(0, Punctuation.comma) ~
          Punctuation.roundClose).?
    ).map { case (loc, option, params) =>
      OptionValue(loc, option, params.getOrElse(Seq.empty[LiteralString]))
    }
  }
  
  def processorDefinitionContents[u:P](statementsSet: StatementsSet): P[OccursInProcessor] =
    P(vitalDefinitionContents | constant | invariant | function | handler(statementsSet) | inlet | outlet | option)
      .asInstanceOf[P[OccursInProcessor]]
}