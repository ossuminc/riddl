---
name: rc
description: Use when cutting a RELEASE CANDIDATE of riddl — tagging X.Y.Z-rc.N, publishing it as a GitHub prerelease, and later promoting it to a final release. Also use when the user says "cut an RC", "release candidate", "rc.1", or asks to promote an RC to final. For a normal release use /ship instead.
---

# Cut a Release Candidate

An RC is a **real, immutable artifact** that nothing resolves to by default. That
second half is the whole design: every distribution channel must require the user
to ask for it **by name**, or an RC becomes what people get by accident.

| Channel | Stable | Release candidate |
|---|---|---|
| GitHub | release | release marked **prerelease** |
| GitHub Packages (Maven) | `1.32.0` | `1.32.0-rc.1` (SemVer sorts it BELOW the release) |
| npm | `latest` dist-tag | **`rc`** dist-tag — `npm publish --tag rc` |
| Homebrew | `Formula/riddlc.rb` | **`Formula/riddlc-rc.rb`** |
| **ossum.ai blog** | announcement post | **nothing — suppressed entirely** |

**Not Maven Central.** riddl publishes with `With.GithubPublishing`; every
coordinate lives in GitHub Packages. Do not go looking for a Sonatype step.

**The blog is the exception that proves the rule.** Every other row makes the RC
reachable but opt-in BY NAME. A blog post has no opt-in — it announces to
everyone — so the only correct RC behaviour is not to post. 2.0.0-rc.1 published
a live post because `release.yml`'s `notify-blog` job had no prerelease guard
while `update-homebrew` did. Guard added:

```yaml
notify-blog:
  if: github.event.release.prerelease != true
```

Boolean `true`, NOT the string `'true'`. GitHub casts mismatched types to a
number before comparing, so `!= 'true'` evaluates `1 != NaN` — always true, guard
silently dead.

**When adding any new release channel, decide its RC behaviour at the same
time** and add a row here. The failure mode is not a channel that does the wrong
thing; it is a channel nobody thought about.

## Naming

`MAJOR.MINOR.PATCH-rc.N`, annotated, no `v` prefix (a `v` breaks sbt-dynver).

```bash
git tag -a 1.32.0-rc.1 -m "Release candidate 1 for 1.32.0"
```

**Dotted `rc.1`, never `RC1`.** SemVer compares dot-separated numeric identifiers
NUMERICALLY, but a bare `RC1` is one alphanumeric identifier compared as ASCII —
so `RC10` sorts before `RC2`. Free to avoid, painful to discover at rc.10.

**Never retag.** A published tag is immutable. Fixes get `-rc.2`.

## Branch

An RC MAY be cut from a release branch such as `release/2`. This is the one
exception to "always publish from `main`", written down in `../CLAUDE.md`, and it
is safe only because every channel above requires opting in by name.

The FINAL release still comes from `main`.

sbt-dynver is branch-agnostic — it resolves tags via `git describe` — so the tag
produces exactly its own version with `isSnapshot=false`. Verified: `1.32.0-rc.1`
on `release/2` → version `1.32.0-rc.1`, not a snapshot.

## Steps

### 1. Certify FROM CLEAN

**First check whether CI already did it.** If a CI run SUCCEEDED on a commit
whose CODE is identical to what you are about to tag, that run is the
certification and you may go straight to step 2. Re-running it locally proves
nothing new.

```bash
git rev-parse HEAD
gh run list --branch release/2 --limit 5 \
  --json number,headSha,conclusion --jq '.[]|"#\(.number) \(.conclusion) \(.headSha[0:9])"'
# If the green run is not on HEAD, check what actually differs:
git diff --stat <green-sha>..HEAD
```

Code means anything the build compiles or reads: `**/*.scala`, `**/*.sbt`,
`project/**`, `**/*.riddl`, test fixtures, `.github/workflows/**`. Commits that
touch only `NOTEBOOK.md`, `CLAUDE.md`, `.claude/**` or other prose do NOT
invalidate a green run — tagging a docs-only commit on top of certified code is
safe.

Everything below applies when code HAS changed since the last green run.

