/*
 * Copyright 2019 Reactific Software LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.reactific.riddl

import com.reactific.riddl.language.ReformatTranslator
import com.reactific.riddl.language.Riddl
import com.reactific.riddl.translator.hugo.HugoTranslator
import com.reactific.riddl.translator.hugo_git_check.HugoGitCheckTranslator
import com.reactific.riddl.translator.kalix.KalixTranslator
import com.reactific.riddl.utils.{Logger, Plugin, RiddlBuildInfo, SysLogger}

import scala.annotation.unused
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Success
import scala.util.control.NonFatal

/** RIDDL Main Program */
object RIDDLC {

  final def main(args: Array[String]): Unit = {
    val resultCode = runMain(args)
    if (resultCode != 0) { System.exit(resultCode) }
  }

  val log: Logger = SysLogger()

  def runMain(args: Array[String]): Int = {
    try {
      RiddlOptions.parse(args) match {
        case Some(options) =>
          if (run(options)) { 0 }
          else { 1 }
        case None =>
          // arguments are bad, error message will have been displayed
          log.info("Option parsing failed, terminating.")
          2
      }
    } catch {
      case NonFatal(exception) =>
        log.severe("Exception Thrown:", exception)
        3
    }
  }

  def run(options: RiddlOptions): Boolean = {
    options.command match {
      case RiddlOptions.From         => from(options)
      case RiddlOptions.Repeat       => repeat(options)
      case RiddlOptions.Help         => help(options)
      case RiddlOptions.Parse        => parse(options)
      case RiddlOptions.Validate     => validate(options)
      case RiddlOptions.Prettify     => prettify(options)
      case RiddlOptions.Hugo         => translateHugo(options)
      case RiddlOptions.HugoGitCheck => hugoGitCheck(options)
      case RiddlOptions.D3           => generateD3(options)
      case RiddlOptions.Info         => info(options)
      case RiddlOptions.Kalix        => translateKalix(options)
      case RiddlOptions.Other(name) =>
        if (options.pluginsDir.nonEmpty){
          val pluginsDir = options.pluginsDir.get
          val loaded = Plugin.loadPluginsFrom[RiddlcCommandPlugin](pluginsDir)
          if (loaded.isEmpty) {
            log.error(s"No command plugins loaded from: $pluginsDir")
            false
          } else {
            loaded.find(_.pluginName == name) match {
              case Some(pl) if pl.isInstanceOf[RiddlcCommandPlugin] =>
                val plugin = pl.asInstanceOf[RiddlcCommandPlugin]
                plugin.run(options)
              case Some(plugin) =>
                log.error(s"Plugin for command $name is the wrong type ${
                  plugin.getClass.getSimpleName}")
                false
              case None =>
                log.error(s"No plugin command matches '$name'")
                false
            }
          }
        } else {
          log.error(s"Command not found. Use the --plugins-dir option")
          false
        }
      case _ =>
        log.error(s"A command must be specified as the first parameter")
        log.info(RiddlOptions.usage)
        false
    }
  }

  def from(options: RiddlOptions): Boolean = {
    options.fromOptions.configFile match {
      case Some(path) => RiddlOptions.loadRiddlOptions(options, path) match {
          case Some(newOptions) => run(newOptions)
          case None =>
            log.error(s"Failed to load riddlc options from $path, terminating.")
            false
        }
      case None =>
        log.error("No configuration file provided")
        false
    }
  }

  def allowCancel(options: RepeatOptions): (Future[Boolean], () => Boolean) = {
    if (!options.interactive) { Future.successful(false) -> (() => false) }
    else {
      Interrupt.aFuture[Boolean] {
        while (
          Option(scala.io.StdIn.readLine("Type <Ctrl-D> To Exit:\n")).nonEmpty
        ) {}
        true
      }
    }
  }

  def repeat(options: RiddlOptions): Boolean = {
    val maxCycles = options.repeatOptions.maxCycles
    val refresh = options.repeatOptions.refreshRate
    val sleepTime = refresh.toMillis
    options.repeatOptions.configFile match {
      case Some(configFile) =>
        RiddlOptions.loadRiddlOptions(options, configFile) match {
          case Some(newOptions) =>
            val (shouldQuit, cancel) = allowCancel(options.repeatOptions)
            def userHasCancelled: Boolean = shouldQuit.isCompleted &&
              shouldQuit.value == Option(Success(true))
            val counter = 1 to maxCycles
            var shouldContinue = true
            var i = counter.min
            while (shouldContinue && !userHasCancelled && i <= counter.max) {
              shouldContinue = run(newOptions)
              if (options.commonOptions.verbose) {
                println(s"Waiting for $refresh, cycle # $i of $maxCycles")
              }
              i += counter.step
              Thread.sleep(sleepTime)
            }
            if (!userHasCancelled) { cancel() }
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

  def help(
    @unused
    options: RiddlOptions
  ): Boolean = {
    println(RiddlOptions.usage)
    true
  }

  def parse(options: RiddlOptions): Boolean = {
    options.parseOptions.inputFile match {
      case Some(path) => Riddl.parse(path, log, options.commonOptions).nonEmpty
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
        val result = Riddl
          .parseAndValidate(inputFile, log, options.commonOptions)
        result.nonEmpty
      case None =>
        log.error("No input file specified for validation")
        false
    }
  }

  def prettify(options: RiddlOptions): Boolean = {
    options.reformatOptions.inputFile match {
      case Some(inputFile) =>
        val result = ReformatTranslator.parseValidateTranslate(
          inputFile,
          log,
          options.commonOptions,
          options.reformatOptions
        )
        result.nonEmpty
      case None =>
        log.error("No input file specified for prettify")
        false
    }
  }

  def translateHugo(options: RiddlOptions): Boolean = {
    options.hugoOptions.inputFile match {
      case Some(inputFile) => HugoTranslator.parseValidateTranslate(
          inputFile,
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
        HugoGitCheckTranslator.parseValidateTranslate(
          inputFile,
          log,
          options.commonOptions,
          options.hugoGitCheckOptions
        )
        log.info("Session concluded")
      case None => log.error(s"Hugo options were not specified")
    }
    false
  }

  def translateKalix(options: RiddlOptions): Boolean = {
    options.kalixOptions.inputFile match {
      case Some(inputFile) => KalixTranslator.parseValidateTranslate(
          inputFile,
          log,
          options.commonOptions,
          options.kalixOptions
        ).nonEmpty
      case None =>
        log.error(s"Hugo options were not specified")
        false
    }
  }

  def generateD3(
    @unused
    options: RiddlOptions
  ): Boolean = {
    log.info(s"D3 Generation  is not yet supported.")
    false
  }

  def info(
    @unused
    options: RiddlOptions
  ): Boolean = {
    log.info("About riddlc:")
    log.info(s"           name: ${RiddlBuildInfo.name}")
    log.info(s"        version: ${RiddlBuildInfo.version}")
    log.info(s"     start year: ${RiddlBuildInfo.startYear}")
    log.info(s"       built at: ${RiddlBuildInfo.builtAtString}")
    log.info(s"       licenses: ${RiddlBuildInfo.licenses}")
    log.info(s"      org. name: ${RiddlBuildInfo.organizationName}")
    log.info(s"      org. page: ${RiddlBuildInfo.organizationHomepage}")
    log.info(s"  scala version: ${RiddlBuildInfo.scalaVersion}")
    log.info(s"    sbt version: ${RiddlBuildInfo.sbtVersion}")
    true
  }
}
