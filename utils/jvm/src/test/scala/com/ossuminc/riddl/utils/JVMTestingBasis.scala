package com.ossuminc.riddl.utils

trait JVMTestingBasis extends AbstractTestingBasis {
  given io: PlatformIOContext = JVMPlatformIOContext()
}

trait JVMTestingBasisWithTestData extends AbstractTestingBasisWithTestData {

  given io: PlatformIOContext = JVMPlatformIOContext()

}
