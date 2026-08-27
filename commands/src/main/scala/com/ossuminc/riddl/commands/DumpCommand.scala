/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands

import com.ossuminc.riddl.command.{Command, CommandOptions}
import com.ossuminc.riddl.commands.project.{ProjectionOutput, ProjectionPass}
import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.passes.{Pass, PassInput, PassesOutput, PassesResult, Riddl}
import com.ossuminc.riddl.utils.{Await, CallBackLogger, PlatformContext, StringHelpers}

import org.ekrich.config.Config
import scopt.OParser

import java.io.File
import java.nio.file.{Files, Path}
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt

object DumpCommand {
  final val cmdName = "dump"

  /** @param json
    *   emit the machine-readable projection instead of the indented AST.
    * @param jsonl
    *   one record per line rather than a single array. What a large corpus wants, because a script
    *   can stream it without holding the whole model in memory.
    * @param includeSpans
    *   emit source spans. Default ON: without them a record cannot be located in the source, which
    *   is half the reason the projection exists.
    * @param resolve
    *   emit references with their resolved target, and an explicit `null` when they do not resolve.
    */
  case class Options(
    inputFile: Option[Path] = None,
    json: Boolean = false,
    jsonl: Boolean = false,
    includeSpans: Boolean = true,
    resolve: Boolean = true,
    // Offered alongside stdout, not instead of it: stdout is now safe (diagnostics moved to
    // stderr), but a file is what a script wants when it will read the projection more than once.
    output: Option[Path] = None
  ) extends CommandOptions {
    def command: String = cmdName
  }
}

/** Dump a model: the indented AST by default, or a flat machine-readable projection with `--json`.
  *
  * The projection exists because riddl-models' campaign scripts had no way to ask a model a
  * structural question and were re-implementing fragments of the grammar in regex — nine defects in
  * one session, three of which reported a confident number computed over nothing. riddlc already
  * knows those answers; this makes them reachable.
  */
class DumpCommand(using pc: PlatformContext) extends Command[DumpCommand.Options](DumpCommand.cmdName) {
  import DumpCommand.Options

  override def getOptionsParser: (OParser[Unit, Options], Options) = {
    val builder = OParser.builder[Options]
    import builder.*
    cmd(DumpCommand.cmdName)
      .children(
        inputFile((v, c) => c.copy(inputFile = Option(v.toPath))),
        opt[Unit]("json")
          .action((_, c) => c.copy(json = true))
          .text("Emit a flat, machine-readable projection instead of the indented AST"),
        opt[Unit]("jsonl")
          .action((_, c) => c.copy(json = true, jsonl = true))
          .text("Like --json but one record per line, for streaming a large corpus"),
        opt[Boolean]("include-spans")
          .action((v, c) => c.copy(includeSpans = v))
          .text("Include source spans in the projection (default: true)"),
        opt[Boolean]("resolve")
          .action((v, c) => c.copy(resolve = v))
          .text("Resolve references, emitting null for ones that do not resolve (default: true)"),
        opt[File]('o', "output")
          .action((v, c) => c.copy(output = Option(v.toPath)))
          .text("Write the projection to this file instead of stdout")
      )
      .text("Print the model's AST, or with --json a machine-readable projection of it")
      -> Options()
  }

  override def interpretConfig(config: Config): Options = {
    val obj = config.getObject(commandName).toConfig
    val inputFile = if obj.hasPath("input-file") then Option(Path.of(obj.getString("input-file")))
    else None
    val json = obj.hasPath("json") && obj.getBoolean("json")
    val jsonl = obj.hasPath("jsonl") && obj.getBoolean("jsonl")
    val includeSpans = !obj.hasPath("include-spans") || obj.getBoolean("include-spans")
    val resolve = !obj.hasPath("resolve") || obj.getBoolean("resolve")
    val output = if obj.hasPath("output") then Option(Path.of(obj.getString("output"))) else None
    Options(inputFile, json || jsonl, jsonl, includeSpans, resolve, output)
  }

