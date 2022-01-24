package com.yoppworks.ossum.riddl.language.parsing

import com.yoppworks.ossum.riddl.language.AST.*
import com.yoppworks.ossum.riddl.language.Terminals.Keywords
import fastparse.*
import fastparse.ScalaWhitespace.*

/** Parser for an entity handler definition */
trait HandlerParser extends ActionParser {

  def onClause[u: P]: P[OnClause] = {
    Keywords.on ~/ location ~ messageRef ~ open ~ anyAction.rep ~ close ~ description
  }.map(t => (OnClause.apply _).tupled(t))

  def handler[u: P]: P[Handler] = {
    P(
      Keywords.handler ~/ location ~ identifier ~ is ~
        ((open ~ undefined(Seq.empty[OnClause]) ~ close) | optionalNestedContent(onClause)) ~
        description
    ).map(t => (Handler.apply _).tupled(t))
  }
}
