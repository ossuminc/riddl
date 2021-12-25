package com.yoppworks.ossum.riddl.generation.hugo

import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.must
import org.scalatest.wordspec.AnyWordSpec

class PrinterTests extends AnyWordSpec with must.Matchers with BeforeAndAfterAll {

  val testFilePath = s"hugo-generator/src/test/input/something.md"
  val input = new java.io.File(testFilePath)
  private[this] var _container: Option[Seq[String]] = None
  private def container: Seq[String] = _container
    .getOrElse(throw new RuntimeException(s"Could not parse input file: $testFilePath"))

  override def beforeAll(): Unit = {
    val source = scala.io.Source.fromFile(input)
    _container = Some(source.getLines.toList)
    source.close()
    container
  }

  "MarkdownPrinter" should {

    "correctly recreate `something.md` test file" in {
      val printer = printSomething
      val expectedLines = container
      val actualLines = printer.lines.toList

      expectedLines must have size actualLines.size
      actualLines.zip(expectedLines).zipWithIndex map { case ((actual, expected), index) =>
        if (actual != expected) {
          fail(s"(line #${index + 1}):\n" + s"$actual\n...does not match...\n$expected")
        } else { succeed }
      }

    }

  }

  private def printSomething: MarkdownPrinter = MarkdownPrinter.empty.println("---")
    .println("title: \"Something\"").println("draft: false").println("---").newline
    .titleEndo(4)(_.italic("Entity").n).newline.title(2)("Description")
    .println("Entities are the main objects that contexts define. They can be ")
    .println("persistent (long-lived) or aggregate (they consume commands and queries).").newline
    .title(2)("Options").println("The following flags/options were specified for this entity:")
    .newline.listEndo(
      _.italic("aggregate").println(" - description of what aggregate means"),
      _.italic("persistent").println(" - description of what persistent means")
    ).newline.title(2)("Types").println("The following types are defined within this entity:")
    .list("somethingDate")(printer =>
      element => printer.link(element, "types", element).space.print("(").italic("alias").print(")")
    ).newline.title(2)("States").println("The following states were defined within this entity:")
    .newline.title(4)("someState").title(5)("Fields:").list("field")(printer =>
      name =>
        printer.bold(name).space.print("(").italic("type:").space
          .linkEndo(_.bold("SomeType"), "/everything", "types", "sometype").print(")")
    ).newline.title(5)("Invariants:").listSimple("(none)").newline.title(2)("Functions")
    .println("The following functions are defined for this entity:")
    .append(printFunction("whenUnderTheInfluence", "(none)", "Nothing", "Boolean")).newline
    .title(2)("Handlers").println("The following command handlers are defined for this entity:")
    .append(printCommandHandler("foo", "(none)", "Something", "Nothing")).newline
    .println("The following event handlers are defined for this entity:").listSimple("(none)")

  private def printFunction(
    name: String,
    description: String,
    requires: String,
    yields: String
  ) = MarkdownPrinter.empty.list(name)(printer =>
    name =>
      printer.bold(name).n.listEndo(
        _.italic("Description:").space.println(description),
        _.italic("requires:").space.bold(requires).n,
        _.italic("yields:").space.bold(yields).n
      )
  ).newline

  private def printCommandHandler(
    name: String,
    description: String,
    command: String,
    yields: String
  ) = MarkdownPrinter.empty.list(name)(printer =>
    name =>
      printer.bold(name).n.listEndo(
        _.italic("Description:").space.println(description),
        _.italic("command:").space.bold(command).n,
        _.italic("yields:").space.bold(yields).n
      )
  ).newline

}
