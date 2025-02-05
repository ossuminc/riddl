/*
 * Copyright 2019-2025 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl

/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

import scala.concurrent.ExecutionContext

package object utils {
  given pc: PlatformContext = JVMPlatformContext()
  implicit val ec: ExecutionContext = pc.ec
}
