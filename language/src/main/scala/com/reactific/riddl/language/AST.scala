/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.language

/** Abstract Syntax Tree This object defines the model for processing RIDDL and
  * producing a raw AST from it. This raw AST has no referential integrity, it
  * just results from applying the parsing rules to the input. The RawAST models
  * produced from parsing are syntactically correct but have no semantic
  * validation. The Transformation passes convert RawAST model to AST model
  * which is referentially and semantically consistent (or the user gets an
  * error).
  */
object AST extends ast.Actions  {

  def findAuthors(
    defn: Definition,
    parents: Seq[Definition]
  ): Seq[AuthorRef] = {
    if (defn.hasAuthors) {
      defn.asInstanceOf[WithAuthors].authors
    }
    else {
      parents.find(d =>
        d.isInstanceOf[WithAuthors] && d.asInstanceOf[WithAuthors].hasAuthors
      ).map(_.asInstanceOf[WithAuthors].authors).getOrElse(Seq.empty[AuthorRef])
    }
  }


}
