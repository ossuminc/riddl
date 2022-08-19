/*
 * Copyright 2019 Reactific Software LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.reactific.riddl.language.parsing

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.Terminals.Keywords
import com.reactific.riddl.language.Terminals.Readability
import fastparse.*
import fastparse.ScalaWhitespace.*

trait HandlerParser extends GherkinParser with FunctionParser {

  def onClauseBody[u:P]: P[Seq[Example]] = {
    open ~
      ((location ~ exampleBody).map { case (l, (g, w, t, b)) =>
        Seq(Example(l, Identifier(l, ""), g, w, t, b))
      } | nonEmptyExamples | undefined(Seq.empty[Example])) ~ close
  }

  def onClause[u: P]: P[OnClause] = {
    Keywords.on ~/ location ~ messageRef ~
      onClauseBody ~ briefly ~ description
  }.map(t => (OnClause.apply _).tupled(t))

  def contextHandler[u: P]: P[Handler] = {
    P(
      Keywords.handler ~/ location ~ identifier ~ is ~
        ((open ~ undefined(Seq.empty[OnClause]) ~ close) |
          optionalNestedContent(onClause)) ~ briefly ~ description
    ).map { case (loc, id, clauses, briefly, description) =>
      Handler(loc, id, None, clauses, briefly, description)
    }
  }

  def entityHandler[u: P]: P[Handler] = {
    P(
      Keywords.handler ~/ location ~ identifier  ~
        (Readability.for_ ~ Keywords.state ~ pathIdentifier).? ~ is ~
        ((open ~ undefined(Seq.empty[OnClause]) ~ close) |
          optionalNestedContent(onClause)) ~ briefly ~ description
    ).map { case (loc, id, state, clauses, briefly, description) =>
      Handler(loc, id, state, clauses, briefly, description)
    }
  }

}
