/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.utils

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** A42: the JVM Figma client's response handling, exercised against recorded payloads.
  *
  * NOT ONE TEST HERE OPENS A SOCKET. `JVMFigmaClient` takes its transport as a parameter precisely
  * so that the network can be replaced by a function returning a recorded body.
  */
class FigmaClientTest extends AnyWordSpec with Matchers {

  /** A realistic, abbreviated `GET /v1/files/{key}/nodes?ids={id}` response. Note the several other
    * `"name"` keys — a regular expression over this document would pick the wrong one.
    */
  private val response: String =
    """{"name":"Storefront Design","role":"owner","lastModified":"2026-07-01T00:00:00Z",
      |"nodes":{"12:30":{"document":{"id":"12:30","name":"Payment Screen","type":"FRAME",
      |"children":[{"id":"12:31","name":"Card Number","type":"TEXT","characters":"1234"}],
      |"absoluteBoundingBox":{"x":0,"y":-12.5,"width":375,"height":812}},
      |"components":{},"schemaVersion":0,"styles":{}}}}""".stripMargin.replace("\n", "")

  private def clientReturning(body: String): JVMFigmaClient =
    JVMFigmaClient((_, _) => FigmaTransport.Body(body))

  "JVMFigmaClient" should {

    "find the frame name at the documented path, not the first 'name' in the document" in {
      clientReturning(response).lookupNode("KEY", "12:30") mustBe
        FigmaLookup.Found("Payment Screen")
    }

    "report a node the API did not return as Missing" in {
      clientReturning("""{"name":"Storefront Design","nodes":{}}""")
        .lookupNode("KEY", "12:30") mustBe FigmaLookup.Missing
    }

    "report a null node entry as Missing" in {
      clientReturning("""{"nodes":{"12:30":null}}""")
        .lookupNode("KEY", "12:30") mustBe FigmaLookup.Missing
    }

    "report a transport failure as Unavailable, never as Missing" in {
      val client = JVMFigmaClient((_, _) => FigmaTransport.Unreachable("connect timed out"))
      client.lookupNode("KEY", "12:30") mustBe FigmaLookup.Unavailable("connect timed out")
    }

    "report a denied file as FileNotFound, distinct from both Missing and Unavailable" in {
      val client = JVMFigmaClient((_, _) => FigmaTransport.NotFound("HTTP 404 for file 'KEY'"))
      client.lookupNode("KEY", "12:30") mustBe FigmaLookup.FileNotFound("HTTP 404 for file 'KEY'")
    }

    "report an unparseable body as Unavailable, never as Missing" in {
      clientReturning("not json at all").lookupNode("KEY", "12:30") match
        case FigmaLookup.Unavailable(_) => succeed
        case other                      => fail(s"expected Unavailable, got $other")
    }

    "handle escapes in the frame name" in {
      clientReturning("""{"nodes":{"1:2":{"document":{"name":"A \"quoted\" name"}}}}""")
        .lookupNode("KEY", "1:2") mustBe FigmaLookup.Found("A \"quoted\" name")
    }
  }

  /** The single decision this layer exists to make: which HTTP statuses mean the design changed,
    * and which mean we simply could not ask.
    */
  "JVMFigmaClient.classify" should {

    "treat 200 as a body" in {
      JVMFigmaClient.classify(200, "KEY", "{}") mustBe FigmaTransport.Body("{}")
    }

    "treat 404 as NotFound, since the file itself was denied" in {
      JVMFigmaClient.classify(404, "KEY", fail("the body must not be read")) match
        case FigmaTransport.NotFound(detail) => detail must include("KEY")
        case other                           => fail(s"expected NotFound, got $other")
    }

    "treat every other failing status as Unreachable, never as drift" in {
      for status <- Seq(401, 403, 429, 500, 502, 503) do
        JVMFigmaClient.classify(status, "KEY", "") match
          case FigmaTransport.Unreachable(reason) => reason must include(status.toString)
          case other => fail(s"expected Unreachable for $status, got $other")
      end for
      succeed
    }
  }

  "FigmaClient.access" should {

    "prefer an explicitly installed client and restore the previous state afterwards" in {
      val stub = new FigmaClient {
        override def lookupNode(fileKey: String, nodeId: String): FigmaLookup =
          FigmaLookup.Found("stubbed")
      }
      FigmaClient.withClient(stub) {
        FigmaClient.access match
          case FigmaAccess.Available(c) =>
            c.lookupNode("a", "b") mustBe FigmaLookup.Found("stubbed")
          case other => fail(s"expected the stub, got $other")
      }
      // Outside the block the platform default is back in charge. Whether that is Available
      // depends on the environment, so only the restoration itself is asserted.
      FigmaClient.access match
        case FigmaAccess.Available(c)     => c mustNot be(stub)
        case FigmaAccess.NotConfigured(_) => succeed
    }

    "restore the previous client even when the body throws" in {
      val stub = new FigmaClient {
        override def lookupNode(fileKey: String, nodeId: String): FigmaLookup = FigmaLookup.Missing
      }
      an[IllegalStateException] must be thrownBy {
        FigmaClient.withClient(stub)(throw new IllegalStateException("boom"))
      }
      FigmaClient.access match
        case FigmaAccess.Available(c)     => c mustNot be(stub)
        case FigmaAccess.NotConfigured(_) => succeed
    }
  }
}
