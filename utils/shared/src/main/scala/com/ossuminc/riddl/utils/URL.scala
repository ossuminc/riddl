package com.ossuminc.riddl.utils

import scala.scalajs.js
import scala.scalajs.js.annotation.*

/** A RIDDL version of a URL since the JVM version isn't useful to Javascript. This just allows
 * a simplified URL that has a scheme, authority, basis and path part.
 * @param scheme
 *   The URL's scheme, either `file`, `http`, or `https`
 * @param authority
 *   The authority part providing the basic location and credentials of the resource. The full
 *   syntax should be `user@host:port`. This field must not contain a /
 * @param basis
 *   The "root" path or basis of the URL. This is for compatibility with "file" scheme as taken
 *   from java.nio.file.Path's functionality that the current working directory will be presumed
 *   as the root of the [[path]]. You can use the [[toRootString]] method to get the URL without
 *   the [[path]] part. [[basis]] should not start with a /
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
    println(this.toString) 
    isEmpty | (
      scheme.matches("(file)|(https?)") &&
        (path.isEmpty | (path.nonEmpty && path.head != '/' && path.last != '/'))
      )
  }

  /** Drop the path part of the URL and return it in classic URL String format */
  @JSExport def toRootString: String =
    if isEmpty then "" else scheme + "://" + authority + { if basis.isEmpty then "" else "/" + basis}

  /** Return the URL in classic string format */
  @JSExport override def toString: String =
    if isEmpty then "" else toRootString + "/" + path

  /** An alternative name for toString for compatibility with java.net.URL  */
  @JSExport def toExternalForm: String = toString

  /** Get the path component of the URL */
  @JSExport def getFullPath: String = "/" + basis + "/" + path

  @JSExport def root: URL = URL(toRootString)

  @JSExport def parent: URL = {
    val index = path.lastIndexOf('/')
    URL(scheme, authority, basis, path.substring(0, index))
  }

  @JSExport def resolve(pathElement: String): URL = {
    require(!pathElement.contains('/'),"Invalid path element content: /")
    URL(scheme, authority, basis, path + "/" + pathElement)
  }
}

@JSExportTopLevel("URL$")
object URL {
  final val fileScheme = "file:"
  final val httpScheme = "http:"
  final val httpsScheme = "https:"
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

  /** Create a URL from a string, ensuring validity first. */
  @JSExport("apply") 
  def apply(url:String): URL = {
    import StringHelpers._
    import scala.util.matching.Regex
    url match {
      case s: String if s.isEmpty => URL()
      case s: String if s.startsWith(fileScheme) =>
        println(filePattern.pattern.pattern())
        s match {
          case filePattern(authority,_,_,_,maybePath) =>
            val path = Option(maybePath).getOrElse("")
            URL(fileScheme.dropRight(1), authority, "", path)
          case _ =>
            throw new IllegalArgumentException(s"Invalid URL provided: $s")
        }
      case s: String if s.startsWith(httpScheme) | s.startsWith(httpsScheme) =>
        val scheme = if s.startsWith(httpScheme) then httpScheme else httpsScheme
        s match {
          case httpPattern(authority,_,_,_,maybePath,_) =>
            val path = Option(maybePath).getOrElse("")
            URL(scheme.dropRight(1), authority, "", path)
        }
      case _ => throw IllegalArgumentException("Invalid URL scheme")
    }
  }
}
