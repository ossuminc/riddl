package com.ossuminc.riddl.utils

class URLTest extends AbstractTestingBasis {

  "URL" should {
    "construct a file URL from string" in {
      val url = URL("file:///this/is/a/path")
      url.scheme must be("file")
      url.authority must be(empty)
      url.basis must be(empty)
      url.path must be("this/is/a/path")
    }
    "throws on bad syntax" in {
      intercept[IllegalArgumentException] {
        URL("ftp:///file/transfer/protocol")
      }
    }
    "accepts http, https & file schemes" in {
      val url0 = URL("http://google.com/")
      val url1 = URL("file:///path/to/file")
      val url2 = URL("http://host.name/path/to/file")
      val url3 = URL("https://host.name/path/to/file")
      val url4 = URL(
      "https://raw.githubusercontent.com/ossuminc/riddl/scalaJs-support/language/jvm/src/test/input/domains/rbbq.riddl")
      val url5 = URL(
        "https://raw.githubusercontent.com/ossuminc/riddl/main/testkit/src/test/input/domains/simpleDomain2.riddl"
      )
    }
  }
}
