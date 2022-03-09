package com.reactific.riddl.idea.lang

import com.intellij.icons.AllIcons
import com.intellij.openapi.fileTypes.{FileType, LanguageFileType}

import javax.swing.Icon


final class RiddlFileType extends LanguageFileType(RiddlLanguage) {
  final val RiddlIcon = AllIcons.FileTypes.Config

  def getIcon: Icon = RiddlIcon
  def getDefaultExtension: String = RiddlFileType.DefaultExtension
  def getDescription = "Reactive interface to domain definition language."
  def getName = "RIDDL"
}
object RiddlFileType {
  final val DefaultExtension = "riddl"

  def isRiddl(fileType: FileType): Boolean = fileType match {
    case _: RiddlFileType => true
    case _ => false
  }
}
