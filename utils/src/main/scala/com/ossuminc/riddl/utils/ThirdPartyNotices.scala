/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.utils

/** Attribution for the open-source software riddl is built upon.
  *
  * This is a hand-maintained CONSTANT rather than a file read at runtime, because it must render
  * identically on the JVM, in Scala.js and in a statically linked Scala Native binary — only the
  * JVM build has a filesystem to read from, and the native binary has no resources at all.
  *
  * The trade is that it can go stale. [[ThirdPartyNoticesTest]] pins the shape (73 columns, every
  * group present, both links) but CANNOT know that a dependency was added, so the list is
  * regenerated whenever dependencies change — see `THIRD-PARTY-NOTICES.txt` at the repository root,
  * which carries the full license texts this summary points at.
  *
  * One line per project, not per artifact: platform variants (`_3`, `_sjs1_3`, `_native0.5_3`) and
  * multi-artifact families (ScalaTest publishes 16, upickle 5, Scala Native 10) collapse to the
  * project a user would recognise. Listing artifacts instead would run past 80 lines and attribute
  * nothing extra.
  */
object ThirdPartyNotices {

  /** Where the full license texts live in the distribution. */
  val noticesFile: String = "THIRD-PARTY-NOTICES.txt"

  /** Where the full license texts live online.
    *
    * VERSION-PINNED on purpose, and not a stray path segment to be tidied away. These notices
    * describe the dependencies of THIS release; a `/latest/` page would show someone holding riddlc
    * 2.0 the notices of a future release that does not describe their artifact, which defeats the
    * point of shipping notices. ossum.tech versions each product independently
    * (`/riddl/<version>/…`), so an unversioned `/riddl/licenses/` cannot resolve at all --
    * `licenses` would have to BE the version.
    *
    * Bump this with each documented minor release. The docs site keeps every version, so older
    * binaries keep resolving. THIRD-PARTY-NOTICES.txt names the same URL in its prose and must be
    * bumped with it.
    */
  val noticesUrl: String = "https://ossum.tech/riddl/2.0/licenses/"

  /** One-line-per-project attribution, grouped by license.
    *
    * Every line is **73** columns or fewer, not 80: this is printed through `pc.log.info`, whose
    * `[info] ` prefix costs 7 columns, so 73 + 7 is what actually lands in an 80-column terminal.
    * Budgeting 80 here wrapped the widest lines on screen. A holder too long to fit continues on an
    * indented second line rather than being truncated.
    *
    * An entry carries `(JVM)`, `(JS)` or `(Native)` when that project ships in ONLY that
    * distribution — otherwise a JVM user reads `Scala Native runtime` in their own output and
    * reasonably wonders why. Projects present on two of three platforms are left unmarked; the tag
    * exists to explain the surprising single-platform entries, not to be a full matrix.
    */
  val summary: String =
    """Third-Party Software
      |════════════════════
      |riddl is built on the work of others. Our thanks to the authors and
      |maintainers of the following open-source projects. An entry marked
      |(JVM), (JS) or (Native) ships only in that distribution.
      |
      |Apache License 2.0 — https://www.apache.org/licenses/LICENSE-2.0
      |  Scala standard library          © LAMP/EPFL and Lightbend, Inc.
      |  scala-collection-compat         © LAMP/EPFL and Lightbend, Inc.
      |  scala-xml                       © LAMP/EPFL and Lightbend, Inc.
      |  Scala.js runtime library (JS)   © LAMP/EPFL
      |  Apache Commons Codec (JVM)      © The Apache Software Foundation
      |  Apache Commons IO (JVM)         © The Apache Software Foundation
      |  Apache Commons Compress (JVM)   © The Apache Software Foundation
      |  Apache Commons Lang (JVM)       © The Apache Software Foundation
      |  sconfig                         © Eric K Richardson and Lightbend, Inc.
      |  Airframe (log, json, ULID)      © Taro L. Saito
      |  sttp (Native)                   © SoftwareMill
      |
      |MIT License — https://opensource.org/licenses/MIT
      |  fastparse                       © Li Haoyi
      |  geny                            © Li Haoyi
      |  sourcecode                      © Li Haoyi
      |  upickle (ujson, upack)          © Li Haoyi
      |  scala-js-dom (JS)               © Li Haoyi and contributors
      |  scopt                           © Martin Ockajak and contributors
      |
      |BSD 3-Clause License — https://opensource.org/licenses/BSD-3-Clause
      |  scala-java-time                 © Stephen Colebourne, Michael
      |                                    Nascimento Santos, Carlos Quiroz
      |  scala-java-locales              © Carlos Quiroz
      |  cldr-api                        © Carlos Quiroz
      |  portable-scala-reflect          © The portable-scala contributors
      |  Scala Native runtime (Native)   © EPFL""".stripMargin

  /** The attribution block as it appears at the end of `riddlc info`, with pointers to the full
    * texts. Kept separate from [[summary]] so the summary can be embedded elsewhere (a docs page,
    * the notices file) without repeating the pointers.
    */
  def formatted: String =
    s"""$summary
       |
       |Full license texts: $noticesFile (in this distribution)
       |                    $noticesUrl
       |riddl itself is © 2019-2026 Ossum Inc., Apache License 2.0.""".stripMargin
}
