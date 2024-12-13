/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing
import fastparse.*

object Punctuation {
  final val asterisk = "*"
  final val atSign = "@"
  final val comma = ","
  final val colon = ":"
  final val curlyOpen = "{"
  final val curlyClose = "}"
  final val dot = "."
  final val equalsSign = "="
  final val plus = "+"
  final val question = "?"
  final val quote = "\""
  final val roundOpen = "("
  final val roundClose = ")"
  final val squareOpen = "["
  final val squareClose = "]"
  final val undefinedMark = "???"
  final val verticalBar = "|"

  // NOTE: Keep this link in synch with the list in TokenStreamParser
  def allPunctuation: Seq[String] = Seq(
    asterisk,
    atSign,
    comma,
    colon,
    curlyOpen,
    curlyClose,
    dot,
    equalsSign,
    plus,
    question,
    quote,
    roundOpen,
    roundClose,
    squareOpen,
    squareClose,
    undefinedMark,
    verticalBar
  )

  def anyPunctuation[u: P]: P[Unit] = {
    P(
      StringIn(
        asterisk,
        atSign,
        comma,
        colon,
        curlyOpen,
        curlyClose,
        dot,
        equalsSign,
        plus,
        question,
        roundOpen,
        roundClose,
        squareOpen,
        squareClose,
        undefinedMark
      )
    )
  }
}
