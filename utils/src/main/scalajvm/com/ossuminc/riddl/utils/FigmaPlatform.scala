/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.utils

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.charset.StandardCharsets
import java.net.URLEncoder
import java.time.Duration
import scala.util.control.NonFatal

/** A42: the JVM's Figma access. A real HTTPS client, but only when a token is present in the
  * environment. No token means no client, which means the drift check does nothing at all — that is
  * how an offline or unconfigured build stays green.
  */
object FigmaPlatform:

  /** How long to wait for the Figma API before giving up. Short on purpose: a slow API must slow a
    * build down by seconds, not minutes, and giving up is harmless (it yields `Unavailable`).
    */
  val Timeout: Duration = Duration.ofSeconds(10)

  def defaultAccess: FigmaAccess =
    Option(System.getenv(FigmaClient.TokenEnvVar)).map(_.trim).filter(_.nonEmpty) match
      case Some(token) => FigmaAccess.Available(JVMFigmaClient(token))
      case None =>
        FigmaAccess.NotConfigured(
          s"the ${FigmaClient.TokenEnvVar} environment variable is not set"
        )
    end match
  end defaultAccess

end FigmaPlatform

/** A42: what one HTTP exchange with Figma can come back as.
  *
  * Three-valued rather than an `Either`, because a 404 must not be lumped in with a timeout: the
  * one says the file is gone, the other says nothing at all. Collapsing them is exactly the
  * distinction this type exists to keep.
  */
enum FigmaTransport:
  /** A 200 and its body. */
  case Body(text: String)

  /** A 404: the file could not be read. */
  case NotFound(detail: String)

  /** Any other non-200, a timeout, or an exception. */
  case Unreachable(reason: String)
end FigmaTransport

/** A42: the entire network layer, and nothing else.
  *
  * `transport` maps a (fileKey, nodeId) pair to one [[FigmaTransport]] outcome. The default
  * performs the real HTTPS GET against the Figma API; injecting a different one lets tests exercise
  * response handling against recorded payloads without a socket.
  */
final class JVMFigmaClient(
  transport: (String, String) => FigmaTransport
) extends FigmaClient:

  def this(token: String) = this(JVMFigmaClient.httpTransport(token))

  override def lookupNode(fileKey: String, nodeId: String): FigmaLookup =
    transport(fileKey, nodeId) match
      case FigmaTransport.Unreachable(reason) => FigmaLookup.Unavailable(reason)
      case FigmaTransport.NotFound(detail)    => FigmaLookup.FileNotFound(detail)
      case FigmaTransport.Body(body) =>
        try
          FigmaJson.nodeName(body, nodeId) match
            case Some(name) => FigmaLookup.Found(name)
            case None       => FigmaLookup.Missing
          end match
        catch
          case NonFatal(x) =>
            FigmaLookup.Unavailable(s"the Figma response could not be understood: ${x.getMessage}")
        end try
    end match
  end lookupNode

end JVMFigmaClient

object JVMFigmaClient:

  private lazy val httpClient: HttpClient =
    HttpClient.newBuilder().connectTimeout(FigmaPlatform.Timeout).build()

  private def encode(s: String): String = URLEncoder.encode(s, StandardCharsets.UTF_8)

  /** Which outcome an HTTP status means. Separated from the GET itself so that the one decision
    * that matters — that 404 is drift and every other failure is not — can be tested without a
    * socket. `body` is by-name so it is not touched for a status that discards it.
    */
  private[utils] def classify(status: Int, fileKey: String, body: => String): FigmaTransport =
    status match
      case 200 => FigmaTransport.Body(body)
      case 404 => FigmaTransport.NotFound(s"the Figma API answered HTTP 404 for file '$fileKey'")
      case other =>
        FigmaTransport.Unreachable(s"the Figma API answered HTTP $other for file '$fileKey'")
    end match
  end classify

  /** The real GET. Every failure mode — a non-200 status, an exception, a timeout — comes back as a
    * value, never as a thrown exception: nothing here can throw at the caller.
    *
    * A 404 is singled out. It means the FILE could not be read, which the caller reports as drift
    * on the grounds that a referenced design file going away is worth knowing about. It is not a
    * certain reading — Figma answers 404 for a file the token cannot see just as it does for one
    * that has been deleted — so the diagnostic says both. Every other failure stays `Unreachable`
    * and produces nothing, because it says nothing about the design.
    */
  def httpTransport(token: String)(fileKey: String, nodeId: String): FigmaTransport =
    try
      val uri = URI.create(
        s"https://api.figma.com/v1/files/${encode(fileKey)}/nodes?ids=${encode(nodeId)}"
      )
      val request = HttpRequest
        .newBuilder(uri)
        .header("X-Figma-Token", token)
        .timeout(FigmaPlatform.Timeout)
        .GET()
        .build()
      val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
      classify(response.statusCode(), fileKey, response.body())
    catch
      case NonFatal(x) =>
        FigmaTransport.Unreachable(
          s"the Figma API could not be reached: ${x.getClass.getSimpleName}: ${x.getMessage}"
        )
    end try
  end httpTransport

end JVMFigmaClient

