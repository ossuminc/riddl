package com.reactific.riddl.language

import java.io.{OutputStream, PrintStream}

/** A PrintStream that captures any output into a String */
final class StringBuildingPrintStream private (
  out: OutputStream,
  stringBuilder: StringBuilder)
    extends PrintStream(out) {

  /** Returns a String of all output written to this PrintStream */
  def mkString(): String = stringBuilder.mkString
}

object StringBuildingPrintStream {

  /** Returns a new StringBuildingPrintStream */
  def apply(): StringBuildingPrintStream = {
    val stringBuilder = new StringBuilder
    val out = new OutputStream {
      override def write(b: Int): Unit = stringBuilder.append(b.toChar)
    }
    new StringBuildingPrintStream(out, stringBuilder)
  }
}
