package com.ossuminc.riddl.language

import com.ossuminc.riddl.language.AST.Root
import com.ossuminc.riddl.utils.{Loader, URL}
import com.ossuminc.riddl.language.parsing.{RiddlParserInput,TopLevelParser} 

import scala.concurrent.{ExecutionContext, Future}
import org.scalatest.funspec.AsyncFunSpec
import org.scalatest.matchers.must.Matchers

class TopLevelParserTest extends AsyncFunSpec with Matchers:
  implicit override def executionContext: ExecutionContext = 
    scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

  describe("TopLevelParser") {
    it("do some parsing") {
      println("constructing URL")
      val url = URL("https://raw.githubusercontent.com/ossuminc/riddl-examples/main/src/riddl/dokn/dokn.riddl")
      println("creating Loader")
      val loader = Loader(url)
      println("Loading data")
      val future = loader.load
      println("Running TopLevelParser asynchronously")
      future.map { data => 
        val input = RiddlParserInput(data, "parsing")
        TopLevelParser.parseInput(input) match {
          case Left(errors) => fail(errors.format)
          case Right(root) =>
            println("Parser succeeded")
            root.domains.head.id.value must be("dokn")
        }
      }
    }
  }
end TopLevelParserTest