/** A42: just enough JSON to pull one name out of one Figma response.
  *
  * A full JSON library is not worth a new dependency on the published `riddl-utils` artifact for a
  * single field, and a regular expression over a deeply nested document with dozens of `"name"`
  * keys would be wrong rather than merely ugly. So this is a small, complete, correct reader for
  * the JSON grammar, used to walk exactly one path: `$.nodes.<nodeId>.document.name`.
  */
private[utils] object FigmaJson:

  /** The name Figma has for `nodeId`, or None if the response does not contain that node. */
  def nodeName(body: String, nodeId: String): Option[String] =
    parse(body) match
      case obj: JsonValue.Obj =>
        for
          nodes <- obj.get("nodes").collect { case o: JsonValue.Obj => o }
          node <- nodes.get(nodeId).collect { case o: JsonValue.Obj => o }
          document <- node.get("document").collect { case o: JsonValue.Obj => o }
          name <- document.get("name").collect { case JsonValue.Str(s) => s }
        yield name
      case _ => None
    end match
  end nodeName

  enum JsonValue:
    case Obj(fields: Map[String, JsonValue])
    case Arr(elements: Seq[JsonValue])
    case Str(value: String)
    case Num(value: Double)
    case Bool(value: Boolean)
    case Null

    def get(key: String): Option[JsonValue] = this match
      case Obj(fields) => fields.get(key)
      case _           => None
  end JsonValue

  def parse(text: String): JsonValue =
    val parser = new Parser(text)
    parser.skipWhitespace()
    val result = parser.value()
    result
  end parse

  private final class Parser(text: String):
    private var pos: Int = 0

    private def fail(what: String): Nothing =
      throw new IllegalArgumentException(s"invalid JSON: expected $what at offset $pos")

    def skipWhitespace(): Unit =
      while pos < text.length && (text.charAt(pos) match
          case ' ' | '\t' | '\n' | '\r' => true
          case _                        => false)
      do pos += 1
      end while
    end skipWhitespace

    private def expect(ch: Char): Unit =
      if pos >= text.length || text.charAt(pos) != ch then fail(s"'$ch'")
      pos += 1
    end expect

    def value(): JsonValue =
      skipWhitespace()
      if pos >= text.length then fail("a value")
      text.charAt(pos) match
        case '{' => obj()
        case '[' => arr()
        case '"' => JsonValue.Str(string())
        case 't' => literal("true", JsonValue.Bool(true))
        case 'f' => literal("false", JsonValue.Bool(false))
        case 'n' => literal("null", JsonValue.Null)
        case _   => number()
      end match
    end value

    private def literal(word: String, result: JsonValue): JsonValue =
      if !text.startsWith(word, pos) then fail(word)
      pos += word.length
      result
    end literal

    private def obj(): JsonValue =
      expect('{')
      skipWhitespace()
      val builder = Map.newBuilder[String, JsonValue]
      if pos < text.length && text.charAt(pos) == '}' then pos += 1
      else
        var more = true
        while more do
          skipWhitespace()
          val key = string()
          skipWhitespace()
          expect(':')
          builder += (key -> value())
          skipWhitespace()
          if pos < text.length && text.charAt(pos) == ',' then pos += 1
          else { expect('}'); more = false }
        end while
      end if
      JsonValue.Obj(builder.result())
    end obj

    private def arr(): JsonValue =
      expect('[')
      skipWhitespace()
      val builder = Seq.newBuilder[JsonValue]
      if pos < text.length && text.charAt(pos) == ']' then pos += 1
      else
        var more = true
        while more do
          builder += value()
          skipWhitespace()
          if pos < text.length && text.charAt(pos) == ',' then pos += 1
          else { expect(']'); more = false }
        end while
      end if
      JsonValue.Arr(builder.result())
    end arr

    private def string(): String =
      expect('"')
      val sb = new StringBuilder
      var done = false
      while !done do
        if pos >= text.length then fail("a closing quote")
        text.charAt(pos) match
          case '"' => pos += 1; done = true
          case '\\' =>
            pos += 1
            if pos >= text.length then fail("an escape sequence")
            text.charAt(pos) match
              case '"'  => sb.append('"'); pos += 1
              case '\\' => sb.append('\\'); pos += 1
              case '/'  => sb.append('/'); pos += 1
              case 'b'  => sb.append('\b'); pos += 1
              case 'f'  => sb.append('\f'); pos += 1
              case 'n'  => sb.append('\n'); pos += 1
              case 'r'  => sb.append('\r'); pos += 1
              case 't'  => sb.append('\t'); pos += 1
              case 'u' =>
                if pos + 4 >= text.length then fail("four hex digits")
                sb.append(Integer.parseInt(text.substring(pos + 1, pos + 5), 16).toChar)
                pos += 5
              case _ => fail("a valid escape")
            end match
          case ch => sb.append(ch); pos += 1
        end match
      end while
      sb.toString
    end string

    private def number(): JsonValue =
      val start = pos
      if pos < text.length && (text.charAt(pos) == '-' || text.charAt(pos) == '+') then pos += 1
      while pos < text.length && (text.charAt(pos) match
          case c if c.isDigit              => true
          case '.' | 'e' | 'E' | '+' | '-' => true
          case _                           => false)
      do pos += 1
      end while
      if start == pos then fail("a number")
      JsonValue.Num(text.substring(start, pos).toDouble)
    end number

  end Parser

end FigmaJson
