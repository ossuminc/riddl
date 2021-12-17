package com.yoppworks.ossum.riddl.generation.hugo

import cats.kernel.Monoid
import cats.kernel.Semigroup

import scala.annotation.tailrec

/** [[PrinterEndo]] is an immutable `endomorphism` text printer.
  */
sealed trait PrinterEndo {
  type Self <: PrinterEndo

  /** End the current line (if there is one) */
  def n: Self
  def space: Self
  def newline: Self

  def indentation: Int
  def indent(spaces: Int): Self
  def outdent(spaces: Int): Self

  def print(text: String): Self
  def println(line: String): Self
  def printLines(lines: Iterable[String]): Self
  def printLines(line: String, lines: String*): Self

  def append(printerEndo: PrinterEndo): Self

  def toString: String
  def lines: Iterable[String]
}

/** Type-class instances for [[PrinterEndo]] */
abstract private[hugo] class PrinterEndoInstances {

  implicit final val printerEndoIsSemigroup: Semigroup[PrinterEndo] =
    (x: PrinterEndo, y: PrinterEndo) => x.append(y)

}

/** [[PrinterEndo]] companion object, for implicits mostly. */
object PrinterEndo extends PrinterEndoInstances

/* Impl details */
private abstract class PrinterEndoBase(protected val newlineChars: String = "\n")
    extends PrinterEndo {

  def printLines(line: String, lines: String*): Self =
    if (lines.isEmpty) println(line) else printLines(line +: lines)

  def append(endo: PrinterEndo): Self = printLines(endo.lines)
}

/** A specific subtype of [[PrinterEndo]] that can print Markdown syntax.
  */
sealed trait MarkdownPrinter extends PrinterEndo {
  override type Self = MarkdownPrinter

  final def title(level: Int)(text: String): Self = titleEndo(level)(_ println text)
  def titleEndo(level: Int)(endoFunc: MarkdownPrinter => MarkdownPrinter): Self

  def link(name: String, linkPaths: String*): Self = linkEndo(_ print name, linkPaths: _*)
  def linkEndo(endoFunc: MarkdownPrinter => MarkdownPrinter, linkPaths: String*): Self

  final def bold(text: String): Self = boldEndo(_ print text)
  def boldEndo(endoFunc: Self => Self): Self

  final def italic(text: String): Self = italicEndo(_ print text)
  def italicEndo(endoFunc: Self => Self): Self

  def list(elements: Iterable[String])(format: MarkdownPrinter => String => MarkdownPrinter): Self
  def listSimple(first: String, rest: String*): Self = listSimple(first +: rest)
  def listSimple(elements: Iterable[String]): Self = list(elements)(ptr => str => ptr.print(str).n)

  def listEndo(lineFuncs: MarkdownPrinter => MarkdownPrinter*): Self
  final def list(
    first: String,
    rest: String*
  )(format: MarkdownPrinter => String => MarkdownPrinter
  ): Self = list(first +: rest)(format)
}