Incremental runs lie. They have hidden a CI-gating grammar failure and a test
that only passed against stale classes, both in one afternoon. There is no
shortcut here.

**Shut the sbt server down FIRST, and confirm the sbt VERSION.** A warm server
keeps running the sbt it booted with and IGNORES a changed `project/
build.properties`, mentioning it only as a passing `[warn] sbt version mismatch,
using: X, in build.properties: "Y", use 'reboot' to use the new value` that
scrolls past in a batch log. On 2.0.0-rc.11 this certified the whole tree under
the OLD sbt after a security bump to 2.0.6 — green, above every floor, and
against the wrong toolchain. `reload` does NOT fix it; only `shutdown` (or
`reboot`) does.

```bash
sbt -batch shutdown                       # stop any warm server
sbt -batch "show sbtVersion"              # MUST equal project/build.properties
```

Then certify:

```bash
sbt clean
sbt "cJVM; cJS; cNative"
sbt tJVM      # separately — a failure in one aborts a chained run
sbt tJS
sbt tNative
```

**Confirm the suites actually RAN.** In sbt 2 `test` resolves to `testQuick`, which
skips suites it judges unaffected, and that judgement SURVIVES `clean` because the
action cache does. The `t*` aliases were built on `test` and therefore certified
nothing for the skipped modules while exiting 0 — this mis-certified 2.0.0-rc.2 on
its first attempt, and in CI the JS row was running 109 of 567 tests with
`languageJS`, `passesJS` and `testkitJS` silently skipped. The aliases now use
`testOnly *`, which ignores incremental state, but VERIFY rather than assume:

```bash
grep -c "No tests to run" <log>     # MUST be 0
```

An exit code of 0 is not evidence. Compare the suite COUNT against the MINIMUMS
below; anything lower means tests were skipped, not deleted, and the run has
certified nothing.

**Minimum test counts** (a release must meet or exceed these):

| Row | Minimum | Suites |
|---|---|---|
| JVM | **2747** | 7 |
| JS | **840** | 5 |
| Native | **2726** | 7 |

**Native raised to 2726 at 2.0.0-rc.17 (2026-08-18).** JVM and JS were NOT
re-measured locally — CI certified them on the exact tagged commit, so there is no
comparable LOCAL total for those two rows and mixing a CI number into a local floor
would corrupt it. Leave a row alone rather than raise it from the wrong measurement.

**rc.17's Native leg is also the worked example of a stitched run.** The seven-module
chain died mid-way with `fatal signal 9` naming `JsonImportRoundTripTest`; that suite
passes alone, and the full `riddlLibNative` suite then passed too (17/144), so it was
transient resource pressure and SIGKILL merely named whichever test was in flight.
`riddlcNative` was run separately afterwards because the `;` chain had aborted before
reaching it. 2726 is therefore a sum across three invocations, every module accounted
for — which is fine for a floor, since the floor counts cases RUN, but say so rather
than implying one clean pass.

