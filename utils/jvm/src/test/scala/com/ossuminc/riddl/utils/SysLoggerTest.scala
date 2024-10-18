package com.ossuminc.riddl.utils

import org.scalatest._
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.must.Matchers
import scala.io.AnsiColor.*

/** Unit Tests For SysLogger */
class SysLoggerTest extends AnyWordSpec with Matchers {

  /** Executes a function while capturing system's stderr, return the result of the function and the captured output.
    * Switches stderr back once code block finishes or throws exception e.g.
    * {{{
    *   val result = capturingStdErr { () =>
    *     System.err.println("hi there!")
    *     123
    *   }
    *
    *   assert(result == (123, "hi there!\n")
    * }}}
    */
  def capturingStdErr[A](f: () => A): (A, String) = {
    System.err.flush()
    val out = System.err
    val printStream = StringBuildingPrintStream()
    try {
      System.setErr(printStream)
      val a = f()
      printStream.flush()
      val output = printStream.mkString()
      (a, output)
    } finally { System.setErr(out) }
  }

  /** Executes a function while capturing system's stdout, return the result of the function and the captured output.
    * Switches stdout back once code block finishes or throws exception e.g.
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
    synchronized {
      System.out.flush()
      val out = System.out
      try {
        val printStream = StringBuildingPrintStream()
        System.setOut(printStream)
        val a = f()
        printStream.flush()
        val output = printStream.mkString()
        (a, output)
      } finally { System.setOut(out) }
    }
  }

  "SysLogger" should {
    given PlatformIOContext = ScalaPlatformIOContext()
    "print error message" in {
      val expected = s"[error] asdf\n"
      val sl = SysLogger(false)
      val captured = capturingStdOut(() => sl.error("asdf"))
      if captured._2 != expected then fail(s"Expected: $expected, Received: ${captured._2}")
      succeed
    }
    "print severe message" in {
      val expected = "[severe] asdf\n"
      val sl = SysLogger(false)
      val captured = capturingStdOut(() => sl.severe("asdf"))
      captured._2 mustBe expected
    }
    "print warning message" in {
      val expected = "[warning] asdf\n"
      val sl = SysLogger(false)
      val captured = capturingStdOut(() => sl.warn("asdf"))
      captured._2 mustBe expected
    }
    "print info message" in {
      val expected = "[info] asdf\n"
      val sl = SysLogger(false)
      val captured = capturingStdOut(() => sl.info("asdf"))
      if captured._2 != expected then fail(s"Expected: $expected, Received: ${captured._2}")
      succeed
    }
    "print many message" in {
      val sl = SysLogger(false)
      val captured = capturingStdOut { () =>
        sl.error("a")
        sl.info("b")
        sl.info("c")
        sl.warn("d")
        sl.severe("e")
        sl.error("f")
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
