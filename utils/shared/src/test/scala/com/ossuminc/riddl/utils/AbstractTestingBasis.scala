package com.ossuminc.riddl.utils

import org.scalatest.TestData
import org.scalatest.fixture
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.{FixtureAnyWordSpec, AnyWordSpec}

trait AbstractTestingBasis extends AnyWordSpec with Matchers

trait AbstractTestingBasisWithTestData extends FixtureAnyWordSpec with Matchers with fixture.TestDataFixture {

  import scala.language.implicitConversions

  extension (td: TestData) {
    implicit def testName: String = td.name
  }

}
