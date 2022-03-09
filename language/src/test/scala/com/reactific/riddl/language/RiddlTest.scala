package com.reactific.riddl.language

import com.reactific.riddl.language.AST.{Domain, RootContainer}
import com.reactific.riddl.language.parsing.RiddlParserInput

import java.io.File
import java.nio.file.Path
import java.time.Instant
import java.util.UUID
import scala.io.Source

class RiddlTest extends ParsingTestBase {

  "timer" should {
    "measure the correct time" in {
      val start = Instant.parse("2007-12-03T00:00:00.00Z")
      val clock = new AdjustableClock(start)

      val logger = StringLogger()
      val result = RiddlImpl.timer(clock, logger, "MyStage", show = true) {
        clock.updateInstant(_.plusSeconds(2))
        123
      }

      result mustBe 123

      clock.instant() mustBe start.plusSeconds(2)
      logger.toString mustBe "[info] Stage 'MyStage': 2.000 seconds\n"
    }
    "not print anything" in {
      val start = Instant.parse("2007-12-03T00:00:00.00Z")
      val clock = new AdjustableClock(start)

      val printStream = StringBuildingPrintStream()
      val result = RiddlImpl.timer(clock, SysLogger(), "MyStage", show = false) {
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
      val log = InMemoryLogger()
      val result = Riddl.parse(
        path = Path.of("language/src/test/input/rbbq.riddl"),
        log,
        options = CommonOptions(showTimes = true)
      )
      result mustNot be(empty)
    }

    "return none when file does not exist" in {
      val log = InMemoryLogger()
      val options = CommonOptions(showTimes = true)
      val result = Riddl.parse(path = new File(UUID.randomUUID().toString).toPath, log, options)
      result mustBe None
    }
    "record errors" in {
      val riddlParserInput: RiddlParserInput = RiddlParserInput(UUID.randomUUID().toString)
      val logger = InMemoryLogger()
      val options = CommonOptions(showTimes = true)
      val result = Riddl.parse(input = riddlParserInput, logger, options)
      result mustBe None
      assert(
        logger.lines().exists(_.level == Logger.Error),
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
    val sl = SysLogger()
    "print error message" in { capturingStdOut(() => sl.error("asdf"))._2 mustBe "[error] asdf\n" }
    "print severe message" in {
      capturingStdOut(() => sl.severe("asdf"))._2 mustBe "[severe] asdf\n"
    }
    "print warn message" in { capturingStdOut(() => sl.warn("asdf"))._2 mustBe "[warning] asdf\n" }
    "print info message" in { capturingStdOut(() => sl.info("asdf"))._2 mustBe "[info] asdf\n" }
    "print many message" in {
      capturingStdOut { () =>
        sl.error("a")
        sl.info("b")
        sl.info("c")
        sl.warn("d")
        sl.severe("e")
        sl.error("f")
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
    def runOne(pathname: String): (Option[RootContainer], InMemoryLogger) = {
      val logger = InMemoryLogger()
      val common = CommonOptions(showTimes = true)
      Riddl.parseAndValidate(new File(pathname).toPath, logger, common) -> logger
    }

    "parse and validate a simple domain from path" in {
      val (result, logger: InMemoryLogger) = {
        runOne("language/src/test/input/domains/simpleDomain.riddl")
      }
      val errors =
      logger.lines().filter(line =>
        line.level == Logger.Severe || line.level == Logger.Error
      ).toSeq
      errors mustBe empty
      result must matchPattern { case Some(RootContainer(Seq(_: Domain))) => }
    }

    "parse and validate nonsense file as invalid" in {
      val (result, logger) = runOne("language/src/test/input/invalid.riddl")
      result mustBe None
      assert(logger.lines().exists(_.level == Logger.Error))
    }

    "parse and validate a simple domain from input" in {
      val content: String = {
        val source = Source.fromFile(new File("language/src/test/input/domains/simpleDomain.riddl"))
        try source.mkString
        finally source.close()
      }
      val logger = InMemoryLogger()
      val common = CommonOptions(showTimes = true)
      val result = Riddl.parseAndValidate(RiddlParserInput(content), logger, common)
      result must matchPattern { case Some(RootContainer(Seq(_: Domain))) => }
    }

    "parse and validate nonsense input as invalid" in {
      val logger = InMemoryLogger()
      val common = CommonOptions(showTimes = true)
      val result = Riddl
        .parseAndValidate(RiddlParserInput("I am not valid riddl (hopefully)."), logger, common)
      result mustBe None
      assert(logger.lines().exists(_.level == Logger.Error))
    }
  }
}
