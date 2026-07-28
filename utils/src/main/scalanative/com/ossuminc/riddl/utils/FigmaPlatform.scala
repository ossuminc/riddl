/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.utils

/** A42: Scala Native has no Figma access.
  *
  * The JVM is where builds and CI run, so that is where drift validation is implemented. Native
  * riddlc could grow an implementation over the sttp/curl stack it already links, but nothing needs
  * it yet, and a half-working one would be worse than an honest absence. The drift check therefore
  * does nothing here — exactly as it does on the JVM without a token.
  */
object FigmaPlatform:
  def defaultAccess: FigmaAccess =
    FigmaAccess.NotConfigured("Figma drift checking is only implemented on the JVM")
end FigmaPlatform
