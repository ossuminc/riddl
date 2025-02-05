/*
 * Copyright 2019-2025 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.utils
import java.io.{PrintWriter, StringWriter}
import java.util.{Objects, StringTokenizer}
import scala.collection.mutable.ListBuffer

object ExceptionUtils {
  private val NOT_FOUND = -1
  private val WRAPPED_MARKER  = " [wrapped] "

  private def getThrowableList(aThrowable: Throwable): List[Throwable] =  {
    val list = ListBuffer.empty[Throwable]
    var throwable = aThrowable
    while throwable != null && !list.contains(throwable) do
      list.append(throwable)
      throwable = throwable.getCause
    list.toList
  }

  private def getStackTrace(throwable: Throwable): String =  {
    if throwable == null then return ""
    val sw  = new StringWriter
    throwable.printStackTrace(new PrintWriter(sw, true))
    sw.toString
  }

  private def getStackFrameList(throwable: Throwable): List[String] = {
    val stackTrace = getStackTrace(throwable)
    val linebreak = System.lineSeparator
    val frames  = new StringTokenizer(stackTrace, linebreak)
    val list  = ListBuffer.empty[String]
    var traceStarted  = false
    var keepGoing = true
    while frames.hasMoreTokens && keepGoing do
      val token  = frames.nextToken
      // Determine if the line starts with <whitespace>at
      val at  = token.indexOf("at")
      if at != NOT_FOUND && token.substring(0, at).trim.isEmpty then
        traceStarted = true
        list.append(token)
      else
        if traceStarted then keepGoing = false
    end while
    list.toList
  }
  private def removeCommonFrames(
    causeFrames: ListBuffer[String],
    wrapperFrames: List[String]
  ): Unit =  {
    Objects.requireNonNull(causeFrames, "causeFrames")
    Objects.requireNonNull(wrapperFrames, "wrapperFrames")
    var causeFrameIndex: Int = causeFrames.size - 1
    var wrapperFrameIndex: Int = wrapperFrames.size - 1
    while causeFrameIndex >= 0 && wrapperFrameIndex >= 0 do
      // Remove the frame from the cause trace if it is the same
      // as in the wrapper trace
      val causeFrame: String = causeFrames(causeFrameIndex)
      val wrapperFrame: String = wrapperFrames(wrapperFrameIndex)
      if causeFrame == wrapperFrame then  causeFrames.remove(causeFrameIndex)
      causeFrameIndex -= 1
      wrapperFrameIndex -= 1
    end while
  }

  val doNothingToggle: Boolean = true
  // NOTE: Using commons.lang3 library from Apache doesn't work Natively, hence this facade
  def getRootCauseStackTrace(throwable: Throwable): List[String] = {
    if doNothingToggle then return List.empty
    if throwable == null then return List.empty
    val throwables: List[Throwable] = getThrowableList(throwable)
    val count = throwables.length
    val frames = ListBuffer.empty[String]
    var nextTrace = getStackFrameList(throwables(count - 1))
    var i = count
    while { i -= 1; i } >= 0 do
      val trace: ListBuffer[String] = ListBuffer.from[String](nextTrace)
      if i != 0 then
        nextTrace = getStackFrameList(throwables(i - 1))
        removeCommonFrames(trace, nextTrace)
      end if
      if i == count - 1 then frames.append(throwables(i).toString)
      else frames.append(WRAPPED_MARKER + throwables(i).toString)
      frames.addAll(trace)
    end while
    frames.toList
  }
}
