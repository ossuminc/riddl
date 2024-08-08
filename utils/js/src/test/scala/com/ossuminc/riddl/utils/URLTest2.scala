package com.ossuminc.riddl.utils

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.must.Matchers

import scalajs.js.annotation._

@JSExportTopLevel("URLTest")
class URLTest2 extends AnyWordSpec with Matchers {

  "URL" must {
    "throws on bad syntax" in {
      intercept[IllegalArgumentException] {
        URL("ftp:///file/transfer/protocol")
      }
    }
    "accepts http, https & file schemes" in {
      val url1 = URL("file:///path/to/file")
      val url2 = URL("http://host.name/path/to/file")
      val url3 = URL("https://host.name/path/to/file")
    }

  }
}
