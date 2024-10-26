package com.ossuminc.riddl.utils

trait JSTestingBasis extends AbstractTestingBasis {
  given io: PlatformContext = DOMPlatformContext()
}

trait JSTestingBasisWithTestData extends AbstractTestingBasisWithTestData {

  given io: PlatformContext = DOMPlatformContext()

}
