package com.yoppworks.ossum.riddl

import com.yoppworks.ossum.riddl.RiddlOptions._
import com.yoppworks.ossum.riddl.language.AST
import com.yoppworks.ossum.riddl.language.Generator
import com.yoppworks.ossum.riddl.language.Generator.Lines
import com.yoppworks.ossum.riddl.language.RiddlParserInput
import com.yoppworks.ossum.riddl.language.Validation
import scopt.OParser

/** RIDDL Main Program
  *
  */
object RIDDL {
  final def main(args: Array[String]): Unit = {
    try {
      OParser.parse(RiddlOptions.parser, args, RiddlOptions()) match {
        case Some(options) =>
          options.command match {
            case Parse =>
              parse(options)
            case Prettify =>
              prettify(options)
            case Validate =>
              validate(options)
            case Translate =>
              translate(options)
            case Unspecified =>
              error(s"A command is required")
          }
        case _ =>
          // arguments are bad, error message will have been displayed
          System.exit(1)
      }
    } catch {
      case xcptn: Throwable =>
        error(xcptn.getClass.getName + ": " + xcptn.getMessage, 2)
    }
  }

  def error(message: String, rc: Int = 1): Unit = {
    System.err.println(message)
    System.exit(rc)
  }

  def timer[T](stage: String, show: Boolean = true)(f: => T): T = {
    if (show) {
      val start = System.currentTimeMillis()
      val result = f
      val stop = System.currentTimeMillis()
      val delta = stop - start
      val seconds = delta / 1000
      val milliseconds = delta % 1000
      println(f"Stage '$stage': $seconds.$milliseconds%03d seconds")
      result
    } else {
      f
    }
  }

  def parse(options: RiddlOptions): Unit = {
    options.inputFile match {
      case Some(file) =>
        timer("parse", options.showTimes) {
          val input = RiddlParserInput(file)
          language.TopLevelParser.parse(input)
        } match {
          case Left(msg) =>
            println(msg)
          case Right(_) =>
            println("Success")
        }
      case None =>
        error("No input file specified")
    }
  }

  def prettify(options: RiddlOptions): Unit = {
    options.inputFile match {
      case Some(file) =>
        timer("parse", options.showTimes) {
          val input = RiddlParserInput(file)
          language.TopLevelParser.parse(input)
        } match {
          case Left(msg) =>
            println(msg)
          case Right(root: AST.RootContainer) =>
            val lines = timer("prettify", options.showTimes) {
              root.content.foldLeft(Lines()) {
                case (prior, container) =>
                  prior.append(Generator.forContainer(container))
              }
            }
            System.out.println(lines)
        }
      case None =>
        error("No input file specified")
    }
  }

  def validate(options: RiddlOptions): Unit = {
    options.inputFile match {
      case Some(file) =>
        timer("parse", options.showTimes) {
          val input = RiddlParserInput(file)
          language.TopLevelParser.parse(input)
        } match {
          case Left(msg) =>
            println(msg)
          case Right(value) =>
            val msgs = timer("validate", options.showTimes) {
              value.content.foldLeft(Validation.NoValidationMessages) {
                case (prior, container) =>
                  prior ++ Validation.validate(
                    container,
                    options.makeValidationOptions
                  )
              }
            }
            val toPrint =
              if (options.suppressWarnings)
                msgs.filterNot(_.kind.isWarning)
              else
                msgs
            toPrint.map(_.format(file.getName)).foreach { msg =>
              System.err.println(msg)
            }
        }
      case None =>
        error("No input file specified")
    }
  }
  def translate(options: RiddlOptions): Unit = {}
}
