package com.yoppworks.ossum.riddl.language

/** Unit Tests For Symbols */
object Symbols {

  object Punctuation {
    val comma = ","
    val colon = ":"
    val equals = "="
    val quoteOpen = "\""
    val quoteClose = "\""
    val curlyOpen = "{"
    val curlyClose = "}"
    val squareOpen = "["
    val squareClose = "]"
    val roundOpen = "("
    val roundClose = ")"

    val all: Seq[String] = Seq(
      comma,
      colon,
      equals,
      quoteOpen,
      quoteClose,
      curlyOpen,
      curlyClose,
      squareOpen,
      squareClose,
      roundOpen,
      roundClose
    )
  }

  object Keywords {}

  object PredefinedTypes {}

  object Readability {
    val is = "is"
  }
}
