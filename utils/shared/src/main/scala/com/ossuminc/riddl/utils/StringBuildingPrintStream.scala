/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.utils

import java.io.OutputStream
import java.io.PrintStream
import scala.collection.mutable
import scala.scalajs.js.annotation.JSExportTopLevel

/** A PrintStream that captures any output into a String */
final class StringBuildingPrintStream private (out: OutputStream, stringBuilder: mutable.StringBuilder)
    extends PrintStream(out) {

  /** Returns a String of all output written to this PrintStream */
  def mkString(): String = stringBuilder.mkString
}

@JSExportTopLevel("StringBuildingPrintStream", "Utils")
object StringBuildingPrintStream {

  /** Returns a new StringBuildingPrintStream */
  def apply(): StringBuildingPrintStream = {
    val stringBuilder = new mutable.StringBuilder
    val out = new OutputStream {
      override def write(b: Int): Unit = stringBuilder.append(b.toChar)
    }
    new StringBuildingPrintStream(out, stringBuilder)
  }
}