**Raised 2026-08-17 at 2.0.0-rc.15 (2737 / 840 / 2456).** This was a LARGE jump
— +268 / +87 / +548 — and it does not reconcile against one session's work,
because the floors were simply not raised by the sessions between 08-14 and
08-17 (the `at` lookup, A20 typed holes, numeric literals, `!`/`not` synonymy,
and rc.15's own four items all landed in between). **A floor that lags is a
weaker gate than one that tracks**, since a skipping bug hides in the slack;
raise it every time, even when nothing else about the run is interesting.

**The deliberate-failure count is TWO again as of 2.0.0-rc.17 (2026-08-18)**, and it
went back up for a legitimate reason. `RunRiddlcOnLocalTest`'s `dokn` and
`shopify-cart` cases exit 7 against `../riddl-examples`, which has NOT migrated to
rc.17's cross-context boundary Error and external-context persistence rule — `dokn`
alone reports 20 boundary + 10 persistence errors. It could not have migrated first:
**the rules ship in the RC the corpus needs in order to validate against them.** They
reproduce identically on JVM and Native, so a platform divergence is ruled out, and
they are BACKLOG [3.6], ruled acceptable when the Error was chosen over a warning.
Expect this to return to ZERO once riddl-examples migrates.
**Everything else is green.** The bar is unchanged: confirm a red case is one of these
TWO, by the exit-7-plus-boundary-errors signature, before accepting it. Anything else
is a regression.

**Do NOT take CI's word for these two.** At rc.17 CI reported both PASSING at the
tagged commit while they genuinely failed — it logged them 0.4 ms apart, replaying a
cached result, because the corpora are external directories whose CONTENT is in no
cache key. See BACKLOG [1.9]. **Certify corpus-dependent suites locally.**

**The Native floor jumped +252 at rc.16** (2456 → 2708) because the corpus suites
stopped downloading and started reading sibling checkouts, and because riddlLib
finally got `scala-jvm-native` test wiring. The JVM/Native gap is −39, from −729.

**Historical note, superseded:** the deliberate-failure count was TWO, down from 17 on
08-14 and 7 earlier on 08-17. The riddl-models corpus gate is **MET**: `Root2JsonCorpusTest`
validation-parity reads **190/190** and `RiddlModelsRoundTripTest` is green, after
riddl-models shipped `99fc29d1` on 2026-08-17. `ReportedIssuesTest` "should 406"
is green too — that fixture was OURS and was migrated to 2.0's `morph` operand
(`70cf5c648`).

**The two that remain belong to `../riddl-examples`**, not to this repo:
`RunRiddlcOnLocalTest`'s `dokn` and `shopify-cart` cases, both exiting 7 because
that corpus has not made the 2.0 syntax migration riddl-models completed. A task
is filed in `riddl-examples/task/2026-08-17-migrate-to-2.0-syntax.md`. **When it
lands, this repo's suite is fully green and this paragraph should say ZERO.**

The minimum above is cases RUN, which is what guards against skipping. **A/B any
remaining failure against a stashed tree before a release** — it is the only way
to tell a new failure from an inherited one, and certifying rc.15 took four
stash-and-rerun cycles to establish that all seven were inherited.

**Consequence for the JVM leg: `tJVM` cannot be run as one `;` chain.** The
chain aborts at `commands`, so `riddlLib` and `riddlc` never run and the leg
looks complete while having skipped two modules. Finish it with separate
`riddlLib/testOnly *` and `riddlc/testOnly *` invocations and count the
`Suites: completed` lines. Observed 2026-08-14.

These are MINIMUMS, not targets — the count only ever goes up as tests are
added. RAISE them whenever a release certifies higher, so the floor tracks
reality; never lower them to make a run pass. A number below the floor is a
skipping bug to find, not a threshold to adjust.

**Raised 2026-08-14 (second time that day)** by the message-value-source Tasks
4-7 certification (2469 / 753 / 1908), whose per-row delta again reconciled
EXACTLY against a prediction made before the run: +16 `passes` on JVM and
Native (9 bare-operand + 6 unused-initiate-id + 1 saga-step), +0 JS because
those suites live in `scala-jvm-native`, which the JS rows do not see. A
seventeenth case (`+1` on JVM and Native `passes`) was added afterwards with the
`WhenStatement.format` fix and re-verified on all three platforms.

**Earlier that day, raised** by the 2.0.0-rc.14 certification (2400 / 750 / 1671),
whose per-row delta reconciled EXACTLY against a prediction made before the run:
+9 JVM, +9 Native, +0 JS, from one 9-case suite in
`passes/src/test/scala-jvm-native`. Predicting first is what makes a total
evidence rather than a number.

**The JVM/Native SPREAD is now a tracked defect, not a fact of life** --
2400 vs 1671 is a 729-case gap, with `commands` (-198) the worst offender
because the riddl-models corpus gate appears to run JVM-only. See `BACKLOG.md`
1. Do not treat a low Native floor as normal.

Earlier floors: 2346 / 715 / 1619 on 2026-08-13; 2267 / 712 / 1552 earlier the same day; 2259 / 712 / 1549 earlier still;
2230 / 712 / 1520
and 2229 / 712 / 1519 on 2026-08-12; 2225 / 712 / 1515 at rc.12; 1846 / 674 /
1339 at rc.10). These are
LOCAL `testOnly *` totals, measured under a throwaway `--sbt-cache`. Local
totals differ from CI's because platform-specific suites vary, so compare CI
against CI and local against local.

