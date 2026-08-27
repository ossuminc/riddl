# Engineering Notebook: RIDDL

Records open work, blockers, and design nuances that future AI
sessions need to know. Release history lives in git tags and
GitHub release notes — don't reproduce it here.

## HANDOFF

Orientation for a session with no memory of this work. **Open work is in `BACKLOG.md`**;
durable facts are in `CLAUDE.md`; what a change TAUGHT us is in this NOTEBOOK's body.
Ask `git` for branch, tree and unpushed span — never trust a written answer to those.

### Build state — verified 2026-08-26

**`2.0.0-rc.26` IS CUT, PUBLISHED AND VERIFIED**, from `c64f3388f` on `release/2`.
Certified cold (throwaway cache 0B -> 285M): **3068 / 1005 / 3026**, zero failures, zero
`No tests to run`, 19 module legs. All five channels checked against their own registry
rather than a log: Maven coordinates at `2.0.0-rc.26`, npm `--tag rc` with `latest` still
1.31.0, Homebrew touching ONLY `riddlc-rc.rb`, `notify-blog` **skipped**.

**Deliberate failures are ZERO**, because both corpora migrated against a STAGED binary
BEFORE the tag. That is now the preferred order in the `/rc` skill — a corpus needs the
RULE, not a published RELEASE. Do not go back to tagging red without a reason.

**`../bin/riddlc` is `2.0.0-rc.26`, built from Scala 3.9.0-RC4 at 15:00.** It is
therefore STALE relative to the working tree, which is on RC6. Restage with
`scripts/publish-and-stage.sh` (never `nativeLink` alone — ivy and binary must move
together). **BAST `FORMAT_REVISION` is 23.**

### The Scala 3.9.0-RC6 upgrade — COMMITTED

**Complete and green, 32 sites across 8 files** (`2c7d36af2`). Compile: 0 errors, 0
compiler warnings on all three platforms. Tests: **3068 / 1005 / 3026 —
byte-identical to the rc.26 certification on RC4**, which is the expected result
because no test source changed, and is therefore evidence that nothing was SKIPPED
rather than that nothing broke.

**`../bin/riddlc` is `2.0.0-rc.26-5-ab3ada49`, built on Scala 3.9.0-RC6** and restaged
2026-08-27 via `scripts/publish-and-stage.sh`, so the ivy artifacts and the binary came
from one invocation and agree. It is a real Mach-O arm64 file, not a symlink and not the
JVM launcher. **Verified by BEHAVIOUR, not by the version string**: the boundary-violating
repro errors and the two legal forms stay clean, which is the inversion of how the same
three models behaved on rc.26. `[0.6]` in BACKLOG carries the site list for the eventual
3.9.0-final bump.

### Traps a fresh session would hit

- **A Scala bump is 32 sites, not one.** The full version is a path segment
  (`target/out/<platform>/scala-<fullVersion>/`), hardcoded in `scala.yml`,
  `release.yml`, `coverage.yml`, `.sonarcloud.properties` and `Dockerfile`. A grep that
  omits `.github/` misses 11 of them. See `[0.6]`.
- **A red CI run on the tagged commit may be STALE rather than a regression.** rc.26's
  was: riddl-models fixed two corpus type errors 13 minutes AFTER CI started. Check
  `git -C ../riddl-models log` for commits timestamped inside the run window — one
  command, decisive — and confirm by RUNNING the model, not by re-reading the log.
- **Re-run the EXTERNAL grammar validators even when CI's grammar job is green.** CI
  reads the corpus as it stood at run time; riddl-models adopted `system.now` in 68
  fields afterwards. The EBNF covered it, but that green job could not have told you so.
- **A test-count prediction is only evidence if the instrument is checked.** Twice this
  session a count was wrong and the tell was arithmetic, never an error message: a file
  RENAMED across source trees (`scala-jvm-native` -> `scala`) adds no cases but changes
  which rows see it, and `git diff --name-only` collapses renames so it scores as new on
  every row. **One row hitting its number exactly while another misses by a constant is
  a prediction bug — a real skipping bug does not spare a row.**

### Certainty

Verified by running, this session: the rc.26 channel checks, the RC6 compile and test
totals, the staged binary's version and behaviour, TatSu 190/190 and 9/9, and that the
CM/To-Do work is committed and pushed in the `ossuminc` repo (`3dae364`, `5cda6fc` —
Reid committed it himself). Assumed, not verified: that RC6 is the newest Scala RC.

### `task/` — empty

The saga/tell boundary request was worked and closed today; 144 files in `task/done/`.
Nothing awaits triage, which is a fact about right now and not a reason to skip the check.

**Run `/ossuminc-skills:check-tasks` in the new session** — triage is the driver's call.

### Unpushed

