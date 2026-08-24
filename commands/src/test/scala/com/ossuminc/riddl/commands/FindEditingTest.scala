/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands

import com.ossuminc.riddl.commands.find.*
import com.ossuminc.riddl.passes.validate.AbstractValidatingTest
import com.ossuminc.riddl.utils.pc
import org.scalatest.TestData

import java.nio.file.{Files, Path}

/** `find`'s mutating half: `-exec`, `-replace`, `-delete`.
  *
  * The end-to-end cases run against a real file in a temp directory, because the safety property
  * being tested is about the FILE SYSTEM -- "nothing was written" cannot be asserted on an in-memory
  * model. Each one checks the file's bytes, not just the command's return value: a rewrite that
  * reports failure while having already written is the precise defect these gates exist to catch.
  */
class FindEditingTest extends AbstractValidatingTest {

  private val model =
    """domain D is {
      |  context C is {
      |    event A is { w: String(1,9) }
      |    command Go is { g: String(1,9) }
      |    aggregate entity Ent is {
      |      inlet In is command C.Go
      |      handler H is {
      |        on command C.Go is { yield event C.A(w = "y") }
      |      }
      |    }
      |    entity Plain is { ??? }
      |  }
      |}
      |""".stripMargin

  /** A model on disk, plus a shell script, in a directory that is removed afterwards. */
  private def withModel(script: String)(check: (Path, Path, String) => Unit): Unit = {
    val dir = Files.createTempDirectory("riddl-find-edit")
    try
      val riddl = dir.resolve("m.riddl")
      Files.writeString(riddl, model)
      val sh = dir.resolve("s.sh")
      Files.writeString(sh, "#!/bin/sh\n" + script)
      sh.toFile.setExecutable(true)
      check(riddl, sh, model)
    finally
      Files.walk(dir).sorted(java.util.Comparator.reverseOrder()).forEach(p => Files.delete(p))
  }

  private def run(riddl: Path, expr: Seq[String]): Either[String, Unit] =
    new FindCommand().run(FindCommand.Options(Some(riddl), expr), None) match
      case Left(messages) => Left(messages.map(_.message).mkString("\n"))
      case Right(_)       => Right(())

  "FindExpression" should {
    "take -exec up to a ';' terminator" in { (_: TestData) =>
      FindExpression.parse(Seq("-type", "entity", "-exec", "echo", "{}", ";")) match
        case Right(p) =>
          p.actions must contain(FindAction.Exec(Seq("echo", "{}"), batched = false))
        case Left(e) => fail(e)
    }
    "take -exec up to a '+' terminator, and mark it batched" in { (_: TestData) =>
      FindExpression.parse(Seq("-exec", "echo", "+")) match
        case Right(p) => p.actions must contain(FindAction.Exec(Seq("echo"), batched = true))
        case Left(e)  => fail(e)
    }
    "reject an -exec with no terminator" in { (_: TestData) =>
      FindExpression.parse(Seq("-exec", "echo", "{}")) match
        case Right(p) => fail(s"should not have parsed: ${p.actions}")
        case Left(e)  => e must include("terminated")
    }
    "reject a batched -replace, since one stdout cannot be several spans" in { (_: TestData) =>
      FindExpression.parse(Seq("-replace", "cat", "+")) match
        case Right(p) => fail(s"should not have parsed: ${p.actions}")
        case Left(e)  => e must include("cannot be batched")
    }
    "know which actions mutate" in { (_: TestData) =>
      FindExpression.parse(Seq("-print")).map(_.mutates) mustBe Right(false)
      FindExpression.parse(Seq("-exec", "x", ";")).map(_.mutates) mustBe Right(false)
      FindExpression.parse(Seq("-delete")).map(_.mutates) mustBe Right(true)
      FindExpression.parse(Seq("-replace", "x", ";")).map(_.mutates) mustBe Right(true)
    }
    "carry the editing flags" in { (_: TestData) =>
      FindExpression.parse(Seq("-delete", "-dry-run", "-keep-going", "-allow-empty")) match
        case Right(p) =>
          p.dryRun mustBe true
          p.keepGoing mustBe true
          p.allowEmpty mustBe true
        case Left(e) => fail(e)
    }
  }

  "FindEditor" should {
    val f = Path.of("x.riddl")
    "refuse overlapping edits rather than pick a winner" in { (_: TestData) =>
      // A nested pair IS an overlap: the inner edit's text lies inside what the outer one replaces,
      // so which survived would depend purely on application order.
      val outer = FindEditor.Edit(f, 0, 100, "a", "outer")
      val inner = FindEditor.Edit(f, 10, 20, "b", "inner")
      FindEditor.plan(Seq(outer, inner)) match
        case Left(problems) => problems.head must include("overlaps")
        case Right(_)       => fail("overlapping edits were accepted")
    }
    "accept edits that merely abut" in { (_: TestData) =>
      val a = FindEditor.Edit(f, 0, 10, "a", "a")
      val b = FindEditor.Edit(f, 10, 20, "b", "b")
      FindEditor.plan(Seq(a, b)) match
        case Right(byFile) => byFile(f).map(_.start) mustBe Seq(10, 0) // back-to-front
        case Left(p)       => fail(p.mkString)
    }
    "apply edits without disturbing each other's offsets" in { (_: TestData) =>
      val text = "0123456789"
      val edits = Seq(
        FindEditor.Edit(f, 8, 10, "END", "b"),
        FindEditor.Edit(f, 0, 2, "START", "a")
      ).sortBy(-_.start)
      FindEditor.apply(text, edits) mustBe "START234567END"
    }
  }

