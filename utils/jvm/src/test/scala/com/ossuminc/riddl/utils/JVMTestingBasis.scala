/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.utils

trait JVMTestingBasis extends AbstractTestingBasis {
  given io: PlatformContext = JVMPlatformContext()
}

trait JVMTestingBasisWithTestData extends AbstractTestingBasisWithTestData {

  given io: PlatformContext = JVMPlatformContext()

}