`release/2` carries the boundary work (`msg-target-crosses-boundary`) unpushed, and the
Computational Model amendment is committed separately in the `ossuminc` repo (`dcaca50`),
also unpushed. Certified tri-platform locally, zero failures on all six rows; CI has not
seen it. Corpus cost measured on the staged binary: **6 findings in 1 of 190 models**
(reactive-bbq), all true positives, migration filed in `riddl-models/task/`.

## 2026-08-27 — a boundary rule that already half-existed (`msg-target-crosses-boundary`)

riddl-models filed it as *"missing validation — an encapsulation violation currently passes
silently"*. The claim was right and the diagnosis was wrong, and the difference is the entry.

**A check already existed and was already being called on the tell target.**
`BasicValidation.checkCrossContextReference` is a NESTED match — `contextOf(definition)` on the
outside, `contextOf(container)` on the inside — and its inner `case None`, meaning *the sender is
outside every context*, was `()`. That is precisely the reported case: a domain-scope saga. Move
the same saga into a sibling context and it warns today. **The bug was an unwritten arm, not an
unwritten check**, which made the fix smaller and told us where it went. "No check exists" is a
conclusion from OUTPUT; the code said something more specific.

**Reading the code also found three partly-overlapping diagnostics already firing on a
cross-context tell** — the reference warning, `stmt-tell-crosses-context` (about the message
type's domain, not the target), and `msg-tell-target-unreachable` (connectors). The new Error
therefore had to REPLACE the reference warning on a transmission target rather than run beside
it, or one defect reports twice. **Adding a check to a crowded area is mostly a question about
what is already there.**

**Reid's ruling widened it and narrowed an exemption.** `forward` is in — it takes the same
target shapes, and leaving it out would make the one statement that DELEGATES the one statement
free to ignore the boundary (the `error`/`terminate` lesson: when a rule is about a property, ask
what else has it). And **no adaptor exemption**, matching the connector rule: *"Adaptors are
intended to cross contexts, but not descend into their internals."* Note the older
`checkCrossContextReference` DOES exempt adaptors and correctly keeps doing so — translating
between contexts is an adaptor's job; reaching inside one is not. Two rules, two exemptions, and
they disagree on purpose.

**Asked to fix the `None` arm, I first built something unreachable, and said so.** Once the
targets routed to the new Error, that arm's only remaining callers were `become` — which does not
parse in a saga step, so it is never outside a context — and a message TYPE, which is a context's
published surface and must stay silent. Reid chose to UNIFY instead: one predicate,
`reachesPastContextBoundary`, consulted by both the Error and the Warning. That is strictly better
than either shipping dead code or documenting the silence, because it makes the arm reachable AND
deletes the second copy of a dispatch — the defect this repo keeps paying for.

**Presenting a decision badly is its own failure.** My first attempt named "the None arm" without
saying what the function was, where it lived, what the other arms did, or what each choice would
cost. Reid sent it back. **A choice is not a question until the reader can reach the same
conclusion you did** — that means the file, the shape, and the consequence of each option.

**Seven of our own fixture sites were true positives**, including an adaptor in
`everything_full.riddl` sending to `outlet APlant.Source.OutCommands` — where `context APlant`
declared NO portlets of its own, so there was nothing legal to address. It now has an inlet and a
relay handler. Fixing fixtures rather than exempting them is the standing rule, and this one had
to be modelled, not edited.

**Two goldens moved and BOTH deltas were reconciled rather than accepted.** The token count went
420 -> 419 — a DROP, after edits that only added text, which is the shape that deserves chasing. A
path tokenizes PER SEGMENT, so `APlant.Source.OutCommands` (5 tokens) -> `APlant.Commands` (3) is
-2 per site, -4 total, while the second site's explanatory comment grew from three `//` lines to
six and each is its own Comment token, +3. Net -1, exactly. Separately, `values` moved +11 in
PassTest AND +11 in VisitingPassTest — two independently-written traversals agreeing on the same
delta, which is evidence about the tree rather than about either counter.

**Corrected, again: corpus impact is not an argument.** Reid: *"Yet again, corpus impact does NOT
MATTER! Stop bringing it up."* Measure it when implementing — it belongs in the commit and in the
task drop — but it is a consequence to report, never a reason to soften, delay or stage a rule
that is correct. Saved as a feedback memory rather than merely noted here.

## 2026-08-26 — rc.26, Scala RC6, and two documents reconciled — DONE

**`2.0.0-rc.26` shipped green**, the second RC to use the stage-first order. Nothing
below is about the release mechanics, which the `/rc` skill now carries; these are the
three things that generalize.

**A red CI run on the commit you are about to tag may be about the CORPUS, not the code.**
rc.26's JVM and Native legs failed on one case — reactive-bbq — while nine other jobs were
green. The cause was rc.25-11's new `put`/`return` type-checking exposing two genuine
corpus type errors, which riddl-models then fixed at 15:11 UTC, thirteen minutes AFTER CI
started at 14:58. **The corpus is a live sibling checkout and is not in any cache key**, so
a long run is not a snapshot of it. The decisive check is one command —
`git -C ../riddl-models log` for commits timestamped inside the run window — and the
confirmation is RUNNING the model (`riddlc from reactive-bbq.conf validate` → 0 errors),
never re-reading the log.

**A prediction is only evidence if the instrument is checked, and the tell is arithmetic.**
The rc.26 delta script scored `RuleIdLogRenderingTest` as a new file worth +3 on every row.
It was a RENAME, from `language/src/test/scala-jvm-native/` into `.../scala/` — **zero new
cases, but the tree changed, so JS gains 3 and JVM and Native gain nothing.**
`git diff --name-only` collapses a rename onto the new path, so `git show <tag>:<new path>`
finds nothing and the file reads as brand new. Use `--name-status` and look for `R###`.
The giveaway was that **JS landed on its number EXACTLY while JVM missed by a constant** —
a real skipping bug does not spare a row. A second instrument error the same day (a leg
parser whose non-greedy stop matched a `=== Throughput Benchmark ===` fixture line) failed
the same way and was caught the same way.

**Reconciling a document against the branch finds errors that predate the last
reconciliation.** Part A of the To-Do List needed 17 corrections and **four were already
wrong when the previous pass ran** — nothing in that window touched them, so a
commits-since-last-date diff structurally cannot see them. The sharpest: A27 asserts the
validator "warns about portability on every use" of `code`, and that warning **has never
existed** (now `[1.21]`). Two more had drifted in the direction nobody watches — recording
work as *outstanding* that had since been DECIDED (A42's scaffolding dropped, A46's
compound-output warning declined). **A declined half is a disposal, not a debt.**

**The Computational Model's failure mode is worse than the To-Do List's, and for a
structural reason.** The To-Do List records DECISIONS, so a stale entry is a wrong fact;
the CM records what a generator MUST DO, so a stale entry is a wrong BUILD. Five places
still said `reply` was deprecated in favour of `yield` — reversed for 2.0, where `yield`
answers a command and `reply` answers a query as distinct nodes. A generator author
following the CM would have emitted one keyword where the language has two.

**Scala 3.9.0-RC4 → RC6 was clean** — 0 errors, 0 compiler warnings, and test totals
byte-identical on all three platforms. The cost is not the compiler, it is that the full
version is a PATH SEGMENT: 32 sites across 8 files, 11 of them in `.github/`. `[0.6]`
carries the list for the 3.9.0-final bump.

## Landed on release/2, 2026-08-18 → 2026-08-25 — what each change taught

Graduated out of § HANDOFF on 2026-08-25, verbatim. This is the reasoning behind the
recent changes on the branch; none of it is orientation, and none of it was recorded
anywhere else — it was pruned from HANDOFF rather than deleted precisely because it
was the only copy.


**PRETTIFY NOW EMITS ONE SPACE AFTER `is` (2026-08-25), so the whole corpus needs
re-prettifying and `.bast` regeneration.** riddl-models knows and is not blocked. This
is the change with the widest byte-level reach on the branch — every model in the corpus
had drifted (188 of 188).

**Whitespace is load-bearing in this project.** riddl-models diffs 190 checked-in `.bast`
files against source BYTE FOR BYTE, and that can only be exact while the corpus is
precisely what prettify emits. Reid: *"Byte non-identical, especially with mere white
space changes, is a source of frustration at best and a source of errors at worst."*

**Two causes, two sites, and they are NOT one bug** — `emitMessageType` added a leading
space its callers had already supplied (hitting all seven aggregate-use-case keywords but
NOT plain `type`), and `add(Seq[LiteralString])` padded its single-string branch (hitting
`term` only, with a trailing space too).

**The durable lesson is about the TEST, not the spacing: idempotence cannot catch a
formatting defect.** `prettify(prettify(x)) == prettify(x)` held the whole time, because
whatever the emitter does is canonical BY CONSTRUCTION — the property is a tautology with
respect to its own output. Only an assertion against a FIXED expectation can contradict
the emitter. Same shape as rc.24-3's stream defect, where nothing asserted which stream a
command wrote to. **When a component defines its own correctness, self-consistency proves
nothing.**

**A test that passes alone and fails in company is worse than no test.** Two suites here
called `System.setOut`, which is process-global, while sbt runs suites in PARALLEL — so
one captured the other's output and failed with a message that read like a code break.
Both now go through `StdStreamCapture`'s JVM-wide lock. **Any suite redirecting a standard
stream must use it**, or the race is back for everyone.


**Constructor arguments are TYPE-CHECKED as of 2026-08-25**, and until then they were
not checked for type AT ALL — only arity, duplication, ordering, name validity and
`empty` cardinality. riddl-generator found it by generating Java that would not compile.
**A generator finding a model defect a validator did not is the wrong division of
labour**, and it is the second time that has happened this week.

**It was never a missing policy.** `isAssignmentCompatible` already answered "no" for
`Id(E)` → `UUID`; nothing at that position had ever asked it. Worth checking for the same
shape elsewhere: `put`, `return` and `require … with` still ask nothing.

**Two `Id`s must name the SAME processor, compared by RESOLVED IDENTITY.** The base rule
is same-CLASS, so every `UniqueId` matched every other. Ruled in regardless of corpus
frequency — the corpus had ZERO instances — because *"wrong is wrong … the point is to
make the language and its expression bulletproof so reliable code can be generated from
it."* `Id(Order)` and `Id(C.Order)` are one entity; two entities both named `Order` in
different contexts are not. Text matching gets BOTH backwards, and the message must name
them fully qualified or it reads "an id of Entity 'Order' is not an id of Entity 'Order'".

**`isAssignmentCompatible` IS DIRECTIONAL and the direction is easy to invert** — it reads
"is `other` assignable to `this`". `UniqueId` accepting `String_` since 2026-08-15 meant
only that a String may fill an `Id` field; nothing said an id could be rendered INTO a
String field, and that asymmetry was invisible until something type-checked the position.
Reid ruled the reverse in (ids and UUIDs have canonical string forms, needed for logging
and display); NOT for `Pattern`, whose whole purpose is to constrain the shape. My own
test asserted the wrong direction first — that is how the asymmetry surfaced.

**A stale background log read as a finished run.** The wait loop matched the PREVIOUS
run's `### DONE` before the new run truncated the file, and reported totals from it —
which showed `passes` unchanged at 1581 after adding two tests, and a failure that had
already been fixed. **The tell was the delta not reconciling**, exactly as the RC skill
prescribes: predict the movement first, and treat a mismatch as an instrument fault
rather than a result. Wait on a completion marker AND a leg count, not the marker alone.


**`../bin/riddlc` IS `2.0.0-rc.24-5-cb05e374`, ahead of rc.24, and riddl-models is
ALREADY RUNNING IT (2026-08-24).** No RC was cut for it, deliberately. Beyond rc.24 it
carries: product-on-stdout, morph-after-morph, and now **prefix truthfulness**.

**A reference's prefix must name what the target was DECLARED as.** Keyed off the
DECLARATION, never off what the reference carries — an alternation declared
`type XEvent is one of { … }` IS a type, so `is type XEvent` stays legal, and keying
off the carried kind would have reddened all 230 such references in reactive-bbq and
been wrong about every one. **A BARE reference is held to the same standard** (Reid's
ruling): `TypeRef.keyword` defaults to `"type"`, so the AST cannot tell an omitted
prefix from a written one, and the prefix exists to remove exactly that ambiguity.
**Corpus cost: 1,032 sites in 188 of 188 models** — no model is clean.
**The rule fires in BOTH directions**, which the requesting task did not anticipate:
riddl's own `dokn.riddl` had `is event CompanyEvent` where `CompanyEvent` is a declared
*alternation*, so that site LOST a keyword rather than gaining one.

**SCOPE IS PARTIAL AND TRACKED AS `[1.10]`.** Only `TypeRef` is covered — portlets,
invariant `requires`, function `requires`/`returns`. A field's type and a type alias
are `AliasedTypeExpression`, a different node, and are NOT checked. Deliberate: 283
portlet vs **542** aliased field references in reactive-bbq alone. A test named *"NOT
yet cover a field's type"* pins the boundary; if scope widens that test fails, which is
the intended direction.

**It also exposed a REFLECTIVITY defect worth remembering the shape of:** the JSON
surface dropped a `TypeRef`'s keyword on schema fields, so a round trip rewrote
`as record X` to `as type X`. BAST was already correct. It was invisible for as long as
`type` was the only keyword anyone wrote — **a fidelity bug can hide behind a field
that only ever holds its default value.**

**`riddlc find -replace` did 13 of the 24 in-repo migration sites**, at Reid's
suggestion, and found sites in an INCLUDED file that a grep on the entry file would
have missed. The script reads `source` + `type.carries` from the projection and returns
the source unchanged when `carries` is absent (an alternation), so those are left alone.


**`../bin/riddlc` IS AHEAD OF rc.24 — it is `2.0.0-rc.24-3-40c0574f`, staged with
`publishLocal`, and that is deliberate (2026-08-24).** Reid's call: *"don't build an RC,
just stage riddlc to ../bin and publishLocal — I want to avoid this round-trip between
riddl-models and riddlc before cutting another RC."* Two fixes ride on it that rc.24 does
NOT have.

**1. A command's PRODUCT goes to stdout; diagnostics stay on stderr** (Reid's rule).
rc.24 moved the logger to stderr — right for `validate` — and thereby emptied the stdout
of `version`, `info`, `about`, `help` and `stats`, which emit through that same logger.
sbt-riddl reads `riddlc version` from stdout, got an empty string, and **every build gate
pinning rc.24 died**. Those five now use `pc.stdoutln`, unprefixed.
**Nothing at this end failed when it broke**: no test asserted which STREAM a command
used, so the contract lived only in a consumer's expectations. `ProductGoesToStdoutTest`
is that gate now, canary-tested by reverting `VersionCommand`.
**`PlatformContext.stdout` writes to STDERR despite its name** — one caller, a debug
diagnostic, so the behaviour is right and only the name lies. Left unrenamed (published
API) and documented, because it is this same trap in miniature.

