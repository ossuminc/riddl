/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.{AST, Messages, definitions, directDefinitions, flatten}
import com.ossuminc.riddl.utils.{Await, PathUtils, URL, ec, pc}
import org.scalatest.TestData

import java.nio.file.Path
import scala.concurrent.duration.DurationInt

/** How a CONSUMER reads the AST, which is not how riddl's own tests read it.
  *
  * riddl validates and transforms by TRAVERSING (HierarchyPass, Finder), and every internal test
  * follows that path. Downstream tools -- generators, docs builders, IDE plugins -- instead reach
  * for `domain.contexts` / `context.entities`. Nothing gated that second path, which is how
  * `context.entities` came to return nothing for an entity written in an included file and stayed
  * that way: riddl-generator emitted 582 files for reactive-bbq without a single entity class in
  * them, and no suite noticed.
  *
  * `everything.riddl` is the right fixture because its domain declares NO context directly -- all
  * three arrive through includes -- so `Everything.contexts` returned an empty Seq for a domain
  * that plainly has three contexts.
  */
class ConsumerReadsIncludedDefinitionsTest extends ParsingTest {

  private val testInput = "language/input"

  private def parseEverything(): Root =
    val url: URL = PathUtils.urlFromCwdPath(Path.of(testInput + "/everything.riddl"))
    Await.result(TopLevelParser.parseURL(url), 10.seconds) match
      case Right(root: Root)                 => root
      case Left(messages: Messages.Messages) => fail(messages.format)
  end parseEverything

  private def everythingDomain: Domain =
    parseEverything().domains.find(_.id.value == "Everything") match
      case Some(d) => d
      case None    => fail("domain 'Everything' not found")
  end everythingDomain

  "a consumer reading the AST" should {

    "see contexts that only exist inside includes" in { (_: TestData) =>
      // All three of these are declared in included files, none directly in the domain body.
      everythingDomain.contexts.map(_.id.value).sorted mustBe Seq("APlant", "Whatever", "full")
    }

    "see an entity nested inside an included context" in { (_: TestData) =>
      val full = everythingDomain.contexts.find(_.id.value == "full") match
        case Some(c) => c
        case None    => fail("context 'full' not found -- it lives in everything_full.riddl")
      full.entities.map(_.id.value) must contain("Something")
    }

    "agree with the traversal-based reading about what belongs to the domain" in { (_: TestData) =>
      // The invariant the accessors exist to satisfy: an include is textual, so flattening it
      // away must not change any answer. If a future accessor is written on plain `filter`, this
      // fails immediately instead of under-reporting silently for a year.
      val beforeFlatten = everythingDomain.contexts.map(_.id.value).sorted
      val domain = everythingDomain
      domain.flatten()
      domain.contexts.map(_.id.value).sorted mustBe beforeFlatten
      domain.includes mustBe empty
    }
  }

  "Contents.definitions" should {

    "descend includes, like the 35 named accessors beside it" in { (_: TestData) =>
      // Synapify asked for this (task 2026-08-06): it walks `.definitions` at 33 sites and had to
      // call `flattenAST` first because this one accessor stopped at the wrapper while its
      // siblings did not. `Everything` declares no context directly -- all three are included.
      val names = everythingDomain.contents.definitions.map(_.id.value)
      names must contain("APlant")
      names must contain("full")
      names must contain("Whatever")
    }

    "still report the container's own direct definitions" in { (_: TestData) =>
      // Transparency must ADD the included ones, not replace the direct ones.
      val names = everythingDomain.contents.definitions.map(_.id.value)
      names must contain("SomeType")
      names must contain("DoAThing")
    }

    "not descend into a nested context's contents" in { (_: TestData) =>
      // filterThroughWrappers descends the provenance wrappers and NOTHING else. `Something` is an
      // entity of context `full`, so it is not a definition of the domain.
      everythingDomain.contents.definitions.map(_.id.value) must not contain "Something"
    }
  }

  "Contents.directDefinitions" should {

    "stay literal, so the callers with a stake in provenance keep it" in { (_: TestData) =>
      // ResolutionPass reads this: it must descend includes but NOT imports, and the transparent
      // form cannot express that. Criterion 3 of the synapify task.
      val names = everythingDomain.contents.directDefinitions.map(_.id.value)
      names must contain("SomeType")
      names must not contain "APlant"
    }
  }

  "the @JSExport consumer helpers" should {

    "return the included contexts from getContexts" in { (_: TestData) =>
      AST.getContexts(everythingDomain).map(_.id.value).sorted mustBe
        Seq("APlant", "Whatever", "full")
    }

    "return the included entity from getEntities" in { (_: TestData) =>
      // getEntities had NO test at all before this, and is exactly what riddl-generator calls.
      val full = everythingDomain.contexts.find(_.id.value == "full").get
      AST.getEntities(full).map(_.id.value) must contain("Something")
    }

    "not double count now that the accessors walk includes themselves" in { (_: TestData) =>
      // These helpers used to be `x.foo ++ x.includes.flatMap(...)`. With include-transparent
      // accessors that shape returns everything twice, so this pins the de-duplication.
      val domain = everythingDomain
      val viaHelper = AST.getContexts(domain).map(_.id.value)
      viaHelper.distinct.size mustBe viaHelper.size
      AST.getTopLevelDomains(parseEverything()).map(_.id.value).distinct.size mustBe
        AST.getTopLevelDomains(parseEverything()).size
    }
  }
}
