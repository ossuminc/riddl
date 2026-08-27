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

  /** The record an `error-sink` inlet must accept. Named for its SOURCE: a generator is what
    * produces it. Models may accept it in an alternation alongside their own error messages.
    */
  final val generatorError: String = "GeneratorError"

  /** The record carrying a message's metadata, selected by `option message_envelope`. Its fields
    * are the CloudEvents v1.0 context attributes, verbatim.
    */
  final val envelope: String = "Envelope"

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
      |  record GeneratorError is {
      |    origin: String,
      |    kind: String,
      |    detail: String,
      |    occurredAt: TimeStamp
      |  } with {
      |    briefly "What a generator sends to the error-sink inlet"
      |    described as {
      |      | The shape every generator uses to report something unrecoverable: a saga whose
      |      | undo retries were exhausted, an adaptor's dead-lettered message, a projector's
      |      | poison event. `origin` names the definition that failed, `kind` classifies it,
      |      | `detail` carries the saga's `failure-message` or the generator's own text.
      |      |
      |      | GeneratorError is for GENERATORS to send; the name states the source. The
      |      | inlet marked `option error-sink` must
      |      | accept it -- either typed by GeneratorError directly, or by an alternation
      |      | including it, so a model may route its own error messages to the same inlet.
      |      |
      |      | Encoded here rather than documented as a convention so that it is ONE definition
      |      | the symbol table resolves: a misspelled field name becomes an error instead of
      |      | silently matching nothing and losing the detail it was meant to carry.
      |      |
      |      | There is deliberately NO predefined receiver. Where hard errors go is the model's
      |      | to say, via `option error-sink`; an `Operations` context belongs in the model
      |      | that wants one, not in this module.
      |    }
      |  }
      |  record Envelope is {
      |    messageId: String,
      |    source: URL,
      |    specversion: String,
      |    type: String,
      |    subject: String?,
      |    time: TimeStamp?,
      |    datacontenttype: String?,
      |    dataschema: URL?
      |  } with {
      |    briefly "The metadata carried alongside a message; the CloudEvents context attributes"
      |    described as {
      |      | The envelope a message travels in, selected by `option message_envelope`. Field
      |      | names are the CloudEvents v1.0 CONTEXT ATTRIBUTES, so a generator emitting
      |      | CloudEvents maps them one-for-one instead of guessing. The four
      |      | CloudEvents-REQUIRED attributes are non-optional here; the four optional ones carry
      |      | `?`.
      |      |
      |      | ONE deviation, and it is forced: CloudEvents `id` is spelled `messageId`, because
      |      | RIDDL requires identifiers of at least three characters and `id` draws a style
      |      | warning. A generator must map `messageId` -> `id` on the wire. Every other
      |      | attribute is verbatim, including `type` and `source`, which are RIDDL keywords
      |      | elsewhere but are accepted as field names.
      |      |
      |      | There is deliberately no `data` field. CloudEvents puts the payload in `data`, but
      |      | in RIDDL the payload IS the message -- it is already modelled, named and typed.
      |      | Duplicating it here would create a second, weaker description of something the
      |      | model already states precisely. This record is the metadata AROUND a message, not
      |      | a wrapper containing one.
      |      |
      |      | Declaring the envelope does not impose a wire format. RIDDL specifies meaning, not
      |      | representation: `option message_envelope` says these attributes accompany the
      |      | messages in its scope, and how they are carried -- CloudEvents JSON, Kafka headers,
      |      | a gRPC metadata map, or nothing at all for an in-process call -- stays the
      |      | generator's choice.
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
      case _ => Seq.empty[(Definition, Parents)])
}
