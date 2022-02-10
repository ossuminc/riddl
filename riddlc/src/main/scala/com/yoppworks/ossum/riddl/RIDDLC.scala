package com.yoppworks.ossum.riddl

import com.yoppworks.ossum.riddl.RiddlOptions.*
import com.yoppworks.ossum.riddl.language.{FormatTranslator, Logger, Riddl, SysLogger}
import com.yoppworks.ossum.riddl.translator.hugo.HugoTranslator

import java.io.{PrintWriter, StringWriter}

/** RIDDL Main Program */
object RIDDLC {

  final def main(args: Array[String]): Unit = { runMain(args) }

  val log: Logger = SysLogger()

  def runMain(args: Array[String]): Boolean = {
    try {
      RiddlOptions.parse(args) match {
        case Some(options) =>
          resolve(options) match {
            case Some(options) =>
              options.command match {
                case Parse => parse(options)
                case Validate => validate(options)
                case Prettify => prettify(options)
                case Hugo => translateHugo(options)
                case D3 => generateD3(options)
                case _ =>
                  log.error(s"A command must be specified as an option")
                  log.info(RiddlOptions.usage)
                  false
              }
            case None => false
          }
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

  def parse(options: RiddlOptions): Boolean = {
    options.inputFile match {
      case Some(path) => Riddl.parse(path, log, options.commonOptions) match {
          case None => false
          case Some(_) =>
            log.info("Parse completed successfully.")
            true
        }
      case None =>
        log.error("No input file provided in options")
        false
    }
  }

  def validate(
    options: RiddlOptions
  ): Boolean = {
    options.inputFile match {
      case Some(path) =>
        Riddl.parseAndValidate(path, log, options.commonOptions,options.validatingOptions).nonEmpty
      case None =>
        log.error("No input file specified")
        false
    }
  }

  def prettify(options: RiddlOptions): Boolean = {
    FormatTranslator.parseValidateTranslate(
      options.inputFile.get,
      log,
      options.commonOptions,
      options.validatingOptions,
      options.reformatOptions
    ).nonEmpty
  }

  def translateHugo(options: RiddlOptions): Boolean = {
    HugoTranslator.parseValidateTranslate(
      options.inputFile.get,
      log,
      options.commonOptions,
      options.validatingOptions,
      options.hugoOptions
    ).nonEmpty
  }

  def generateD3(options: RiddlOptions): Boolean = {
    log.info(s"D3 Generation from ${options.inputFile} is not yet supported.")
    false
  }
}
