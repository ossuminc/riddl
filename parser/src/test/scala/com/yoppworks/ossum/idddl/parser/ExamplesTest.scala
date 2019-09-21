package com.yoppworks.ossum.idddl.parser

import java.io.File

import org.scalatest.MustMatchers
import org.scalatest.WordSpec

/** Unit Tests For ExamplesTest */
class ExamplesTest extends WordSpec with MustMatchers {

  "ExamplesTest" should {
    "compile the Reactive BBQ example" in {
      val file = new File("parser/src/test/input/rbbq.idddl")
      println(file.getCanonicalPath)
      Parser.parseFile(file) match {
        case Left(str) ⇒
          fail(str)
        case Right(dom) ⇒
          succeed
      }
    }
  }
}
