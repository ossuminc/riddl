package com.reactific.riddl.prettify

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.must.Matchers

import java.nio.file.Files
import java.nio.file.Path
import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.ast.At

/** Tests For RiddlFileEmitter */
class RiddlFileEmitterTest extends AnyWordSpec with Matchers {

  val path: Path = Path.of("prettify/target/test/rfe.out")
  val rfe = RiddlFileEmitter(path)

  "RiddlFileEmitter" should {
    "add literal strings" in {
      val strings = Seq(LiteralString(At.empty, "string"))
      rfe.clear
      rfe.add(strings)
      rfe.toString mustBe " \"string\" "
    }
    "add string" in {
      rfe.clear
      rfe.add("string")
      rfe.toString mustBe "string"
    }
    "add option" in {
      rfe.clear
      rfe.add(Some("string"))(identity)
      rfe.toString mustBe "string"
    }
    "add indent" in {
      rfe.clear
      rfe.addIndent()
      rfe.toString mustBe ""
      rfe.indent
      rfe.addIndent()
      rfe.toString mustBe "  "
    }
    "outdent catches unmatched" in {
      rfe.clear
      intercept[IllegalArgumentException] { rfe.outdent }
    }
    "starts a definition with a brace" in {
      rfe.clear
      val defn = Domain(At.empty, Identifier(At.empty, "domain"))
      rfe.openDef(defn, withBrace = true)
      defn.isEmpty mustBe true
      rfe.toString mustBe "domain domain is { ??? }"
    }
    "emits Strngs" in {
      rfe.clear
      val s1 = Strng(At.empty, Some(3L), Some(6L))
      val s2 = Strng(At.empty, Some(3L), None)
      val s3 = Strng(At.empty, None, Some(6L))
      val s4 = Strng(At.empty)
      rfe.emitString(s1).toString mustBe "String(3,6)"
      rfe.clear
      rfe.emitString(s2).toString mustBe "String(3)"
      rfe.clear
      rfe.emitString(s3).toString mustBe "String(,6)"
      rfe.clear
      rfe.emitString(s4).toString mustBe "String"
    }
    "emits descriptions" in {
      rfe.clear
      val desc = BlockDescription(At.empty, Seq(LiteralString(At.empty, "foo")))
      rfe.emitDescription(Some(desc))
      rfe.toString mustBe " described as {\n  |foo\n}\n"
    }

    val patt = Pattern(At.empty, Seq(LiteralString(At.empty, "^stuff.*$")))

    "emit patterns" in {
      rfe.clear
      rfe.emitPattern(patt)
      rfe.toString mustBe "Pattern(\"^stuff.*$\") "
    }

    "emit type expressions" in {
      rfe.clear
      rfe.emitTypeExpression(Decimal(At.empty)).toString mustBe "Decimal"
      rfe.clear
      rfe.emitTypeExpression(Real(At.empty)).toString mustBe "Real"
      rfe.clear
      rfe.emitTypeExpression(DateTime(At.empty)).toString mustBe "DateTime"
      rfe.clear
      rfe.emitTypeExpression(Location(At.empty)).toString mustBe "Location"
      rfe.clear
      rfe.emitTypeExpression(patt).toString mustBe "Pattern(\"^stuff.*$\") "
    }

    "emit examples" in {
      rfe.clear
      val example = Example(
        At.empty,
        Identifier(At.empty, "ex-maple"),
        Seq(GivenClause(
          At.empty,
          Seq(LiteralString(At.empty, "It's like this, see"))
        )),
        Seq(WhenClause(
          At.empty,
          NotCondition(
            At.empty,
            Comparison(
              At.empty,
              lt,
              ArbitraryExpression(LiteralString(At.empty, "one")),
              ArbitraryExpression(LiteralString(At.empty, "two"))
            )
          )
        )),
        Seq(ThenClause(
          At.empty,
          ArbitraryAction(
            At.empty,
            LiteralString(At.empty, "ya gots ta do betta"),
            None
          )
        )),
        Seq(ButClause(
          At.empty,
          ArbitraryAction(
            At.empty,
            LiteralString(At.empty, "no at familia expense"),
            None
          )
        ))
      )

      val examples = Seq(example, example)
      rfe.emitExamples(examples)
      val expected =
        """example ex-maple is {
          |  given  "It's like this, see"  when not(<("one","two"))  then "ya gots ta do betta"  but "no at familia expense"}
          |example ex-maple is {
          |  given  "It's like this, see"  when not(<("one","two"))  then "ya gots ta do betta"  but "no at familia expense"}
          |""".stripMargin
      rfe.toString mustBe expected
    }

    "emit to a file" in {
      rfe.clear
      val path = rfe.emit()
      Files.exists(path)
      Files.size(path) == 0
    }
  }
}
