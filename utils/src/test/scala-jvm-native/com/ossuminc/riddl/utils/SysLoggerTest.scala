/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.utils

import org.scalatest._
import scala.io.AnsiColor.*

/** Unit Tests For SysLogger */
class SysLoggerTest extends AbstractTestingBasis with SequentialNestedSuiteExecution {

  /** Executes a function while capturing system's stdout, return the result of the function and the
    * captured output. Switches stdout back once code block finishes or throws exception e.g.
    * {{{
    *   val result = capturingStdErr { () =>
    *     System.out.println("hi there!")
    *     123
    *   }
    *
    *   assert(result == (123, "hi there!\n")
    * }}}
    */
  def capturingStdOut[A](f: () => A): (A, String) = {
    val out = System.out
    val printStream = StringBuildingPrintStream()
    synchronized {
      System.out.flush()
      try {
        System.setOut(printStream)
        val a = f()
        printStream.flush()
        val output = printStream.mkString()
        (a, output)
      } finally {
        System.setOut(out)
        printStream.close()
      }
    }
  }

  "SysLogger for error" should {
    "print error message" in {
      val expected = s"[error] asdf\n"
      pc.withLogger(SysLogger()) { sl =>
        var captured: (Unit, String) = () -> ""
        pc.withOptions(CommonOptions.noANSIMessages) { _ =>
          captured = capturingStdOut(() => pc.log.error("asdf"))
          captured._2 mustBe expected
        }
      }
    }
    Thread.`yield`()
  }
  // These three were commented out for an intermittent failure -- the capture
  // picking up "random garbage" -- which was never a bug in SysLogger. utils'
  // suites ran in parallel while capturingStdOut swapped the global System.out,
  // so a concurrent suite's output landed in the capture. `Test /
  // parallelExecution := false` for utils removes the interference, so they are
  // back on.
  "SysLogger for severe" should {
    "print severe message" in {
      val expected = "[severe] asdf\n"
      val result = pc.withLogger(SysLogger()) { (sl: SysLogger) =>
        pc.withOptions(CommonOptions.noANSIMessages) { _ =>
          val captured = capturingStdOut(() => pc.log.severe("asdf"))
          captured._2 mustBe expected
        }
      }
      result
    }
    Thread.`yield`()
  }
  "SysLogger for warning" should {
    "print warning message" in {
      val expected = "[warning] asdf\n"
      pc.withLogger(SysLogger()) { (sl: SysLogger) =>
        pc.withOptions(CommonOptions.noANSIMessages) { _ =>
          val captured = capturingStdOut(() => pc.log.warn("asdf"))
          captured._2 mustBe expected
        }
      }
    }
    Thread.`yield`()
  }
  "SysLogger for info" should {
    "print info message" in {
      val expected = "[info] asdf\n"
      pc.withLogger(SysLogger()) { (sl: SysLogger) =>
        pc.withOptions(CommonOptions.noANSIMessages) { _ =>
          val captured = capturingStdOut(() => pc.log.info("asdf"))
          captured._2 mustBe expected
        }
      }
    }
    Thread.`yield`()
  }

  "Logger for deprecation" should {
    "render a distinct [deprecated] label" in {
      pc.withOptions(CommonOptions.noANSIMessages) { _ =>
        val logger = StringLogger()
        logger.deprecate("asdf")
        logger.toString mustBe "[deprecated] asdf\n"
      }
    }
    "keep deprecations distinct from warnings in mixed output" in {
      pc.withOptions(CommonOptions.noANSIMessages) { _ =>
        val logger = StringLogger()
        logger.warn("w")
        logger.deprecate("d")
        logger.toString mustBe
          """[warning] w
            |[deprecated] d
            |""".stripMargin
      }
    }
  }

  "SysLogger for many" should {
    "print many message" in {
      pc.withLogger(SysLogger()) { (sl: SysLogger) =>
        pc.withOptions(CommonOptions.noANSIMessages) { _ =>
          val captured = capturingStdOut { () =>
            pc.log.error("a")
            pc.log.info("b")
            pc.log.info("c")
            pc.log.warn("d")
            pc.log.severe("e")
            pc.log.error("f")
          }
          captured._2 mustBe
            """[error] a
              |[info] b
              |[info] c
              |[warning] d
              |[severe] e
              |[error] f
              |""".stripMargin
        }
      }
    }
  }
  Thread.`yield`()
}
