/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.utils

import com.reactific.riddl.utils.TextFileWriter.*

import java.nio.charset.StandardCharsets

import scala.annotation.tailrec
import scala.collection.mutable

/** Unit Tests For TextFileWriter */
abstract class TextFileWriter extends OutputFile {

  override def toString: String = sb.toString

  val newLine = System.lineSeparator()
  def nl: this.type = { sb.append(newLine); this }

  def fillTemplateFromResource(
    resourceName: String,
    substitutions: Map[String, String]
  ): Unit = {
    val src = this.getClass.getClassLoader.getResourceAsStream(resourceName)
    require(src != null, s"Failed too load '$resourceName' as a stream")
    val templateBytes = src.readAllBytes()
    val template = new String(templateBytes, StandardCharsets.UTF_8)
    val result = substitute(template, substitutions)
    sb.append(result)
  }
}

object TextFileWriter {

  def substitute(
    template: String,
    substitutions: Map[String, String]
  ): String = {
    val textLength = template.length
    val builder = new mutable.StringBuilder(textLength)

    @tailrec def loop(text: String): mutable.StringBuilder = {
      if (text.isEmpty) { builder }
      else if (text.startsWith("${")) {
        val endBrace = text.indexOf("}")
        if (endBrace < 0) { builder.append(text) }
        else {
          val replacement = substitutions.get(text.substring(2, endBrace))
            .orNull
          if (replacement != null) {
            builder.append(replacement)
            loop(text.substring(endBrace + 1))
          } else {
            builder.append("${")
            loop(text.substring(1))
          }
        }
      } else {
        val brace = text.indexOf("${")
        if (brace < 0) { builder.append(text) }
        else {
          builder.append(text.substring(0, brace))
          loop(text.substring(brace))
        }
      }
    }
    loop(template).toString()
  }
}
