package com.yoppworks.ossum.riddl.language

import java.time.Instant
import java.io.File
import java.util.UUID
import com.yoppworks.ossum.riddl.language.AST.Domain
import com.yoppworks.ossum.riddl.language.AST.RootContainer
import com.yoppworks.ossum.riddl.language.Logging.Lvl
import com.yoppworks.ossum.riddl.language.Riddl.SysLogger
import com.yoppworks.ossum.riddl.language.parsing.RiddlParserInput

import scala.io.Source

class RiddlTest extends ParsingTestBase {

  "timer" should {
    "measure the correct time" in {
      val start = Instant.parse("2007-12-03T00:00:00.00Z")
      val clock = new AdjustableClock(start)

      val printStream = StringBuildingPrintStream()
      val result = RiddlImpl.timer(clock, printStream, "MyStage", show = true) {
        clock.updateInstant(_.plusSeconds(2))
        123
      }

      result mustBe 123

      clock.instant() mustBe start.plusSeconds(2)
      printStream.mkString() mustBe "Stage 'MyStage': 2.000 seconds\n"
    }
    "not print anything" in {
      val start = Instant.parse("2007-12-03T00:00:00.00Z")
      val clock = new AdjustableClock(start)

      val printStream = StringBuildingPrintStream()
      val result = RiddlImpl.timer(clock, printStream, "MyStage", show = false) {
        clock.updateInstant(_.plusSeconds(2))
        123
      }

      result mustBe 123

      clock.instant() mustBe start.plusSeconds(2)
      printStream.mkString() mustBe ""
    }
  }

  "parse" should {
    "parse a file" in {
      val logger = new InMemoryLogger
      val result = Riddl.parse(
        path = new File("language/src/test/input/rbbq.riddl").toPath,
        logger = logger,
        options = RiddlTest.DefaultOptions
      )

      result must matchPattern { case Some(_) => }
    }
    "return none when file does not exist" in {
      val logger = new InMemoryLogger
      val result = Riddl.parse(
        path = new File(UUID.randomUUID().toString).toPath,
        logger = logger,
        options = RiddlTest.DefaultOptions
      )
      result mustBe None
    }
    "record errors" in {
      val logger = new InMemoryLogger
      val riddlParserInput: RiddlParserInput = RiddlParserInput(UUID.randomUUID().toString)
      val result = Riddl
        .parse(input = riddlParserInput, logger = logger, options = RiddlTest.DefaultOptions)
      result mustBe None
      assert(
        logger.lines().exists(_.level == Lvl.Error),
        "When failing to parse, an error should be logged."
      )
    }
  }

  /** Executes a function while capturing system's stderr, return the result of the function and the
    * captured output. Switches stderr back once code block finishes or throws exception e.g.
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
    val out = System.err
    val printStream = StringBuildingPrintStream()
    try {
      System.setErr(printStream)
      val a = f()
      val output = printStream.mkString()
      (a, output)
    } finally { System.setErr(out) }
  }

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
    try {
      System.setOut(printStream)
      val a = f()
      val output = printStream.mkString()
      (a, output)
    } finally { System.setOut(out) }
  }

  "SysLogger" should {
    "print error message" in {
      capturingStdErr(() => SysLogger.error("asdf"))._2 mustBe "[error] asdf\n"
    }
    "print severe message" in {
      capturingStdErr(() => SysLogger.severe("asdf"))._2 mustBe "[severe] asdf\n"
    }
    "print warn message" in {
      capturingStdErr(() => SysLogger.warn("asdf"))._2 mustBe "[warning] asdf\n"
    }
    "print info message" in {
      capturingStdErr(() => SysLogger.info("asdf"))._2 mustBe "[info] asdf\n"
    }
    "print many message" in {
      capturingStdErr { () =>
        SysLogger.error("a")
        SysLogger.info("b")
        SysLogger.info("c")
        SysLogger.warn("d")
        SysLogger.severe("e")
        SysLogger.error("f")
      }._2 mustBe """[error] a
                    |[info] b
                    |[info] c
                    |[warning] d
                    |[severe] e
                    |[error] f
                    |""".stripMargin
    }
  }

  "parseAndValidate" should {
    "parse and validate a simple domain from path" in {
      val result = Riddl.parseAndValidate(
        new File("language/src/test/input/domains/simpleDomain.riddl").toPath,
        new InMemoryLogger,
        RiddlTest.DefaultOptions
      )
      result must matchPattern { case Some(RootContainer(Seq(_: Domain))) => }
    }
    "parse and validate nonsense file as invalid" in {
      val logger = new InMemoryLogger
      val result = Riddl.parseAndValidate(
        new File("language/src/test/input/invalid.riddl").toPath,
        logger,
        RiddlTest.DefaultOptions
      )
      result mustBe None
      assert(logger.lines().exists(_.level == Lvl.Error))
    }
    "parse and validate a simple domain from input" in {
      val content: String = {
        val source = Source.fromFile(new File("language/src/test/input/domains/simpleDomain.riddl"))
        try source.mkString
        finally source.close()
      }
      val result = Riddl
        .parseAndValidate(RiddlParserInput(content), new InMemoryLogger, RiddlTest.DefaultOptions)
      result must matchPattern { case Some(RootContainer(Seq(_: Domain))) => }
    }
    "parse and validate nonsense input as invalid" in {
      val logger = new InMemoryLogger
      val result = Riddl.parseAndValidate(
        RiddlParserInput("I am not valid riddl (hopefully)."),
        logger,
        RiddlTest.DefaultOptions
      )
      result mustBe None
      assert(logger.lines().exists(_.level == Lvl.Error))
    }
  }
}

object RiddlTest {

  val DefaultOptions: Riddl.Options = new Riddl.Options {
    override val showTimes: Boolean = true
    override val showWarnings: Boolean = true
    override val showMissingWarnings: Boolean = true
    override val showStyleWarnings: Boolean = true
  }
}
