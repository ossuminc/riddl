/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl

import com.ossuminc.riddl.utils.{DOMPlatformContext, PlatformContext}

import scala.concurrent.ExecutionContext

package object utils {
  given pc: PlatformContext = DOMPlatformContext()
  implicit val ec: ExecutionContext = pc.ec
}
