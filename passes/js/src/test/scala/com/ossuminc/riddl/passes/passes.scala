/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl

import com.ossuminc.riddl.utils.{DOMPlatformContext, PlatformContext}

package object passes {
  given pc: PlatformContext = DOMPlatformContext()
}
