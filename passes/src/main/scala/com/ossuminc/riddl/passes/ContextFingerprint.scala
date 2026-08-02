/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes

import com.ossuminc.riddl.language.AST.{Context, Definition, Domain, RiddlValue, Root}
import com.ossuminc.riddl.language.{nonEmpty, toSeq}

/** Identifies a Context within the AST by its domain path and context name, providing a stable key
  * for caching.
  */
case class ContextPath(
  domainPath: Seq[String],
  contextName: String
):
  override def toString: String =
    (domainPath :+ contextName).mkString(".")
end ContextPath

/** Fingerprint of a Context's source text, used to detect changes between validation runs.
  */
case class ContextFingerprint(
  path: ContextPath,
  hash: Long
)

object ContextFingerprint:

  /** Compute fingerprints for all Contexts in a Root. Uses the source text between loc.offset and
    * the end of the Context definition as the fingerprint input.
    */
  def computeAll(root: Root): Map[ContextPath, Long] =
    val result =
      scala.collection.mutable.Map.empty[ContextPath, Long]
    def walkDomains(
      domains: Seq[Domain],
      parentPath: Seq[String]
    ): Unit =
      domains.foreach { domain =>
        val domainPath = parentPath :+ domain.id.value
        domain.contexts.foreach { context =>
          val cp = ContextPath(domainPath, context.id.value)
          val hash = hashContext(context)
          result(cp) = hash
        }
        // Recurse into nested domains
        walkDomains(domain.domains, domainPath)
      }
    end walkDomains
    walkDomains(root.domains, Seq.empty)
    result.toMap
  end computeAll

  /** Fingerprint each domain's OWN content -- everything that is not a Context or a subdomain.
    *
    * [[computeAll]] sees only Contexts, so an edit to a definition sitting DIRECTLY in a domain (a
    * domain-level `type`, say) changed no fingerprint at all and the incremental validator
    * concluded nothing had changed, silently serving stale messages and hiding real errors. That is
    * worse than being slow, which is why this exists. Reported by riddl-vscode, which stopped using
    * the incremental validator because of it.
    *
    * Keyed by domain path. Cheap: one source-span hash per domain, not per definition.
    */
  def computeDomainOwnContent(root: Root): Map[Seq[String], Long] =
    val result = scala.collection.mutable.Map.empty[Seq[String], Long]
    def walkDomains(domains: Seq[Domain], parentPath: Seq[String]): Unit =
      domains.foreach { domain =>
        val domainPath = parentPath :+ domain.id.value
        val ownText = domain.contents.toSeq
          .collect {
            case _: Context => "" // covered by computeAll
            case _: Domain  => "" // covered by its own entry
            case value      => spanOf(value)
          }
          .mkString("\u0000")
        result(domainPath) = fnv1a64(ownText)
        walkDomains(domain.domains, domainPath)
      }
    end walkDomains
    walkDomains(root.domains, Seq.empty)
    result.toMap
  end computeDomainOwnContent

  /** The source text a value occupies, or its rendered form when the span is not usable. */
  private def spanOf(value: RiddlValue): String =
    val source = value.loc.source
    val start = value.loc.offset
    val end = value.loc.endOffset
    if start >= 0 && end > start && end <= source.data.length then source.data.substring(start, end)
    else value.format
  end spanOf

  /** Hash a Context by its source text span. Uses the FNV-1a hash for fast, cross-platform hashing
    * with good collision resistance for change detection.
    */
  private def hashContext(context: Context): Long =
    val source = context.loc.source
    val start = context.loc.offset
    val end =
      if context.contents.nonEmpty then
        val lastItem = context.contents.toSeq.last
        lastItem match
          case d: Definition => d.loc.endOffset.max(start)
          case v: RiddlValue => v.loc.endOffset.max(start)
      else context.loc.endOffset
    val text =
      if start >= 0 && end > start &&
        end <= source.data.length
      then source.data.substring(start, end)
      else context.format // fallback
    fnv1a64(text)
  end hashContext

  /** FNV-1a 64-bit hash — fast, no external deps, works on all platforms (JVM, JS, Native).
    */
  private def fnv1a64(text: String): Long =
    var hash: Long = 0xcbf29ce484222325L // FNV offset basis
    var i = 0
    while i < text.length do
      hash = hash ^ text.charAt(i).toLong
      hash = hash * 0x100000001b3L // FNV prime
      i += 1
    end while
    hash
  end fnv1a64
end ContextFingerprint
