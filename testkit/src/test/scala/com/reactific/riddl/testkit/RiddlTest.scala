package com.reactific.riddl.testkit

import com.reactific.riddl.language.AST.Domain
import com.reactific.riddl.language.AST.RootContainer
import com.reactific.riddl.language.Messages.Messages
import com.reactific.riddl.language.parsing.RiddlParserInput
import com.reactific.riddl.language.*
import com.reactific.riddl.utils.StringBuildingPrintStream
import com.reactific.riddl.utils.SysLogger

import java.io.File
import java.nio.file.Path
import java.util.UUID
import scala.io.Source

class RiddlTest extends ParsingTestBase {

  "parse" should {
    "parse a file" in {
      Riddl.parse(
        path = Path.of("testkit/src/test/input/rbbq.riddl"),
        options = CommonOptions(showTimes = true)
      ) match {
        case Left(errors) => fail(errors.mkString(System.lineSeparator()))
        case Right(_)     => succeed
      }
    }

    "return an error when file does not exist" in {
      val options = CommonOptions(showTimes = true)
      val path = new File(UUID.randomUUID().toString).toPath
      Riddl.parse(path, options) match {
        case Right(root) => fail(s"File doesn't exist, can't be \n$root")
        case Left(errors) =>
          require(errors.size == 1)
          errors.head.message must include(s"$path does not exist")
      }
    }
    "record errors" in {
      val riddlParserInput: RiddlParserInput =
        RiddlParserInput(UUID.randomUUID().toString)
      val options = CommonOptions(showTimes = true)
      Riddl.parse(input = riddlParserInput, options) match {
        case Right(_) => succeed
        case Left(errors) if errors.nonEmpty =>
          require(
            errors.exists(_.kind == Messages.Error),
            "When failing to parse, an Error message should be logged."
          )
        case Left(errors) => fail(errors.mkString(System.lineSeparator()))
      }
    }
  }

  /** Executes a function while capturing system's stderr, return the result of
    * the function and the captured output. Switches stderr back once code block
    * finishes or throws exception e.g.
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

  /** Executes a function while capturing system's stdout, return the result of
    * the function and the captured output. Switches stdout back once code block
    * finishes or throws exception e.g.
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
    "print error message" in {
      capturingStdOut(() => sl.error("asdf"))._2 mustBe "[error] asdf\n"
    }
    "print severe message" in {
      capturingStdOut(() => sl.severe("asdf"))._2 mustBe "[severe] asdf\n"
    }
    "print warning message" in {
      capturingStdOut(() => sl.warn("asdf"))._2 mustBe "[warning] asdf\n"
    }
    "print info message" in {
      capturingStdOut(() => sl.info("asdf"))._2 mustBe "[info] asdf\n"
    }
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
    def runOne(pathname: String): Either[Messages, Validation.Result] = {
      val common = CommonOptions(showTimes = true)
      Riddl.parseAndValidate(new File(pathname).toPath, common)
    }

    "parse and validate a simple domain from path" in {
      runOne("testkit/src/test/input/domains/simpleDomain.riddl") match {
        case Right(result) => result must matchPattern {
            case Validation
                  .Result(_, RootContainer(Seq(_: Domain), _), _, _, _) =>
          }
        case Left(errors) => assert(errors.forall(_.kind != Messages.Error))
      }
    }

    "parse and validate nonsense file as invalid" in {
      runOne("testkit/src/test/input/invalid.riddl") match {
        case Right(root)  => fail(s"Should not have parsed, but got:\n$root")
        case Left(errors) => assert(errors.exists(_.kind == Messages.Error))
      }
    }

    "parse and validate a simple domain from input" in {
      val content: String = {
        val source = Source.fromFile(new File(
          "testkit/src/test/input/domains/simpleDomain.riddl"
        ))
        try source.mkString
        finally source.close()
      }
      val common = CommonOptions(showTimes = true)
      val input = RiddlParserInput(content)
      Riddl.parseAndValidate(input, common) match {
        case Right(result) => result must matchPattern {
            case Validation
                  .Result(_, RootContainer(Seq(_: Domain), _), _, _, _) =>
          }
        case Left(messages) => assert(messages.forall(_.kind != Messages.Error))
      }
    }

    "parse and validate nonsense input as invalid" in {
      val common = CommonOptions(showTimes = true)
      val input = RiddlParserInput("I am not valid riddl (hopefully).")
      Riddl.parseAndValidate(input, common) match {
        case Right(_)       => succeed
        case Left(messages) => assert(messages.exists(_.kind == Messages.Error))
      }
    }
  }
}
