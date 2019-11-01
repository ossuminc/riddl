package com.yoppworks.ossum.riddl.idea

package org.jetbrains.plugins.hocon

package lang

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.LanguageFileType
import javax.swing.Icon

/** File type for Riddl Language*/
final class RiddlFileType extends LanguageFileType(RiddlLanguage) {
  def getIcon: Icon = HoconIcon
  def getDefaultExtension: String = HoconFileType.DefaultExtension
  def getDescription = "HOCON"
  def getName = "HOCON"
}

object HoconFileType {
  val DefaultExtension = "conf"

  def isHocon(fileType: FileType): Boolean = fileType match {
    case _: HoconFileType => true
    case _                => false
  }
}
