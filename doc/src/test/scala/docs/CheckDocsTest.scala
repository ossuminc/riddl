/*
 * Copyright 2019-2024 Ossum, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package docs

import org.scalatest.Assertion
import org.scalatest.wordspec.AnyWordSpec

import java.nio.file.{Files, Path}
import scala.collection.mutable.ArrayBuffer

/** Unit Tests To Check Documentation Examples */
class CheckDocsTest extends AnyWordSpec {

  def runHugo(srcDir: Path): Assertion = {
    import scala.sys.process.*
    val lineBuffer: ArrayBuffer[String] = ArrayBuffer[String]()
    var hadErrorOutput: Boolean = false
    var hadWarningOutput: Boolean = false

    def fout(line: String): Unit = {
      lineBuffer.append(line)
      if !hadWarningOutput && line.contains("WARN") then hadWarningOutput = true
    }

    def ferr(line: String): Unit = {
      lineBuffer.append(line);
      hadErrorOutput = true
    }

    val logger = ProcessLogger(fout, ferr)
    Files.isDirectory(srcDir)
    val cwdFile = srcDir.toFile
    val proc = Process("hugo", cwd = Option(cwdFile))
    proc.!(logger) match {
      case 0 =>
        if hadErrorOutput then {
          fail("hugo wrote to stderr:\n  " + lineBuffer.mkString("\n  "))
        } else if hadWarningOutput then {
          fail("hugo issued warnings:\n  " + lineBuffer.mkString("\n  "))
        } else {
          succeed
        }
      case rc: Int =>
        fail(s"hugo run failed with rc=$rc:\n  " + lineBuffer.mkString("\n  "))
    }
  }

  "Docs" should {
    "run successfully with hugo" in {
      val srcDir = Path.of("doc/src/main/hugo")
      runHugo(srcDir)
    }
  }
}