**A delta that does not reconcile is the signal, not the total.** At rc.12 the
same 11 new cases moved JVM and Native by +11 and JS by **0** — correct,
because the suite lives in `src/test/scala-jvm-native`. Work out what each row
SHOULD move by before running, and treat a mismatch as a skipping bug. The
reverse trap is just as real: a suite in `src/test/scala` that is abstract with
concrete runners only in `JVMTests`/`JSTests` moves JVM and JS but not Native.

**That reverse trap is not hypothetical, and one test is enough to find it.**
The 2026-08-13 identity certification predicted +68 Native and got **+67**.
Chasing the single missing case showed `TypeParserTest` is abstract with
runners only in `JVMTests`/`JSTests` — and then that **13 shared `language`
parser suites, 169 cases, have NEVER run on Native** (filed in `BACKLOG.md`).
The hazard was already documented in the paragraph above while a floor absorbed
it silently for months, because **a total cannot tell you what is missing from
it.** Predict first: 1619 read on its own would have been written down as
"close enough" without a second glance.

**The Native floor went DOWN at rc.10 (1624 → 1339), and that is not a softened
gate.** It is the one case the "never lower" rule does not cover: the metric
changed definition. Until 2026-08-05 `tNative` named the `.jvm` rows for 5 of
its 7 modules, so the Native floor had been calibrated against a leg that was
mostly measuring JVM. Fixing the alias to name the real `*Native` rows dropped
the count by 368, and the drop is fully accounted for:

- The two rows that were ALREADY Native — `passesNative` (723) and
  `riddlcNative` (21) — are bit-identical before and after. Every row that
  CHANGED dropped.
- Those five modules hold 45 test files under `src/test/scalajvm`, which Scala
  Native cannot compile or run. `language` alone has 18, matching its −311.
- No coverage was lost: those suites run in the JVM leg, which certified 1846.

So the gate got more honest and the number got smaller; those are the same
event. **If a Native floor ever drops again, this is the standard of proof** —
per-row before/after, with the unchanged rows shown unchanged. Absent that, a
drop is a skipping bug.

Then every validator CI gates on:

```bash
cd language/src/test/scalajvm/python
.venv/bin/python ebnf_tatsu_validator.py     # MUST report zero "Unexpected failures"
.venv/bin/python gbnf_validator.py
.venv/bin/python validate_external_riddl.py --repo ../../../../../riddl-examples
.venv/bin/python validate_external_riddl.py --repo ../../../../../riddl-models
# ^ both of these now run in CI's ebnf-grammar-validation job, so a green CI run
#   covers them and this block is only for the code-changed path.
```

Use `.venv/bin/python`, not the Homebrew `python3` on PATH — TatSu is installed
in the project venv and the system Python is EXTERNALLY-MANAGED.

Read the TatSu result properly: `N/M passed` is not a failure count. The number
that matters is the **Unexpected failures** section; skipped include-fragments
and expected failures are accounted for by design.

### 2. Tag and publish

```bash
git tag -a 1.32.0-rc.1 -m "Release candidate 1 for 1.32.0"
git push origin 1.32.0-rc.1
gh release create 1.32.0-rc.1 --prerelease --title "1.32.0-rc.1" --notes-file <notes>
```

`--prerelease` is what sets `github.event.release.prerelease`, which the
workflows branch on. Without it an RC is treated as a normal release and reaches
stable users.

Release notes matter as much as for a final release — say what is being trialled
and what feedback is wanted.

**Each RC records only its DELTA.** The first RC of a line carries the full
release story; every later one says what changed since the previous RC and links
back. Do not restate the whole release each time.

**At promotion, MERGE the RC notes.** The final release notes are the rc.1 body
plus every subsequent delta, folded together into one coherent document — not a
pointer to a chain of prereleases, which nobody follows. See "Promoting to
final".