  "find -replace" should {
    "leave the file byte-identical when the script returns the source unchanged" in { (_: TestData) =>
      // The gate on span accuracy. If `loc.offset`/`endOffset` did not bound the node exactly, an
      // identity replacement would still change the bytes -- so this fails loudly rather than
      // producing a subtly-shifted model.
      withModel("""python3 -c "import sys,json; sys.stdout.write(json.load(sys.stdin)['source'])"""") {
        (riddl, sh, original) =>
          run(riddl, Seq("-type", "entity", "-replace", sh.toString, ";")) mustBe Right(())
          Files.readString(riddl) mustBe original
      }
    }
    "apply a real edit" in { (_: TestData) =>
      val py = "import sys,json; sys.stdout.write(json.load(sys.stdin)['source'].replace('Plain','Renamed'))"
      withModel(s"""python3 -c "$py"""") { (riddl, sh, _) =>
        run(riddl, Seq("-type", "entity", "-name", "Plain", "-replace", sh.toString, ";")) mustBe
          Right(())
        Files.readString(riddl) must include("entity Renamed")
      }
    }
    "write NOTHING when the result does not parse" in { (_: TestData) =>
      withModel("cat > /dev/null; echo 'not riddl {{{'") { (riddl, sh, original) =>
        run(riddl, Seq("-type", "entity", "-name", "Plain", "-replace", sh.toString, ";")) match
          case Left(msg) => msg must include("does not parse")
          case Right(_)  => fail("a non-parsing rewrite was accepted")
        Files.readString(riddl) mustBe original
      }
    }
    "write NOTHING when the result parses but introduces validation errors" in { (_: TestData) =>
      // The half a parse check cannot catch: syntactically fine, semantically broken.
      val body = "entity Plain is { handler Q is { on command C.NoSuchCommand is { do \\\"x\\\" } } }"
      withModel(s"""cat > /dev/null; echo "$body"""") { (riddl, sh, original) =>
        run(riddl, Seq("-type", "entity", "-name", "Plain", "-replace", sh.toString, ";")) match
          case Left(msg) => msg must include("introduces")
          case Right(_)  => fail("a rewrite that breaks validation was accepted")
        Files.readString(riddl) mustBe original
      }
    }
    "write NOTHING when a script produces no output, unless -allow-empty" in { (_: TestData) =>
      withModel("cat > /dev/null") { (riddl, sh, original) =>
        run(riddl, Seq("-type", "entity", "-name", "Plain", "-replace", sh.toString, ";")) match
          case Left(msg) => msg must include("no output")
          case Right(_)  => fail("an empty replacement was silently accepted")
        Files.readString(riddl) mustBe original
      }
    }
    "write NOTHING when the script fails" in { (_: TestData) =>
      withModel("cat > /dev/null; exit 3") { (riddl, sh, original) =>
        run(riddl, Seq("-type", "entity", "-replace", sh.toString, ";")) match
          case Left(msg) => msg must include("exited 3")
          case Right(_)  => fail("a failing script was accepted")
        Files.readString(riddl) mustBe original
      }
    }
    "write NOTHING under -dry-run" in { (_: TestData) =>
      val py = "import sys,json; sys.stdout.write(json.load(sys.stdin)['source'].replace('Plain','Renamed'))"
      withModel(s"""python3 -c "$py"""") { (riddl, sh, original) =>
        run(riddl, Seq("-type", "entity", "-name", "Plain", "-replace", sh.toString, ";", "-dry-run")) mustBe
          Right(())
        Files.readString(riddl) mustBe original
      }
    }
    "refuse an overlapping run whole, rather than applying part of it" in { (_: TestData) =>
      withModel("""python3 -c "import sys,json; sys.stdout.write(json.load(sys.stdin)['source'])"""") {
        (riddl, sh, original) =>
          run(riddl, Seq("(", "-type", "entity", "-o", "-type", "handler", ")", "-replace", sh.toString, ";")) match
            case Left(msg) => msg must include("overlapping")
            case Right(_)  => fail("overlapping edits were applied")
          Files.readString(riddl) mustBe original
      }
    }
  }

  "find -delete" should {
    "remove the node's span" in { (_: TestData) =>
      withModel("true") { (riddl, _, _) =>
        run(riddl, Seq("-type", "entity", "-name", "Plain", "-delete")) mustBe Right(())
        Files.readString(riddl) must not include "Plain"
      }
    }
  }

  "find -exec" should {
    "run once per match and hand the script the node's source" in { (_: TestData) =>
      val out = Files.createTempFile("find-exec", ".txt")
      try
        withModel(s"""python3 -c "import sys,json; open('$out','a').write(json.load(sys.stdin)['kind']+' '+sys.argv[1]+chr(10))" "$$1"""") {
          (riddl, sh, _) =>
            run(riddl, Seq("-type", "entity", "-exec", sh.toString, "{}", ";")) mustBe Right(())
            val lines = Files.readString(out).linesIterator.toSeq
            lines.size mustBe 2
            lines.head must startWith("entity ")
            lines.map(_.split(" ").last) must contain("D.C.Plain")
        }
      finally Files.deleteIfExists(out)
    }
    "report a non-zero exit" in { (_: TestData) =>
      withModel("cat > /dev/null; exit 4") { (riddl, sh, _) =>
        run(riddl, Seq("-type", "entity", "-name", "Plain", "-exec", sh.toString, ";")) match
          case Left(msg) => msg must include("exited 4")
          case Right(_)  => fail("a failing -exec was reported as success")
      }
    }
  }
}
