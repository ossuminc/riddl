package com.yoppworks.ossum.riddl

import com.yoppworks.ossum.riddl.RiddlOptions.loadRiddlOptions
import com.yoppworks.ossum.riddl.language.{FormatTranslator, Logger, Riddl, SysLogger}
import com.yoppworks.ossum.riddl.translator.hugo.HugoTranslator

import java.io.{PrintWriter, StringWriter}
import scala.annotation.unused

/** RIDDL Main Program */
object RIDDLC {

  final def main(args: Array[String]): Unit = { runMain(args) }

  val log: Logger = SysLogger()

  def runMain(args: Array[String]): Boolean = {
    try {
      RiddlOptions.parse(args) match {
        case Some(options) =>
          run(options)
        case None =>
          // arguments are bad, error message will have been displayed
          System.err.println("Option parsing failed, terminating.")
          false
      }
    } catch {
      case xcptn: Throwable =>
        val sw = new StringWriter
        xcptn.printStackTrace(new PrintWriter(sw))
        System.err.println(s"Exception Thrown: ${xcptn.toString}\n")
        System.err.println(sw.toString)
        false
    }
  }

  def run(options: RiddlOptions): Boolean = {
    options.command match {
      case RiddlOptions.From => from(options)
      case RiddlOptions.Parse => parse(options)
      case RiddlOptions.Validate => validate(options)
      case RiddlOptions.Prettify => prettify(options)
      case RiddlOptions.Hugo => translateHugo(options)
      case RiddlOptions.Git => translateGit(options)
      case RiddlOptions.D3 => generateD3(options)
      case _ =>
        log.error(s"A command must be specified as an option")
        log.info(RiddlOptions.usage)
        false
    }
  }

  def from(options: RiddlOptions): Boolean = {
    options.fromOptions.configFile match {
      case Some(path) =>
        loadRiddlOptions(options, path) match {
          case Some(newOptions) =>
            run(newOptions)
          case None =>
            log.error(s"Failed too load riddlc options from $path")
            false
        }
      case None =>
        log.error("No configuration file provided")
        false
    }
  }

  def parse(options: RiddlOptions): Boolean = {
    options.parseOptions.inputFile match {
      case Some(path) =>
        Riddl.parse(path, log, options.commonOptions).nonEmpty
      case None =>
        log.error("No input file provided in options")
        false
    }
  }

  def validate(
    options: RiddlOptions
  ): Boolean = {
    options.validateOptions.inputFile match {
      case Some(path) =>
        Riddl.parseAndValidate(path, log, options.commonOptions,
          options.validatingOptions).nonEmpty
      case None =>
        log.error("No input file specified for validation")
        false
    }
  }

  def prettify(options: RiddlOptions): Boolean = {
    options.reformatOptions.inputPath match {
      case Some(_) =>
        FormatTranslator.parseValidateTranslate(
          log,
          options.commonOptions,
          options.validatingOptions,
          options.reformatOptions
        ).nonEmpty
      case None =>
        log.error("No input file specified for prettify")
        false
    }
  }

  def translateGit(@unused options: RiddlOptions): Boolean = {
    log.info(s"Git Translation is not yet supported.")
    false
  }

  def translateHugo(options: RiddlOptions): Boolean = {
    options.hugoOptions.inputPath match {
      case Some(_) =>
        HugoTranslator.parseValidateTranslate(
          log,
          options.commonOptions,
          options.validatingOptions,
          options.hugoOptions
        ).nonEmpty
      case None =>
        log.error("No input file specified for hugo translation")
        false
    }
  }

  def generateD3(@unused options: RiddlOptions): Boolean = {
    log.info(s"D3 Generation  is not yet supported.")
    false
  }
}
