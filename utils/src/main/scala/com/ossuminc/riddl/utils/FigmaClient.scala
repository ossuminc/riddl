/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.utils

/** A42: the answer to "does this Figma node exist, and what is it called?".
  *
  * Deliberately four-valued, because the ways of not finding a node are not equivalent.
  * `Unavailable` is NOT drift: a network timeout, an expired token or a rate limit tells us nothing
  * about the design. `Missing` and `FileNotFound` both are drift, but about different things — one
  * node has gone, versus the whole file has.
  */
enum FigmaLookup:
  /** The node exists; `nodeName` is the name Figma has for it. */
  case Found(nodeName: String)

  /** The API answered successfully and the node was not among the results. */
  case Missing

  /** The API denied the file itself (HTTP 404). Reported as drift, deliberately: the file has most
    * likely been deleted or moved. It is worth knowing that this reading is not certain — Figma
    * answers 404 for a file the token cannot see just as it does for one that no longer exists — so
    * the message this produces says so.
    */
  case FileNotFound(detail: String)

  /** No answer could be obtained. Never reported as drift; at most informational. */
  case Unavailable(reason: String)
end FigmaLookup

/** A42: the whole of the Figma REST surface that drift validation needs, kept to one method so the
  * network layer is thin, isolated, and trivially stubbed in tests.
  */
trait FigmaClient:
  /** Look up one node of one file. Implementations MUST NOT throw: any failure is reported as
    * [[FigmaLookup.Unavailable]] so that a build can never fail because of the network.
    */
  def lookupNode(fileKey: String, nodeId: String): FigmaLookup
end FigmaClient

/** A42: whether a [[FigmaClient]] can be had at all, and if not, why not — so the validator can say
  * something useful ("the flag is on but there is no token") instead of silently doing nothing.
  */
enum FigmaAccess:
  case Available(client: FigmaClient)
  case NotConfigured(reason: String)
end FigmaAccess

object FigmaClient:

  /** Name of the environment variable holding the Figma personal access token. */
  val TokenEnvVar: String = "FIGMA_TOKEN"

  private var overridden: Option[FigmaClient] = None

  /** Run `doIt` with `client` standing in for the platform's real client. This is the test seam:
    * every test of drift validation supplies a stub through it, so no test ever touches the
    * network. Restores the previous value in a `finally` so a throwing body cannot leak the
    * override into another suite.
    */
  def withClient[T](client: FigmaClient)(doIt: => T): T =
    val saved = overridden
    overridden = Some(client)
    try doIt
    finally overridden = saved
    end try
  end withClient

  /** The client to use, or the reason there is none. Consults the test override first, then the
    * platform default (a real HTTPS client on the JVM when a token is present; nothing anywhere
    * else — see `FigmaPlatform`).
    */
  def access: FigmaAccess =
    overridden match
      case Some(client) => FigmaAccess.Available(client)
      case None         => FigmaPlatform.defaultAccess
    end match
  end access

end FigmaClient
