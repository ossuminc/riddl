package com.yoppworks.ossum.riddl.translator.hugo

import java.nio.file.Path

import com.yoppworks.ossum.riddl.language.AST
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Tests For HugoFile */
class HugoFileSpec extends AnyWordSpec with Matchers {

  val loc = AST.Location()
  val container = AST.Domain(loc, AST.Identifier(loc, "test"))
  val path = Path.of("target/test.txt")
  var hfile = HugoFile(container, path)

  "HugoFile" should {
    "construct" in {
      hfile.lines.isEmpty must be(true)
      hfile.indentLevel == 0 must be(true)
      hfile.container must be(container)
      hfile.path must be(path)
    }
    "manage indentation" in { pending }
  }
}
