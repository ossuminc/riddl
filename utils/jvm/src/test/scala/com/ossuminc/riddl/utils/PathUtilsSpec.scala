/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.utils

/** Unit Tests For PathUtils */
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.must.Matchers

import java.nio.file.Path
import scala.collection.mutable

/** Tests For PathUtils */
class PathUtilsSpec extends AnyWordSpec with Matchers {

  val miss: mutable.ListBuffer[Path] = mutable.ListBuffer.empty[Path]
  val diffSize: mutable.ListBuffer[(Path, Path)] = mutable.ListBuffer
    .empty[(Path, Path)]
  val diffCont: mutable.ListBuffer[(Path, Path)] = mutable.ListBuffer
    .empty[(Path, Path)]
  def clear: Unit = {
    miss.clear()
    diffSize.clear()
    diffSize.clear()
  }
  def missing(path: Path): Boolean = {
    miss += path
    false
  }
  def differentSize(path1: Path, path2: Path): Boolean = {
    diffSize += path1 -> path2
    false
  }
  def differentContent(path1: Path, path2: Path): Boolean = {
    diffCont += path1 -> path2
    false
  }
  "PathUtils" should {
    "compare empty directories" in {
      val path1 = Path.of("utils/jvm/src/test/input/empty")
      val path2 = path1
      clear
      PathUtils.compareDirectories(path1, path2)(
        missing,
        differentSize,
        differentContent
      )
      miss mustBe empty
      diffSize mustBe empty
      diffCont mustBe empty
    }
    "compare size difference directories" in {
      val path1 = Path.of("utils/jvm/src/test/input/size1")
      val path2 = Path.of("utils/jvm/src/test/input/size2")
      clear
      PathUtils.compareDirectories(path1, path2)(
        missing,
        differentSize,
        differentContent
      )
      miss mustBe empty
      diffCont mustBe empty
      diffSize mustNot be(empty)
    }
    "compare content difference directories" in {
      val path1 = Path.of("utils/jvm/src/test/input/diff1")
      val path2 = Path.of("utils/jvm/src/test/input/diff2")
      clear
      PathUtils.compareDirectories(path1, path2)(
        missing,
        differentSize,
        differentContent
      )
      miss mustBe empty
      diffCont mustNot be(empty)
      diffSize mustBe (empty)
    }
  }
}