**2. `morph` after `morph` on one path is an Error.** One new arm on the existing
`set`-after-`morph` walk, with its OWN reason — borrowing the `set` message would tell an
author their morph "writes a record that is no longer current", which is not what is
wrong with it. **The rule is SEQUENCE ON ONE PATH, never morph count per clause**: two
morphs on different `when` branches stay legal, pinned by a test, because a count-based
rule would break every conditional transition in the corpus. Corpus cost measured across
all 190 models: **ONE site** (`admission-discharge`), a true positive, filed there.


**`riddlc find` IS COMPLETE — all three phases (2026-08-24).** `dump --json` is the
projection, `find` queries it, and `-exec`/`-replace`/`-delete` edit through it. The
reason it exists: riddl-models drives its migrations from ~10 regex-over-text Python
scripts, which produced NINE defects in one session, three of them the dangerous shape
where the run succeeds and reports a confident number computed over nothing.

**The editing gate is the part to understand before changing it.** Nothing is written
until every script has run, no two spans overlap, and the rewritten model has been
re-parsed AND re-validated; a model that stops parsing OR merely gains errors is fully
restored. Overlaps are REFUSED rather than ordered — a nested pair is an overlap, so
which rewrite survived would depend on application order — and that is what makes
back-to-front application within a file correct.

