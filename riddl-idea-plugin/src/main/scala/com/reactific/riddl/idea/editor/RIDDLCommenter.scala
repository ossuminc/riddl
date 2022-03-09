package com.reactific.riddl.idea.editor

import com.intellij.lang.CodeDocumentationAwareCommenter
import com.intellij.psi.PsiComment
import com.intellij.psi.tree.IElementType
import com.reactific.riddl.idea.lexer.RiddlTokenType

class RIDDLCommenter extends CodeDocumentationAwareCommenter {

  def getLineCommentPrefix = "//"

  def getLineCommentTokenType: IElementType = RiddlTokenType.DoubleSlashComment

  def getBlockCommentSuffix: Null = null

  def getBlockCommentPrefix: Null = null

  def getCommentedBlockCommentPrefix: Null = null

  def getCommentedBlockCommentSuffix: Null = null

  def getDocumentationCommentLinePrefix: String = null

  def getBlockCommentTokenType: IElementType = null

  def getDocumentationCommentTokenType: IElementType = null

  def isDocumentationComment(element: PsiComment): Boolean = false

  def getDocumentationCommentSuffix: String = null

  def getDocumentationCommentPrefix: String = null
}
