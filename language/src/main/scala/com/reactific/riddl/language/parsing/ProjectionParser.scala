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
import com.reactific.riddl.language.ast.Location
import fastparse.*
import fastparse.ScalaWhitespace.*

/** Unit Tests For FunctionParser */
trait ProjectionParser extends TypeParser with HandlerParser {

  def projectionOptions[u: P]: P[Seq[ProjectionOption]] = {
    P("").map(_ => Seq.empty[ProjectionOption])
  }

  def projectionInclude[u: P]: P[Include] = {
    include[ProjectionDefinition, u](projectionDefinitions(_))
  }

  def projectionDefinitions[u: P]: P[Seq[ProjectionDefinition]] = {
    P(term | author | projectionInclude | handler).rep(0)
  }

  def projectionBody[
    u: P
  ]: P[(Seq[ProjectionOption], Aggregation, Seq[ProjectionDefinition])] = {
    P(
      undefined((
        Seq.empty[ProjectionOption],
        Aggregation(Location.empty, Seq.empty[Field]),
        Seq.empty[ProjectionDefinition]
      )) | (projectionOptions ~ aggregation ~ projectionDefinitions)
    )
  }

  /** Parses projection definitions, e.g.
    *
    * {{{
    *   projection myView is {
    *     foo: Boolean
    *     bar: Integer
    *   }
    * }}}
    */
  def projection[u: P]: P[Projection] = {
    P(
      location ~ Keywords.projection ~/ identifier ~ is ~ open ~
        projectionBody ~ close ~ briefly ~ description
    ).map {
      case (
            loc,
            id,
            (options, aggregation, definitions),
            briefly,
            description
          ) =>
        val groups = definitions.groupBy(_.getClass)
        val handlers = mapTo[Handler](groups.get(classOf[Handler]))
        val includes = mapTo[Include](groups.get(classOf[Include]))
        val authors = mapTo[Author](groups.get(classOf[Author]))
        val terms = mapTo[Term](groups.get(classOf[Term]))
        Projection(
          loc,
          id,
          aggregation,
          handlers,
          authors,
          includes,
          options,
          terms,
          briefly,
          description
        )
    }
  }
}
