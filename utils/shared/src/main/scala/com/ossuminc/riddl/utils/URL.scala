/*
 * Copyright 2024 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.ossuminc.riddl.utils

import scala.scalajs.js
import scala.scalajs.js.annotation.*

/** A RIDDL version of a URL since the JVM version isn't useful to Javascript. This just allows
 * a simplified URL that has a scheme, authority, basis and path part.
 *
 * @param scheme
 *   The URL's scheme, either `file`, `http`, or `https`
 * @param authority
 *   The authority part providing the basic location and credentials of the resource. The full
 *   syntax should be `user@host:port`. This field must not contain a /
 * @param basis
 * The "root" path or basis of the URL. This is for compatibility with "file" scheme as taken
 * from java.nio.file.Path's functionality that the current working directory will be presumed
 * as the root of the [[path]]. You can use the [[toBasisString]] method to get the URL without
 * the [[path]] part. [[basis]] should not start with a /
 * @param path
 *   The path of the resource. Fragments (part after #) can be included in the path if necessary.
 *   This [[URL]] implementation doesn't check the validity or existence of the fragment.
 *   [[path]] should not start with a /
 * */
@JSExportTopLevel("URL")
case class URL(scheme: String="", authority: String="", basis: String="", path: String="") extends AnyRef {
  require(isValid, s"Invalid URL syntax: $toString")

  /** Determine if the URL is empty. Empty URLs are valid */
  @JSExport def isEmpty: Boolean = scheme.isEmpty && authority.isEmpty && basis.isEmpty && path.isEmpty

  /** Determine if the URL is not empty */
  @JSExport def nonEmpty: Boolean = !isEmpty

  /** Determine if the URL is valid. URLs are valid if they are empty or they have a valid scheme name
   * and the path is not empty  */
  @JSExport def isValid: Boolean = {
    isEmpty | (
      scheme.matches("(file)|(https?)") &&
        (basis.isEmpty | (basis.nonEmpty && basis.head != '/' && basis.last != '/')) &&
        (path.isEmpty | (path.nonEmpty && path.head != '/' && path.last != '/'))
      )
  }

  /** Drop the path part of the URL and return it in classic URL String format */
  @JSExport def toBasisString: String =
    if isEmpty then "" else scheme + "://" + authority + { if basis.isEmpty then "" else "/" + basis}

  /** Return the URL in classic string format */
  @JSExport override def toString: String =
    if isEmpty then "" else toBasisString + "/" + path

  /** An alternative name for toString for compatibility with java.net.URL  */
  @JSExport def toExternalForm: String = toString

  /** Get the path component of the URL */
  @JSExport def toFullPathString: String = "/" + basis + "/" + path

  @JSExport def root: URL = URL(toBasisString)

  @JSExport def parent: URL = {
    val index = path.lastIndexOf('/')
    val url_path = if (index < 0) then "" else path.substring(0,index)
    URL(scheme, authority, basis, path)
  }

  @JSExport def resolve(pathElement: String): URL = {
    require(!pathElement.startsWith("/"),"Invalid path element starts with: /")
    val p =
      if path.isEmpty then
        pathElement
      else if path.contains("/") then
        val prefix = path.substring(0, path.lastIndexOf('/'))
        prefix + "/" + pathElement
      else
        pathElement
      end if
    URL(scheme, authority, basis, p)
  }
}

@JSExportTopLevel("URL$")
object URL {
  final val fileScheme = "file"
  final val httpScheme = "http"
  final val httpsScheme = "https"
  private final val namePattern = """[A-Za-z0-9_:.+-]*""".r
  private final val authorityPattern = s"""(?<authority>(($namePattern@)?$namePattern(:[0-9]{1,5})?))""".r
  private final val pathPattern = """(?<path>[A-Za-z0-9_:.+-][A-Za-z0-9_:.+/-]*)""".r
  private final val httpPattern = s"""https?://$authorityPattern/$pathPattern?([?#][^#?/]+)?""".r
  private final val filePattern = s"file://$authorityPattern/$pathPattern".r

  /** The empty URL.  */
  @JSExport
  val empty: URL = URL("")

  /** Test if a string is a valid URL */
  @JSExport
  def isValid(url: String): Boolean =
    filePattern.matches(url) | httpPattern.matches(url)

  /** Create a URL from a string, ensuring validity first.
   * This URL constructor parses a url string for validity and then constructs the URL. In no
   * case will it construct a URL with a basis.
   * @constructor
   * @param url
   *   The string to parse into a URL. Only supports file: http: and https: schemes
   */
  @JSExport("apply")
  def apply(url:String): URL = {
    import StringHelpers._
    import scala.util.matching.Regex
    url match {
      case s: String if s.isEmpty => URL()
      case s: String if s.startsWith(fileScheme) =>
        s match {
          case filePattern(authority,_,_,_,maybePath) =>
            val path = Option(maybePath).getOrElse("")
            URL(fileScheme, authority, "", path)
          case _ =>
            throw new IllegalArgumentException(s"Invalid URL provided: $s")
        }
      case s: String if s.startsWith(httpsScheme) | s.startsWith(httpScheme) =>
        val scheme = if s.startsWith(httpsScheme) then httpsScheme else httpScheme
        s match {
          case httpPattern(authority,_,_,_,maybePath,_) =>
            val path = Option(maybePath).getOrElse("")
            URL(scheme, authority, "", path)
        }
      case _ => throw IllegalArgumentException("Invalid URL scheme")
    }
  }

  /** Construct a URL from a partial path that is interpreted as the suffix
   * to the current working directory (cwd).
   *
   * @param path
   *   The trailing path to add to the current working directory. This must not start with a '/' or an
   *   exception will be thrown.
   * @return
   *   The corresponding URL
   */
  def fromCwdPath(path: String): URL = {
    require(path.head != '/')
    val cwd = Option(System.getProperty("user.dir")).getOrElse("").drop(1)
    URL(fileScheme, "", cwd, path)
  }

  /** Construct a URL from a path string. The entire path is taken as the basis of the URL
   * so relative paths can be constructed from it. Generally only directory paths should
   * be used with this constructor.
   *
   * @param path
   *  The full path of the intended URL. This *must* start with a / or an exception will be thrown.
   * @return
   *   The corresponding URL
   */
  def fromFullPath(path: String): URL = {
    require(path.startsWith("/"))
    if path.endsWith("/") then
      URL(fileScheme, "", path.drop(1).dropRight(1), "")
    else
      val pathStr = path.drop(1)
      val lastSlash = pathStr.lastIndexOf('/')
      if lastSlash > 0 then
        val basis = pathStr.substring(0,lastSlash)
        val newPath = pathStr.substring(lastSlash+1)
        URL(fileScheme, "", basis, newPath)
      else
        URL(fileScheme, "", pathStr, "")
      end if
    end if
  }
}
