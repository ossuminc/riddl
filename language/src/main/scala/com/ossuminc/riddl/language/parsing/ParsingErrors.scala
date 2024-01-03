/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.language.{At, CommonOptions, Messages}
import com.ossuminc.riddl.utils.Timer
import fastparse.Parsed.{Failure, Success}
import fastparse.internal.Lazy
import fastparse.{P, *}
import org.apache.commons.lang3.exception.ExceptionUtils

import java.io.File
import java.nio.file.Path
import java.util.concurrent.{ExecutorService, Executors}
import scala.annotation.unused
import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

/** A trait for tracking errors generated by fastparse  */
trait ParsingErrors {

  protected def current: RiddlParserInput

  protected def stack: InputStack
  
  protected val errors: mutable.ListBuffer[Messages.Message] = mutable.ListBuffer.empty[Messages.Message]
  
  def error(message: String): Unit = {
    val msg = Messages.Message(At.empty(current), message, Messages.Error)
    errors.append(msg)
  }
  
  def error(loc: At, message: String, context: String = ""): Unit = {
    val msg = Messages.Message(loc, message, Messages.Error, context)
    errors.append(msg)
  }
  
  def addMessage(message: Messages.Message): Unit = errors.append(message)

  private def mkTerminals(list: List[Lazy[String]]): String = {
    list
      .map(_.force)
      .map {
        case s: String if s.startsWith("char-pred")  => "pattern"
        case s: String if s.startsWith("chars-with") => "pattern"
        case s: String if s == "fail"                => "whitespace after keyword"
        case s: String                               => s
      }
      .distinct
      .sorted
      .mkString("(", " | ", ")")
  }

  def makeParseFailureError(failure: Failure): Unit = {
    val location = current.location(failure.index)
    val trace = failure.trace()
    val msg = trace.terminals.value.size match {
      case 0 => "Expected nothing"
      case 1 => s"Expected " + mkTerminals(trace.terminals.value)
      case _ => s"Expected one of " + mkTerminals(trace.terminals.value)
    }
    val context = trace.groups.render + stack.sourceNames.drop(1).mkString("\n  from", "\n  from", "\n" )
    error(location, msg, context)
  }

   def makeParseFailureError(exception: Throwable, loc: At  = At.empty): Unit = {
    val message = ExceptionUtils.getRootCauseStackTrace(exception).mkString("\n", "\n  ", "\n")
    error(loc, message)
  }
}
