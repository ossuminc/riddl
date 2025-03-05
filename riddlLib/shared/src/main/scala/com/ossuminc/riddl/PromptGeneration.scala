/*
 * Copyright 2019-2025 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.passes.PassesResult
import com.ossuminc.riddl.passes.symbols.{SymbolsOutput, SymbolsPass}

case class PromptGeneration(passesResult: PassesResult) {

  extension (sb: StringBuilder)
    private def description(definition: Definition): StringBuilder =
      sb
        .append("briefly described as \"")
        .append(definition.briefString)
        .append("\", fully described as \"")
        .append(definition.descriptionString)
        .append("\"")
      sb
  end extension

  def apply(definition: Definition, prompt: String): Either[Messages, String] =
    val parents: Parents = passesResult.symbols.parentsOf(definition)
    val stack: DefinitionStack = DefinitionStack(parents*)
    stack.push(definition)
    val result = new StringBuilder
    stack.foldLeft(result) { (sb: StringBuilder, definition: Definition) =>
      definition match
        case d: Domain =>
          sb.append(s"In the knowledge Domain named ")
            .append(d.id.format)
            .append(", ")
            .description(d)
            .append(",\n")
        case c: Context =>
          sb.append(s"In the context of ")
            .append(c.id.format)
            .append(",")
            .description(c)
            .append(",\n")
        case e: Entity =>
          sb.append(s"For the entity named ")
            .append(e.id.format)
            .append(",")
            .description(e)
            .append(",\n")
        // TODO: Write other cases
        case _: Definition => ()
      end match
      sb
    }
    result.append(": ").append(prompt)
    Right(result.toString)
}
