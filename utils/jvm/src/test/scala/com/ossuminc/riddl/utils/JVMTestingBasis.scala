package com.ossuminc.riddl.utils

trait JVMTestingBasis extends AbstractTestingBasis {
  given io: PlatformContext = JVMPlatformContext()
}

trait JVMTestingBasisWithTestData extends AbstractTestingBasisWithTestData {

  given io: PlatformContext = JVMPlatformContext()

}
