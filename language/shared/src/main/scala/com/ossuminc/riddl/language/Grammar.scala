/*
 * Copyright 2019-2026 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language

import scala.io.Source
import scala.util.{Try, Using}

/** Provides access to the RIDDL grammar resources bundled with this module.
  *
  * The EBNF grammar is included as a classpath resource and can be loaded
  * by any project that depends on riddl-language.
  */
object Grammar:

  /** The classpath location of the EBNF grammar file */
  val EbnfResourcePath: String = "riddl/grammar/ebnf-grammar.ebnf"

  /** Load the EBNF grammar from the classpath.
    *
    * @return
    *   The EBNF grammar as a string, or an error message if loading failed
    */
  def loadEbnfGrammar: Either[String, String] =
    val classLoader = getClass.getClassLoader
    Option(classLoader.getResourceAsStream(EbnfResourcePath)) match
      case Some(stream) =>
        Using(Source.fromInputStream(stream, "UTF-8")) { source =>
          source.mkString
        }.toEither.left.map(_.getMessage)
      case None =>
        Left(s"Grammar resource not found: $EbnfResourcePath")
  end loadEbnfGrammar

  /** Load the EBNF grammar, throwing an exception if not found.
    *
    * @return
    *   The EBNF grammar as a string
    * @throws RuntimeException
    *   if the grammar resource cannot be loaded
    */
  def loadEbnfGrammarOrThrow: String =
    loadEbnfGrammar match
      case Right(grammar) => grammar
      case Left(error)    => throw new RuntimeException(error)

end Grammar