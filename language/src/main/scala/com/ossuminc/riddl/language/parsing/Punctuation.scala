package com.ossuminc.riddl.language.parsing

object Punctuation {
  final val asterisk = "*"
  final val at = "@"
  final val comma = ","
  final val colon = ":"
  final val curlyOpen = "{"
  final val curlyClose = "}"
  final val dot = "."
  final val equalsSign = "="
  final val ellipsis = "..."
  final val ellipsisQuestion = "...?"
  final val exclamation = "!"
  final val plus = "+"
  final val question = "?"
  final val quote = "\""
  final val roundOpen = "("
  final val roundClose = ")"
  final val squareOpen = "["
  final val squareClose = "]"
  final val undefinedMark = "???"
  final val verticalBar = "|"

  val all: Seq[String] = Seq(
    comma,
    colon,
    dot,
    equalsSign,
    quote,
    quote,
    curlyOpen,
    curlyClose,
    roundOpen,
    roundClose,
    undefinedMark,
    verticalBar
  )
}
