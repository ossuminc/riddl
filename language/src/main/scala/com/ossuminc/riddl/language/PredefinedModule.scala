/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.parsing.TopLevelParser
import com.ossuminc.riddl.utils.PlatformContext

/** The RIDDL *standard module* — a small library of definitions that is available to EVERY model
  * with no `import` and no author declaration.
  *
  * The module is not hand-built AST: it is written as ordinary RIDDL in [[source]] and PARSED once,
  * lazily, then cached. That keeps the standard library readable and forces it through exactly the
  * same parser every user model goes through. `PredefinedModuleTest` asserts that it parses AND
  * validates cleanly so a typo here can never reach a user.
  *
  * The module is **never injected into a user's `Root.contents`**. A model that does not mention
  * the predefined definitions produces byte-identical prettify/BAST/JSON output and exactly the
  * messages it produced before this module existed. Availability comes solely from
  * `SymbolsPass.postProcess` seeding the symbol table (and parentage) with these definitions.
  *
  * Its two terminators exist because, under the unified streaming model, every port is the endpoint
  * of exactly one connector (A31): every outlet must terminate somewhere and every inlet must be
  * fed. `BottomlessPit` is /dev/null for a stream; `ForeverEmpty` is a source that never produces.
  * Both are typed `Drain`, which is [[AST.Anything]] — the type that absorbs any message.
  */
object PredefinedModule {

  /** The name of the predefined module. */
  final val name: String = "Riddl"

  /** The name of the predefined sink that consumes everything and emits nothing. */
  final val bottomlessPit: String = "BottomlessPit"

  /** The name of the predefined source that never produces anything. */
  final val foreverEmpty: String = "ForeverEmpty"

  /** The RIDDL source of the standard module. Definitions live DIRECTLY in the module (no
    * domain/context wrapping) because `ModuleContents` is the wide `NebulaContents` union.
    */
  final val source: String =
    """module Riddl is {
      |  type Drain is Anything with {
      |    briefly "The universal stream type; it absorbs any message"
      |  }
      |  processor BottomlessPit as sink is {
      |    inlet hole is type Drain with {
      |      briefly "The inlet that swallows everything sent to it"
      |    }
      |    handler Swallow is {
      |      on other {
      |        do "discard the message"
      |      } with {
      |        briefly "Everything received is discarded"
      |      }
      |    } with {
      |      briefly "Accepts every message and discards it"
      |    }
      |  } with {
      |    briefly "A sink that consumes everything delivered to it and emits nothing"
      |    described as {
      |      | /dev/null for a stream. Every port is the endpoint of exactly one connector, so
      |      | an outlet that legitimately has no consumer must still terminate somewhere.
      |      | Terminate it here to say "deliberately discarded" rather than inventing a
      |      | placeholder consumer that models something untrue. Any number of connectors may
      |      | drain into its single inlet.
      |    }
      |  }
      |  processor ForeverEmpty as source is {
      |    outlet void is type Drain with {
      |      briefly "The outlet that never emits anything"
      |    }
      |    handler Silence is {
      |      on other {
      |        do "produce nothing"
      |      } with {
      |        briefly "Nothing is ever yielded"
      |      }
      |    } with {
      |      briefly "Never produces a message"
      |    }
      |  } with {
      |    briefly "A source that never produces anything on its outlet"
      |    described as {
      |      | The dual of BottomlessPit. Every inlet must be fed by exactly one connector, so
      |      | an inlet whose producer has not been modelled yet must still be fed. Draw from
      |      | here to say "deliberately never supplied". Any number of connectors may draw
      |      | from its single outlet.
      |    }
      |  }
      |} with {
      |  briefly "The RIDDL standard module; always in scope, no import required"
      |  described as {
      |    | The RIDDL standard module. Its definitions are available to every model with no
      |    | import and no author declaration. It is never part of a model's own AST: a model
      |    | that does not reference it is completely unaffected by it.
      |  }
      |}
      |""".stripMargin

  private var cachedModule: Option[Module] = None
  private var cachedEntries: Seq[(Definition, Parents)] = Seq.empty[(Definition, Parents)]
  private var cachedDefinitions: Seq[Definition] = Seq.empty[Definition]

  /** The singleton [[AST.Module]] for the standard library. Parsed on first use and cached forever;
    * every later call returns the SAME instance so identity (`eq`) comparisons are meaningful.
    *
    * @throws IllegalStateException
    *   if the bundled source fails to parse — that is a compiler defect, not a user error.
    */
  def module(using PlatformContext): Module = synchronized {
    cachedModule match
      case Some(m) => m
      case None =>
        val m = parseSource
        cachedModule = Some(m)
        cachedEntries = collectEntries(m, Parents.empty)
        cachedDefinitions = cachedEntries.map(_._1)
        m
    end match
  }

  /** Every [[AST.Definition]] the standard module holds paired with its [[AST.Parents]], the module
    * itself first (with no parents), in tree order. This is exactly what `SymbolsPass` needs to
    * seed both its symbol table and its parentage map.
    */
  def symbolEntries(using PlatformContext): Seq[(Definition, Parents)] = {
    module // force the parse/caching
    cachedEntries
  }

  /** Every [[AST.Definition]] the standard module holds, the module itself first, in tree order. */
  def definitions(using PlatformContext): Seq[Definition] = {
    module // force the parse/caching
    cachedDefinitions
  }

  /** A [[AST.Root]] wrapping the standard module, for passes/tests that need a root. */
  def root(using PlatformContext): Root = Root(At.empty, Contents[RootContents](module))

  /** True when `definition` IS one of the standard module's definitions. The comparison is by
    * REFERENCE IDENTITY against the parsed singleton, never by name, so a user definition that
    * happens to be called `BottomlessPit` is not mistaken for the predefined one.
    */
  def isPredefined(definition: Definition)(using PlatformContext): Boolean =
    definitions.exists(_ eq definition)

  private def parseSource(using PlatformContext): Module =
    TopLevelParser.parseString(source) match
      case Left(messages) =>
        throw new IllegalStateException(
          s"The predefined RIDDL module failed to parse:\n${messages.format}"
        )
      case Right(root) =>
        root.contents.filter[Module].headOption match
          case Some(m) => m
          case None =>
            throw new IllegalStateException(
              "The predefined RIDDL source did not yield a module named " + name
            )
        end match
    end match

  private def collectEntries(
    definition: Definition,
    parents: Parents
  ): Seq[(Definition, Parents)] =
    (definition -> parents) +: (definition match
      case branch: Branch[?] =>
        val nested: Parents = branch +: parents
        branch.contents.definitions.flatMap(collectEntries(_, nested))
      case _ => Seq.empty[(Definition, Parents)]
    )
}
