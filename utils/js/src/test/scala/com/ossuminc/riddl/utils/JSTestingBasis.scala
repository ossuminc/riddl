package com.ossuminc.riddl.utils

trait JSTestingBasis extends AbstractTestingBasis {
  given io: PlatformIOContext = DOMPlatformIOContext()
}

trait JSTestingBasisWithTestData extends AbstractTestingBasisWithTestData {

  given io: PlatformIOContext = DOMPlatformIOContext()

}
