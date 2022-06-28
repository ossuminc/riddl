package com.reactific.riddl.utils

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.nio.file.Files
import java.nio.file.Path

class TextFileWriterTest extends AnyWordSpec with Matchers {
  "TextFileWriter" must {
    "implement template substitutions" in {
      val template = "blah ${foo1} blah ${foo2} blah"
      val substitutions = Map("foo1" -> "dee", "foo2" -> "dee")
      val result = TextFileWriter.substitute(template, substitutions)
      result mustBe ("blah dee blah dee blah")
    }

    case class TestTextFileWriter(filePath: Path) extends TextFileWriter
    "fill a template from a resource" in {
      val path = Files.createTempFile("test", "txt")
      val ttfw = TestTextFileWriter(path)
      val substitutions = Map("foo1" -> "dee", "foo2" -> "dee")
      ttfw.fillTemplateFromResource("blah-foo.txt", substitutions)
      ttfw.write()
      val content = Files.readString(path)
      Files.delete(path)
      content mustBe ("blah dee blah dee blah\n")
    }
  }
}