### 2b. Publish the libraries to GitHub Packages

**Nothing in CI does this** — `release.yml` builds binaries and dispatches, and
`npm-publish.yml` handles npm. The Maven-format artifacts are published from your
machine, and 2.0.0-rc.1 initially shipped without them because this step did not
exist: every coordinate still read 1.31.0 while riddlc, npm and Homebrew were all
on the RC.

**Publish from the TAG, not the branch head.** dynver derives the version from
`git describe`, so a branch head even one commit past the tag publishes
`1.32.0-rc.1-1-<hash>` instead of `1.32.0-rc.1`.

```bash
git status --porcelain                     # MUST be empty; dynver marks a dirty tree
git checkout 1.32.0-rc.1                   # detached HEAD, on purpose
sbt -batch "show version"                  # MUST print exactly 1.32.0-rc.1
sbt -batch "clean; publish"                # add `test` ONLY if step 1 had to run locally
git checkout release/2                     # or wherever you came from
```

**`clean` always; `test` only when the code is not already certified.** Step 1's
rule applies here too — re-running the suite against code a green CI run already
covered proves nothing and costs the better part of an hour. `clean` is NOT
optional either way: publishing must not pick up stale compiled output. (On
2.0.0-rc.3 the `test` in this step skipped 10 modules via `testQuick` anyway, so
it was not even a real suite run.)

Capture the WHOLE log — `| tail -N` throws away the
test summary and leaves you unable to show that tests ran, and a pipeline's exit
code is `tail`'s, not sbt's. Confirm sbt's own `[success]` line, and that a
`Tests: succeeded …` summary is present.

Verify against the registry rather than trusting the log:

```bash
gh api "/orgs/ossuminc/packages/maven/com.ossuminc.riddl-utils_3/versions?per_page=100" \
  --jq '[.[].name] | index("1.32.0-rc.1")'      # non-null == published
```

Expect every aggregated module across all three platforms — `riddl-utils`,
`riddl-language`, `riddl-passes`, `riddl-testkit`, `riddl-lib` (`_3`,
`_sjs1_3`, `_native0.5_3`), `riddl-commands` (`_3`, `_native0.5_3`), `riddlc`,
and the plugin. `diagrams`/`doc`/`prettify`/`stats` are retired or moved to
riddl-gen and must NOT appear.

**The sbt plugin publishes as `sbt-riddl_sbt2_3`** — an sbt 2 coordinate, since
this repo builds on sbt 2 and the plugin source is Scala 3 against the sbt 2 API.
It is therefore NOT consumable by the sbt 1.x consumer repos, which resolve
`sbt-riddl_2.12_1.0`; that line stops at 1.31.0 and cannot be revived from here
without maintaining a Scala 2.12 copy of the plugin. Consumers wanting a 2.x
plugin need their own sbt 2 / sbt-ossuminc 3.x migration first.

Consumers that only need the LIBRARIES are fine either way: those are ordinary
Scala 3 artifacts and resolve from an sbt 1.x build. And a consumer that just
wants the new riddlc can set `riddlcVersion := "1.32.0-rc.1"` on whatever
sbt-riddl it already has — the plugin shells out to a downloaded binary and never
parses RIDDL itself.

### 3. npm — automatic, do NOT publish by hand

`npm-publish.yml` triggers on the release and publishes for you, with the
dist-tag derived from the version (`rc` for `X.Y.Z-rc.N`). There is nothing to
run.

Publishing manually first makes that workflow FAIL with `E409 Cannot publish over
existing version` — a red X on the release for no reason. That happened on
2.0.0-rc.1.

Just confirm afterwards that the workflow logged `with tag rc`, and that
`latest` did not move.

Note the package publishes to **GitHub Packages**, not npmjs.com: `.npmrc` scopes
`@ossuminc` to `npm.pkg.github.com`. A consumer without that mapping cannot
resolve it.

### 4. Homebrew — **`riddlc-rc`**

**Built on both sides** (riddl `0850570c9`, homebrew-tap `50b2f28`).