private final case class MarkdownPrinterImpl(
  builder: Vector[String],
  currentLine: Option[String],
  indentation: Int = 0)
    extends PrinterEndoBase with MarkdownPrinter {
  override def toString: String = lines.mkString(newlineChars)

  def lines: Iterable[String] = builder ++ currentLine

  def titleEndo(level: Int)(endoFunc: MarkdownPrinter => MarkdownPrinter): MarkdownPrinter =
    endoFunc(print("#".repeat(math.max(1, level))).space)

  def linkEndo(endoFunc: MarkdownPrinter => MarkdownPrinter, linkPaths: String*): MarkdownPrinter =
    endoFunc(this.print("[")).print(s"](${linkPaths.mkString("/")})")

  def boldEndo(endoFunc: MarkdownPrinter => MarkdownPrinter): MarkdownPrinter =
    endoFunc(print("**")).print("**")

  def italicEndo(endoFunc: MarkdownPrinter => MarkdownPrinter): MarkdownPrinter =
    endoFunc(print("_")).print("_")

  def list(
    elements: Iterable[String]
  )(format: MarkdownPrinter => String => MarkdownPrinter
  ): MarkdownPrinter = elements.foldLeft(newline.indent(2)) { case (printer, element) =>
    format(printer.print("* "))(element)
  }.outdent(2)

  override def listEndo(lineFuncs: MarkdownPrinter => MarkdownPrinter*): MarkdownPrinter =
    if (lineFuncs.isEmpty) { this }
    else {
      lineFuncs.foldLeft(indent(2)) { case (printer, func) => func(printer.print("* ")) }.outdent(2)
    }

  /* PrinterEndo Implementation */

  def n: MarkdownPrinter = currentLine match {
    case Some(ln) => copy(builder :+ ln, None)
    case None     => this
  }

  def space: MarkdownPrinter = print(" ")

  def newline: MarkdownPrinter = currentLine match {
    case Some(ln) => copy(builder :+ ln :+ indentSpaces, None)
    case None     => copy(builder :+ indentSpaces, None)
  }

  def indent(spaces: Int): MarkdownPrinter =
    if (spaces <= 0) { this }
    else { copy(indentation = indentation + spaces) }

  def outdent(spaces: Int): MarkdownPrinter =
    if (spaces <= 0) { this }
    else { copy(indentation = math.max(0, indentation - spaces)) }

  def print(text: String): MarkdownPrinter =
    if (!text.contains(newlineChars)) { printInternal(text) }
    else {
      splitLines(text) match {
        case Nil       => this
        case ln +: Nil => printLineInternal(ln)
        case lines     => printLinesInternal(lines)
      }
    }

  def println(line: String): MarkdownPrinter =
    if (!line.contains(newlineChars)) { printLineInternal(line) }
    else {
      splitLines(line) match {
        case Nil       => this
        case ln +: Nil => printLineInternal(ln)
        case lines     => printLinesInternal(lines)
      }
    }

  def printLines(lines: Iterable[String]): MarkdownPrinter =
    printLinesInternal(lines.toSeq, keepBlankLines = true)

  @inline
  private def indentSpaces: String = " " * indentation

  @inline
  private def splitLines(text: String): Seq[String] = text.split(newlineChars).filterNot(_.isEmpty)
    .toSeq

  @tailrec
  private def printInternal(text: String, keepBlankLines: Boolean = false): MarkdownPrinterImpl =
    if (text.isEmpty && !keepBlankLines) { this }
    else if (text.contains(newlineChars)) {
      val lines :+ last = splitLines(text)
      printLinesInternal(lines).printInternal(last)
    } else { copy(currentLine = (currentLine orElse Some(indentSpaces)).map(_ + text)) }

  private def printLineInternal(
    line: String,
    keepBlankLines: Boolean = false
  ): MarkdownPrinterImpl =
    if (line.isEmpty && !keepBlankLines) { this }
    else if (line.contains(newlineChars)) { printLinesInternal(splitLines(line)) }
    else {
      val newLine = currentLine match {
        case Some(ln) => ln + line
        case None     => indentSpaces + line
      }
      copy(builder = builder :+ newLine, None)
    }

  private def printLinesInternal(
    lines: Seq[String],
    keepBlankLines: Boolean = false
  ): MarkdownPrinterImpl = lines match {
    case Nil           => this
    case single +: Nil => printLineInternal(single, keepBlankLines)
    case multiple => multiple.foldLeft(this) { case (endo, ln) =>
        endo.printLineInternal(ln, keepBlankLines)
      }
  }

}

/** Type-class instances for [[MarkdownPrinter]] */
abstract private[hugo] class MarkdownPrinterInstances {

  implicit final val markdownPrinterIsMonoid: Monoid[MarkdownPrinter] =
    new Monoid[MarkdownPrinter] {
      final def empty = MarkdownPrinter.empty
      final def combine(x: MarkdownPrinter, y: MarkdownPrinter) = x.append(y)
    }

}

object MarkdownPrinter extends MarkdownPrinterInstances {
  final val empty: MarkdownPrinter = MarkdownPrinterImpl(Vector.empty, None)
}
