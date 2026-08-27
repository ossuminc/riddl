/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.parsing.{AbstractParsingTest, RiddlParserInput}
import com.ossuminc.riddl.utils.{ec, pc}
import org.scalatest.TestData

/** `canContain` must agree with what the PARSER accepts.
  *
  * This is the property that makes centralising containment worth anything. `canContain` is derived
  * from the `XContents` unions, and the parser's rules are written separately from those unions --
  * so the two CAN disagree, and a consumer trusting the predicate would then reject a drop riddlc
  * would have accepted (or offer one it would reject).
  *
  * The check is by construction rather than by table: parse a model, walk every parent/child pair
  * it actually produced, and assert the parent admits the child. Anything the parser builds is by
  * definition legal, so a `false` here is a real divergence. That also means this test gets
  * stronger for free as fixtures grow, instead of needing a new case per construct.
  */
class CanContainTest extends AbstractParsingTest {

  private def parse(src: String, origin: String): Root =
    parseTopLevelDomains(RiddlParserInput(src, origin)) match
      case Right(root) => root
      case Left(msgs)  => fail(s"parse of $origin failed:\n${msgs.format}")

  /** Every (parent, child) pair in the tree, descending through the provenance wrappers exactly as
    * `canContain` treats them.
    */
  private def pairs(container: Container[?]): Seq[(Container[?], RiddlValue)] =
    container.contents.toSeq.flatMap { child =>
      val here: Seq[(Container[?], RiddlValue)] = Seq(container -> child)
      val below = child match
        case c: Container[?] => pairs(c)
        case _               => Seq.empty
      here ++ below
    }

  "canContain" should {

    "admit every parent/child pair the parser actually builds" in { (td: TestData) =>
      val src =
        """domain d is {
          |  type T is Integer
          |  // a comment is legal content too
          |  author A is { name: "N" email: "e@x.com" }
          |  user U is "human"
          |  context c is {
          |    type S is String
          |    command Go is { x: Integer }
          |    record R is { a: Integer }
          |    constant K is Integer = "1"
          |    invariant I is "true"
          |    function f is { requires record R returns record R }
          |    entity e is {
          |      state st of record R
          |      handler h is { on command Go { set field e.st.a to "1" } }
          |      invariant EI is "true"
          |    }
          |    adaptor ad to context d.c is { ??? }
          |    projector p is { updates repository d.c.repo }
          |    repository repo is { ??? }
          |    saga s is {
          |      requires record R
          |      step One is { do "a" } reverted by { do "ua" }
          |      step Two is { do "b" } reverted by { do "ub" }
          |    }
          |    source src is { outlet o is command Go }
          |    sink snk is { inlet i is command Go }
          |    connector cn is { from outlet d.c.src.o to inlet d.c.snk.i }
          |    group g is {
          |      page pg is { ??? }
          |      button b activates type d.c.S
          |    }
          |  }
          |  epic ep is {
          |    user d.U wants "to do" so that "it is done"
          |    case one is { user d.U wants "to open" so that "he edits" ??? }
          |  }
          |}
          |""".stripMargin

      val root = parse(src, "cancontain")
      val all = (root +: Seq.empty).flatMap(r => pairs(r))
      all.size must be > 30 // the walk actually visited a real tree, not an empty one

      val violations = all.collect {
        case (parent: Branch[?], child) if !parent.canContain(child) =>
          s"${parent.kind} '${parent.id.value}' rejects ${child.getClass.getSimpleName}" +
            s" -- but the parser put it there"
      }
      withClue(violations.mkString("\n", "\n", "\n")) { violations mustBe empty }
    }

    "answer for a kind name without an instance, for a palette" in { (td: TestData) =>
      val root = parse("domain d is { context c is { entity e is { ??? } } }", "kinds")
      val domain = root.domains.head
      val context = domain.contexts.head

      domain.canContainKind("Context") mustBe true
      domain.canContainKind("context") mustBe true // case-insensitive
      domain.canContainKind("Entity") mustBe false // NOT direct: a Context holds entities
      context.canContainKind("Entity") mustBe true
      context.canContainKind("Domain") mustBe false

      // The palette listing is the same answer in list form.
      domain.containableKinds must contain("Context")
      domain.containableKinds must not contain "Entity"
    }

    "be direct-containment only" in { (td: TestData) =>
      val root = parse("domain d is { context c is { entity e is { ??? } } }", "direct")
      val domain = root.domains.head
      val entity = domain.contexts.head.entities.head
      // A Domain can hold a Context that can hold this Entity, but cannot hold it itself.
      domain.canContain(entity) mustBe false
      domain.contexts.head.canContain(entity) mustBe true
    }

    "treat Include as transparent, answering for what it carries" in { (td: TestData) =>
      val root = parse("domain d is { context c is { ??? } }", "wrapper")
      val domain = root.domains.head
      val context = domain.contexts.head
      val entity = Entity(At.empty, Identifier(At.empty, "e"))

      import com.ossuminc.riddl.utils.URL
      val holdingEntity = Include[RiddlValue](At.empty, URL.empty, Contents(entity))
      // A Context may hold an Entity, so it may hold an include carrying one; a Domain may not.
      context.canContain(holdingEntity) mustBe true
      domain.canContain(holdingEntity) mustBe false

      // An empty wrapper commits to nothing and is legal anywhere.
      val empty = Include[RiddlValue](At.empty, URL.empty, Contents.empty[RiddlValue]())
      domain.canContain(empty) mustBe true
    }

    "reject what the parser would reject" in { (td: TestData) =>
      val root = parse("domain d is { context c is { entity e is { ??? } } }", "negative")
      val domain = root.domains.head
      val context = domain.contexts.head
      val entity = context.entities.head

      // A Handler is not domain content; a State is not context content.
      domain.canContain(Handler(At.empty, Identifier(At.empty, "h"))) mustBe false
      context.canContain(
        State(
          At.empty,
          Identifier(At.empty, "s"),
          RecordRef(At.empty, PathIdentifier(At.empty, Seq("R")))
        )
      ) mustBe false
      // An Entity cannot hold another Entity -- contexts do not nest and neither do entities.
      entity.canContain(Entity(At.empty, Identifier(At.empty, "e2"))) mustBe false
    }
  }
}
