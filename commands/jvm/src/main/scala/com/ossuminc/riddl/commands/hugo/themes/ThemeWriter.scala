/*
 * Copyright 2019-2025 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands.hugo.themes

import com.ossuminc.riddl.commands.hugo.HugoPass
import com.ossuminc.riddl.commands.hugo.writers.MarkdownWriter
import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.passes.{PassInput, PassesOutput}
import com.ossuminc.riddl.utils.{CommonOptions, PlatformContext}

import java.nio.file.Path

trait ThemeWriter {
  this: MarkdownWriter =>

  def themeName: String

  protected val messages: Messages.Accumulator = Messages.Accumulator.empty

  def fileHead(
    title: String,
    weight: Int,
    desc: Option[String],
    extras: Map[String, String] = Map.empty[String, String]
  ): Unit

  def containerHead(cont: Branch[?]): Unit

  def leafHead(definition: Leaf, weight: Int): Unit

  def codeBlock(items: Seq[Statements]): Unit

  def notAvailable(thing: String, title: String = "Unavailable"): Unit

  def emitMermaidDiagram(lines: Seq[String]): Unit
}

object ThemeWriter {
  def apply(
    path: Path,
    input: PassInput,
    outputs: PassesOutput,
    options: HugoPass.Options
  )(using PlatformContext): MarkdownWriter = {
    options.hugoThemeName match {
      case None                            => GeekDocWriter(path, input, outputs, options)
      case Some(GeekDocWriter.name) | None => GeekDocWriter(path, input, outputs, options)
      case Some(DotdockWriter.name)        => DotdockWriter(path, input, outputs, options)
      case Some(s)                         => GeekDocWriter(path, input, outputs, options)
    }
  }
}
