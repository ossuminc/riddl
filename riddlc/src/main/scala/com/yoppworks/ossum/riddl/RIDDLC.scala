package com.yoppworks.ossum.riddl

import com.yoppworks.ossum.riddl.language.{FormatTranslator, Logger, Riddl, SysLogger}
import com.yoppworks.ossum.riddl.translator.hugo.HugoTranslator
import com.yoppworks.ossum.riddl.translator.hugo_git_check.HugoGitCheckTranslator

import scala.annotation.unused
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.control.NonFatal

/** RIDDL Main Program */
object RIDDLC {

  final def main(args: Array[String]): Unit = {
    val resultCode = runMain(args)
    if (resultCode != 0) {
      System.exit(resultCode)
    }
  }

  val log: Logger = SysLogger()

  def runMain(args: Array[String]): Int = {
    try {
      RiddlOptions.parse(args) match {
        case Some(options) =>
          if ( run(options)) {
            0
          } else {
            1
          }
        case None =>
          // arguments are bad, error message will have been displayed
          log.info("Option parsing failed, terminating.")
          2
      }
    } catch {
      case NonFatal(exception) =>
        log.error("Exception Thrown:", exception)
        3
    }
  }

  def run(options: RiddlOptions): Boolean = {
    options.command match {
      case RiddlOptions.From => from(options)
      case RiddlOptions.Repeat => repeat(options)
      case RiddlOptions.Help => help(options)
      case RiddlOptions.Parse => parse(options)
      case RiddlOptions.Validate => validate(options)
      case RiddlOptions.Prettify => prettify(options)
      case RiddlOptions.Hugo => translateHugo(options)
      case RiddlOptions.HugoGitCheck => hugoGitCheck(options)
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
        RiddlOptions.loadRiddlOptions(options, path) match {
          case Some(newOptions) =>
            run(newOptions)
          case None =>
            log.error(s"Failed to load riddlc options from $path, terminating.")
            false
        }
      case None =>
        log.error("No configuration file provided")
        false
    }
  }

  def repeat(options: RiddlOptions): Boolean = {
    val maxLoops = options.repeatOptions.maxLoops
    val refresh = options.repeatOptions.refreshRate
    val sleepTime = refresh.toMillis
    options.repeatOptions.configFile match {
      case Some(configFile) =>
        RiddlOptions.loadRiddlOptions(options, configFile) match {
          case Some(newOptions) =>
            val shouldQuit = Future[Unit] {
              while (
                Option(scala.io.StdIn.readLine("Type <Ctrl-D> To Exit: ")).nonEmpty
              ) ()
            }
            val counter = 1 to maxLoops
            var shouldContinue = true
            var i = counter.min
            while (shouldContinue && !shouldQuit.isCompleted && i < counter.max) {
              i += counter.step
              shouldContinue = run(newOptions)
              if (options.commonOptions.verbose) {
                println(s"Waiting for $refresh, loop # $i of $maxLoops")
              }
              Thread.sleep(sleepTime)
            }
            shouldContinue
          case None =>
            log.error(s"Failed too load riddlc options from $configFile")
            false
        }
      case None =>
        log.error("No configuration file provided")
        false
    }
  }

  def help(@unused options: RiddlOptions): Boolean = {
    println(RiddlOptions.usage)
    true
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
      case Some(inputFile) =>
        Riddl.parseAndValidate(inputFile, log, options.commonOptions).nonEmpty
      case None =>
        log.error("No input file specified for validation")
        false
    }
  }

  def prettify(options: RiddlOptions): Boolean = {
    options.reformatOptions.inputFile match {
      case Some(inputFile) =>
        FormatTranslator.parseValidateTranslate(
          inputFile,
          log,
          options.commonOptions,
          options.reformatOptions
        ).nonEmpty
      case None =>
        log.error("No input file specified for prettify")
        false
    }
  }

  def translateHugo(options: RiddlOptions): Boolean = {
    options.hugoOptions.inputFile match {
      case Some(inputFile) =>
        HugoTranslator.parseValidateTranslate(inputFile,
          log,
          options.commonOptions,
          options.hugoOptions
        ).nonEmpty
      case None =>
        log.error("No input file specified for hugo translation")
        false
    }
  }

  def hugoGitCheck(options: RiddlOptions): Boolean = {
    options.hugoGitCheckOptions.hugoOptions.inputFile match {
      case Some(inputFile) =>
        HugoGitCheckTranslator.parseValidateTranslate(inputFile, log,
          options.commonOptions, options.hugoGitCheckOptions
        )
        log.info("Session concluded")
      case None =>
        log.error(s"Hugo options were not specified")
    }
    false
  }

  def generateD3(@unused options: RiddlOptions): Boolean = {
    log.info(s"D3 Generation  is not yet supported.")
    false
  }
}
