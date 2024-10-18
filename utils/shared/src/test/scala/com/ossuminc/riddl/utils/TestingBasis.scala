package com.ossuminc.riddl.utils

import com.ossuminc.riddl.utils.ScalaPlatformIOContext
import org.scalatest.TestData
import org.scalatest.fixture
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.{FixtureAnyWordSpec, AnyWordSpec}

trait TestingBasis extends AnyWordSpec with Matchers {
  given io: PlatformIOContext = ScalaPlatformIOContext()
}

trait AbstractTestingBasisWithTestData extends FixtureAnyWordSpec with Matchers with fixture.TestDataFixture {

  import scala.language.implicitConversions

  extension (td: TestData) {
    implicit def testName: String = td.name
  }

}

trait TestingBasisWithTestData extends AbstractTestingBasisWithTestData {

  given io: PlatformIOContext = ScalaPlatformIOContext()

}
