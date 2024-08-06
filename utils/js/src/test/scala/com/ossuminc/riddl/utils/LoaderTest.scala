package com.ossuminc.riddl.utils

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import scala.concurrent.ExecutionContext.Implicits.global

class LoaderTest extends AnyWordSpec with Matchers {

  "Loader" must {
    "load" in {
      import scala.concurrent.ExecutionContext.Implicits.global
      import scala.concurrent.duration.DurationInt
      import scala.concurrent.{Await, ExecutionContext}
      val url = URL(
        "https://raw.githubusercontent.com/ossuminc/riddl/scalaJs-support/language/jvm/src/test/input/domains/rbbq.riddl"
      )
      val contentF = Loader(url).load.map[String] { (content: String) =>
        info(content); content
      }
    }
  }
}
