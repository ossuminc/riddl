package com.ossuminc.riddl.prettify

import com.ossuminc.riddl.passes.translate.TranslatingOptions

import java.nio.file.Path

trait PrettifyOptions extends TranslatingOptions {
  def singleFile: Boolean = true
}