**Three defects surfaced only because a test ran from somewhere other than the model's
directory, or searched a file grep had quietly given up on:**
- `fileOf` resolved a node's `origin` — the SHORT name error messages use — as a path,
  so editing worked from the model's own directory and failed everywhere else.
- The argument split ate the expression's `(`, `)` and `;`, so parentheses had never
  worked at all. Phase 2 shipped with grouping broken because no end-to-end test used it.
- A literal NUL byte in `FindCommand.scala` (phase 2's `-print0`) made the file BINARY to
  grep, which then returned nothing for every search against it — silently, exit 0. The
  same false-absence family as the recursive-grep trap in `../CLAUDE.md`.

**`-replace` scripts get the span's SOURCE TEXT**, as a `source` key on the JSON record.
Without it the identity transform is not expressible: a script that cannot see what it is
replacing must reconstruct it from the structured record, so leaving a node alone would
already be a rewrite. `find -type entity -replace 'jq -r .source' \;` being
byte-identical is the gate on span accuracy, and is a test.


**`terminate` IS NOW TERMINAL IN ITS BLOCK (2026-08-20), and it is NOT in rc.20.** The
task arrived from riddl-models at 14:51, during the rc.20 run. `checkErrorTerminal` is
now `checkBlockTerminal` and matches `terminate` as well as `error`. **The lesson is the
asymmetry, not the fix**: rc.19 made `error` terminal and reordered 268 corpus statements
for exactly this reason, and walked straight past a `set state` after a `terminate` in
reactive-bbq — for a full release, until riddl-models spotted it BY EYE. When a rule is
about unreachability, ask what ELSE ends a block.
**The message states each terminator's OWN reason** (`error` refuses, `terminate`
destroys the instance) rather than matching the two together, which was the suggested
fix and would have told authors their `terminate` "refuses" and offered `require` as the
conditional alternative. `on term` needs no exemption — different statement list.
Canary-tested, corpus impact zero (measured two ways), CM §4.5 updated.
**CONFIRMED BY THE REPORTER and CLOSED** — riddl-models re-verified it against the
staged `2.0.0-rc.20-2-c1212d73`: one error per unreachable statement (two statements
⇒ two errors), `on term` exempt, their corpus clean. Task in `task/done/`.

