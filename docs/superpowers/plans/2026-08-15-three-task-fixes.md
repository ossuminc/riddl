# Three triaged task fixes — plan

**Date:** 2026-08-15
**Branch:** `release/2`
**Source:** the three riddl-models reports triaged at the start of this session.
Verified against the code before planning; each defect is real and located.

**Order:** A → B → C. A degrades as riddl-models' migration succeeds; B is a hard
parse error with a misleading message; C changes the wire format and is best done
last, while the revision decision is fresh.

## Global constraints

- Scala 3 syntax only.
- Every parser change needs a matching EBNF change, then a GBNF regeneration.
  Validators live in `language/src/test/scalajvm/python`; run them with
  `.venv/bin/python` (Homebrew python3 is externally managed and fails).
- Do NOT run `scalafmt` / `scalafmtCheckAll` — deferred to the 2.0 release.
- `<module>/testOnly *`, never bare `test`. ONE sbt invocation, `;` separators.
  Count the `Suites: completed` lines.
- NEVER `pkill -f sbt`.
- Three corpus suites are RED for pre-existing reasons: `Root2JsonCorpusTest`
  (59/190), `RiddlModelsRoundTripTest`, and riddlc's local-corpus tests. A/B with
  `git stash` before attributing anything.
- Commit messages end with `Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>`.

---

## Fix A — the populates-repository check is blind to the `ValueRef` arm

**Report:** `task/2026-08-14-valueref-migration-blinds-the-populates-repository-check.md`

**The defect, confirmed in code.**
`ValidationPass.scala:3266-3270`:

```scala
val sentType = ts.msg match
  case mr: MessageRef => resolution.refMap.definitionOf[Type](mr.pathId)
  case c: Constructor => resolution.refMap.definitionOf[Type](c.ref.pathId)
  case _: ValueRef    => None // type comes from the clause; not a declaration here
```

The check gives up on exactly the arm the corpus is migrating *to*. riddl-models
measured it corpus-wide with a negative control: **863 warnings → 9** after
migrating 10,298 forwarding sites, with nothing about the models changed;
reverting ONE site to the bare form brought its warning back.

**Why it matters more than the count.** It gets worse precisely as the migration
succeeds. Once the bare form is an Error everywhere, every site is a `ValueRef`
and this check is dead corpus-wide *while still appearing to pass*.

**The fix.** Resolve the operand's type rather than pattern-matching on how it was
written. `operandType` (`ValidationPass.scala:803`) already does exactly this for
all three arms and is the shape to reuse:

```scala
private def operandType(m: MessageRef | Constructor | ValueRef): Option[Type] = m match
  case mr: MessageRef => resolution.refMap.definitionOf[Type](mr.pathId)
  case c: Constructor => resolution.refMap.definitionOf[Type](c.ref.pathId)
  case vr: ValueRef   => resolution.refMap.definitionOf[Type](vr.path)
```

**The sweep the report asks for, and it is the larger half.** Every check that
pattern-matches `MessageRef` is now potentially half-blind, because a second arm
carries the same information by a different route. Grep for `case .*: MessageRef`
across `passes/src/main` and, for each, decide whether it is asking *"how was this
written"* (fine) or *"what type is this"* (blind). Report the full list with the
verdict for each — the sweep's value is the audit, not just the fixes.

`9d04c0d47` did this once already for the addressing and completeness checks and
missed this one, so treat "it was swept before" as no evidence at all.

**Tests.** A projector `tell`ing an event to a repository that does not define it
must warn identically whether written as `tell event C.E to repository R` or as
`on e: event C.E is { tell e to repository R }`. Assert an exact count on both,
not `nonEmpty` — under-firing is the bug and `nonEmpty` cannot see a drop from 2
to 1. Add a negative control where the type IS defined in the repository, so the
check is not simply always-on.

**Corpus expectation.** This should RAISE the warning count on riddl-models
substantially — that is the fix working, not a regression. Record the before/after
numbers in the reply to the task file.

---

## Fix B — an identifier beginning with `to` fails to parse in `send`/`tell`

**Report:** `task/2026-08-14-value-ref-starting-with-to-fails-to-parse.md`

**The defect, confirmed in code.** `Readability.readable` (`:15-17`) is:

```scala
def readable[u: P](key: String): P[Unit] = P(key)
```

