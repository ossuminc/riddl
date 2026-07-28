/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.utils

import java.io.{FileNotFoundException, IOException}
import java.nio.charset.MalformedInputException

/** Why a load did not produce content.
  *
  * These are ordinary, expected conditions — a mistyped filename, a directory named where a file
  * belongs, a binary file handed to a text parser — not crashes, so `PlatformContext.loadSafe`
  * returns them rather than throwing. `load` used to throw, and the exception travelled all the
  * way to the command-level catch-all, where a user saw
  * `[severe] Exception Thrown:: java.nio.charset.MalformedInputException: Input length = 1` —
  * a Java class name, and one that does not even say which file.
  *
  * This is an ADT rather than a String because each case wants a different diagnosis from the
  * caller: "check the path" is not the advice for "this is a directory". It cannot be
  * `Messages` — that lives in the `language` module, which depends on `utils` and not the other
  * way round — so translating a failure into a located, suggestion-bearing message is the
  * caller's job.
  *
  * Every case carries the [[URL]], because the first thing anyone asks is "which file?".
  */
enum LoadFailure:

  /** Nothing exists at this URL. Usually a typo or a stale path in a config file. */
  case NotFound(url: URL)

  /** Something is there, but it is a directory, not a file. */
  case NotAFile(url: URL)

  /** It exists and is a file, but this process may not read it. */
  case Unreadable(url: URL)

  /** The bytes are not text in the expected encoding — a binary file, or the wrong charset. */
  case Undecodable(url: URL, detail: String)

  /** Anything else: a network failure, a timeout, a malformed URL. Deliberately a catch-all so
    * that `loadSafe` has somewhere to put a surprise rather than throwing it.
    */
  case Unreachable(url: URL, detail: String)

  /** The URL this failure concerns. */
  def url: URL

  /** A short, human-readable statement of what went wrong. Callers are expected to add their own
    * location and suggestion; this is the "what", not the "what to do about it".
    */
  def describe: String = this match
    case NotFound(u)          => s"No such file: ${u.toExternalForm}"
    case NotAFile(u)          => s"Not a file (it is a directory): ${u.toExternalForm}"
    case Unreadable(u)        => s"File is not readable: ${u.toExternalForm}"
    case Undecodable(u, d)    => s"File is not valid UTF-8 text: ${u.toExternalForm} ($d)"
    case Unreachable(u, d)    => s"Could not read ${u.toExternalForm}: $d"
  end describe
end LoadFailure

object LoadFailure:

  /** Classify a thrown exception into the failure it actually represents.
    *
    * The JVM's I/O exceptions do not distinguish "is a directory" from any other `IOException`
    * except by message text, so that one is matched on the message — unpleasant, but the
    * alternative is reporting a directory as an unspecified failure.
    */
  def from(url: URL, throwable: Throwable): LoadFailure = throwable match
    case _: FileNotFoundException                                     => NotFound(url)
    case io: IOException if Option(io.getMessage).exists(_.contains("Is a directory")) =>
      NotAFile(url)
    case m: MalformedInputException =>
      Undecodable(url, Option(m.getMessage).getOrElse("malformed input"))
    case _: java.nio.charset.CharacterCodingException =>
      Undecodable(url, "character encoding error")
    case other =>
      val name = other.getClass.getSimpleName
      Unreachable(url, Option(other.getMessage).map(m => s"$name: $m").getOrElse(name))
  end from
end LoadFailure
