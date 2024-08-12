package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.*
import fastparse.*
import fastparse.MultiLineWhitespace.*

trait VitalDefinitionParser extends TypeParser with ReferenceParser with CommonParser{

  def vitalDefinitionContents[u: P]: P[OccursInVitalDefinition] =
    P(typeDef | comment | term | authorRef | briefDescription | blockDescription | urlDescription)
      .asInstanceOf[P[OccursInVitalDefinition]]
  end vitalDefinitionContents


}
