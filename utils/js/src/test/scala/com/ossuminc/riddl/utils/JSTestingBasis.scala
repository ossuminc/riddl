/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.utils

trait JSTestingBasis extends AbstractTestingBasis {
  given io: PlatformContext = DOMPlatformContext()
}

trait JSTestingBasisWithTestData extends AbstractTestingBasisWithTestData {

  given io: PlatformContext = DOMPlatformContext()

}