**THE BACKLOG HAS ONE OPEN ITEM.** `[0.2]` upgrade riddl-vscode — and it is blocked
BY DESIGN, not overlooked: it consumes `@ossuminc/riddl-lib` from npm, which carries
only published releases, so it cannot take a staged build and chasing it between RCs
would mean cutting an RC for its benefit. Everything else in `BACKLOG.md` is struck
(32 items). **That is not a licence to invent work** — per `CLAUDE.md`, 2.0 ships when
the Computational Model is met, not when the backlog empties, and there is no
"defer to 2.1" pile.

**`2.0.0-rc.24` IS CUT, PUBLISHED AND FULLY VERIFIED (2026-08-24), AND IS STAGED AT
`../bin/riddlc`.** All five channels checked against the registries: **20/20** Maven at
exactly `2.0.0-rc.24`, npm `rc` dist-tag (with `latest` still 1.31.0), Homebrew touching
only `riddlc-rc.rb` (`riddlc.rb` still 1.31.0), **`notify-blog: skipped`**, both native
binaries built. Cold cache (116K -> 269M), floors raised to **2903 / 917 / 2864**, delta
+65/+14/+65 reconciled to the case against a prediction written first.

**The staged `../bin/riddlc` IS rc.24 and is deliberate** — riddl-models needs the
every-field constructor rule and the no-`set`-after-`morph` rule to migrate against, and
verified enforcing them (dokn: exit 7, 5 morph errors) before being announced.

