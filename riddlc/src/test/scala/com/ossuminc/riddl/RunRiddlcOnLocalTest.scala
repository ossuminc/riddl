/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl

import com.ossuminc.riddl.commands.RunCommandSpecBase
import com.ossuminc.riddl.utils.{pc, PathUtils, Zip}
import org.scalatest.Assertion

import java.net.URL
import java.nio.file.Files
import java.nio.file.Path

/** Run a "from" command on a specific .conf file from a specific set of local paths. These tests are designed to be
  * "pending" if the given directory doesn't exist. This allows you to develop local test cases for testing RIDDL
  */
class RunRiddlcOnLocalTest extends RunCommandSpecBase {

  // NOTE: This test will succeed if the cwd or config don't exist to allow
  //  it to pass when run from GitHub workflow. Beware of false positives
  //  when using it to test locally.

  def runOnLocalProject(cwd: String, config: String, cmd: String): Assertion = {
    if Files.isDirectory(Path.of(cwd)) then {
      val fullPath = Path.of(cwd, config)
      info(s"config path is: ${fullPath.toAbsolutePath.toString}")
      if Files.isReadable(fullPath) then {
        val args = Seq("from", fullPath.toString, cmd)
        runWith(args)
      } else {
        info(s"Skipping unreadable $fullPath")
        succeed
      }
    } else {
      info(s"Skipping non-directory: $cwd")
      succeed
    }
  }

  def runOnGitHubProject(
    organization: String,
    repository: String,
    branch: String,
    pathToSrcDir: String,
    confFile: String,
    cmd: String
  ): Assertion = {
    val repo: String = s"https://github.com/$organization/$repository/archive/refs/heads/$branch.zip"
    val url: URL = java.net.URI.create(repo).toURL
    val tmpDir: Path = Files.createTempDirectory(repository)
    val srcDir: Path = tmpDir.resolve(repository + "-" + branch).resolve(pathToSrcDir)
    require(Files.isDirectory(tmpDir), "Temp directory failed to create")
    val fileName = PathUtils.copyURLToDir(url, tmpDir)
    val zip_path = tmpDir.resolve(fileName)
    Zip.unzip(zip_path, tmpDir)
    zip_path.toFile.delete()
    runOnLocalProject(srcDir.toString, confFile, cmd)
  }

  "riddlc" should {
    "validate FooBarTwoDomains" in {
      val cwd = "/Users/reid/Code/ossuminc/riddl-examples"
      val config = "src/riddl/FooBarSuccess/FooBar.conf"
      runOnLocalProject(cwd, config, "validate")
    }
    "validate on ossuminc/institutional-commerce" in {
      runOnGitHubProject(
        "ossuminc",
        "institutional-commerce",
        "main",
        "src/main/riddl",
        "ImprovingApp.conf",
        "parse" // FIXME: should be "validate"
      )
    }
    "validate riddl-examples dokn" in {
      val cwd = "/Users/reid/Code/Ossum/riddl-examples"
      val config = "src/riddl/dokn/dokn.conf"
      runOnLocalProject(cwd, config, "validate")
    }
  }
}
