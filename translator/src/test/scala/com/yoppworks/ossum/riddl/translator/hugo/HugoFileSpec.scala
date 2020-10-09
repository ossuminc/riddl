package com.yoppworks.ossum.riddl.translator.hugo

import java.nio.charset.StandardCharsets
import java.nio.file.Path

import com.yoppworks.ossum.riddl.language.AST
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.io.Source

/** Tests For HugoFile */
class HugoFileSpec extends AnyWordSpec with Matchers {

  val loc = AST.Location()
  val container = AST.Domain(loc, AST.Identifier(loc, "test"))
  val fileName = "target/test.txt"
  val path = Path.of(fileName)
  var hfile = HugoFile(container, path)

  "HugoFile" should {
    "construct" in {
      hfile.lines.isEmpty must be(true)
      hfile.indentLevel == 0 must be(true)
      hfile.container must be(container)
      hfile.path must be(path)
    }
    "manage indentation" in {
      hfile.indent
      hfile.addLine("foo")
      hfile.outdent
      hfile.addLine("bar")
      hfile.lines.result must be("  foo\nbar\n")
    }
    "writes to file" in {
      hfile.write()
      val src = Source.fromFile(fileName)(StandardCharsets.UTF_8)
      val content = src.mkString
      content must be("  foo\nbar\n")
    }
  }
}