**Deliberate failures are now ONE — riddl-examples' `dokn`, 5 morph errors.** `commands` is
**297/297 green**, re-measured on a fresh cache after riddl-models migrated its 2,309
partial constructors and 115 set-after-morph sites the same afternoon (`e645e9d9`,
`4a566707`) **using rc.24's own `dump --json` projection**. It was 190 for about ninety
minutes. Do not trust a count here without re-measuring: the corpus is a live sibling
checkout, so this number goes stale in hours. **Confirm any red by its MESSAGE**, not its
count.

**A corpus suite went red mid-certification because the CORPUS MOVED**, and this is worth
recognising on sight: `Root2JsonCorpusTest` failed on reactive-bbq, and re-running it
alone read 190/190 clean. riddl-models had committed twice *during* the run, one commit
converting that very model. Nothing in riddl changed. Check
`git -C ../riddl-models log` for commits timestamped inside your run before believing a
corpus regression, and re-check on a FRESH cache — the corpus is not in the cache key, so
a warm store replays a stale FAILURE as readily as a stale pass.

**rc.23 adds `empty`** — the minimum-cardinality inhabitant of a type, with `none` as a
SYNONYM producing the identical node. Legal exactly where the minimum cardinality is zero;
the ascribed `empty T*` is the form that works where the position supplies no type.
**BAST `FORMAT_REVISION` 21** — the next BAST change must bump to 22.

**Two `empty` limitations, both PRE-EXISTING and both worth knowing before they read as
new bugs:** a bare `empty` is not checked against a field typed INLINE (the expected-type
machinery resolves only NAMED types), and `let e: T* = empty` does not parse because
`let`'s ascription is a type REFERENCE. `let e = empty T*` works.

**rc.22 EXISTS BECAUSE rc.21 SHIPPED A DEFECT — do not use rc.21 for corpus work.**
ossum.tech found that rc.21's two delivery checks were blind to ALTERNATIONS in both
directions: an inlet typed `one of { A or B }` was not satisfied by clauses for A and B,
and a clause naming the union did not receive a `tell` of A. Since `type XEvent is one of
{…}` on an inlet is the corpus idiom, it demanded what no legal spelling could satisfy
except `on other` — the SAME unsatisfiable-demand trap the discard-sink exemption exists
to avoid, reintroduced one commit later in a different guise. **346 false positives**:
tell-side 6379 -> **6172**, inlet-side 906 -> **767**.
**Ruled**: a union inlet needs a clause for EVERY member, and the message NAMES the ones
missing. The any-member alternative measured 455 and was declined, because a rule whose
cheapest escape is `on other` pushes models toward less specificity.
**Correction to the report**: `on event <alternation>` is NOT an Error — it parses,
validates and satisfies the check.

**rc.21 carries four changes, three of them reported by riddlg.** `tell` may address an
INSTANCE (a value typed `Id(...)`, incl. `self.id` and alias-reached fields); two new
CompletenessWarnings for a message delivered where nothing can receive it; `terminate`
ends its block; and `resolvePath`'s unchecked cast is fixed. **BAST `FORMAT_REVISION`
20** — the next BAST change must bump to 21.

**THE CORPUS DRAWS 6,939 COMPLETENESS WARNINGS, AND THEY ARE TRUE POSITIVES.**
6,172 tell-side + 767 inlet-side across 190 models, **0 errors** (rc.22 numbers; rc.21's
6,379 + 906 were inflated by the alternation defect). That is 84% of all
corpus tells — checked by hand before being reported, because a number that size usually
means a broken check. It does not: the corpus systematically tells an event to an entity
and handles it somewhere else. Migration filed at
`riddl-models/task/2026-08-22-handle-the-messages-you-are-told.md`. Reid: *"Correct is
correct."* **Do not soften the checks to make this number go down.**

**THE CORPUS GATE IS GREEN AND THERE ARE ZERO DELIBERATE FAILURES.** Both corpora have
migrated through rc.19's `forward` statement, narrowed discharge rule and
`error`-is-terminal rule; riddl-models validates at 0 errors / 0 warnings and
riddl-examples at 9/9. **A red case is a real signal — there is no expected-failure
list to reach for.** This reverses the red state recorded here on 2026-08-18, and it is
the same shape every time: a rule with corpus cost ships in an RC, the corpora migrate
against it, the gate returns to green. Withholding the RC is what would prevent the fix.