- `release.yml`'s `update-homebrew` job branches on
  `github.event.release.prerelease` and sends `client_payload[formula]` of
  `riddlc-rc` or `riddlc`.
- The tap has `Formula/riddlc-rc.rb` (class `RiddlcRc`) with `conflicts_with`
  in both directions, and `update-formula.yml` routes on that payload field,
  defaulting to `riddlc` when absent and rejecting any other value before it
  becomes a file path.

**Exercised end to end and working** — rc.9, rc.10 and rc.11 each updated
`Formula/riddlc-rc.rb` and nothing else, leaving `Formula/riddlc.rb` on the
stable 1.31.0. Still worth the one-line check per RC, since it is cheap and the
failure would be silent:

```bash
git -C ../homebrew-tap fetch origin
git -C ../homebrew-tap show --stat --oneline origin/main   # must touch ONLY riddlc-rc.rb
```

Why a separate formula: Homebrew's `devel` block is deprecated and removed, and
there is no prerelease flag, so a versioned formula is the only way to ship an RC
without displacing the stable one. `brew upgrade` then tracks each line
independently, and the formula NAME is the "experimental" marking — users opt in
with `brew install ossuminc/tap/riddlc-rc`. (homebrew-core forbids unstable
versions, but that governs the official tap, not ours.)

**Why `riddlc-rc` and not `riddlc@rc`.** Homebrew derives a formula's class name
from its filename, and the `@` -> `AT` conversion fires ONLY when `@` is followed
by a DIGIT (`formulary.rb:453`). `riddlc@rc` therefore resolves to a class named
`Riddlc@rc`, which is not valid Ruby, so the formula CANNOT BE LOADED — every
`brew install` failed with "Expected to find class Riddlc@rc". `@`-suffixed names
are for numeric version lines only.

`riddlc@2` would have loaded, but it reads as "the 2.x line" and would mislead
once 2.0.0 ships. A plain `riddlc-rc` gives class `RiddlcRc`, and — because it is
not a VERSIONED formula — `conflicts_with` draws no `FormulaAudit/Conflicts`
offense, so there is no lint divergence to justify.

### 5. Check the native binary reports the RC

```bash
sbt "reload; riddlcNative/nativeLink"
target/out/native0.5/scala-<ver>/riddlc/riddlc info    # MUST say the RC version
```

`reload` first: a long-running sbt server computes dynver's version and the
`gitCommit` BuildInfo key at PROJECT-LOAD time and does not re-read git when HEAD
moves. Without it the freshly linked binary cheerfully reports the PREVIOUS
version — verified on 2.0.0-rc.2, where `show version` returned
`2.0.0-rc.1-13-<hash>` from a server started before the tag existed.

**Do not copy the binary to `~/Code/ossuminc/bin/` as a ROUTINE step.** Anyone
wanting a released RC gets it from the tap: `brew upgrade
ossuminc/tap/riddlc-rc`. For an ordinary release this step is just a sanity check
that the tag produced a binary reporting the right version.

### Publishing has THREE preconditions, not one

Treat them as a checklist. Each has bitten someone, twice in one day on
2026-08-03:

1. **Clean working tree.** sbt-dynver appends a timestamp
   (`…-9c922e42-20260803-1238`) when anything tracked is uncommitted, so the
   published version stops being reproducible from a commit — even when the
   dirty file is only markdown. Commit or stash FIRST.

   **Check it with `git -C <repo>` or an absolute path.** `git status` prints
   paths RELATIVE TO THE CURRENT DIRECTORY, and a shell that has wandered — say
   into `language/src/test/scalajvm/python` to run the validators — reports
   `M project/build.properties` for a DIFFERENT file of that name. On
   2.0.0-rc.11 that sent me diagnosing the wrong file, including "fixing" it.
   Two `build.properties` exist in this repo; only the root one pins the build.
2. **`reload` before `publishLocal`.** A long-running sbt server freezes dynver
   at project-load time; that is how rc.2 shipped a binary reporting rc.1.
3. **Wait for `nativeLink` to FINISH before copying the binary.** Copying while
   the link is still running stages the previous build under the new version's
   name, which is the worst of both.

