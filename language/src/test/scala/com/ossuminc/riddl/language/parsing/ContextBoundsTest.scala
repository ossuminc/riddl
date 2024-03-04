package com.ossuminc.riddl.language.parsing

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import fastparse.*


class ContextBoundsTest extends AnyWordSpec with Matchers {
  
  def aParser[u:P]: P[String] = {
    P()
  }

}
