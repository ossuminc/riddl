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

import com.reactific.riddl.commands.CommandPlugin
import com.reactific.riddl.language.Messages
import com.reactific.riddl.utils.{Logger, SysLogger}
import com.reactific.riddl.language.Messages.*

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
      val (common, remaining) = RiddlOptions.parseCommonOptions(args)
      common match {
        case Some(commonOptions) =>
          if (remaining.isEmpty) {
            log.error("No command argument was provided")
            1
          } else {
            val name = remaining.head
            CommandPlugin.
              runCommandWithArgs(name, remaining, log, commonOptions) match {
              case Right(_) => 0
              case Left(messages) =>
                Messages.logMessages(messages, log) + 1
            }
          }
        case None =>
          // arguments are bad, error message will have been displayed
          log.info("Option parsing failed, terminating.")
          1
      }
    } catch {
      case NonFatal(exception) =>
        log.severe("Exception Thrown:", exception)
        SevereError.severity + 1
    }
  }
}
