package com.ossuminc.riddl.utils

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.must.Matchers

class LoaderTest extends AnyWordSpec with Matchers {

  "Loader" must {
    "load" in {
      import scala.concurrent.{Await, ExecutionContext}
      import scala.concurrent.duration.DurationInt
      import scala.concurrent.ExecutionContext.Implicits.global
      val url = URL(
        "https://raw.githubusercontent.com/ossuminc/riddl/main/language/jvm/src/test/input/domains/rbbq.riddl"
      )
      val contentF = Loader(url).load
      val content = Await.result(contentF, 5.seconds)
      // info(content)
    }
  }
}
