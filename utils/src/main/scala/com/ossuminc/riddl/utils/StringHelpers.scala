/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.utils

object StringHelpers {
  // extension (str: String)

  def toPrettyString(
    obj: Any,
    depth: Int = 0,
    paramName: Option[String] = None
  ): String = {
    val buf = new StringBuffer(1024)
    val nl = System.lineSeparator()

    def doIt(
      obj: Any,
      depth: Int,
      name: Option[String]
    ): Unit = {
      val indent = "  " * depth
      val prettyName = name.fold("")(x => s"$x: ")
      val ptype = obj match {
        case _: Iterable[Any] => ""
        case obj: Product     => obj.productPrefix
        case _                => obj.toString
      }

      buf.append(s"$indent$prettyName$ptype$nl")

      obj match {
        case seq: Iterable[Any] => seq.foreach(doIt(_, depth + 1, None))
        case obj: Product =>
          (obj.productIterator zip obj.productElementNames)
            .foreach { case (subObj, pName) =>
              doIt(subObj, depth + 1, Some(pName))
            }
        case _ =>
      }
    }

    doIt(obj, depth, paramName)
    buf.toString
  }

}
