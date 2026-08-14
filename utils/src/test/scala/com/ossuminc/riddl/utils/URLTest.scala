/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

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
    // A trailing slash is how a directory URL is normally written, and `described at
    // https://ossum.tech/docs/riddl/` is the shape riddl-models hit. `isValid` rejected any
    // non-empty path ending in `/`, which made the parser's acceptance of one throw here instead
    // of producing a URL.
    //
    // Confined to http(s) on purpose: for the `file` scheme a URL names something to be READ, so a
    // trailing slash denotes a directory and would be a mistake worth catching.
    "accepts a trailing slash on an http path" in {
      val url = URL("https://ossum.tech/docs/riddl/")
      url.path must be("docs/riddl/")
      // Round trip: the slash is preserved, not normalized away, so prettify can emit what the
      // author wrote.
      url.toExternalForm must be("https://ossum.tech/docs/riddl/")
    }
    "still rejects a trailing slash on a file path" in {
      intercept[IllegalArgumentException] {
        URL(URL.fileScheme, "", "", "this/is/a/directory/")
      }
    }
    "accepts http, https & file schemes" in {
      val url0 = URL("http://google.com/")
      val url1 = URL("file:///path/to/file")
      val url2 = URL("http://host.name/path/to/file")
      val url3 = URL("https://host.name/path/to/file")
      val url4 = URL(
        "https://raw.githubusercontent.com/ossuminc/riddl/main/language/input/domains/rbbq.riddl"
      )
      val url5 = URL(
        "https://raw.githubusercontent.com/ossuminc/riddl/main/testkit//domains/simpleDomain2.riddl"
      )
    }
  }
}
