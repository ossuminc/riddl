package com.yoppworks.ossum.riddl.generation.hugo

import cats.kernel.Monoid

import scala.annotation.tailrec

/** LinePrinter allows printing to a line, handles newlines, and aggregates completed lines */
final private class LinePrinter private (
  accumulated: Vector[String],
  currentLine: String,
  val indentation: Int) {
  import LinePrinter._
  def this() = this(Vector.empty, LinePrinter.emptyString, 0)

  /* Public API */
  override def toString: String = (accumulated ++ current).mkString(newlineChars)
  def lines: Seq[String] = accumulated ++ current

  def currentIsBlank: Boolean = currentLine.length == indentation

  def indent(spaces: Int): LinePrinter =
    if (spaces <= 0) { this }
    else if (currentIsBlank) {
      val indent = math.min(indentation + spaces, maximumIndent)
      copy(currentLine = " " * indent, indentation = indent)
    } else { copy(indentation = math.min(indentation + spaces, maximumIndent)) }

  def outdent(spaces: Int): LinePrinter =
    if (spaces <= 0) { this }
    else if (currentIsBlank) {
      val indent = math.max(indentation - spaces, 0)
      copy(currentLine = " " * indent, indentation = indent)
    } else { copy(indentation = math.max(indentation - spaces, 0)) }

  def print(text: String): LinePrinter =
    if (text.isEmpty) { this }
    else if (!text.contains(newlineChars)) { printCurrent(text, nl = false) }
    else {
      splitLines(text) match {
        case lns :+ last => printLinesInternal(lns).printCurrent(last, nl = false)
        case one +: Nil  => printCurrent(one, nl = true)
        case Nil         => this
      }
    }

  def println(text: String): LinePrinter =
    if (!text.contains(newlineChars)) { printCurrent(text, nl = true) }
    else {
      splitLines(text) match {
        case Nil   => printCurrent(emptyString, nl = true)
        case lines => printLinesInternal(lines)
      }
    }

  def printLines(lines: Seq[String]): LinePrinter = printLinesInternal(lines.flatMap(splitLines))

  def appendLines(lines: Seq[String]): LinePrinter = printLinesInternal(lines)

  /* Internal methods */
  @inline
  private def blankLine: String = if (indentation == 0) "" else " " * indentation
  @inline
  private def current: Option[String] = if (currentLine.isEmpty) None else Some(currentLine)

  private def printCurrent(text: String, nl: Boolean): LinePrinter =
    if (nl && currentLine.nonEmpty) { copy(accumulated :+ currentLine + text, blankLine) }
    else if (nl) { copy(accumulated :+ text) }
    else if (text.nonEmpty) { copy(currentLine = currentLine + text) }
    else { this }

  private def printLinesInternal(lines: Seq[String]): LinePrinter = {
    @tailrec
    def loop(remain: Seq[String], printer: LinePrinter): LinePrinter = remain match {
      case ln +: tail => loop(tail, printer.printCurrent(ln, nl = true))
      case Nil        => printer
    }

    loop(lines, this)
  }

  /* Product-like `copy` */
  private def copy(
    accumulated: Vector[String] = this.accumulated,
    currentLine: String = this.currentLine,
    indentation: Int = this.indentation
  ): LinePrinter = new LinePrinter(accumulated, currentLine, indentation)
}

/** Companion for [[LinePrinter]] */
private object LinePrinter {
  final val newlineChars = "\n"
  final val maximumIndent = 120
  final val emptyString = ""

  final def empty: LinePrinter = new LinePrinter

  final def apply(
    lines: Iterable[String] = Iterable.empty[String],
    indentation: Int = 0
  ): LinePrinter = new LinePrinter(lines.toVector, emptyString, indentation)

  @inline
  private final def splitLines(text: String): Seq[String] = text.split(newlineChars)
    .filterNot(_.isEmpty).toSeq
}

/** [[PrinterEndo]] is an immutable `endomorphism` text printer. */
trait PrinterEndo {
  type Endo <: PrinterEndo

  /** End the current line (if there is one) */
  def n: Endo
  def space: Endo
  def newline: Endo

  def indentation: Int
  def indent(spaces: Int): Endo
  def outdent(spaces: Int): Endo

  def print(text: String): Endo
  def println(line: String): Endo
  def printLines(lines: Iterable[String]): Endo
  def printLines(line: String, lines: String*): Endo

  def append(printerEndo: PrinterEndo): Endo

  def toString: String
  def lines: Seq[String]
}

/* Impl details */
private abstract class LinePrinterEndo[Contract <: PrinterEndo](printer: LinePrinter) {
  import LinePrinter.emptyString

  /* Public API */
  def n: Contract = copy(if (printer.currentIsBlank) printer else printer.println(emptyString))
  def space: Contract = copy(printer.print(" "))
  def newline: Contract = copy(printer.println(emptyString))

