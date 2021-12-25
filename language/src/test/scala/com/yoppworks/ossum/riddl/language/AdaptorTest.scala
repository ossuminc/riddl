package com.yoppworks.ossum.riddl.language

import com.yoppworks.ossum.riddl.language.AST.Adaptor

/** Unit Tests For ConsumerTest */
class AdaptorTest extends ParsingTest {
  "Adapters" should {
    "handle undefined body" in {
      val input = """adaptor PaymentAdapter for context Foo is {
                    |  ???
                    |}
                    |""".stripMargin
      parseDefinition[Adaptor](input) match {
        case Left(errors) =>
          val msg = errors.map(_.format).mkString
          fail(msg)
        case Right(_) => succeed
      }
    }
  }
}