  override def run(
    options: Options,
    outputDirOverride: Option[Path]
  ): Either[Messages, PassesResult] = {
    options.withInputFile { (inputFile: Path) =>
      implicit val ec: ExecutionContext = pc.ec
      val future = RiddlParserInput.fromPathSafe(inputFile.toString).map {
        case Left(messages) => Left(messages)
        case Right(rpi) =>
          if options.json then dumpProjection(rpi, options)
          else
            Riddl.parseAndValidate(rpi).map { result =>
              if !pc.options.quiet then
                pc.log.info(s"AST of $inputFile is:")
                pc.log.info(StringHelpers.toPrettyString(result, 1, None))
              end if
              result
            }
      }
      // The logger swap wraps the whole synchronous Await, NOT the body inside the Future:
      // `withLogger` is `synchronized` on the PlatformContext, and calling it from inside the
      // future deadlocked against the awaiting thread -- the run died with a 30-second
      // TimeoutException and produced no output at all.
      if options.json then
        pc.withLogger(CallBackLogger((_, msg) => System.err.println(msg))) { _ =>
          Await.result(future, 30.seconds)
        }
      else Await.result(future, 30.seconds)
    }
  }

  /** **`shouldFailOnError = false` is deliberate.** The projection has to work on a model that does
    * not validate cleanly — that is precisely when a script needs to ask it questions. Refusing on
    * errors would make it useless against the corpus it exists to repair.
    */
  private def dumpProjection(
    rpi: RiddlParserInput,
    options: Options
  ): Either[Messages, PassesResult] = {
    Riddl.parseAndValidate(rpi, shouldFailOnError = false).map { result =>
      val projection = Pass.runPass[ProjectionOutput](
        PassInput(result.root),
        PassesOutput(),
        ProjectionPass(
          PassInput(result.root),
          result.outputs,
          includeSpans = options.includeSpans,
          resolve = options.resolve
        )
      )
      emit(projection.records, options)
      result
    }
  }

  /** The records go to STDOUT with `println`; the count goes to STDERR with `System.err`.
    *
    * Neither may go through `pc.log`, which writes to stdout with an `[info]` prefix — the first
    * version did exactly that for the count and produced a stream whose last line was not JSON, so
    * `python -c 'json.loads(line)'` failed on output that looked fine to the eye. Anything that is
    * not a record belongs on stderr, or the projection cannot be piped, which is its whole purpose.
    *
    * The count is printed unconditionally, INCLUDING zero, for the same reason `find` will do it: a
    * script that received nothing must be able to tell that apart from a model with nothing in it.
    */
  private def emit(records: Seq[ujson.Obj], options: Options): Unit = {
    val text =
      if options.jsonl then records.map(r => ujson.write(r)).mkString("\n")
      else ujson.write(ujson.Arr.from(records), indent = 2)
    options.output match
      case Some(path) =>
        Option(path.getParent).foreach(Files.createDirectories(_))
        Files.writeString(path, text + "\n")
        pc.log.info(s"${records.size} nodes written to $path")
      case None =>
        // `System.out.println`, NOT a bare `println`: the latter is `Console.println`, whose stream
        // is a thread-local fixed at class load, and this runs on an executor thread inside the
        // future. In production both name the same object; under a test that redirects stdout they
        // do not, so the projection would appear to vanish. Found in ValidateCommand 2026-08-25.
        System.out.println(text)
        // The count goes to the LOG, which now writes to stderr, so it cannot corrupt a piped
        // stream. Printed unconditionally, including zero: a script that received nothing must be
        // able to tell that apart from a model that contains nothing.
        pc.log.info(s"${records.size} nodes")
  }

  override def loadOptionsFrom(configFile: Path): Either[Messages, Options] = {
    super.loadOptionsFrom(configFile).map { options =>
      resolveInputFileToConfigFile(options, configFile)
    }
  }

  override protected def replaceInputFile(options: Options, inputFile: Path): Options =
    options.copy(inputFile = Some(inputFile))
}
