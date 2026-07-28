/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.utils

/** A42: Scala.js has no Figma access.
  *
  * The JVM is where builds and CI run, so that is where drift validation is implemented. In the
  * browser there is no environment to read a token from and a cross-origin request to the Figma API
  * would be blocked anyway; under Node the platform IO context is itself still a stub. Rather than
  * pretend, this reports plainly that it is not configured, and the drift check does nothing —
  * exactly as it does on the JVM without a token.
  */
object FigmaPlatform:
  def defaultAccess: FigmaAccess =
    FigmaAccess.NotConfigured("Figma drift checking is only implemented on the JVM")
end FigmaPlatform
