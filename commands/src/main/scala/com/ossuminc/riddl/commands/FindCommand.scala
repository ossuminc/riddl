/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands

import com.ossuminc.riddl.command.{Command, CommandOptions}
import com.ossuminc.riddl.commands.find.*
import com.ossuminc.riddl.commands.project.{ProjectedNode, ProjectionOutput, ProjectionPass}
import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.passes.{Pass, PassInput, PassesOutput, PassesResult, Riddl}
import com.ossuminc.riddl.utils.{Await, PlatformContext}

import org.ekrich.config.Config
import scopt.OParser

import java.nio.file.{Files, Path}
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt

object FindCommand {
  final val cmdName = "find"

  case class Options(
    inputFile: Option[Path] = None,
    expression: Seq[String] = Seq.empty
  ) extends CommandOptions {
    def command: String = cmdName
  }
}

/** `riddlc find <input> -- <expression>` — Unix `find`, over RIDDL definitions instead of files.
  *
  * *"grep is not a substitute parser for RIDDL."* The things found are definitions, statements,
  * ports and fields; the tests ask about kinds, names, containment, cardinality and resolved types;
  * and the answers are riddlc's own rather than a regex approximation of the grammar.
  *
  * **`--` is required** before the expression. riddlc already owns `-d`, `-q`, `-v`, `-w`, `-s` and
  * `-c` as global options, so a bare `-depth` would be ambiguous; everything after `--` is passed
  * through verbatim and never reinterpreted as a riddlc option.
  *
  * **It runs on a model that does not validate.** That is deliberate and is the whole point: the
  * corpus that most needs querying is the one with errors in it.
  *
  * `-exec` runs a command per match; `-replace` and `-delete` REWRITE the model, and are gated on a
  * re-parse and re-validation of the whole model before a single byte is written.
  */
