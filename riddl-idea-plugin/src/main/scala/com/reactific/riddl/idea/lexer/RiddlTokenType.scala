package com.reactific.riddl.idea.lexer

import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import com.reactific.riddl.idea.lang.RiddlLanguage


sealed class RiddlTokenType(debugString: String) extends IElementType(debugString, RiddlLanguage)

object RiddlTokenType extends TokenType {
  final val InlineWhitespace = new RiddlTokenType("INLINE_WHITESPACE")
  final val LineBreakingWhitespace = new RiddlTokenType("LINE_BREAKING_WHITESPACE")
  final val BadCharacter = new RiddlTokenType("BAD_CHARACTER")
  final val LBrace = new RiddlTokenType("LBRACE")
  final val RBrace = new RiddlTokenType("RBRACE")
  final val LBracket = new RiddlTokenType("LBRACKET")
  final val RBracket = new RiddlTokenType("RBRACKET")
  final val LParen = new RiddlTokenType("LPAREN")
  final val RParen = new RiddlTokenType("RPAREN")
  final val Colon = new RiddlTokenType("COLON")
  final val Comma = new RiddlTokenType("COMMA")
  final val Equals = new RiddlTokenType("EQUALS")
  final val PlusEquals = new RiddlTokenType("PLUS_EQUALS")
  final val Period = new RiddlTokenType("PERIOD")
  final val SubLBrace = new RiddlTokenType("SUB_LBRACE")
  final val QMark = new RiddlTokenType("QMARK")
  final val SubRBrace = new RiddlTokenType("SUB_RBRACE")
  final val HashComment = new RiddlTokenType("HASH_COMMENT")
  final val DoubleSlashComment = new RiddlTokenType("DOUBLE_SLASH_COMMENT")
  final val VerticalBarComment = new RiddlTokenType("VERTICAL_BAR_COMMENT")
  final val UnquotedChars = new RiddlTokenType("UNQUOTED_CHARS")
  final val QuotedString = new RiddlTokenType("QUOTED_STRING")
  final val MultilineString = new RiddlTokenType("MULTILINE_STRING")
}
