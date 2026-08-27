/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.utils

/** Why a load did not produce content.
  *
  * These are ordinary, expected conditions — a mistyped filename, a directory named where a file
  * belongs, a binary file handed to a text parser — not crashes, so `PlatformContext.loadSafe`
  * returns them rather than throwing. `load` used to throw, and the exception travelled all the way
  * to the command-level catch-all, where a user saw `[severe] Exception Thrown::
  * java.nio.charset.MalformedInputException: Input length = 1` — a Java class name, and one that
  * does not even say which file.
  *
  * This is an ADT rather than a String because each case wants a different diagnosis from the
  * caller: "check the path" is not the advice for "this is a directory". It cannot be `Messages` —
  * that lives in the `language` module, which depends on `utils` and not the other way round — so
  * translating a failure into a located, suggestion-bearing message is the caller's job.
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

  /** Anything else: a network failure, a timeout, a malformed URL. Deliberately a catch-all so that
    * `loadSafe` has somewhere to put a surprise rather than throwing it.
    */
  case Unreachable(url: URL, detail: String)

  /** The URL this failure concerns. */
  def url: URL

  /** A short, human-readable statement of what went wrong. Callers are expected to add their own
    * location and suggestion; this is the "what", not the "what to do about it".
    */
  def describe: String = this match
    case NotFound(u)       => s"No such file: ${u.toExternalForm}"
    case NotAFile(u)       => s"Not a file (it is a directory): ${u.toExternalForm}"
    case Unreadable(u)     => s"File is not readable: ${u.toExternalForm}"
    case Undecodable(u, d) => s"File is not valid UTF-8 text: ${u.toExternalForm} ($d)"
    case Unreachable(u, d) => s"Could not read ${u.toExternalForm}: $d"
  end describe
end LoadFailure

object LoadFailure:

  /** Classify a thrown exception into the failure it actually represents.
    *
    * MATCHES ON CLASS NAME, NOT CLASS. This file is SHARED across JVM, Scala.js and Native, and
    * `java.io.FileNotFoundException`, `java.nio.charset.MalformedInputException` and friends are
    * not in the Scala.js javalib. Naming them here compiled fine and then failed the Scala.js
    * LINKER for any consumer whose reachable graph included this method:
    *
    * {{{
    * Referring to non-existent class java.io.FileNotFoundException
    *   called from com.ossuminc.riddl.utils.LoadFailure$.from(...)
    * }}}
    *
    * riddl's own `riddlLibJS/fullLinkJS` did NOT catch it, because dead-code elimination never
    * reached this method from riddl-lib's exports; Synapify found it, where all code is Scala.js
    * and every parse reaches here.
    *
    * Name-matching also fixes a SECOND defect the linker error hid: `DOMPlatformContext` throws its
    * own `FileNotFoundException(url)` case class, which a match on `java.io`'s type could never
    * catch, so a missing file on JS was classified `Unreachable` rather than `NotFound`. Matching
    * the simple name catches every platform's spelling of the same condition.
    *
    * The message test for "is a directory" was already stringly-typed: the JVM does not distinguish
    * that case from any other `IOException` except by message text.
    */
  def from(url: URL, throwable: Throwable): LoadFailure =
    val simpleName = throwable.getClass.getSimpleName
    val message = Option(throwable.getMessage).getOrElse("")
    if simpleName.contains("FileNotFound") || simpleName.contains("NoSuchFile") then NotFound(url)
    else if message.toLowerCase.contains("is a directory") then NotAFile(url)
    else if simpleName.contains("AccessDenied") || message.toLowerCase.contains("permission denied")
    then Unreadable(url)
    else if simpleName.contains("MalformedInput") then
      Undecodable(url, if message.isEmpty then "malformed input" else message)
    else if simpleName.contains("CharacterCoding") || simpleName.contains("Unmappable") then
      Undecodable(url, "character encoding error")
    else Unreachable(url, if message.isEmpty then simpleName else s"$simpleName: $message")
  end from
end LoadFailure
