package com.yoppworks.ossum.riddl

import com.yoppworks.ossum.riddl.RiddlOptions.*
import com.yoppworks.ossum.riddl.language.AST.RootContainer
import com.yoppworks.ossum.riddl.language.Validation.ValidatingOptions
import com.yoppworks.ossum.riddl.language.FormatTranslator
import com.yoppworks.ossum.riddl.language.Logger
import com.yoppworks.ossum.riddl.language.Riddl
import com.yoppworks.ossum.riddl.translator.hugo.HugoTranslator

import java.nio.file.Path
import scala.annotation.unused

/** RIDDL Main Program */
object RIDDLC {

  final def main(args: Array[String]): Unit = { runMain(args) }

  def runMain(args: Array[String]): Boolean = {
    try {
      RiddlOptions.parse(args) match {
        case Some(options) => options.command match {
            case Parse    => parse(options)
            case Validate => validate(options)
            case Prettify => prettify(options)
            case Hugo     => translateHugo(options)
            case OldHugo  => generateHugo(options)
            case D3       => generateD3(options)
            case _ =>
              options.log.error(s"A command must be specified as an option")
              options.log.info(RiddlOptions.usage)
              false
          }
        case None =>
          // arguments are bad, error message will have been displayed
          System.err.println("Option parsing failed, terminating.")
          false
      }
    } catch {
      case xcptn: Throwable =>
        System.err.println(xcptn.getClass.getName + ": " + xcptn.getMessage)
        false
    }
  }

  def parse(options: RiddlOptions): Boolean = {
    options.parseOptions.inputPath match {
      case Some(path) => Riddl.parse(path, options.parseOptions.parsingOptions) match {
          case None => false
          case Some(_) =>
            options.log.info("Parse completed successfully.")
            true
        }
      case None =>
        options.log.error("No input file provided in options")
        false
    }
  }

  private def parseAndValidate(
    inputPath: Option[Path],
    options: ValidatingOptions
  ): Option[RootContainer] = {
    inputPath match {
      case Some(path) => Riddl.parseAndValidate(path, options)
      case None =>
        options.log.error("No input file specified")
        None
    }
  }

  def validate(options: RiddlOptions): Boolean = {
    parseAndValidate(options.validateOptions.inputPath, options.validatingOptions).nonEmpty
  }

  def prettify(options: RiddlOptions): Boolean = {
    parseAndValidate(
      options.reformatOptions.inputPath,
      options.reformatOptions.validatingOptions
    ) match {
      case None =>
        options.log.error("Translation to prettify was cancelled due to parse or validation errors")
        false
      case Some(root) => FormatTranslator.translate(root, options.reformatOptions).nonEmpty
    }
  }

  def translateHugo(options: RiddlOptions): Boolean = {
    parseAndValidate(options.hugoOptions.inputPath, options.hugoOptions.validatingOptions) match {
      case None =>
        options.log.error("Translation to Hugo was cancelled due to parse or validation errors")
        false
      case Some(root) => HugoTranslator.translate(root, options.hugoOptions).nonEmpty
    }
  }

  def required[A](opt: Option[A])(log: Logger, errorMsg: String): Option[A] = opt match {
    case ok @ Some(_) => ok
    case None =>
      log.error(errorMsg)
      None
  }

  def printError(log: Logger, error: Throwable): Unit = {
    log.error(error.toString)
    log.error("Stack Trace:")
    error.printStackTrace(System.err)
  }

  def generateHugo(
    @unused
    options: RiddlOptions
  ): Boolean = {
    false
    /*
    val tOpts = options.translationOptions
    for {
      astRoot <- Riddl.parse(tOpts.inputPath.get, options.log, options.validationOptions)
      genOpts = GeneratorOptions(tOpts.outputPath.get.toFile.getAbsolutePath,
        tOpts.projectName.get, options.verbose)
      _ <- HugoGenerator.attempt(astRoot, genOpts).leftMap(printError(options.log,_)).toOption
    } yield {
      options.log.info("Hugo documentation generated!")
    }
    true

     */
  }

  def generateD3(options: RiddlOptions): Boolean = {
    options.log.info(s"D3 Generation from ${options.d3Options.inputPath} is not yet supported")
    false
  }
}
