# riddl build, packaging and API reference

Reference material split out of `CLAUDE.md` on 2026-09-10, when that file
exceeded the 150k character limit at which it stops being loaded in full.

These sections are consulted during a **named task** — you know you are adding a
command, publishing the npm package, or wiring a new module — so a pointer is
enough to reach them. The traps that must fire unprompted (the target-path
layout, the EBNF/parser sync requirement, the reflectivity round-trip
requirement, the Scala-version bump surface, and everything under "Subtle
Patterns and Gotchas") stayed in `CLAUDE.md` deliberately.

See also `language-constructs.md` for per-construct language detail.

---

## Adding a New Command

(Corrected 2026-08-24 — this section previously named a `def name: String` and a
`context: PlatformContext` parameter, copied from an API predating `Command`'s
current shape. Neither exists.)

1. **Options** live in the command's companion, with the name as a constant:
   ```scala
   object MyCommand {
     final val cmdName = "mine"
     case class Options(inputFile: Option[Path] = None) extends CommandOptions {
       def command: String = cmdName
     }
   }
   ```
2. **The class takes `using PlatformContext` and passes the name to the base**:
   `class MyCommand(using pc: PlatformContext) extends Command[MyCommand.Options](MyCommand.cmdName)`
3. **Implement**:
   - `override def getOptionsParser: (OParser[Unit, Options], Options)` — a
     scopt `cmd(...)` plus the default `Options()`
   - `override def run(options: Options, outputDirOverride: Option[Path]): Either[Messages, PassesResult]`
   - `override def interpretConfig(config: Config): Options` — required for
     `riddlc from <conf> <cmd>`; read the block named by `commandName`
   - `override def loadOptionsFrom(...)` calling `resolveInputFileToConfigFile`,
     and `override protected def replaceInputFile(...)`, so a `.conf`'s
     `input-file` resolves relative to the `.conf` rather than the cwd
   - `override def run(args: Array[String], ...)` ONLY when the command needs
     arguments scopt cannot model — `find` does, because its expression is full
     of bare `(`, `)` and `;` tokens.
4. **Register it in THREE places**, or it is missing on one platform:
   `commands/src/main/scalajvm/.../CommandLoader.scala` (`loadCommandNamed`
   **and** the `optionParsers` Seq that `riddlc help` renders from), the same
   two in `commands/src/main/scalanative/.../CommandLoader.scala`, and
   `commands/src/main/scala/.../Commands.scala` (`loadCommandNamed`, a third
   copy).
5. **Add a block to `commands/input/cmdoptions.conf`** so `from` works and the
   standard options-reading test covers it.

**Diagnostics go to STDERR** (`pc.log`, since 2026-08-23); anything a script is
meant to parse goes to stdout with `println`. A command that prints its result
through `pc.log` produces a stream whose lines are prefixed `[info]` — invisible
to the eye and fatal to a pipe.

**The global `--dry-run` cannot be implemented on top of.**
`Commands.handleCommandRun` short-circuits on it and logs "Would have executed…"
*without ever invoking the command*. A command needing a real dry run declares
its own flag, as `find -dry-run` does.

---

## AST Architecture

- **`Contents[CV]`** is an opaque type wrapping `ArrayBuffer[CV]`. Extensions:
  `.toSeq`, `.isEmpty`, `.nonEmpty`. `.toList` and `.iterator` are NOT
  available — use `contents.toSeq.map{…}.toJSArray` for JS conversion.
- **Token is a Scala 3 enum**, not case classes: type name via
  `token.getClass.getSimpleName.replace("$","")`, text via
  `token.loc.source.data.substring(token.loc.offset, token.loc.endOffset)`.
- **`At`** carries `line`, `col`, `offset`, `endOffset`, `source`, always
  1-based. BAST delta-encodes offsets to compress them.

## Pass Framework

Prefer `HierarchyPass`, which maintains parent context: implement
`process(value: RiddlValue, parents: ParentStack)` pattern-matching the node
types you care about, declare `def requires(): Seq[Pass]`, override `result` to
return your `PassOutput` subclass, then add to the standard passes or invoke
explicitly. (`Pass` and `DepthFirstPass` are the other bases.)

`BASTWriterPass` (passes module) extends `HierarchyPass` and uses the
`BASTWriter` utilities (language module) for byte writing — write speed is
sacrificed for read speed, with string interning for deduplication.


## NPM Packaging — `@ossuminc/riddl-lib`

`riddlLib` exports a TypeScript-friendly `RiddlAPI` object: method names
preserved (not minified) via `@JSExport`, returns shaped as
`{ succeeded: boolean, value?: object, errors?: Array<object> }`, and all Scala
types converted to plain JS (`List`→`Array`, case classes→plain objects,
`Either`→the result object). ESModule (`"type": "module"`); consumers write
`import { RiddlAPI } from '@ossuminc/riddl-lib'`. Published to GitHub Packages.

```bash
sbt riddlLibJS/npmPrepare        # assemble package (pure sbt)
sbt riddlLibJS/npmPack           # .tgz tarball
sbt riddlLibJS/npmPublishGithub  # or npmPublishNpmjs
```

CI: `.github/workflows/npm-publish.yml`, on release or manual dispatch. Docs:
`NPM_PACKAGING.md`, `TYPESCRIPT_API.md`.

## Import vs Include

- **Include is context-aware** and already implemented: `include
  "entities.riddl"` may appear anywhere in the hierarchy, and the parser rules
  come from the enclosing container — an include in a Context must contain
  Context-valid content.
- **Import loads BAST files**: `import "f.bast"` (all Nebula contents),
  `import domain X from "f.bast"` (selective), `import type T from "f.bast" as
  MyT` (aliased). Legal at root level, inside domains and inside contexts; 14
  definition kinds. Key files: `CommonParser.bastImport()` /
  `selectiveBastImport()`, `TopLevelParser.loadBASTImports()` (post-parse
  loading), `BASTLoader`, `AST.BASTImport`; validation is in `ValidationPass`.


## RiddlLib & RiddlAPI

Core parsing/validation logic lives in `RiddlLib` (shared trait + companion
object, `riddlLib/shared/…/RiddlLib.scala`) and is usable on JVM, JS and Native
with a `given PlatformContext` in scope (each platform's
`com.ossuminc.riddl.utils.pc`). The JS-only `RiddlAPI.scala` is a thin facade
that delegates to it and converts results to plain JavaScript objects, adding
`@JSExport` methods, `getDomains`, `inspectRoot` and helpers like
`formatErrorArray`.

- **Every `origin` parameter goes through `RiddlLib.originToURL()`** — a leading
  `/` means `URL.fromFullPath(origin)`, otherwise
  `URL(URL.fileScheme, "", "", origin)`.
- **Scala 3 lambdas**: write `lines.foreach(line => pc.log.info(line))`, NOT
  `lines.foreach(pc.log.info)` — Scala 3 does not convert a by-name parameter
  (`=> String`) to a function (`String => Unit`).
- **Code shared between riddlc commands (JVM) and RiddlAPI (JS) goes in
  `utils/`** — e.g. `InfoFormatter`, used by `InfoCommand` and by `RiddlAPI` via
  `@JSExport`.


## GitHub Workflows

All use JDK 25. **scala.yml** — parallel JVM/Native/JS matrix build, 60min
timeout, dependency scanning with SARIF upload. **coverage.yml** — auto on PRs
and pushes, 45min. **hugo.yml** — triggers only on Hugo/doc changes (not all
`.scala` files), with ScalaDoc caching. **release.yml** — see Build/CI below.

