package com.yoppworks.ossum.riddl

import com.yoppworks.ossum.riddl.RiddlOptions.*
import com.yoppworks.ossum.riddl.language.AST.RootContainer
import com.yoppworks.ossum.riddl.language.{FormatConfig, FormatTranslator, Riddl}
import com.yoppworks.ossum.riddl.language.Riddl.SysLogger
import com.yoppworks.ossum.riddl.generation.hugo.HugoGenerator
import com.yoppworks.ossum.riddl.generation.hugo.GeneratorOptions
import scopt.OParser
import cats.syntax.all.*

/** RIDDL Main Program
  */
object RIDDLC {

  final def main(args: Array[String]): Unit = {
    runMain(args)
  }

  def runMain(args: Array[String]): Boolean = {
    try {
      RiddlOptions.parse(args) match {
        case Some(options) =>
          options.command match {
            case Parse       => parse(options)
            case Validate    => validate(options)
            case Translate   => translate(options)
            case _ =>
              SysLogger.error(s"A command must be specified as an option")
              SysLogger.info(OParser.usage(RiddlOptions.parser))
              false
          }
        case None  =>
          // arguments are bad, error message will have been displayed
          SysLogger.error("Option parsing failed, terminating.")
          false
      }
    } catch {
      case xcptn: Throwable =>
        SysLogger.error(xcptn.getClass.getName + ": " + xcptn.getMessage)
        false
    }
  }

  def parse(options: RiddlOptions): Boolean = {
    options.inputFile match {
      case Some(file) => Riddl.parse(file.toPath, SysLogger, options) match {
          case None    => false
          case Some(_) =>
            SysLogger.info("Parse Completed."); true
        }
      case None =>
        SysLogger.error("No input file specified"); false
    }
  }

  def parseAndValidate(options: RiddlOptions): Option[RootContainer] = {
    options.inputFile match {
      case Some(file) =>
        Riddl.parseAndValidate(file.toPath, SysLogger, options)
      case None =>
        SysLogger.error("No input file specified")
        None
    }
  }

  def validate(options: RiddlOptions): Boolean = {
    parseAndValidate(options).nonEmpty
  }

  def translate(options: RiddlOptions): Boolean = {
    options.outputKind match {
      case Kinds.Prettify => prettify(options)
      case Kinds.Hugo => generateHugo(options)
      case Kinds.D3 => generateD3(options)
      case x: Kinds.Value =>
        SysLogger.error(s"Translation $x not yet implemented")
        false
    }
  }

  def prettify(options: RiddlOptions): Boolean = {
    parseAndValidate(options) match {
      case None =>
        SysLogger.error("Translation to prettify was cancelled due to parse or validation errors")
        false
      case Some(root) =>
        Riddl.timer(stage = "translate", options.showTimes) {
          val config = FormatConfig()
          val translator = new FormatTranslator
          val files = translator.translate(root, options.outputDir.map(_.toPath), SysLogger, config)
          files.nonEmpty
        }
    }
  }

  def generateHugo(options: RiddlOptions): Boolean = {
    def required[A](opt: Option[A])(errorMsg: String): Option[A] = opt match {
      case ok @ Some(_) => ok
      case None =>
        SysLogger.error(errorMsg)
        None
    }

    def printError(error: Throwable): Unit = {
      SysLogger.error(error.toString)
      SysLogger.error("Stack Trace:")
      error.printStackTrace(System.err)
    }

    for {
      inputFile <- required(options.inputFile)("No input file specified")
      outputDir <- required(options.outputDir)("No output directory specified")
      astRoot <-
        required(Riddl.parse(inputFile.toPath, SysLogger, options))("Could not parse riddl file")
      genOpts = GeneratorOptions(outputDir.getAbsolutePath, options.projectName, options.verbose)
      _ <- HugoGenerator.attempt(astRoot, genOpts).leftMap(printError).toOption
    } yield {
      SysLogger.info("Hugo documentation generated!")
    }
    true
  }

  def generateD3(options: RiddlOptions): Boolean = {
    SysLogger.info(s"D3 Generation from ${options.inputFile.map(_.getName)} is not yet supported")
    false
  }
}