class FindCommand(using pc: PlatformContext)
    extends Command[FindCommand.Options](FindCommand.cmdName) {
  import FindCommand.Options

  override def getOptionsParser: (OParser[Unit, Options], Options) = {
    val builder = OParser.builder[Options]
    import builder.*
    cmd(FindCommand.cmdName)
      .children(
        inputFile((v, c) => c.copy(inputFile = Option(v.toPath)))
      )
      .text(
        "Find definitions in a model, in the manner of Unix find. The expression follows a '--' " +
          "separator: riddlc find model.riddl -- -type entity -name 'Order*'"
      )
      -> Options()
  }

  override def interpretConfig(config: Config): Options = {
    val obj = config.getObject(commandName).toConfig
    val inputFile =
      if obj.hasPath("input-file") then Option(Path.of(obj.getString("input-file"))) else None
    val expression =
      if obj.hasPath("expression") then
        obj.getString("expression").split("\\s+").toSeq.filter(_.nonEmpty)
      else Seq.empty
    Options(inputFile, expression)
  }

  /** Separates the expression from the options WITHOUT relying on `--`.
    *
    * scopt treats any argument starting with `-` as an option, so the expression cannot simply be
    * declared as positional args — `-type entity` arrives as "Unknown option -type".
    *
    * **`--` is accepted and stripped, but NOT required, and that is a deliberate departure.** The
    * JVM launcher is an sbt-native-packager bash script that CONSUMES the first `--` itself
    * (`--) shift && no_more_snp_opts=1 && break`), so requiring it would mean typing `-- --` on the
    * JVM and a single `--` on the native binary — precisely the platform-dependent behaviour this
    * command was asked to avoid. It is safe to relax because riddlc's own options are positional
    * BEFORE the command name (`parseCommonOptions` takes them with `takeWhile(_.startsWith("-"))`),
    * so everything after `find <input>` is unambiguously the expression already.
    */
  override def run(
    args: Array[String],
    outputDirOverride: Option[Path] = None
  ): Either[Messages, PassesResult] = {
    val afterCmd = args.dropWhile(_ == FindCommand.cmdName)
    // Exactly ONE argument is the input file; everything after it is expression, whatever it looks
    // like. An earlier version took every leading argument that did not begin with `-`, which ate
    // the expression's own `(`, `)` and `;` -- so parentheses failed with "Unknown argument '('"
    // and grouping was unusable. `find` has the same shape (paths, then expression) and the same
    // reason to stop at the first one: the expression's vocabulary is not all dash-prefixed.
    val (fileArgs, rest) =
      if afterCmd.headOption.exists(a => !a.startsWith("-") && a != "--") then afterCmd.splitAt(1)
      else (Array.empty[String], afterCmd)
    // Every bare `--` is dropped, not just a leading one. `find m.riddl -dry-run -- -type X` used
    // to fail with "unknown test '--'", which reads as a problem with the separator rather than
    // with the flag's position -- riddl-models hit exactly that and reported the wrong cause. Both
    // documented forms now work, and `--` is accepted anywhere it is written.
    val expression = rest.filterNot(_ == "--").toSeq
    parseOptions(Array(FindCommand.cmdName) ++ fileArgs) match
      case None       => Left(Messages.errors("find: could not parse options"))
      case Some(opts) => run(opts.copy(expression = expression), outputDirOverride)
  }

  override def run(
    options: Options,
    outputDirOverride: Option[Path]
  ): Either[Messages, PassesResult] = {
    options.withInputFile { (inputFile: Path) =>
      FindExpression.parse(options.expression) match
        case Left(err) => Left(Messages.errors(s"find: $err"))
        case Right(parsed) =>
          loadAndValidate(inputFile).flatMap { result =>
            val matched = select(result, parsed)
            if parsed.mutates then mutate(inputFile, result, matched, parsed)
            else
              render(matched, parsed)
              runExecs(matched, parsed).flatMap { _ =>
                report(matched.size, parsed.expectMin).map(_ => result)
              }
          }
    }
  }

  /** Parse + validate, tolerating errors.
    *
    * Kept separate and SYNCHRONOUS because the mutating path has to run it a second time, on the
    * rewritten files, before deciding whether the rewrite may stand.
    */
  private def loadAndValidate(inputFile: Path): Either[Messages, PassesResult] = {
    implicit val ec: ExecutionContext = pc.ec
    val future = RiddlParserInput.fromPathSafe(inputFile.toString).map {
      case Left(messages) => Left(messages)
      // `shouldFailOnError = false` is what implements the ruling that `find` works on a model that
      // does not validate -- which is exactly when it is needed.
      case Right(rpi) => Riddl.parseAndValidate(rpi, shouldFailOnError = false)
    }
    Await.result(future, 60.seconds)
  }

  private def select(
    result: PassesResult,
    parsed: FindExpression.Parsed
  ): Seq[ProjectedNode] = {
    val projection = Pass.runPass[ProjectionOutput](
      PassInput(result.root),
      PassesOutput(),
      ProjectionPass(PassInput(result.root), result.outputs)
    )
    // A statement's operand kinds come from the `value-reference` nodes whose span sits inside its
    // own. Statement spans NEST, so this is containment against THIS node only -- the same trap
    // riddl-models hit summing over spans and counting a `when`'s contents twice.
    val valueRefs = projection.nodes.filter { n =>
      n.record.value.get("kind").contains(ujson.Str("value-reference"))
    }
    def kindsWithin(n: ProjectedNode): Seq[String] =
      val loc = n.value.loc
      valueRefs.collect {
        case vr if vr.value.loc.offset >= loc.offset && vr.value.loc.endOffset <= loc.endOffset =>
          vr.record.value.get("resolvedKind").collect { case s: ujson.Str => s.str.toLowerCase }
      }.flatten
    val ctx = FindContext(depthOf = n => n.parents.size, operandKindsOf = kindsWithin)
    val all = projection.nodes.filter(n => parsed.expr.matches(n, ctx))
    if parsed.actions.contains(FindAction.Quit) then all.take(1) else all
  }

  // -----------------------------------------------------------------------------------------------
  // -exec
  // -----------------------------------------------------------------------------------------------

  /** Runs each `-exec`. The child inherits stdout, as find's `-exec` does; the JSON record arrives
    * on its stdin, which is the part that makes this more useful than find -- the script gets the
    * node's resolved facts, not just its name.
    */
  private def runExecs(
    matched: Seq[ProjectedNode],
    parsed: FindExpression.Parsed
  ): Either[Messages, Unit] = {
    val failures = scala.collection.mutable.ListBuffer.empty[String]
    parsed.actions.foreach {
      case FindAction.Exec(cmd, batched) =>
        if batched then
          val argv = cmd.flatMap {
            case "{}" => matched.map(FindRender.identity)
            case a    => Seq(a)
          }
          val stdin = ujson.write(ujson.Arr.from(matched.map(FindEditor.recordFor)))
          val r = FindEditor.run(argv, stdin, inherit = true)
          if r.exit != 0 then failures.append(s"-exec exited ${r.exit}")
        else
          matched.foreach { n =>
            if failures.isEmpty || parsed.keepGoing then
              val argv = cmd.map(a => if a == "{}" then FindRender.identity(n) else a)
              val r = FindEditor.run(argv, ujson.write(FindEditor.recordFor(n)), inherit = true)
              if r.exit != 0 then
                failures.append(s"-exec exited ${r.exit} for ${FindRender.identity(n)}")
          }
      case _ => ()
    }
    if failures.isEmpty then Right(())
    else Left(Messages.errors(failures.mkString("; ")))
  }

  // -----------------------------------------------------------------------------------------------
  // -replace / -delete
  // -----------------------------------------------------------------------------------------------

  /** Rewrites the matched spans, but only if the result still parses and validates no worse.
    *
    * The order is deliberate and is the whole safety story:
    *
    *   1. run every script and collect the replacement text, applying NOTHING yet;
    *   2. refuse the entire run if any script failed, or if any two spans overlap;
    *   3. compute each file's new text in memory;
    *   4. write, re-parse, re-validate -- and RESTORE every file if the model got worse.
    *
    * Step 4 writes before it knows the answer because riddlc resolves `include` against the file
    * system: a model whose fragments live in several files cannot be re-parsed from memory. The
    * originals are held in memory for the duration, so a rejected rewrite is undone completely;
    * the exposure is a crash during the re-parse, which is narrow and is the price of validating
    * the real thing rather than an approximation of it.
    */
  private def mutate(
    inputFile: Path,
    before: PassesResult,
    matched: Seq[ProjectedNode],
    parsed: FindExpression.Parsed
  ): Either[Messages, PassesResult] = {
    if matched.isEmpty then
      report(0, parsed.expectMin).map(_ => before)
    else
      buildEdits(matched, parsed).flatMap { edits =>
        FindEditor.plan(edits) match
          case Left(problems) =>
            // Overlapping edits are refused as a whole rather than partially applied: which of two
            // conflicting rewrites won would otherwise depend on application order.
            Left(Messages.errors(("find: overlapping edits, nothing written" +: problems).mkString("\n")))
          case Right(byFile) =>
            val originals = byFile.keys.map(f => f -> Files.readString(f)).toMap
            val rewritten = byFile.map { case (f, es) => f -> FindEditor.apply(originals(f), es) }
            if parsed.dryRun then
              // `-dry-run`, NOT the global `--dry-run`: `Commands.handleCommandRun` short-circuits
              // on the global flag and never invokes the command at all, so a command cannot
              // implement a meaningful dry run on top of it.
              FindEditor.showDiff(originals, rewritten)
              report(matched.size, parsed.expectMin).map(_ => before)
            else applyAndVerify(inputFile, before, originals, rewritten, matched.size, parsed)
      }
  }

  private def buildEdits(
    matched: Seq[ProjectedNode],
    parsed: FindExpression.Parsed
  ): Either[Messages, Seq[FindEditor.Edit]] = {
    val edits = scala.collection.mutable.ListBuffer.empty[FindEditor.Edit]
    val failures = scala.collection.mutable.ListBuffer.empty[String]
    matched.foreach { n =>
      if failures.isEmpty || parsed.keepGoing then
        val who = FindRender.identity(n)
        FindEditor.fileOf(n) match
          case None => failures.append(s"$who has no source file to edit")
          case Some(file) =>
            val loc = n.value.loc
            parsed.actions.foreach {
              case FindAction.Delete =>
                edits.append(FindEditor.Edit(file, loc.offset, loc.endOffset, "", who))
              case FindAction.Replace(cmd) =>
                val argv = cmd.map(a => if a == "{}" then who else a)
                val r = FindEditor.run(argv, ujson.write(FindEditor.recordFor(n)), inherit = false)
                if r.exit != 0 then failures.append(s"-replace exited ${r.exit} for $who")
                else if r.stdout.isEmpty && !parsed.allowEmpty then
                  // Silence is far likelier to be a broken script than an intended deletion, and
                  // the intended deletion has its own spelling.
                  failures.append(
                    s"-replace produced no output for $who (use -allow-empty, or -delete)"
                  )
                else edits.append(FindEditor.Edit(file, loc.offset, loc.endOffset, r.stdout, who))
              case _ => ()
            }
    }
    // In replace mode a partial application is worse than none: the model would be left in a state
    // neither the author nor the script intended. So one failure discards every edit, `-keep-going`
    // or not -- what that flag buys is a COMPLETE list of what went wrong instead of the first.
    if failures.nonEmpty then
      Left(Messages.errors(("find: nothing written" +: failures.toSeq).mkString("\n")))
    else Right(edits.toSeq)
  }

  /** Writes and verifies through the ONE shared gate in [[FindEditor.applyVerified]].
    *
    * `validate --fix` asks the identical question, so the write-revalidate-restore logic lives
    * there rather than being copied here.
    */
  private def applyAndVerify(
    inputFile: Path,
    before: PassesResult,
    originals: Map[Path, String],
    rewritten: Map[Path, String],
    count: Int,
    parsed: FindExpression.Parsed
  ): Either[Messages, PassesResult] = {
    FindEditor.applyVerified(
      originals,
      rewritten,
      before.messages.count(_.isError),
      () => loadAndValidate(inputFile),
      "find"
    ) match
      case Left(messages) => Left(messages)
      case Right(after) =>
        pc.log.info(s"${rewritten.size} file(s) rewritten")
        report(count, parsed.expectMin).map(_ => after)
  }

  private def render(matched: Seq[ProjectedNode], parsed: FindExpression.Parsed): Unit = {
    // No action given means `-print`, exactly as in find -- and, also as in find, giving `-exec`
    // SUPPRESSES the default print, because the script is the output.
    val printing = parsed.actions.filterNot(_ == FindAction.Quit)
    val actions = if printing.isEmpty then parsed.actions :+ FindAction.Print else parsed.actions
    actions.foreach {
      case FindAction.Print     => matched.foreach(n => println(FindRender.print(n)))
      case FindAction.Location  => matched.foreach(n => println(FindRender.location(n)))
      case FindAction.PathOnly  => matched.foreach(n => println(FindRender.identity(n)))
      case FindAction.Print0    => matched.foreach(n => print(FindRender.identity(n) + "\u0000"))
      case FindAction.ListTable => FindRender.table(matched).foreach(println)
      case FindAction.Printf(f) => matched.foreach(n => print(FindRender.printf(n, f)))
      case FindAction.Quit      => ()
      // Not rendering actions. Enumerated rather than left to a wildcard: `commands` compiles with
      // `--no-warnings`, so a fall-through here would be a silent no-op for a future action, which
      // is the exact defect the total-dispatch rule exists to prevent.
      case _: FindAction.Exec    => () // runExecs owns these
      case _: FindAction.Replace => () // the mutating path never calls render
      case FindAction.Delete     => ()
    }
  }

  /** The count is printed ALWAYS, including zero, and through the log — which now writes to stderr,
    * so it cannot corrupt a piped result.
    *
    * This is the single most important behaviour in the command. The recurring failure in this
    * repository is not a wrong answer but a confident answer computed over nothing: a selector that
    * silently matched nothing is indistinguishable from a clean corpus, and three of riddl-models'
    * nine scripted defects were exactly that. `-expect-min` turns it into a failure.
    */
  private def report(count: Int, expectMin: Option[Int]): Either[Messages, Unit] = {
    pc.log.info(s"$count matched")
    expectMin match
      case Some(min) if count < min =>
        Left(Messages.errors(s"find: expected at least $min match(es) but found $count"))
      case _ => Right(())
  }

  override def loadOptionsFrom(configFile: Path): Either[Messages, Options] =
    super.loadOptionsFrom(configFile).map(options => resolveInputFileToConfigFile(options, configFile))

  override protected def replaceInputFile(options: Options, inputFile: Path): Options =
    options.copy(inputFile = Some(inputFile))
}
