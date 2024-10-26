/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language

import com.ossuminc.riddl.utils.{DOMPlatformContext, PlatformContext}
package object parsing {
  given io: PlatformContext = DOMPlatformContext()
}