**Certified from a genuinely COLD cache** (`-Dsbt.global.localcache=/tmp/sbt-verify-rc20`,
140K → 264M), 19 module-legs each in its own sbt invocation, zero failures and zero
`No tests to run`: **JVM 2801, JS 872, Native 2762** (floors raised to match). The
+10/+9/+10 delta reconciles exactly — 9 shared cases in `passes/src/test/scala`
(`ErrorTerminalTest` 5, `UndeclaredResponseTest` 4) plus 1 in
`commands/src/test/scala-jvm-native` (`RiddlModelsRoundTripTest`'s whole-corpus fullness
case) that JS correctly does not see, `commands` having no JS row.

**Completeness 4h/4i were WRONG and are corrected (2026-08-18, post-rc.16).** Reid's
ruling: **a processor receives only through its OWN inlet and publishes only through
its OWN outlet** — `tell` included, since it is the same operation as `send`. Both
checks had asked about the CONTEXT (4h never asked about the entity at all); both are
now per-entity, and 4i's context-level form is gone. **A cross-context connector
reaching past the boundary is now an ERROR** — binding a peer to a contained entity's
command/query set is the coupling a bounded context exists to prevent. Recorded in
`CLAUDE.md` and CM § 8.1.

**Neither fix is in any tag — rc.16 predates all of it.** The boundary Error costs the
corpus **491 violations across 184 of 198 models**, ruled acceptable in advance and
filed as **[3.6]**. Both questions it raised are now RULED and needed no code:
**[1.6]** an Adaptor gets **no exemption** — being the translator does not make it the
boundary; **[1.7]** a `tell` target needs **both** a declared inlet **and** a connector,
because `tell` implies a channel that must be modelled. Neither should be "fixed" later
by someone finding the strictness surprising.

**riddlg's index task is CLOSED** (`task/done/2026-08-18-declare-an-index-without-a-repository.md`).
Ruled: `Schema.indices` owns it, no field option. Built a CompletenessWarning for a
queried repository with no index (26 corpus sites, predicted 26). **The measurement
worth keeping**: naming the queried FIELD is not derivable — 406/406 repository
query bodies are prose, and query-type fields map to a stored field 6% of the time.

**riddl-models is CLEAN and riddl-examples is the only thing keeping the gate red.**
Verified with `2.0.0-rc.16-20-c075f1af`: riddl-models 190 entry points at 0 errors /
0 completeness / 0 usage (12 warnings + 2 style left, both now removable by them);
riddl-examples still at 49 errors / 48 completeness. Task drops in both repos.
**rc.17 cannot certify clean until riddl-examples is migrated** — its 44 boundary
errors are what fail the 192 tests in `commands`/`riddlLib`/`riddlc`.

**Two riddlc defects riddl-models found are FIXED** (`c075f1af0`): the `persistent`
contradiction (fire on CROSSING, not touching) and the unsatisfiable "consider an
adaptor" advisory. **The trap worth remembering: a check whose demand no legal
spelling could satisfy** — Error without the keyword, Warning with it.

**Build state, verified 2026-08-18:**

- **`2.0.0-rc.16` is cut, published and FULLY VERIFIED** — 11/11 Maven
  coordinates, npm `rc` dist-tag with `latest` still 1.31.0, homebrew touched only
  `riddlc-rc.rb`, `notify-blog: skipped`, both native binaries built.
- **`~/Code/ossuminc/bin/riddlc` is the clean tagged `2.0.0-rc.20`** (native binary),
  staged 2026-08-20 from the tag with `reload` first. **Verified, not assumed**: it
  reports `2.0.0-rc.20` exactly, validates a riddl-models model silently clean, and a
  negative control (an unresolvable type) still produces findings — silence alone is
  not evidence a binary is working. It is `nativeLink` output, a single Mach-O binary,
  NOT `riddlc/stage`'s JVM universal. Reid's purpose for it: teaching Synapify the 2.0
  language, GBNF's removal included.
  Previous binaries kept beside it as `riddlc.rc15/16/17.bak`.
  **This supersedes the earlier "leave it at rc.15 until 2.0.0 final" ruling** — Reid
  asked for a staged build explicitly.
- **BAST `FORMAT_REVISION` 19 has SHIPPED. The next BAST change MUST bump to 20.**
  Every "rides 19 because it has not shipped" argument in the code is history.

**EVERY SUITE ON EVERY PLATFORM IS GREEN. There are no known-red suites.**

- JVM **2801**, JS **872**, Native **2762** (cold-cache tri-platform certification,
  2026-08-20). riddl-models validation-parity **190/190**; riddl-examples 9/9.
- JVM/Native gap **−39**, unchanged. `commands`, `riddlLib`, `riddlc` at parity.
- **The corpus suites now assert they covered the WHOLE corpus.** Their assertions
  were all RELATIVE, so three models satisfied them as well as 190 and a truncated
  corpus passed silently; both now carry an absolute floor and fail when the corpus
  is present but partial. CI additionally discards `v2/ac` after cache restore,
  because the corpora are cloned by a workflow step and are in no cache key — so a
  verdict computed before a rule landed could replay as valid. A replayed result and
  a truncated corpus look identical from outside: a fast, green suite.

