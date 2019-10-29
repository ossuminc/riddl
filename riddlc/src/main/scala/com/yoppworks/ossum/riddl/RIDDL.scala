package com.yoppworks.ossum.riddl

import com.yoppworks.ossum.riddl.RiddlOptions._
import com.yoppworks.ossum.riddl.language.AST.RootContainer
import com.yoppworks.ossum.riddl.language.RiddlParserInput
import com.yoppworks.ossum.riddl.language.Validation
import com.yoppworks.ossum.riddl.translator.ParadoxTranslator
import com.yoppworks.ossum.riddl.translator.FormatTranslator
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

  def parseAndValidate(options: RiddlOptions): Option[RootContainer] = {
    options.inputFile match {
      case Some(file) =>
        timer("parse", options.showTimes) {
          val input = RiddlParserInput(file)
          language.TopLevelParser.parse(input)
        } match {
          case Left(msg) =>
            println(msg)
            None
          case Right(root) =>
            val msgs = timer("validate", options.showTimes) {
              root.contents.foldLeft(Validation.NoValidationMessages) {
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
            if (toPrint.isEmpty)
              None
            else
              Some(root)
        }
      case None =>
        error("No input file specified")
        None
    }
  }

  def validate(options: RiddlOptions): Unit = {
    parseAndValidate(options)
  }

  def translate(options: RiddlOptions): Unit = {
    if (options.configFile.isEmpty) {
      error("No translation configuration file provided")
    } else {
      parseAndValidate(options) match {
        case None =>
        case Some(root) =>
          timer("translate", options.showTimes) {
            options.outputKind match {
              case Kinds.Prettify =>
                FormatTranslator.translate(root, options.configFile.get)
              case Kinds.Paradox =>
                ParadoxTranslator.translate(root, options.configFile.get)
              case x: Kinds.Value =>
                println(s"Translation $x not yet implemented")
            }
          }
      }
    }
  }
}
