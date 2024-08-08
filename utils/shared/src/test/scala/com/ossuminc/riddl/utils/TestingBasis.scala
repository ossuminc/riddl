package com.ossuminc.riddl.utils

import org.scalatest.{TestData, TestSuite}
import org.scalatest.fixture
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.{FixtureAnyWordSpec, AnyWordSpec}

abstract class TestingBasis extends AnyWordSpec with Matchers

abstract class TestingBasisWithTestData extends FixtureAnyWordSpec with Matchers with fixture.TestDataFixture {

  import scala.language.implicitConversions

  extension (td: TestData) {
    implicit def testName: String = td.name
  }
}