  def indentation: Int = printer.indentation
  def indent(spaces: Int): Contract = copy(printer.indent(spaces))
  def outdent(spaces: Int): Contract = copy(printer.outdent(spaces))

  def print(text: String): Contract = copy(printer.print(text))
  def println(line: String): Contract = copy(printer.println(line))
  def printLines(lines: Iterable[String]): Contract = copy(printer.printLines(lines.toSeq))
  def printLines(line: String, lines: String*): Contract =
    if (lines.isEmpty) { copy(printer.println(line)) }
    else { copy(printer.printLines(line +: lines)) }

  def append(printerEndo: PrinterEndo): Contract = copy(printer.appendLines(printerEndo.lines))

  def lines: Seq[String] = printer.lines
  override def toString: String = printer.toString

  /* Product-like `copy` */
  def copy(printer: LinePrinter = this.printer): Contract
}

/** Type-class instances for [[PrinterEndo]] */
abstract private[hugo] class PrinterEndoInstances {

  implicit final val printerEndoIsMonoid: Monoid[PrinterEndo] = new Monoid[PrinterEndo] {
    final def empty = PrinterEndo.empty
    final def combine(x: PrinterEndo, y: PrinterEndo) = x.append(y)
  }

}

/** [[PrinterEndo]] companion object, for implicits mostly. */
object PrinterEndo extends PrinterEndoInstances {

  final val empty: PrinterEndo = PrinterEndoImpl(LinePrinter.empty)

  private final case class PrinterEndoImpl(printer: LinePrinter)
      extends LinePrinterEndo[PrinterEndo](printer) with PrinterEndo {
    type Endo = PrinterEndo
    def copy(printer: LinePrinter): PrinterEndoImpl = PrinterEndoImpl(printer)
  }

}

/** A specific subtype of [[PrinterEndo]] that can print Markdown syntax. */
trait MarkdownPrinter extends PrinterEndo {
  override type Endo = MarkdownPrinter

  final def title(level: Int)(text: String): Endo = titleEndo(level)(_ println text)
  def titleEndo(level: Int)(endoFunc: MarkdownPrinter => MarkdownPrinter): Endo

  final def link(name: String, linkPaths: String*): Endo = linkEndo(_ print name, linkPaths: _*)
  def linkEndo(endoFunc: MarkdownPrinter => MarkdownPrinter, linkPaths: String*): Endo

  final def bold(text: String): Endo = boldEndo(_ print text)
  def boldEndo(endoFunc: Endo => Endo): Endo

  final def italic(text: String): Endo = italicEndo(_ print text)
  def italicEndo(endoFunc: Endo => Endo): Endo

  def list(
    elements: Iterable[String]
  )(format: MarkdownPrinter => String => MarkdownPrinter
  ): Endo

  final def listSimple(first: String, rest: String*): Endo = listSimple(first +: rest)
  def listSimple(elements: Iterable[String]): Endo = list(elements)(ptr => str => ptr.print(str).n)

  def listEndo(lineFuncs: MarkdownPrinter => MarkdownPrinter*): Endo

  final def list(
    first: String,
    rest: String*
  )(format: MarkdownPrinter => String => MarkdownPrinter
  ): Endo = list(first +: rest)(format)
}

private final case class MarkdownPrinterImpl(printer: LinePrinter)
    extends LinePrinterEndo[MarkdownPrinter](printer) with MarkdownPrinter {

  def copy(printer: LinePrinter): MarkdownPrinterImpl = MarkdownPrinterImpl(printer)

  def titleEndo(level: Int)(endoFunc: MarkdownPrinter => MarkdownPrinter): MarkdownPrinter =
    endoFunc(print("#".repeat(math.max(1, level))).space)

  def linkEndo(endoFunc: MarkdownPrinter => MarkdownPrinter, linkPaths: String*): MarkdownPrinter =
    endoFunc(print("[")).print(s"](${linkPaths.mkString("/")})")

  def boldEndo(endoFunc: MarkdownPrinter => MarkdownPrinter): MarkdownPrinter =
    endoFunc(print("**")).print("**")

  def italicEndo(endoFunc: MarkdownPrinter => MarkdownPrinter): MarkdownPrinter =
    endoFunc(print("_")).print("_")

  def list(
    elements: Iterable[String]
  )(format: MarkdownPrinter => String => MarkdownPrinter
  ): MarkdownPrinter = elements.foldLeft(newline.indent(2)) { case (printer, element) =>
    format(printer.print("* "))(element).n
  }.outdent(2)

  def listEndo(lineFuncs: MarkdownPrinter => MarkdownPrinter*): MarkdownPrinter =
    if (lineFuncs.isEmpty) { this }
    else {
      lineFuncs.foldLeft(indent(2)) { case (printer, func) => func(printer.print("* ")) }.outdent(2)
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
  final val empty: MarkdownPrinter = MarkdownPrinterImpl(LinePrinter.empty)
}