**Corpus suites now read SIBLING CHECKOUTS** (`../riddl-models`,
`../riddl-examples`) and SKIP when absent — they no longer download. CI clones them
with `git clone` (not actions/checkout, which cannot write outside the workspace).
**The failure mode of that design is SILENCE**: a failed clone leaves the suites
skipping and the log green, so check for "skipping rather than failing" before
believing a corpus run passed.

**`loadBytes` on Native: content is INTACT; REDIRECTS are what it cannot do.** sttp's
Scala Native curl backend exposes only `Content-Length` among response headers, so
`Location` is invisible and a 302 cannot be followed — by hand or via
`FollowRedirectsBackend`. Use a direct URL on Native, or fetch on the JVM.
**An earlier note here claimed the backend truncated binary content at the first NUL.
That was a theory, it was wrong, and it survived three sessions because it fitted the
symptom** — the "short body" was the 302's own empty body. It was settled by making the
error print its own evidence (`Headers present: [Content-Length]`) rather than guessing
a fourth time.

## Incoming Tasks

**At session start**, check the `task/` directory for pending work
requests from other projects. Each `.md` file describes a task
(e.g., a dependency upgrade). Treat unresolved tasks as to-do
items unless already completed (verifiable from this notebook,
CLAUDE.md, or git log). After completing a task, append results
to the task file and note the disposition below.


## Current Status

**Last Updated**: 2026-08-26

`release/2` is the 2.0 branch and carries everything below; `main` is still at
**1.31.0** and is where 2.0.0 final will be tagged from. Do not read a number
here without re-checking it — this section has been stale before.

- **Latest RC: `2.0.0-rc.26`**, published and verified on all five channels.
  Floors **3068 / 1005 / 3026**. BAST `FORMAT_REVISION` **23**.
- **Scala 3.9.0-RC6** is committed on the branch (`2c7d36af2`); rc.26 itself was
  built on RC4. `[0.6]` carries the 32-site list for the 3.9.0-final bump.
- **Open work is `BACKLOG.md`**, not this file. 2.0 ships when the Computational
  Model is met, not when the backlog empties (CLAUDE.md § Definition of Done),
  and there is no "defer to 2.1" pile.

**This notebook was pruned on 2026-08-26** — roughly 60 dated entries covering
2026-07-24 → 2026-08-20 were removed after their durable half was graduated into
`CLAUDE.md`. Nothing was summarised or rewritten; entries were kept whole or
removed whole, and everything removed is in `git log` for this file. The three
sections that carried open work rather than narrative were filed as BACKLOG
`[2.7]`–`[2.10]` instead of being deleted.

## Blocked

(none)

## Scheduled

| Date     | Task |
|----------|------|
| At 2.0.0 final | **Drop `RIDDL_MODELS_BRANCH: release/2` from `scala.yml`** once the riddl-models 2.0 corpus merges to its `main`. The override exists because `RiddlModelsRoundTripTest` falls back to downloading a branch zip when there is no local checkout, and riddl-models `main` still holds 1.x models — so CI failed on the 2.0 grammar while local runs (reading the developer's `release/2` checkout) passed 189/189. The default in the test is `main`, so deleting the line is the whole fix. |

The CodeQL v3 → v4 upgrade that sat here is DONE — `upload-sarif@v4` went in
with the Node 20 action sweep, well ahead of its December 2026 deadline.

## Design Nuances (for future work)

### BAST Format Decisions

Any change that bumps `FORMAT_REVISION` will need to honor or
revisit these:

| Decision | Rationale |
|----------|-----------|
| Custom binary format (not Proto/FlatBuffers) | Memory-mappable; ~10x faster than reparsing source |
| String + Path interning tables | Deduplication; path table sits after string table so no header change was needed when it was added |
| Delta-encoded locations w/ zigzag | ~70% space savings; zigzag handles negative deltas |
| Line/col dropped from BAST | Computed from offset; saves ~4 bytes/node |
| Compact tag numbering (1-67) | Eliminates gaps; easier maintenance |
| Metadata flag in tag high bit | Tags fit in 7 bits; saves 1 byte for empty metadata |
| Dedicated message-ref tags | Eliminates polymorphism, saves 1 byte/ref |
| Inline PathIdentifier / inline TypeRef in known positions | Position is unambiguous in refs / inlet/outlet/state/input — saves 1 byte each |
| Source-file change markers (not per-location path index) | Sources change rarely vs locations |
| Single integer `VERSION = 1` | Stays at 1 until the schema is finalized for external users; granular changes use `FORMAT_REVISION` |
| HTTP compression > library compression | HTTP layer already handles transport |

**Disjoint tag sets**: `readNode()` handles `NODE_*` tags only;
`readTypeExpression()` handles `TYPE_*` tags only. Crossing them
causes byte misalignment that surfaces as "Invalid string table
index" deserialization errors.