Then verify what you actually shipped — `riddlc version` and the ivy paths —
before telling any consumer a version number.

**But DO stage one when a consumer needs a rule that has not shipped.** That is
what the directory is for, and it recurs: riddl-models could not merge its
duplicate adaptors until it had a riddlc that could FIND them, and no release
had the check. Build it, `mkdir -p ~/Code/ossuminc/bin`, copy, and verify it
enforces the new rule before telling anyone it is ready:

```bash
sbt "reload; riddlcNative/nativeLink"
cp target/out/native0.5/scala-<ver>/riddlc/riddlc ~/Code/ossuminc/bin/riddlc
~/Code/ossuminc/bin/riddlc from <a-model-that-violates-it>.conf validate   # must complain
```

An earlier version of this step said never to stage one at all. That was written
when the practice was retired and proved too absolute within the day.

### 6. Soak, then iterate or promote

Fixes go on the branch and get a new `-rc.N`. Never retag.

## Promoting to final

When the RC is good, the final release comes from `main`:

```bash
git tag -a 1.32.0 -m "Release 1.32.0"      # ideally the SAME commit as the last RC
git push origin 1.32.0
```

dynver picks the most recently created tag when several point at one commit, so
promoting without new commits works cleanly.

**Merge the RC notes first.** The final release body is rc.1's full release
story with each later RC's delta folded in where it belongs — a user reading the
2.0.0 notes should not have to visit three prereleases to learn what shipped.
Collect them with:

```bash
gh release list --limit 20 | grep -- -rc.
gh release view <tag> --json body --jq .body
```

Then run `/ship` as normal, and move the npm `latest` pointer:

```bash
npm dist-tag add @ossuminc/riddl-lib@1.32.0 latest
```

## Red flags

- Certifying incrementally instead of from clean, when code HAS changed.
- Certifying without `sbt -batch shutdown` and a `show sbtVersion` check, so a
  warm server runs the OLD sbt and the whole green run certifies the wrong
  toolchain. The mismatch is a single `[warn]` line in a batch log.
- Reading `git status` from a shell that has wandered out of the repo root. Its
  paths are relative to cwd, so it can name a different file than you think.
- Verifying a shared-code change on JVM and JS only. Scala NATIVE is the row
  that breaks differently: it rejects regex lookahead the other two accept, and
  a pattern compiled in a `val` fails at class INITIALISATION, surfacing as a
  Severe message with EMPTY text that names nothing. `passesNative/testOnly *`
  is one command; rc.5 was pushed twice without it.
- Lowering the minimum test counts so a run passes, instead of finding the
  skipping bug.
- Reading `92/113` as "21 failures" — or ignoring a non-empty Unexpected list.
- Omitting `--prerelease`, so the workflows treat it as a stable release.
- Publishing npm by hand — the workflow does it, and a manual publish makes it
  fail with a 409.
- Announcing the first RC before confirming the dispatch left `Formula/riddlc.rb`
  untouched.
- Retagging an existing RC instead of cutting the next one.
- Staging a native binary without `reload`, so it reports a stale commit.
- Trusting `sbt -batch` to defeat the frozen-dynver problem. It does NOT. On
  2.0.0-rc.8, `sbt -batch "show version"` at the freshly-checked-out tag printed
  `2.0.0-rc.7-3-<hash>` — the PREVIOUS tag, at a commit that was not even HEAD —
  while `git describe` correctly said `2.0.0-rc.8`. Publishing then would have
  shipped every coordinate under an rc.7 snapshot version. Put `reload` FIRST in
  every version-sensitive invocation (`reload; clean; publish`,
  `reload; riddlcNative/nativeLink`) and confirm `show version` prints the bare
  tag before publishing anything.
- Publishing libraries from the branch head instead of the tag, yielding
  `X.Y.Z-rc.N-<n>-<hash>` instead of `X.Y.Z-rc.N`.
- Skipping step 2b, so riddlc/npm/Homebrew carry the RC while every Maven
  coordinate still reads the previous release.
- Piping an sbt publish through `tail`, then reporting a green run you cannot
  evidence.
