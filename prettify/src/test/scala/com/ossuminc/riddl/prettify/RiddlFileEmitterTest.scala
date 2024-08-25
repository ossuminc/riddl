package com.ossuminc.riddl.prettify

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.At
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.nio.file.{Files, Path}


/** Tests For RiddlFileEmitter */
class RiddlFileEmitterTest extends AnyWordSpec with Matchers {

  private val path: Path = Path.of("prettify/target/test/rfe.out")
  val rfe = RiddlFileEmitter(path)

  "RiddlFileEmitter" should {
    "add literal strings" in {
      val string = LiteralString(At.empty, "string")
      val strings = Seq(string)
      val twoStrings = Seq(string, string)
      rfe.clear()
      rfe.add(strings)
      rfe.toString mustBe " \"string\" "
      rfe.clear()
      rfe.add(twoStrings)
      rfe.toString mustBe "\n\"string\"\n\"string\"\n"
    }
    "add string" in {
      rfe.clear()
      rfe.add("string")
      rfe.toString mustBe "string"
    }
    "add option" in {
      rfe.clear()
      rfe.add(Some("string"))(identity)
      rfe.toString mustBe "string"
      rfe.clear()
      rfe.add(Option.empty[String])(identity)
      rfe.toString mustBe ""
    }
    "add indent" in {
      rfe.clear()
      rfe.addIndent()
      rfe.toString mustBe ""
      rfe.incr
      rfe.addIndent()
      rfe.toString mustBe "  "
    }
    "outdent catches unmatched" in {
      rfe.clear()
      intercept[IllegalArgumentException] { rfe.decr }
    }
    "starts a definition with/out a brace" in {
      rfe.clear()
      val defn = Domain(At.empty, Identifier(At.empty, "domain"))
      rfe.openDef(defn)
      defn.isEmpty mustBe true
      rfe.toString mustBe "domain domain is { ??? }\n"
      rfe.clear()
      rfe.openDef(defn, withBrace = false)
      rfe.toString mustBe "domain domain is "
    }
    "emits Strngs" in {
      rfe.clear()
      val s1 = String_(At.empty, Some(3L), Some(6L))
      val s2 = String_(At.empty, Some(3L), None)
      val s3 = String_(At.empty, None, Some(6L))
      val s4 = String_(At.empty)
      rfe.emitString(s1).toString mustBe "String(3,6)"
      rfe.clear()
      rfe.emitString(s2).toString mustBe "String(3)"
      rfe.clear()
      rfe.emitString(s3).toString mustBe "String(,6)"
      rfe.clear()
      rfe.emitString(s4).toString mustBe "String"
    }
    "emits descriptions" in {
      rfe.clear()
      val desc = BlockDescription(At.empty, Seq(LiteralString(At.empty, "foo")))
      rfe.emitDescription(Some(desc))
      rfe.toString mustBe "described as {\n  |foo\n}\n"
    }

    val patt = Pattern(At.empty, Seq(LiteralString(At.empty, "^stuff.*$")))

    "emit patterns" in {
      rfe.clear()
      rfe.emitPattern(patt)
      rfe.toString mustBe "Pattern(\"^stuff.*$\")"
    }

    "emit type expressions" in {
      rfe.clear()
      rfe.emitTypeExpression(Decimal(At.empty, 8, 3)).toString mustBe "Decimal(8,3)"
      rfe.clear()
      rfe.emitTypeExpression(Real(At.empty)).toString mustBe "Real"
      rfe.clear()
      rfe.emitTypeExpression(DateTime(At.empty)).toString mustBe "DateTime"
      rfe.clear()
      rfe.emitTypeExpression(Location(At.empty)).toString mustBe "Location"
      rfe.clear()
      rfe.emitTypeExpression(patt).toString mustBe "Pattern(\"^stuff.*$\")"
      rfe.clear()
      rfe.emitTypeExpression(Abstract(At.empty)).toString mustBe "Abstract"
      rfe.clear()
      rfe.emitTypeExpression(SpecificRange(At.empty, Integer(At.empty), 24, 42)).toString mustBe "Integer{24,42}"
    }

    "emit to a file" in {
      rfe.clear()
      val path = rfe.emit()
      Files.exists(path)
      Files.size(path) == 0
    }
  }
}
