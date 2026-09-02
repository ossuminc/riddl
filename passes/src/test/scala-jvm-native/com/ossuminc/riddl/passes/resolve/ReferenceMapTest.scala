/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.resolve

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.{At, Contents, Messages, *}
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.passes.PassesResult
import com.ossuminc.riddl.passes.validate.AbstractValidatingTest
import com.ossuminc.riddl.utils.{pc, ec, Await}
import com.ossuminc.riddl.utils.PathUtils

import java.nio.file.Path
import org.scalatest.TestData

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class ReferenceMapTest extends AbstractValidatingTest {

  protected def create: Future[PassesResult] = {
    val url = PathUtils.urlFromCwdPath(Path.of("language/input/everything.riddl"))
    RiddlParserInput.fromURL(url).map { rpi =>
      simpleParseAndValidate(rpi) match {
        case Left(messages) => fail(messages.format)
        case Right(result)  => result
      }
    }
  }

  "ReferenceMap" must {
    val result: PassesResult = Await.result(create, 10.seconds)
    val refMap = result.refMap

    "convert to a pretty string" in { _ =>
      refMap.toString must not be empty
    }
    "have correct size" in { _ =>
      info("size: " + refMap.size.toString)
      // 34: a repository schema's `of <name> as type <T>` data clauses are resolved (they used to
      // be skipped, which made stored types look unused), and so are the references in a `send`
      // or `tell` nested inside a conditional (which used to leave MessageFlowPass unable to find
      // them).
      //
      // 2026-08-14, 33 -> 34: `Pass.traverse` now descends into the bodies of `when`/`match`/
      // `foreach`, so an ORDINARY reference inside one enters the refMap. Before, only names
      // carried LEXICALLY into a nested body (a `let`, a loop element, a lifecycle parameter)
      // resolved there; a state field or message type did not, because nothing ever put it in the
      // map. This number can only GROW from that change -- a shrink means something stopped
      // resolving and is a regression, not a golden to bump.
      // 2026-08-27: +4. `everything_APlant.riddl` gained a context-level `inlet Commands`, a
      // relay `handler Intake` with one bound `on` clause, and `dokn.riddl`'s
      // `entity Location` gained `type LocationEvent` and `outlet LocationEvents_out`
      // -- all so senders address a context instead of reaching onto a portlet of
      // something it contains (`msg-target-crosses-boundary`).
      // 2026-09-02: +4. `everything_full.riddl` gained `ProjectIt.Outgoing`,
      // `StoreIt.Incoming` and the `ProjectToStore` connector (whose two endpoint refs both
      // land in the map) so its projector's `tell` has a modelled channel -- A6 reachability
      // became an Error.
      refMap.size must be(42)
    }

    "have definitionOf(pathId:String) work" in { _ =>
      refMap.definitionOf[Author]("Reid") match {
        case None                 => fail("Expected to find Author 'Reid'")
        case Some(author: Author) => author.name.s mustBe "Reid"
      }
    }

    "inserts a value and finds it" in { _ =>
      val context: Context = Context(At(), Identifier(At(), "context"))
      val parent: Branch[?] = Domain(At(), Identifier(At(), "domain"))
      val pid = PathIdentifier(At(), Seq("wrong-name"))
      refMap.add[Context](pid, parent, context)
      refMap.definitionOf[Context](pid, parent) must not be empty
    }

    "have definitionOf(pid: PathIdentifier, parent: Branch) work" in { _ =>
      val pid = PathIdentifier(At.empty, Seq("Sink", "InCommands"))
      val context =
        result.root.contents.filter[Domain].head.includes.head.contents.filter[Context].head
      val parent = context.connectors.head
      parent.id.value mustBe "AChannel"
      refMap.definitionOf[Inlet](pid) match {
        case Some(actual: Inlet) =>
          actual.id.value mustBe ("InCommands")
          // [4.1]: `streamlets` is `Seq[Processor[?]]` now, so this match is no longer exhaustive
          // on `Some(_: Streamlet)` — which `-Werror` caught. The case wants the port-bearing
          // processor named "Sink" whatever kind declared it, so it matches on the wider type.
          val expected = context.streamlets.find("Sink")
          expected match {
            case Some(streamlet) =>
              streamlet.id.value mustBe ("Sink")
              streamlet.inlets must (not be (empty))
              val expected = streamlet.inlets.head
              actual mustBe expected
            case None => fail("Didn't find streamlets 'Sink'")
          }
        case None => fail("Expected to find 'Source'")
      }
    }

    "have definitionOf(ref: References[T], parent: Branch) work" in { (td: TestData) =>
      val context = result.root.contents
        .filter[Domain]
        .head
        .includes(1)
        .contents
        .filter[Context]
        .head
      val entity = context.entities.head
      val expected = entity.types(2)
      val pid = PathIdentifier(At.empty, Seq("Something", "someData"))
      val ref = TypeRef(At(), "record", pid)
      // State is a Branch, so the refMap stores the State's type ref
      // with the State as parent (not the Entity)
      val state = entity.states.head
      refMap.definitionOf[Type](ref, state) match {
        case Some(actual: Type) =>
          actual mustBe expected
          actual.id.value mustBe ("someData")
        case None => fail("Expected to find 'Something'")
      }
    }
  }

}
