/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.*
import fastparse.*
import fastparse.MultiLineWhitespace.*

trait VitalDefinitionParser extends TypeParser with CommonParser {

  def vitalDefinitionContents[u: P]: P[OccursInVitalDefinition] =
    P(typeDef | comment).asInstanceOf[P[OccursInVitalDefinition]]
  end vitalDefinitionContents

}