No word boundary — unlike `Keywords.keyword` (`:28-30`), which ends in
`~~ &(isNotKeywordChar)`. `boundMessageValue` (`StatementParser.scala:132`) guards
with `!to`, so `tourCompleted` trips the guard, the `ValueRef` arm dies, and the
error surfaces from `messageValue` as *"Expected one of (command | event | query
| result)"* — pointing at a message kind when the real problem is that the
author's identifier began with two particular letters.

Reporters hit it on 4 of 10,298 migrated sites (`TourCompleted`,
`ToleranceEvaluated`, `TouchpointRecorded` ×2) and renamed the bindings to work
around it. **That ratio is the point: rare enough to look like a one-off, common
enough that any corpus-wide migration trips it.**

**Two fix scopes — this is the one judgment call in the three.**

1. **Narrow:** boundary-check only the `!to` guard in `boundMessageValue`.
2. **General:** give `readable` a word boundary, fixing all twelve readability
   words (`and are as at by for from in of so that to wants with`) at once.

**Recommendation: the general fix, with the narrow one as the fallback.** A
readability word matching the prefix of a longer identifier is wrong everywhere,
not just here, and this is the third time in two days that patching an instance
has left the shape alive. But it is a TIGHTENING: any place the grammar currently
relies on a readability word matching a prefix will start failing. Run the full
suite plus the corpus; **if the general fix reddens anything that is not obviously
a latent bug of the same kind, fall back to the narrow fix and file the general
one** with the evidence. Do not force it through.

**Tests.** Table-driven, from the report's own negative controls: `tourCompleted`,
`toleranceEvaluated`, `totalX` must parse; `abcCompleted`, `termX`, `typeX` must
keep parsing (they are the controls proving this is specific to `to`, not a
general keyword-prefix problem). Cover BOTH `send` and `tell`. Remember a
parse-failure assertion cannot use `parseAndValidate` — its `Left` branch calls
`fail` directly.

**Reply to riddl-models** when it lands: they renamed three bindings to
participle-first forms as a workaround and asked to be told so they can normalise
them back.

---

## Fix C — BAST drops every URL's scheme and authority

**Report:** `task/2026-08-14-shown-by-loses-its-url-scheme-and-host-through-bast.md`

**The defect is BROADER than reported.** They reported `shown by` losing
`https://ossum.tech` and coming back as `file:///`. It is not a `ShownBy` bug.
`BASTWriter.writeURL` (`:2363`) writes only two of four fields:

```scala
writeString(url.basis)
writeString(url.path)
```

and `BASTReader.readURL` (`:2885`) rebuilds with two of them hardcoded:

```scala
if basis.isEmpty && path.isEmpty then URL.empty
else URL(URL.fileScheme, "", basis, path)
```

`URL` is `case class URL(scheme, authority, basis, path)`. **Every URL through
BAST loses its scheme and authority.** `described at <url>` round-trips in their
model only because its URL is already file/relative — which is exactly the
discriminator they suspected.

**The fix.** Write all four fields; read all four back. Keep the `URL.empty`
sentinel behaviour for the all-empty case.

**Revision decision — this rides 18, it does not bump to 19.** `FORMAT_REVISION`
18 was spent by numeric literals (`6cfeceb2f`) and **has not shipped in a
release**, so folding this in avoids a needless `.bast` regeneration across
riddl-models, on the same reasoning BACKLOG § 2 already applies to A20 and A38.
Extend the revision-18 comment to name this change too. If 18 has shipped by the
time this lands, bump to 19 instead and say so in the commit.

**Tests.** Round-trip a `ShownBy` with `https://ossum.tech/mockups/survey-map` and
assert scheme, authority and path all survive — assert the FIELDS, not
`toExternalForm`, so a failure names which one was lost. Add `described at
<http url>` in the same test, since the report notes it currently round-trips by
luck and would silently regress. Include a definition AFTER the URL to catch
misalignment: a BAST error names where the reader derailed, never what derailed
it.

**Regenerate `language/input/import/NotImplemented.bast`** from its own directory
if the revision changes. Verify with `cmp -l` that only byte 12 differs.

**Reply to riddl-models** with the finding that it was never a `ShownBy` bug, so
they know their other URLs were affected too.
