# Engineering Notebook: RIDDL

Records open work, blockers, and design nuances that future AI
sessions need to know. Release history lives in git tags and
GitHub release notes — don't reproduce it here.

## HANDOFF

Orientation for a session with no memory of this work. **Open work is in
`BACKLOG.md`** (items carry stable `[section.n]` IDs); durable facts are in
`CLAUDE.md`; what things TAUGHT us is in this NOTEBOOK's body. Ask `git` for
branch, tree and unpushed span — anything written here about those is stale the
moment someone commits.

**Build state — verified by running the command, 2026-08-17:**

- **`~/Code/ossuminc/bin/riddlc` (NATIVE) is STALE**, and the local ivy artifacts
  with it. **Deliberately (Reid, 2026-08-17): riddlg is mid-analysis and no
  consumer is waiting.** Not an oversight. It predates the `at` lookup, A38, and
  everything below. Restage with `scripts/publish-and-stage.sh` before handing
  anything to a consumer.
- **BAST `FORMAT_REVISION` 18 HAS SHIPPED, in `2.0.0-rc.15`. THE NEXT BAST CHANGE
  MUST BUMP TO 19 — no exceptions.** Revision 18 carried four changes on the
  "18 has not shipped yet, so no file in anyone's hands has one" argument. That
  argument is now spent, permanently: files carrying an 18 exist. Riding it again
  would put two mutually unreadable wire formats under one revision number, and it
  would fail SILENTLY — the gate passes and the reader misaligns.
- **`2.0.0-rc.15` was cut 2026-08-17** from `release/2` (tag, prerelease, and all
  20 Maven coordinates published from the tag). See "RC verification left
  outstanding" below.

**In flight: nothing.** Every item touched on 2026-08-17 is committed, green, and
recorded. `[2.3]` remains open but is at a clean stopping point with its next
slice named.

**`release/2` is AHEAD of the `2.0.0-rc.15` tag.** `[2.6]` (imports resolve
without flatten) landed AFTER the tag, so it is **not in rc.15** — it ships in
rc.16. Check `git log 2.0.0-rc.15..HEAD` before attributing anything to the RC.

**What landed 2026-08-17** (autonomous run): the MessageFlowPass `let`-local task
(`b6b3dd03e`) and the `DependencyAnalysisPass.typeDeps` defect its sweep found
(`1a3c1cf05`); `[2.3]`'s output-producing slice (`7296cfc27`); `[2.4]`
Streamlet→Processor (`2c19d6d70`); `[2.2]` A38 plus the JSON
interaction-metadata hole its fixture exposed (`e4f6f33f3`); and — after the RC
tag — `[2.6]` imports resolving without flatten (`f99bd3d27`). CM updated for A38
(`adfce7c`) and for `[2.6]` (`c92af7f`) in the `ossuminc` repo — that is a
SEPARATE git repo, commit there separately.

**ALL EIGHT QUESTIONS WERE RULED on 2026-08-17 and every one is now IMPLEMENTED.**
`BACKLOG.md` § 4 records the rulings and the commits. Three reversed what had been
built on my recommendation, which is the point of asking: `[4.1]` (`streamlets`
now means every port-bearing processor, not the `Streamlet` case class — the
accessors I had added are deleted), `[4.2]` (`typeDeps` is a full TYPE-DEPENDENCY
graph, not a message one), `[4.4]` (every processor's shape counts). `[2.1]`
needed no code at all — the ruling was already implemented and tested. `[4.5]`
went moot when riddl-models shipped.

**Test baseline, all executed 2026-08-17 — memorize the RED ones or you will
chase them:**

- JVM: `language` 71 suites/731, `passes` 218/1446, `riddlLib` 143, `utils`,
  `commands` — all green EXCEPT the known-red corpus suites below.
- `tJS` fully green. `tNative` green except the same known-red suites.
- **THE CORPUS GATE IS MET.** `Root2JsonCorpusTest` validation-parity reads
  **190/190** and `RiddlModelsRoundTripTest` is green, after riddl-models shipped
  `99fc29d1` (*"the corpus validates 188/188 with zero errors"*) on 2026-08-17.
  `ReportedIssuesTest` "should 406" is green too — that fixture was OURS, using
  the pre-2.0 `morph … with record X` operand, and is migrated.
- **TWO known-red cases remain, and BOTH belong to `../riddl-examples`**:
  `RunRiddlcOnLocalTest`'s `dokn` and `shopify-cart`, exiting 7 because that
  corpus has not made the 2.0 migration riddl-models completed. A task is filed
  in `riddl-examples/task/2026-08-17-migrate-to-2.0-syntax.md`. **When it lands
  this repo is fully green** — do not treat those two as permanent.

**Traps — every one bit someone here.**

- **A "missing data" defect is often a WRONG ANSWER instead.** Three of four this
  week were. The cheap discriminator: revert the fix and read what the test says
  — `"C" was not equal to "Source"` is a wrong answer, `None was not equal to
  Some(…)` is a missing one. Do it before writing the comment.
- **A fixture is a detector, and the best ones exercise a COMBINATION.** A38's
  fixture (a refusal step WITH metadata) found that all thirteen interaction
  kinds lost their metadata through JSON. Neither half was untested; the
  intersection was.
- **`Keywords.keyword` ends in `./`, a CUT** (`Keywords.scala:39`). Any optional
  keyword-led clause must be wrapped in `NoCut` or the enclosing alternative
  cannot backtrack.
- **`-Werror` is NOT a net for a new `Value` arm**, and a wildcard arm makes a
  match exhaustive — so the prescribed terminal `throw` is itself what silences
  the compiler. `language`/`commands` also compile `--no-warnings`.
- **A green corpus proves nothing about a construct the corpus lacks.**
- **Verify a backlog item before working it.** Two entries this week described
  work already done, and one number was stale by a whole model.
- **Never `sbt … | tail -N` for a multi-module run** — it throws away the module
  summaries that tell you what actually ran, and cost a full re-run today.
  Redirect to a file and grep it. Count `Suites: completed` against the number of
  modules you asked for.

**`task/` — ONE file. `2026-08-04-security.md` is Reid's own RBAC draft marked
*"do not act on this"*** — a design seed, not a request. The MessageFlowPass
report is DONE and moved to `task/done/` with a Results section (which also
corrects three claims the report itself made).

**`2.0.0-rc.15` IS FULLY VERIFIED (2026-08-17).** Every `gh api` call failed with
a 503 for about two hours immediately after the release was created — `/user`
included, so it was not scoped to packages — and the four skill-required checks
were run once it recovered. All four pass:

1. **All 11 Maven coordinates** published at `2.0.0-rc.15`, confirmed against the
   registry itself, `sbt-riddl_sbt2_3` included.
2. **npm** published under dist-tag **`rc`** (*"Publishing under dist-tag 'rc'"*),
   and `latest` did NOT move — `npm dist-tag ls` reads `latest: 1.31.0`,
   `rc: 2.0.0-rc.15`.
3. **homebrew-tap** commit `58b9350` touched **only** `Formula/riddlc-rc.rb`;
   `Formula/riddlc.rb` is still on stable `1.31.0`.
4. **`release.yml`**: jvm-build and both native builds succeeded, and
   **`notify-blog: skipped`** — the prerelease guard held, so no blog post.

**Whose outage it was is NOT settled, and the first write-up here asserted
GitHub without qualification — corrected.** Reid reported a **Claude** incident
13:56-15:29 UTC that day, which matches the window closely. The evidence is
genuinely mixed: the response body was GitHub's own 503 text, but **`git push`
succeeded throughout the same window while every `gh api` call failed**, and a
GitHub-wide outage would normally take both down. Record the symptom (`gh api`
503 while git worked) rather than a cause, and check both status pages before
blaming either.

The lesson worth keeping is about the release: the tag, the prerelease and the
publish all completed BEFORE the failures began (published 13:39:29Z, failures
from ~13:56), and every one of them uses the git protocol or an upload endpoint
rather than the REST API. **A release can be complete and unverifiable at the
same time**, so record which of the two you are looking at.

**Run `/ossuminc-skills:check-tasks` in the new session** — triage is the
driver's call, not the handoff's.

## Four "missing data" bugs, three of which were WRONG ANSWERS (2026-08-17)

An autonomous run over the MessageFlowPass task file, `[2.3]`, `[2.4]` and `[2.2]`.
The code is in `b6b3dd03e`, `7296cfc27`, `1a3c1cf05`, `2c19d6d70`, `e4f6f33f3`. What
belongs here is a pattern that showed up four times in one day and was NOT what the
reports said it was.

**I filed each of these expecting missing output. Three produced confident wrong
output instead.**

- riddl-models reported `MessageFlowPass` warning on `let`-local operands. The warning
  was the cheap half: the pass took its `case _ =>` arm, so the EDGE was never added —
  every flow whose operand was a `let`-local was silently absent from the graph the
  simulator and generator consume, while the model reported zero errors.
- `[2.4]`'s port-to-owner walk was filed as narrowing to `Streamlet`. It did narrow —
  and then its FALLBACK SUCCEEDED, on the enclosing Context, because a Context is a
  Processor too. The flow graph named the container as producer and the entity not at
  all. `DiagramsPass` fell back to the port itself, drawing arrows outlet→inlet with
  neither owner shown.
- `DependencyAnalysisPass.typeDeps` was recorded only under
  `parents.collectFirst { case t: Type => t }`. A `tell`'s parents are its on-clause,
  handler, processor, context and domain — never a Type. The guard could not succeed, so
  a public field documented as *"map from each type to types it references"* answered
  "nothing references anything" for every model ever analyzed.

**The check that separates the two is cheap and I nearly skipped it every time: revert
the fix and read what the test actually says.** `"C" was not equal to "Source"` is a
wrong answer. `None was not equal to Some(...)` is a missing one. I wrote "the edge was
dropped in silence" in a comment before running that, and it was false.

**A pass should publish what it resolved, not make the next pass re-derive it.**
`let`-locals are LEXICAL by design — not Definitions, statement-ordered, deliberately
outside the symbol table — so no path-keyed lookup can ever find one, and only
`checkStatementScopes`, which threads the scope as it walks, can resolve an operand
that names one. So `ValidationOutput.deliverableTypes` carries the answer and
`DeliverableTypes.of` is the single read path. The alternative was copying the walk into
each consumer, which is the "dispatch written twice" shape this repo keeps paying for.

**A FIXTURE IS A DETECTOR, and the best ones exercise a COMBINATION neither half owns.**
A38's fixture put `with { briefly "…" }` on a refusal step. That found a defect A38 had
nothing to do with: `JsonAstBuilder.buildInteraction` hardcoded
`Contents.empty[MetaData]()`, so **every one of the thirteen interaction kinds lost its
metadata on every JSON round trip** — 745 affected keys in the corpus. It survived
because no fixture in the repo had ever put metadata on an interaction step. Neither
"interactions" nor "metadata" was untested; their intersection was. This is the same
lesson as the corpus fixture that exposed the `1e3` EBNF divergence, one step further:
add the fixture for the COMBINATION, not for the feature.

**Sizing a slice by grep is cheap even when CLASSIFYING one is not.** `[2.3]` carried a
198-site figure and a note that classification is reading, not grepping — both true, and
together they made the next slice look like a day's work. One grep
(`case _ *=> *"`) says the output-producing slice is **ten sites**. It took an hour:
three fixed, four examined and cleared with the reasoning recorded, three already done.
The item now names the next slice AND warns that its own grep-shaped framing cannot find
the defects its examples are made of — neither the `typeDeps` guard nor the
`let`-local gap was a `case _ =>` arm at all.

**Two smaller things.** `sbt … | tail -100` threw away the two module summaries I needed
and cost a full re-run; redirect to a file and grep it. And the JSON `knownKeys` guard
earned its keep — it rejected a new `meta` key and pointed at `metadata`, which is what
every other DTO already uses.

## A night spent on BACKLOG § 1 — what the list itself got wrong (2026-08-15)

Twelve items worked end to end. Nine built, three queued for a ruling. The code
is in the commits; what belongs here is that **the backlog was wrong about its
own contents in six distinct ways**, and every one was cheap to detect and
expensive to have believed.

**Two entries described work that was already done.** The numeric-literal
limitation had been closed by `6cfeceb2f`; the unused-`initiate`-id check was
built and green with seven cases. Both were found by *running the entry's own
example* rather than reading the entry. That is now three in one day, counting
the 49-alias list that riddl-models had already cleared.

**Two entries prescribed a fix that would have caused damage.** The
`OnInit`/`OnTerm` item said to move `parameters` after `contents`/`metadata`;
doing so would have broken all five positional construction sites to fix one
consumer, and the constraint it cited dissolves the moment the field is
defaulted. The JSON item proposed a consumed-keys tracker wrapping `ujson.Obj`,
which cannot work: most DTOs are read by upickle's derived `macroRW`, which a
wrapper cannot instrument.

**One entry's central measurement was wrong by 294x.** The cross-context `tell`
seam was filed on a heuristic saying 5,301 crossings (64% of all tells). Counted
by resolution: **18** (0.24%), all in two models. The entry's strategic
conclusion — that the rule "will bite widely", which was the argument for
warn-then-flip — was built entirely on that number and does not survive it.

**One entry contradicted itself.** Clusterability asks for both a `clustered`
keyword and `self.isClustered`, but writing the keyword is exactly what makes
clustering statically knowable, which is what disqualifies the field under the
admission test. You can have either, not both.

**One entry's headline described the milder half of a bundle.** The two
narrow-operand gaps were filed together as "false-positive-only, zero corpus
impact". True of one; the other was a **missed Error**, and the bundling is
plausibly why it sat unexamined.

**The one technical pattern worth carrying forward: a comment asserting what
some OTHER function does is unverified, and three were false today.**
`ResolutionPass` claimed `letType` special-cased predefined keywords (it did
not). `widenedOperandType`'s scaladoc claimed no call site could see a `foreach`
body, while the call site two lines above `checkTellAddressing` said it is
"reached at ANY depth" — two claims about one path, in one file, disagreeing,
and the false one was load-bearing. CLAUDE.md already names this shape under
Total Dispatch; what today adds is that it is not rare.

**Method note that paid for itself repeatedly: move it, compile it, move it
back.** Four files that no import scan flags as JVM-bound are, because they name
a JVM-only TYPE or extend a JVM-only base. Guessing cost minutes; the compiler
answered in seconds.

## The 49-alias list was owed to nobody (2026-08-15) — CLOSED

The backlog's most urgent item — *"⚠ THE FULL LIST WAS NEVER DELIVERED"*,
certification blocked until it lands, ~44 sites written down nowhere — was
**already finished when it was written.** riddl-models cleared all 49 in
`29598ad1` on 2026-08-14, deriving the list from its own `riddlc` run and
classifying all three classes. Our entry demanding it is dated the 15th.

**A backlog item can be stale in the one direction we never check.** This file
already teaches that a red suite's recorded COUNT rots because nobody
re-measures it. This is the same rot in an item's *existence*: it asserted that
another repo owed us something, and there was nothing in the entry — which was
detailed, specific, and correct about the cause — to suggest the work might be
done. **Re-verify an item that depends on another repo's state against that
repo, before acting on it.** Cost here was a full corpus sweep to discover the
answer was "nothing to do"; cheaper would have been `git log` in riddl-models.

**Prove the instrument can fire before believing a zero.** The sweep reported
zero ambiguity Errors, which is exactly what a broken sweep reports. So a
positive control was built first — a model that must produce the error — in
BOTH spellings: inline `Id(entity Order)` and `type OrderId is Id(entity
Order)`, the alias form that `ccd278c00` turned on and the only one the corpus
uses. A control in the inline spelling alone would have proven nothing about
the case in question. **Match the control to the shape you are asking about,
not merely to the message you want to see.**

**The sweep's own first version silently lost ten models.** Output files were
keyed on the `.conf` basename, and ten corpus models share one
(`benefits-administration`, `case-management`, `order-management`, …), so ten
results were overwritten: 180 files reported as 190. Caught only by reconciling
the file count against the exit-code count, which is the same discipline as
counting `Suites: completed` against modules asked for. **A sweep needs its own
canary; "it ran and produced output" is not evidence it covered the input.**

**What the sweep did find is worth more than the list.** The backlog carried a
second item calling the drop from 173/189 to 59/190 an unexplained regression
needing its own investigation. It is explained, and there is no mystery: 131
models carry an Error, and **130 of them carry exactly one error class** — 343
"names a message type, not a value" plus 19 for records, i.e. the bare-message-
operand migration riddl-models is mid-way through. The 131st is `reactive-bbq`,
which does not parse at all for its two unmigrated `terminate` lines. Nothing
else appears in the corpus at all. A question that had been filed as needing
diagnosis was answered as a by-product of measuring something else.

## A collector that stopped one level short (2026-08-15) — DONE

`b8a6057fb`. `DiagramsPass.captureUseCase` collected actors from a use case's
top-level contents only, giving `InteractionContainer` (`sequence`, `parallel`,
`optional`) an arm that returned `Seq.empty`. Reported by riddl-generator, whose
docs generator crashed on reactive-bbq. Task file with the full A/B is in
`task/done/2026-08-15-usecase-actors-empty-when-steps-are-nested.md`.

**The interesting part is not the missing recursion, it is which failures were
invisible.** One use case (`ReservationFlow`) returned an EMPTY actors map, and
that is the one that produced a crash and a bug report. Four others returned
PARTIAL maps — `OrderingFlow` 3 of 6, `PaymentFlow` 4 of 6, `WalkInSeating` 4 of
5 — because they happened to have some steps at the top level. A partial answer
raises no exception and does not look wrong: the diagram renders, with some
participants. **The case that crashed is the case we were lucky about.** When a
consumer reports an empty result, ask what the same defect does when it is only
partly wrong, and go looking for those.

**Three failure shapes now, all called "silent fall-through", and this one fits
none of the existing two.** The dispatch was explicit and total (no catch-all),
and it deferred to nothing (so no unverified claim about code elsewhere). It was
simply a *wrong answer written deliberately* — an arm that said "containers
contribute no actors" when the renderer walks straight into them. The rule this
adds: **a collector must descend as far as the code consuming its output does.**
Filed as a third widening of BACKLOG § 2's audit item.

**`.sortWith(…).toMap` throws the sort away, and only above four elements.**
Fixing the recursion exposed it: `actorsFirst` exists to put users on the left of
the diagram, and `Map1`..`Map4` preserve insertion order incidentally while the
fifth entry becomes a hash-ordered `HashMap`. So the ordering was correct for
small use cases and silently wrong for large ones — and unreachable for nested
ones, which had no actors at all. `immutable.VectorMap` fixes it while remaining
a `Map[String, Definition]`, so no exported signature moved. **A sorted
collection converted to a `Map` is not sorted; the bug hides below five
elements**, which is exactly the size most tests use.

**There was no use case coverage in the diagrams tests at all** — zero mentions
of `usecase`, `UseCase` or `actors` across all four suites. The only assertion
was `useCaseDiagrams must not be (empty)`, which counts diagrams rather than
their contents, and passes on `everything.riddl`, whose cases are all `???`. The
task file said the existing coverage "passes today with the bug present"; the
truth was weaker and worth recording, because *"a suite exists for this file"*
was doing the reassurance work that *"a test exercises this behaviour"* should.

**Verifying against the corpus needed a scratch copy.** reactive-bbq does not
parse at HEAD — its two `terminate entity X` lines predate `7b356120a` and the
riddl-models migration has not landed — so criterion 3 was checked against a
`cp -R` copy with those two lines migrated to `terminate self.id`. Recorded
because the number in the A/B table came from that copy, not from the corpus as
it stands, and because it is a reminder that `PassCostBenchmark` and
`Root2JsonCorpusTest` stay red until riddl-models moves.

## `terminate` learns which instance it kills (2026-08-15) — DONE

`7b356120a`. `TerminateStatement.processor: ProcessorRef` became `target: Value` typed
`Id(entity E)`; args moved behind `with (…)`. CM §4.5 records it (commit `aee6462` in the
`ossuminc` coordination repo).

**The design came from a consumer refusing to guess, and that is the entry worth keeping.**
riddl-generator could lower every other rc.14 construct and stopped at this one, emitting an
`AI FILL` marker instead — because `terminate` DESTROYS, so a wrong instance deletes the wrong
row, where the same mistake in `tell` merely reads one. It filed the question rather than
picking the plausible reading (the AST comment implied "always `self`"). **A consumer that
declines to guess is doing design work for you**; the marker was worth more than a lowering.

**The corpus argued for the option NOT chosen, and that is fine.** Both real `terminate`s in
riddl-models are self-termination, and CM:1668's single-writer discipline says destroying
another instance from outside races its in-flight messages — so self-only would have covered
100% of existing use with a principled story. Reid chose expressiveness: a supervisor must be
able to end a specific instance without a command round-trip. **Corpus counts measure what has
been written, never what the language must permit.**

**A "free consequence" that was not free.** The design was picked believing singletons were
excluded automatically — no instances, so no `Id`, so nothing to terminate. Reid's ruling on
the same day made that false: **a singleton's `Id` is how you SEND IT MESSAGES**, denoting its
singular deployment, with shard selection being load management rather than identity. So
`Id(context C)` is a good value that is simply not a legal thing to end, and the restriction
had to be written by hand in two places. The lesson is narrow and repeatable: **when a design
justifies itself by "the type system already prevents this", check that it does.** Here the
type system was deliberately kept wide for an unrelated and better reason.

**The check that ran and found nothing, silently.** `checkTerminate` resolved its target's
entity through the refMap alone. The refMap holds only paths that were WRITTEN — but
`valueTypeExpr` SYNTHESIZES a `UniqueId` for `initiate` and for `self.id`, carrying a
fully-qualified `pathOf(p)` that was never a written reference. So every `terminate` whose
target came from either — which is to say the two idiomatic spellings — resolved to `None` and
skipped all its checks. **Three tests passed while proving nothing**, because "accepted" and
"never examined" are the same observation from outside. Found only by instrumenting per
CLAUDE.md's debugging rule; reading the code did not reveal it, and the ACCEPT tests could not.
Fixed with a `symbols.lookup` fallback. Same family as every other false green recorded here:
the signal that something was skipped is absent, not wrong.

**A stale number defended itself for weeks.** BACKLOG claimed `RiddlModelsRoundTripTest` was
red for "16 of 189 models". Measured this session: `commands` is 115/130, and A/B with the work
stashed gives *the same 130*. The figure was never right, and nothing had re-measured it because
the suite was already known-red — **a known-red suite stops being measured, so its recorded count
rots unchallenged.** Corrected in BACKLOG with the A/B method attached.

## A20 typed holes land (2026-08-15) — DONE

Five tasks: `PromptValue.typeEx: Option[TypeExpression]` + parser (reusing
`TypeParser.typeExpression`, widened to `private[parsing]`), prettify (a new
`PromptValue.ascriptionFormat` that strips the aliased-type `type` keyword and
recurses through `Cardinality` wrappers), validation (restate/contradict plus
a conservative unascribed-hole warning gated on which call sites actually
carry an expected type), BAST at `FORMAT_REVISION` 18 (riding the bump
numeric literals already spent, not a new one), JSON, and this verification
task. Commits `a1e040e55`..`746557d4c`, plus this task's fixture, BACKLOG
edits, and the ossum.tech task drop.

**The design's stated goal — "a typed hole is a seam, and the ascription
must never lie about it" — turned into a concrete rule: RESTATE, never
OVERRIDE.** `let x: Real = prompt("...") as String` is a validation Error
(the `let`'s declared type and the ascription disagree), and the comparison
is deliberately **syntactic, not resolved-type** — `constant G: Real =
prompt("...") as SomeAliasOfReal` is still an Error even when the alias's
underlying type is `Real`, mirroring how A57 treats an alias as a distinct
name rather than a transparent synonym. The corollary: **a `constant` with a
`prompt` value needs no ascription at all**, because the constant's own type
declaration already supplies it — `as Real` there is legal but says nothing
the reader didn't already know.

**A format bug — the sixth instance of "a dispatch written twice hides the
incomplete copy behind the complete one" in three days (CLAUDE.md, Total
Dispatch) — was found by Task 2 rather than merely proving Task 1's work.**
`PromptValue.format` called `TypeExpression.format` directly, and
`AliasedTypeExpression.format` always includes its `type` keyword, so
`prompt("x") as OrderId` prettified to `as type OrderId` — a string that does
not re-parse to the same AST (`type` there means something else). The first
fix was itself top-level-only — the instance-fix reflex recurring INSIDE the
fix for an instance-fix defect — and needed a second round to recurse through
`Optional`/`ZeroOrMore`/`OneOrMore`/`SpecificRange` the same way
`RiddlFileEmitter.emitTypeExpression` does, or `as OrderId?` regains the same
bug one level down. `AST.scala` cannot call `RiddlFileEmitter` (language
before passes, not the reverse), so the two copies cannot be merged and must
be kept in step by hand — which is exactly why this class of bug keeps
recurring here.

**`Currency` cannot appear bare in an example, and this project's own design
doc got it wrong before Task 3 caught it.** `Currency` is a predefined type
requiring a `country` argument (`Currency(USD)`), so `prompt("...") as
Currency` does not compile; the design doc's several `as Currency` examples
are illustrative shorthand only. Every fixture in this plan (test suites AND
the corpus fixture) uses `Real`, `String`, `Boolean`, or a declared alias
instead, and the ossum.tech task drop carries the same warning forward so the
documented examples don't repeat the mistake.

**This task's TatSu baseline (106/129, from the plan's Task 5 brief) was
already stale by the time verification ran.** Other work landed on
`release/2` between when that number was recorded and this session (modality
aliases, presentation verbs), moving the true immediately-prior baseline to
107/130. Re-measured by temporarily removing the new fixture rather than
trusting the brief's number — the lesson generalizes: **a baseline written
into a plan brief is a snapshot, not a constant; re-measure it at execution
time rather than trusting the number the plan was written against.**

**`ReportedIssuesTest`'s "should 406" failure is a fourth pre-existing red,
not a new one — but it had never been named on its own before.** The
numeric-literals handoff recorded `riddlc`'s corpus-suite failures as "18
succeeded / 3 failed" without separately naming which three; this session's
`git stash -u` A/B (identical count and identical failing test names, before
and after the new fixture) confirms it predates this plan too. It concerns
`morph entity ... to state ... with record ...` (`riddlc/input/issues/406.riddl`,
an in-repo fixture, not the external riddl-examples corpus) and is unrelated
to typed holes — filed as a fact for the next session rather than
investigated, since investigating it was out of this task's scope.

Eight tasks: `NumericLiteral` AST node + parser, prettify, widen `Comparand`
(A28) and `Constant.value` (four kinds), integer-type conformance, BAST at
`FORMAT_REVISION` 18, JSON, and this verification task. Commits
`eeb9e4707`..`a52844a8a`, plus the grammar fix and corpus fixture below.

**A28's literal-comparand ban had no uptake to protect.** `comparand` used to
admit only refs — `count > 5` was a parse error, forcing `count > MaxCount` —
on the theory that magic numbers should always be named. Reid reversed it
2026-08-14 after checking: the entire 189-model riddl-models corpus held
**exactly one** `constant`. The ban's cost (every literal comparison needs a
named constant) had nothing to show for it, because almost nobody was naming
one anyway. `comparand` now accepts a bare `NumericLiteral` and a literal
comparand draws a StyleWarning suggesting a name, not a parse error — advice
survives, the hard block does not.

**`Constant.format` emitted `const`, which is not a keyword** (`constant` is)
— so its output never re-parsed. Invisible for the same reason
`WhenStatement.format` was: `PrettifyVisitor` does not call `.format` for a
`Constant`, it routes through `RiddlFileEmitter.emitConstant`, so the
round-trip tests that would have caught it never touched the broken copy.
This is the second instance in this repo of "a dispatch written twice hides
the incomplete copy behind the complete one" (CLAUDE.md, Total Dispatch) —
worth treating as a standing risk anywhere prettify keeps a `format` method
AND a separate emitter for the same node.

**The three integer types had no documented range until this work.**
`Integer`, `Whole` and `Natural` existed in the AST and grammar with no
stated meaning anywhere — not the code, the grammar, the language reference,
or the Computational Model — so nothing could enforce a distinction between
them. Ruled by Reid 2026-08-14: `Integer` signed, `Whole` non-negative
(`>= 0`), `Natural` positive (`>= 1`). `checkNumericLiteralConformance`
enforces it now, but **only against literals** — `NumericType.isAssignmentCompatible`
still lets a reference of any numeric type flow anywhere, deliberately: a
literal's value is statically known where a reference's is not, so a literal
is held to the stricter standard. Task dropped in
`../ossum.tech/task/2026-08-15-integer-type-ranges.md`; Computational Model
update queued in `BACKLOG.md` § 0.

**The TatSu grammar validator hides a nameguard trap for bare letter tokens
next to digits.** `numeric_literal`'s exponent marker was written
`("e" | "E")`, which parses fine as prose but fails under TatSu's generated
parser specifically for `1e3` (no explicit sign) — TatSu's default
`nameguard` bounds any word-like quoted literal to a word boundary, so `e`
immediately followed by a digit reads as "might be the start of a longer
identifier" and refuses to match, even though `e+3`/`e-3` (non-alnum next
character) work fine. Fixed by writing the marker as an inline regex,
`/[eE]/`, which bypasses nameguard entirely — same idiom already used by
`mime_type`'s `/[a-z.*-]*/` and `markdown_line`'s `/[^\n]*\n?/` elsewhere in
the same grammar file. Found adding the `numeric-literals.riddl` corpus
fixture (TatSu baseline moved 105/128 -> 106/129, confirming the fixture is
actually exercised, not merely present).

## Incoming Tasks

**At session start**, check the `task/` directory for pending work
requests from other projects. Each `.md` file describes a task
(e.g., a dependency upgrade). Treat unresolved tasks as to-do
items unless already completed (verifiable from this notebook,
CLAUDE.md, or git log). After completing a task, append results
to the task file and note the disposition below.

---


## `!` and `not` become one node (2026-08-15) — DONE

`9c8b0cfb6..631f64bcc`, five tasks plus a whole-branch review and its fix wave. `!` is now legal
everywhere `not` is, both spellings build the identical `NotExpression`, and `WhenStatement.negated`
is gone.

**A measurement made the decision, and then made the job smaller.** The ruling left one question
open — which spelling prettify emits. The corpus answered it: **597 `not` against zero `!`**.
Authors already write the word form, so `!` converges to `not`, matching the precedent where
`A | B` prettifies to `one of { A or B }`. The same zero then made the expensive-looking part
cheap: deleting a public field from `WhenStatement` broke no model, because nothing used the
spelling that produced it.

**The corpus can only ever confirm what the corpus contains.** It has zero `!=` uses, so no corpus
run could catch the one real hazard here — a `!` prefix above `comparison` swallowing the `!` of
`!=`. Tests are the only net, and three were built. It later turned out `!=` is safe by STRUCTURE
anyway (`notExpr` is only entered where an operand begins, so it never sees a leading `!=`) and the
guard is inert defence-in-depth — but that was established by reading the composition, not by any
test passing. **A green corpus on a construct the corpus does not contain is not evidence.**

**Grep by NODE NAME, not field name.** Deleting `negated` looked like a `grep negated` job. Three
sites had positional `WhenStatement(…, _)` patterns that never mention the field they destructure,
found only by grepping `WhenStatement(`. Positional patterns are invisible to a field-name search,
and `language`/`commands` compile `--no-warnings`, so the compiler does not cover for you either.

**The most dangerous stale line was in a document, not the code.** `JSON_INPUT.md` still showed
`"negated": false` in its `when` example. That file exists so AI producers can emit
schema-constrained JSON — and `JsonModel`'s reader has **no unknown-key rejection**, so a producer
following the document would have had its negation silently dropped with no diagnostic. Chasing
that down established the general defect: the reader never diffs present-against-consumed keys for
*any* DTO, so every obsolete or misspelled key across the whole JSON input path is silently
ignored. Filed. **A stale example in a machine-facing document is a data-loss bug, not a typo.**

## A filed defect that was 22 times bigger than filed (2026-08-15) — DONE

`b55d1d5cc`, `bb46de1db`, `9dcfc646e`, `a3c0aa345`. Three filed defects fixed. Two were exactly
what they said. The third was not, and that is the entry worth keeping.

**I filed it as "`Finder` never descends into a `when` condition."** The audit found **29
field-held sites, 27 of them unreachable** — `MatchStatement`'s cases and guards,
`Correlation.timeoutStatements`, `SagaStep`'s do/undo blocks, `RequireStatement.argument`,
`InvariantBlock`, `PromptValue.typeEx`, the `Constructor`/`Call`/`Initiate` argument lists, the
`LogicalExpression`/`NotExpression` operands, and more. So **anything reading the AST through
`Finder` rather than a `Pass` has been silently missing content across 27 node fields** — and the
consumers most exposed are precisely the ones that ENUMERATE rather than traverse, which is
riddl-generator. Nothing errored. It just returned shorter lists.

**The lesson is about how the defect was found, not what it was.** It surfaced because a BAST test
tried to find a `ComparisonExpression` inside a `when` condition and got nothing back. One symptom,
one field — and behind it, twenty-seven. **A field-drop defect has no natural blast radius: the
instance you notice is the one your test happened to walk, not the extent of the problem.** That is
the difference between this family and a dispatch defect, where the compiler at least knows the
arms exist.

**And the first fix still claimed completeness while missing an arm.** The review caught
`PromptValue` — the only one of `Value`'s eleven arms holding a nested structure — absent from
`fieldChildren` entirely, despite being named explicitly in the brief. Fixed, and the second pass
answered the coverage question exhaustively rather than by assertion.

**What it cost to not fix the shape:** the consolidated `fieldChildren` is one extension point
instead of four scattered special cases, which is a real improvement, and it still ends in
`case _ => Seq.empty`. Logged with the other unaudited catch-alls rather than held, because the
consolidation was worth landing — but arm 12 of `Value` will be invisible on the day it is added.

## A20: what only a WHOLE-BRANCH review could see (2026-08-15) — DONE

10 commits, `a1e040e55..2e3be3ade`. `prompt("…") as T` types the seam between the
deterministic tier and the AI tier. Five tasks, each individually reviewed and each passing.
**Then the whole-branch review found four Important defects, and that gap is the lesson.**

**Per-task review is structurally blind to a missing owner.** Nothing resolved the ascription's
type reference. `ResolutionPass` had `case _: PromptValue => ()` with the comment *"AI-computed
literal text, no references"* — true before A20 and false after it, since `typeEx` can hold an
`AliasedTypeExpression` with a `PathIdentifier`. So `prompt("x") as Nonexistent` produced **no
message at all**: a model validating clean while naming a type that need not exist, which is the
one thing that undercuts a feature whose entire purpose is that the seam be checkable. No task
owned resolution, so no task's review asked about it. **When a feature adds a field, ask which
pass owns it — the answer "none" is invisible from inside any single task.**

**The shape-vs-instance failure recurred INSIDE the fix for its own previous instance.** Task 2
found `ascriptionFormat` emitting `as type OrderId` and fixed it — top-level only. Told to fix
the shape, it recursed through the cardinality wrappers — still only 5 of ~18 type-expression
shapes. Then Task 3 wrote `typeAscriptionName`, **a third copy of the same dispatch**, comparing
Scala class names, so `let x: OrderId = prompt("d") as OrderId?` reported a false contradiction
against itself. Three copies, each incomplete in a different way, all written within hours of
each other by people who had just been told about the pattern. That is the seventh instance in
three days. **The durable fix was to delete a copy, not patch it** — prettify's validated
positions now route through the emitter's total `emitTypeExpression`. One residual remains, in
constructor arguments, and it is filed rather than hidden.

**The design document's own examples did not compile.** `as Currency` appears throughout it, and
`Currency` is a predefined type requiring a `country` argument. Four shipped source comments
carried it too, one also asserting — falsely — that `Currency` resolves to `Real`. A spec that
has never been run against the parser accumulates this quietly.

**A ruling grounded in a count beat one grounded in judgement.** The open design question was how
aggressively to warn about an untyped hole. The answer came from measuring: all 288 corpus uses
already carry a type, 273 of them because the author wrote it unprompted. So the conservative
warning — one case, everything unwired stays silent — was not caution, it was what the evidence
supported. The distinction that made it precise: *"we did not wire this position"* is not the
same fact as *"the language cannot type this position"*.

## Numeric literals, and four bugs found by RUNNING rather than reading (2026-08-15) — DONE

21 commits, `7d5ea5b28..2e75ae3e7`. RIDDL can now write a number. The feature itself went
as designed; what is worth keeping is how the defects were found.

**Every real bug in this work was found by executing something, not by reading it.** The
plan specified `CharIn("0-9").rep(1)` for the digit runs, and it read correctly to me, to
the implementer, and to the first reviewer. It is wrong: under `MultiLineWhitespace`,
fastparse's `.rep` skips whitespace BETWEEN repetitions no matter what the surrounding `~~`
says, so `record R(1 2)` parsed as ONE literal with the text `"1 2"`. The reviewer caught it
by running the combinator standalone against fastparse 3.1.1 and getting
`Parsed.Success("1 2", 5)`. The same reviewer later confirmed the identical bug in the EBNF
by running TatSu, and a third verified the regenerated BAST fixture by `cmp -l` on the git
blobs rather than trusting the report. **Reading a parser combinator tells you what you think
it means; running it tells you what it does.**

**The same defect shape appeared FIVE times in two days, twice re-created after being
filed.** `CharIn(...).rep(1)` was fixed in the new parser rule, then found pre-existing in
`CommonParser.naturalNumber` and filed — and then written *again* into the EBNF, after the
lesson. Separately, `asLong` (`text.toLong`) was placed inside a match guard while the parser
accepts unbounded digit runs, so `constant N: Natural = <20 digits>` threw
`NumberFormatException` during validation and surfaced as `[severe] Exception Thrown` with no
line number — the *same class* of bug the branch had filed to BACKLOG one day earlier.
**"Fix the SHAPE, not the instance" is not advice about tidiness. Filing an instance does not
inoculate anyone, including the person who filed it.**

**`autoFixable = true` was a lie, and the lie was structural.** The new quoted-constant
deprecation claimed prettify would resolve it. Prettify did not: the parser kept the value as
a `LiteralString` and the emitter re-emitted it, so a migration tool trusting the flag would
report a fix that never happened. The fix was not to clear the flag but to make it TRUE — the
parser now CONSUMES the quoted numeric, exactly as `ConnectorOptionToIntention` does. **That
is what makes `autoFixable` honest anywhere: the old spelling must not survive parsing.**

**Three ways a test could not fail, all caught in review.** A "must be a parse error" case
routed through `parseAndValidate`, whose `Left` branch calls `fail` directly, so its
assertion was unreachable and it could only pass. A round-trip case asserting only that the
output re-parses — which `1.5` does perfectly well after `1.50` has been mangled into it. And
the standing trap of a `(td: TestData)` lambda on a plain `AnyWordSpec`, which constructs a
`Function1` and never runs the body. **Assert the exact value, and check the harness can
express the failure you are claiming to test.**

**A reversal has to move the comments, or the code starts lying.** Widening `Comparand` so
`count > 5` parses contradicted an explicit A28 comment — *"so magic-constant comparisons
cannot be constructed at all"* — repeated in the AST, the parser, the BAST codec docs, two
test files and a corpus fixture. The final review found four still standing after the task
that changed the behaviour. The BAST one mattered most: it is the wire-format spec for anyone
writing a reader, and it both omitted the new discriminator and asserted the old rule.

**The evidence for the reversal was a count, not an argument.** A28 required naming your
constants. The entire 189-model corpus contained **one** constant — and it was
`constant PointsPerDollar is Natural = "10"`, a number in quotes. The rule had no uptake to
protect, plausibly because the only way to name a number was to put it in a string.

**A fixture under `input/` is the only thing the CI grammar validators can see.** The
corpus fixture added in the last task immediately exposed a real parser/EBNF divergence:
`1e3` failed under TatSu without an explicit exponent sign. No internal test would ever have
found it, because internal tests exercise fastparse and never the documented grammar.

**Unrelated, and it needs its own look:** `Root2JsonCorpusTest` is at **59/190**, while
BACKLOG had recorded 173/189. A/B stash testing confirms the ~114-model gap predates this
work entirely. The stale number is corrected and the regression is filed.

## Three defects that hid behind a SECOND copy of the same dispatch (2026-08-14) — DONE

Tasks 4-6 of the message-value-source plan: warn on a bare message operand, warn on an
`initiate` id nobody uses, pin that a saga step may create and destroy instances. Commits
`f40822d3e`, `691f0e28c`, `11bf9c59c`. Two pre-existing defects fell out, and they are the
part worth keeping.

**A duplicated dispatch means the surface that proves totality is not the surface that runs.**
`AST.WhenStatement.format` matched four arms over a five-member union — no `PromptValue` — so
`when prompt("…")` threw a `MatchError`. It had survived because `PrettifyVisitor` does **not**
route through it: `RiddlFileEmitter.emitStatement` keeps its own copy of that dispatch, and
that copy has the arm. So the reflectivity round trip, which is the thing that normally proves
`format` total, could never reach the hole — the two copies were written correct and complete
INDEPENDENTLY, and only one of them was ever exercised. Prettifying `when prompt("…")` with
the released rc.14 binary produces correct output, which is exactly why nobody looked.
**When you find two implementations of one dispatch, the tested one tells you nothing about
the other.** It became reachable the moment Task 5 started rendering a clause body.

**Fix the SHAPE, not the instance — the second reminder in two days.** `aggregateFieldsOf`
followed the alias chain with no cycle guard, so `type A is B` / `type B is A` recursed until
the stack died. That is the identical defect fixed for its sibling `fieldsWithOwner` in rc.14,
comment and all; the shape was never grepped for. It was latent only because no caller reached
a cyclic alias, and `CheckMessagesTest` reproduced it as a `StackOverflowError` on the first
full run after Task 4 added one. The flaky-benchmark round recorded this same lesson a day
earlier.

**Choosing the conservative mechanism over the precise one, deliberately.** Task 5 has to
decide whether a `let`-bound id is used. The obvious way — enumerate the escape routes — is a
walk that must stay total over both the statement kinds AND every value-bearing FIELD each one
carries, and `statementValues` has already silently dropped two such fields
(`RequireStatement.argument`, `MatchCase.guard`). A missed route there is a FALSE warning on
correct code. So usage is decided from the RENDERED body instead: RIDDL is reflective by
mandate, a nesting statement's `format` renders its whole block, and a `format` that dropped an
operand would already be failing a round-trip test. It over-counts — a name inside a `do
"restart worker"` string reads as a use — and that is the safe direction. **Where a walk must
be total to be CORRECT, prefer a mechanism that cannot be incomplete over one that merely is
not incomplete today.**

**What the corpus measurement was actually for.** Task 4's field-less exemption had to be
sized before the number already quoted to riddlg could stand. Measured over all 189 entry
points: **14,714** bare message refs reached, **62** exempt, **14,652** warned, plus **645**
bare `morph` record refs; nothing unresolved. So the exemption removes 0.4% and the ~14,700
figure holds. Two things worth keeping from doing it properly: the design's example of a
field-less message, `event Started is { }`, **does not parse** (`is { ??? }` is the shape RIDDL
admits, and it lands on the same empty aggregate), and a source grep and the compiler disagree
by ~0.2% about how many bare refs exist, because the corpus has 1,001 `.riddl` files and only
those reached from the 189 `.conf` roots are ever validated.

**`;`-chained certification still stops at the first red module.** `tJVM` aborted at
`commands`' 16 expected-red corpus cases, so `riddlLib` and `riddlc` never ran — and the run
LOOKS like a completed leg. Documented in CLAUDE.md, and it still cost a re-run. With two
tests red on purpose, the JVM leg must be finished module-by-module.

## A capability nobody called, and a skip arm justified by a survey of its callers (2026-08-14) — DONE

riddl-models reported six constructs the source emitter could not render back. Five were real
(`method`, `shown by`, `table of … of […]`, `attachment`'s mime type, `replica of`); a sixth —
comments inside a record — was found while fixing them and had not been reported. Commits
`2ebe24a6c` and `80bb93b40`.

**The two silent ones each had a different flavour of the same mistake: a claim about code
elsewhere, believed but never checked.**

`doMethod` was a no-op carrying the comment *"Methods are handled by their type."* They were not.
`emitFields` took a `Seq[Field]` and both callers passed `.fields`, while an aggregate's contents
are `Field | Method | Comment` — so methods and comments were dropped. Meanwhile `emitMethod` was
fully written and had **zero callers anywhere in the repo**: the capability existed, was correct,
and was never invoked. A no-op justified by an assertion about other code is only as good as that
assertion, and nothing was checking this one.

`ShownBy` was skipped outright by `Pass.processValue`, in an arm whose comment said such values are
*"read by the definition that holds them."* That was true of every visitor **that existed** — and
false for the one whose whole job is to write source back out. **"The holder reads it" is a survey
of today's callers, not a property of the node**, so it is a poor reason to withhold a visitor
hook. `ShownBy` now has `doShownBy`, alongside the `Enumerator`/`Requires`/`Returns` precedent.

**Emitter defects and writer defects fail in opposite directions, and riddl-models named the tell.**
A BAST *writer* defect announces itself: the reader derails, loudly, somewhere far from the cause.
An *emitter* defect is silent — everything exits 0, the output still parses, still validates, and
simply contains less than the author wrote. Their reliable detector is **node count changing when a
construct is added or removed**, which is how they caught it: `bastify` reported 11 nodes with the
method and 9 without, while the round-tripped source came back at 9.

**What made it urgent rather than merely wrong:** `sbt riddlcPrettify` rewrites the whole corpus in
place. Every `method` in riddl-models was one prettify run from deletion, and the run would have
exited 0.

**Two lessons about the fix itself.**

*Uniformity is not a virtue when the parser is asymmetric.* The attachment mime type must be BARE
and the ULID form's argument must stay QUOTED — the same asymmetry `emitDescription` already
carries for `described at` (bare URL) vs `described in file` (quoted path). A tidy-looking uniform
fix breaks one of the two. Both are now pinned by tests so the next tidier finds out immediately.

*A test written after the fix proves nothing.* The `shown by` epic case was added after the code
worked, so it was **revert-proved** — disable the emission, watch it go red, restore it. Everything
else was watched to fail first.

**The compiler was no help and could not have been.** `-Werror` is live in `passes`, but a wildcard
arm makes a match syntactically exhaustive, so the terminal `throw` this repo prescribes is exactly
what silences the exhaustivity warning. The sweep for other instances was done by reading:
every `Unit = ()` hook in `PrettifyVisitor`, then `processValue`'s remaining skip arm.

**The trailing-slash item that rode along** is a different shape worth its own line: `described at
https://…/docs/riddl/` did not parse, but **the EBNF had always allowed it** — the fastparse
`urlPath` was stricter than the grammar it implements. So the fix was to the parser and the EBNF
went untouched, which is the opposite of the usual parser/EBNF sync obligation. It also took two
edits, not one: `URL.isValid` independently rejected a path ending in `/`, so fixing only the
parser converted a clean parse error into a thrown `IllegalArgumentException`. **A constraint you
are relaxing is often written down twice.**

## A rule that duplicated the language, and the syntax it had taken with it (2026-08-14) — DONE

`on term` required a leading `Id(<enclosing processor>)` parameter. The reasoning was sound as far
as it went — `on term` is invoked from OUTSIDE the instance, so the caller must say which one — but
Reid pointed out it does not reach the conclusion: **`self` is in scope for the whole clause body
and stays live to the very end of it**, so `self.id` already names the instance being terminated.
The requirement made the author restate what the language supplies, and made the argumentless
form — the one that will be common — a hard Error.

**The part worth remembering is the knock-on.** The bare `terminate P` form had been removed weeks
earlier, and the justification recorded in the parser was entirely derivative: *"`on term`'s
leading `Id(...)` parameter is REQUIRED, so a no-argument `terminate` can never satisfy the arity
check and is unreachable in any valid model."* That is a true statement about a world with the
requirement in it. Delete the requirement and the syntax removal it justified has nothing left
holding it up — but nothing in the code would have said so, because the parser comment reads as an
independent design decision, not as a consequence. It was found only by reading the comment while
removing the thing it depended on. **When removing a rule, grep for what was justified BY it**;
a derived decision outlives its premise silently.

Also a TDD lesson, caught by the revert proof rather than by discipline. Four of the five new tests
went red with the implementation reverted; one — *"`self.id` readable in the clause body"* — passed
in BOTH states. It asserted that no error message mentioned `self`, and with the fix absent the
clause failed for a different reason (the missing parameter), so the assertion never spoke to
`self` at all. **A test that passes before and after measures nothing**, and writing the test and
the implementation together is exactly how that goes unnoticed: had it been run red first, the
vacuity would have been obvious. Strengthened to `justErrors mustBe empty`.


## A check that was wrong for the common spelling hid 49 real defects (2026-08-14) — DONE

riddl-models filed the rc.14 addressing check as a false positive: *"carries no field typed
`Id(X)`"* fired on messages that plainly carried the id. Their diagnosis was already correct — it
compared the field's WRITTEN type rather than its resolved one — and the code confirmed it in one
line. `isAddressFieldFor` matched `f.typeEx` against `case uid: UniqueId` and fell to `false` for
everything else, so a field typed by the named alias `type OrderId is Id(entity Order)` was never a
candidate. The sibling `fieldsWithOwner` two functions above ALREADY followed the alias chain — but
for the message type, never for the field's type. The fix is the same step in the second place.

**The lesson is about which spelling a check was tested against.** The alias form is riddl-models'
documented house style (*"Type IDs as `{Name}Id`"*), so the check recognised only the spelling
almost nobody writes and misfired on the one everybody writes. Every test we had used the inline
form, so the suite was green and unanimous about a check that was inert in production. Two commands
differing ONLY in the alias — one flagged, one not — is the whole bug, and is now a test.

**Then the fix turned 16 of 189 corpus models red, and that was the valuable part.** Fixing the
alias case made previously-invisible fields into candidates, so messages carrying two of them
started producing the ambiguity Error. A stash-and-rerun A/B established the baseline was
189/189 green, so all 49 were attributable. Checking the corpus SOURCES rather than reasoning
from the message text — the step that mattered — showed three classes, all corpus-side:
genuine two-id ambiguity (`CartsMerged {targetCartId, sourceCartId}`), actor fields legitimately
of the same entity (`identityId` + `suspendedBy`), and **wrong-entity aliases**:
`type TaskId is Id(NursingContext.NurseShift)`, `type ReportId is Id(ImagingExam)`,
`type MemberId is Id(Enrollment)`. A name-heuristic sweep found 17 such candidates corpus-wide,
several not yet erroring because nothing `tell`s them yet.

Those had been wrong since they were written and nothing could see them. **A broken check does not
merely fail to report — it certifies.** The fourth sighting of "a green corpus is evidence about
the corpus", but the first where the corpus going RED was the evidence.

**Reid's ruling on the follow-up question**: alias chains are followed, nesting is NOT. A `result R
is { thing: ThingBase }` whose nested record carries the id stays flagged, because descending into
aggregates is an unbounded search with no principled stopping point — turtles all the way down.
Renaming is transparent; containment is not.

**Found on the way, and worse than the bug being fixed**: a cyclic alias (`type A is B` / `type B
is A`) crashed rc.14 outright — `StackOverflowError` in `fieldsWithOwner`, reproduced against the
RELEASED binary, reaching the author as `[severe] Exception Thrown` with no line number. The fix
would have added a second path into it, so both walks now carry a visited list keyed on reference
identity (a `Set` would fuse two distinct identical alias declarations, since `Definition.equals`
is structural). Worth noting how it was found: not by a test, but by asking "my fix adds a
recursion — is the existing one guarded?" and then spending two minutes proving the answer with
the staged binary instead of assuming it.

Also shipped: `option persistent` retired for Connector in `RecognizedOptions` (synapify), whose
own comment had predicted its retirement — the intentions shipped in rc.14 and the entry did not,
so a tool's picker offered a spelling its Problems pane then flagged. Third drift between those
two tables; the Connector half of the behavioural drift guard now exists alongside the Entity one.


## A `Container` that isn't a `Branch` is invisible to the writer pass (2026-08-14) — DONE

riddl-models filed `task/2026-08-13-interaction-blocks-break-bast-round-trip.md`: `sequence {
... }` / `parallel { ... }` / `optional { ... }` interaction blocks bastify without complaint and
then fail to unbastify, with the reporter's own diagnosis already correct — node count went DOWN
(9 -> 8) when the block was ADDED, the same tell that caught the `constant`/`Method` defect in
`4ca2906dc`. This is the third instance of the family CLAUDE.md's BAST section already names:
`BASTImport` needed dedicated `openBASTImport`/`closeBASTImport` hooks because it "extends
`Container` but not `Branch`, so without the hooks it falls through and its contents are never
visited." `InteractionContainer` has exactly that shape and nobody had given it the hooks.

**Root cause, precisely.** `Branch = Definition + Container`, and `InteractionContainer` (the base
of the three block kinds) has no `id` — it can't be a `Definition`, so it can't be a `Branch`.
`BASTWriterPass.traverse` (which overrides the base `Pass.traverse` to interleave count-then-items
for every multi-content node: `Correlation`, `SagaStep`, on-clauses, ...) had no case for it, so it
fell to the generic `wm: WithMetaData` fallback. That fallback calls `process()` only — which
writes the header and the contents COUNT (`writeContents`'s whole contract is "write the count,
trust the caller to write the items") — and never descends. The reader's `readContentsDeferred`
then consumed N nodes that were never written, desynchronizing the stream. Confirmed independently
that this ISN'T a validation gap too: `ValidationPass`/`ResolutionPass` don't use the generic
`Branch` traversal for `Interaction` either — `PrettifyVisitor.doInteraction` already manually
recurses into a block's contents itself (`emitInteractionContents`), which is the established,
correct pattern for a node that structurally cannot be pushed onto `ParentStack` (`push` requires
`Branch[?]`). The fix follows that same shape, added locally to `BASTWriterPass` rather than as a
new `Pass`-level hook: only the writer needs "children immediately follow the count" ordering: no
other pass needed to push `InteractionContainer` as a parent.

**The sweep found two more of the identical shape, not literally "Container" this time but the
same "count now, items later, and later never came" contract failure.** `invariant ... is {
<statements> <predicate> }` (A28 + 2026-08-04) has its statements in a field of `InvariantBlock`,
which isn't even a `Container`; `Invariant` is a `Leaf`, so nothing generic ever walked them
either. That one had a wrinkle the interaction fix didn't: `writeInvariant` writes the block's
predicate *inline*, before returning control to the pass, so the deferred statement items had to
land on the wire *after* `requires` (the next field `writeInvariant` writes), not right after their
own count — `BASTReader.readInvariantNode` needed restructuring to defer building the
`InvariantBlock` until after `requires` was read, then read the items. And while checking the
task's explicit ask — "check that Relationship's discriminator cannot collide with the interaction
kinds' 0/1/2/10-17" — the real answer was worse than a collision risk: `writeRelationship` wrote
**no discriminator byte at all**, so the shared-tag reader (which unconditionally reads one before
the location, for every arm including its own `Relationship` default) misread every relationship's
own location as its own dispatch byte. Corruption on every occurrence, latent since `relationship`
was first serializable, caught only because this task asked to look at exactly that spot.

**What this teaches, generalized:** `writeContents`'s contract — write a count, trust an unrelated
caller elsewhere in the codebase to write the items — is a trap that has now fired at least four
times (`constant`/`Method`'s tag collision was adjacent, not this exact shape, but same family).
Every fix in this family so far has been "teach the specific missing traversal path," never "make
the mismatch structurally impossible." That remains true after this round too — not fixed, just
flagged again, now with three more data points. If a fifth instance turns up, that's the signal to
stop patching call sites and change the contract itself (e.g., a writer method that returns "how
many items I still owe" and a chokepoint that refuses to finalize a node until that count is
satisfied).

FORMAT_REVISION 15 -> 16. `NotImplemented.bast` regenerated from its own directory (per BACKLOG §0
policy) — 93 bytes, single-byte diff at byte 12 (the revision short), confirmed with `cmp -l`.
New test: `InteractionBlockBASTRoundTripTest` in `passes/src/test/scala-jvm-native/`, 9 cases
(three block kinds, nesting, the node-count-delta pin, invariant block statements, relationship
x2, format revision), verified executing (not just compiling) on both JVM and Native. Full
`language`/`passes`/`commands` suites green (66+187+17 = 270 suites, 668+1196+245 = 2109 tests, 0
failures) as one `;`-chained `-batch` invocation so nothing was silently skipped by an early abort.

## The gap was instantiation, not addressing (2026-08-13)

riddl-generator filed a narrow ask: `tell command Ship to entity Sales.Order`
names a TYPE, and an entity is not a singleton, so a generator cannot know
which aggregate to load. They offered three candidate answers and said they had
no stake in which we picked.

We picked their option 3 — a first-class way to denote an instance — and it is
worth recording that we did **not** pick it because it was the most expressive.
We picked it because options 1 and 2 are both unimplementable. Each presupposes
that an instance can already EXIST and be referred to, and RIDDL had no way to
bring one into being. `Id(P)` was a type with no producer anywhere in the
language: every value of it would have had to arrive from outside the model.
Ship only the addressing half and you ship a vocabulary that can never be used.

So the plan's centre of gravity moved off the construct that was reported.
Eight tasks: one is `tell` addressing. The rest are `Id(P)` widening, `self`,
parameterised `on init`/`on term`, `initiate`, `terminate`, and the effect bans
that follow from calling those two effects.

**The generalisation: when a consumer reports that they cannot NAME something,
check whether anything in the language PRODUCES it.** riddlg's framing — "the
send site cannot name the target" — was accurate and pointed at the wrong file.
The real shape was "nothing mints the thing the send site would name". A gap
report describes a symptom from where the consumer stands; it is not a
localisation.

### The corpus set the severity, and it was not close

Before writing the diagnostic we counted: riddl-models holds **7,556 `tell`s**
(5,155 entity-targeted) against **7** `Id(...)`-typed fields in the entire
996-file corpus. The certified measurement is 5,087 missing-address
CompletenessWarnings over 189 models — 98.7% of every entity-targeted tell —
with **zero** new errors.

That ratio decided the severity in one step. As an Error it would have
condemned essentially every model in existence, including ours, and the feature
would have shipped with a migration attached. As a CompletenessWarning it says
something precisely true: the model is under-specified, because the modeller has
not yet said which instance. That is not noise to suppress — it is a
measurement of how much of the corpus predates the ability to express the
thing at all.

Ambiguity, by contrast, IS an Error. Two fields typed `Id(Order)` and no `by`
is a contradiction, not an omission, and there is nothing for a later pass to
fill in. Omissions warn; contradictions fail.

### The cheapest design won on resolution cost, not on power

`self`'s type is a synthesized `Aggregation` rather than a bespoke AST node.
The payoff is that `let me = self` followed by `me.id` runs through the SAME
`ValueRef` path walk as every other value, so **no resolution rule anywhere had
to learn that `self` exists**. The bespoke node would have needed a special
case at each of those sites, and each would have been a place to forget.

The price is that the type is not user-nameable — `self.id` is `Id(Order)` here
and `Id(Shipping)` there — so `let me: T = self` has no `T` to write. That is a
real restriction and it was accepted knowingly: passing `self.id` is what a
modeller wants in every case we could construct.

### `-Werror` never told us anything, and we had written down that it would

Seven missed dispatch (or dispatch-INPUT) sites across this branch. Every one
was found by a person reading code or by a code review. **Zero** by the
compiler — while `CLAUDE.md` told each session that the compiler warning "is
the whole safety net".

Two reasons, and the second is the one that matters. `language` and `commands`
carry `--no-warnings` next to `-Werror`, so there is nothing to escalate there.
But in `passes`, where most of the seven lived, warnings are on and it still
never fired — because **a wildcard arm makes a match exhaustive, so the
terminal `throw` this repo prescribes is itself what silences the compiler.**
Follow our own rule and you are guaranteed never to be told the hierarchy grew.
The `throw` is a real net, but it fires at run time on the first test that
reaches the arm, which means it protects you exactly as far as your tests
reach.

Corrected in `CLAUDE.md` § Total Dispatch. The wider lesson is about the file
itself: a confident, wrong sentence in project memory is worse than silence,
because every session inherits it and stops looking.

Task 7's review added the other half. `statementValues` was genuinely total
over the statement kinds and *still* never yielded `RequireStatement.argument`
or `MatchCase.guard`, so `require X with initiate entity Order` slipped past
four separate walks built on it — each individually correct. **Auditing the
match arms proves nothing about the fields each arm forgot to return.**

### Predicting the floor delta by ONE found a hole nobody was looking for

Certification predicted +79 JVM / +3 JS / +68 Native from the source root of
every suite the branch added, before reading the log. Actual: 2346 / 715 /
**1619** — JVM and JS exact, Native **one short**.

One test. The rule says a delta that does not reconcile is a skipping bug, not
a total to accept, so it got chased, and it was not a skipping bug — it was the
prediction being wrong for an interesting reason. `TypeParserTest` lives in
`language/src/test/scala`, which I read as "runs on all three platforms". It is
**abstract**, and its concrete runners are declared only in
`scalajvm/…/JVMTests.scala` and `scalajs/…/JSTests.scala`. There has never been
a Native one.

Pulling that thread: **13 shared parser suites, 169 test cases, have never
executed on Native at all** — `TypeParserTest`, `StatementsTest` (52 cases),
`HandlerTest`, `ParsingTestTest`, `StreamingParserTest`, `TokenParserTest` and
the rest. Confirmed by diffing the suite names in the JS and Native `language`
rows of the certification log: they overlap almost nowhere. Filed in
`BACKLOG.md`.

The trap itself was already written down — `.claude/skills/rc/SKILL.md` names
"abstract with concrete runners only in `JVMTests`/`JSTests`" as the reverse
trap — which is the uncomfortable part: **the gate that catches a thing and the
knowledge of the thing are not the same asset.** A documented hazard sat next
to a floor that had absorbed it silently for months, because the floor is a
total and a total cannot say what is missing from it.

The value of predicting before reading is not the agreement when it holds. A
number computed afterwards can always be rationalised — 1619 would have been
written down as "+67, close enough" without a second glance. One computed
beforehand turns a one-test gap into a question you are obliged to answer.

One inconsistency was found and deliberately NOT fixed: `checkInitiate` and
`checkTerminate` do not honour the `???`-stub exemption that
`checkTellAddressing` does, so `initiate entity Order(x = "1")` against
`entity Order is { ??? }` errors by reasoning from an unwritten body. Filed in
`BACKLOG.md`. A behaviour change inside the run that certifies it would have
invalidated the run.


## Two consumer-found defects the corpus could not have found (2026-08-13)

riddl-models and ossum.tech each reported a bug the same day. Neither could have
been caught by any gate this repo runs, and for the SAME structural reason both
times: **the corpus cannot contain a counter-example to a rule it has been
edited to satisfy.**

- riddl-models had already **deleted** the offending `constant` from
  reactive-bbq so their `.bast` files stayed readable. So our
  `RiddlModelsRoundTripTest`, which bastifies their live checkout, was green
  over a corpus with the failing construct removed from it.
- ossum.tech's `when !isValid` fence is in their docs, not our test inputs. No
  `.riddl` under `language/input/` uses `!`.

This is the third instance of the pattern in a week (synapify's fan-in sink was
the second). It is worth naming: **a green corpus is evidence about the corpus,
not about the language.** When a rule tightens, the models that violated it get
edited, and the evidence that the rule was wrong disappears with them.

### `constant` corrupted every byte after itself in BAST

`writeConstant` and `writeMethod` both wrote `NODE_FIELD` — "similar to fields",
said the comments. But a Constant appends its literal VALUE and a Method appends
its ARGUMENT LIST, and the reader, having read a Field, left those bytes in the
stream. Everything downstream was misread.

**The reader had said so, in a comment:** *"This is ambiguous … For now, assume
Field. Writer should disambiguate better."* That is the part worth keeping. A
known-ambiguous decode is not a rough edge to revisit; it is a corruption that
has not been triggered yet. The rule is now in CLAUDE.md: two node kinds may
share a tag only if they write byte-identical payloads.

**The error message was actively misleading, and could not be otherwise.**
One constant surfaced as `Invalid string table index` in a 13-node model and as
`Invalid invariant condition kind: 67` in a 9618-node one — sending both
riddl-models and this session to bisect an innocent invariant. A desynchronised
byte stream names where it DERAILED, never what derailed it; by detection time
the evidence is gone. We fixed what was fixable (the reader's context stack now
knows Constant and Method, so it stops blaming Field) and told the reporter
plainly that the general property is not achievable.

Fixed with `NODE_CONSTANT` (109) / `NODE_METHOD` (110) and `FORMAT_REVISION` 14.
`Method` had the identical defect with no repro and was fixed alongside it.

### `when !isValid` threw, and the "enumerate the hierarchy" rule did not help

`stateReadsIn` had no arm for `Identifier`, so a documented form that validated
on rc.11 threw on rc.13. The tempting lesson — *enumerate the sealed
hierarchy* — is wrong, because that rule was already in force and WAS
followed: the InvariantCondition fix the day before audited `Value`
exhaustively.

The actual trap is that `statementValues` yields a domain **wider than
`Value`**. `WhenStatement.condition` is `LiteralString | Identifier | ValueRef |
BooleanExpression | PromptValue`, and `Identifier` is in no other member. So an
exhaustive audit of the nearest-looking type still misses it. **Enumerate the
domain of the FUNCTION.** The throw did its job; the enumeration was aimed at
the wrong hierarchy.

### A ruling: `not` is the only general-purpose negation

`!` is accepted in exactly one position — `when !<bare-identifier>` — and will
not be extended to paths or to `require`/`let`. It buys no expressiveness and
costs four surfaces (parser, EBNF, GBNF, prettify) plus a second spelling
authors must choose between; RIDDL's operators are words and `!` is the
outlier. It is KEPT rather than deprecated, since it parses today and models
use it.

The ruling is only defensible while `not` genuinely covers the same ground, so
that is asserted rather than assumed — `when not isValid` has a test.

### Process notes, both self-inflicted

- **Certification was restarted three times** because the tree kept moving under
  it: a fixture regeneration, a stale reader comment, a missing `Method` case.
  Settle the tree, THEN certify. Each restart cost ~5 minutes and the last one
  nearly cost a false certification.
- **Regenerate `NotImplemented.bast` from its OWN directory.** Run from the repo
  root it bakes `language/input/import/…` into the file instead of the bare
  filename — 93 bytes becomes 115 and the diff stops being a one-field revision
  bump. Recipe in BACKLOG § 0. `sbt clean` deletes the stage, so regenerate
  BEFORE certifying.

## Four rulings, and a label with two homes (2026-08-11)

Reid ruled on a batch of open items; four are built and out of BACKLOG.

**A projector's record is what it SENDS.** "Projector X lacks a required Record
definition" fired on a correct 1-for-1 event→command translator. The check only
ever inspected `projector.types`, so it was asking the wrong question rather
than asking too much. It now discovers the type from the `tell` statement
targeting the repository, and **where that type is defined does not affect
whether the requirement is met** — that is a separate Warning: *"T populates R
but is not defined in it"*, because the data that populates the database
belongs with the repository. Warning, not Error: defining it elsewhere works.

**`???` is a body that says "don't expect much", so validation must EXEMPT it.**
Reid's ruling, and it is general rather than a carve-out for one check: any
definition with a `???` body earns at most a Missing warning about the body and
skips every other rule. The practical form is that a check must not reason from
what a `???` body does NOT contain — `repository R is { ??? }` is not missing
its handlers, it is unwritten, and a rule that fires on it fires on nearly every
stub in the corpus. Now in CLAUDE.md § "Validation Specifics" because it
constrains every check anyone adds.

**Invariant shadowing is a Warning, innermost wins.** Legal, because narrowing a
rule inside one state is a real intent — but silently shadowing a CHECK is the
failure mode the implicit-invariant work exists to remove, so it is said out
loud. It lives in `checkInvariantScope`, which already has the parent chain.

**A display label that lived in two places drifted, exactly as you'd expect.**
`RangeType.kind` was `"Range"` while the only parseable spelling is
`range(2,4)`. Lowering `kind` did NOT fix the error text, because
`AST.errorDescription` held a SECOND hardcoded copy — `case RangeType(_, min,
max) => s"Range($min,$max)"`. The test caught it. `errorDescription` now
delegates to `format`, so there is one source of truth and it cannot drift
again. The JSON discriminator is still `"Range"` and is deliberately NOT tied to
either: it is a wire format, hardcoded at both the read and write sites.

## The mystery failure was a real bug, already reported (2026-08-13)

One certification run failed `RiddlModelsRoundTripTest` on reactive-bbq with
`BAST deserialization failed: Invalid invariant condition kind: 67`. It arrived
right after the connector work bumped `FORMAT_REVISION` 12 -> 13, so it looked
obviously mine; it did not reproduce afterwards, and it was briefly written up
here as unexplained with contention as the leading hypothesis.

**It is explained, it is not mine, and the explanation was already sitting in
`task/`.** riddl-models had filed
`2026-08-13-constant-breaks-bast-round-trip.md` that morning: a `constant`
cannot survive a BAST round trip, found on **rc.13**, and — quoting the report —
"in the full reactive-bbq model the *same single constant* surfaces as `Invalid
invariant condition kind: 67`". Character for character the error chased here.

**Why it stopped reproducing.** The same report says they removed the `constant`
from reactive-bbq so the corpus keeps a readable `.bast`. That edit landed
between the failing run and the re-runs. So the concurrent session did not cause
the failure — it *removed the cause* mid-investigation, which is a far more
confusing thing to be on the receiving end of than a plain race.

**Three lessons, in order of how much time each would have saved.**

1. **Read `task/` before diagnosing.** The answer was on disk, filed hours
   earlier, complete with a 13-node repro. Hours of bisecting the codec bought
   less than one `cat` would have.
2. **A deserialization error names where the reader derailed, not what derailed
   it.** The report makes the same point and asks for it as an acceptance
   criterion. The invariant was innocent; so were connectors. When a BAST reader
   reports a garbage tag, treat the named construct as a *position*, not a
   suspect.
3. **"Unexplained" was the right thing to write, and the right thing to
   revisit.** Recording it as an open question rather than pinning it on a
   plausible-looking cause is what made it cheap to correct once the real cause
   turned up. The failure mode to avoid was declaring the connector codec guilty.

Certification remains non-hermetic while it reads a live `../riddl-models` — do
not run corpus work, or leave another session editing it, during a certification.

## A stale capture is not a baseline (2026-08-13)

Connector intentions landed: `persistent` and `at-least-once` | `at-most-once` as
keywords before `connector`, replacing the option. Three things worth keeping.

**The design question answered itself in the authority.** The plan flagged "what
does a connector with NO delivery intention mean?" as needing a ruling before
building. Computational Model §25.7 already said it: at-least-once on durable
realizations, "weaker only as a knowing deployment downgrade, **never a silent
one**". So no default had to be invented, no completeness warning was warranted,
and `at-most-once` as a keyword is exactly the mechanism that section demands.
**Read the authority before asking for a ruling** — half the open question was
already settled in writing.

It also drew the line for what does NOT belong: §25.7 calls `unordered`
"permission, not mandate" with a best-effort obligation, which is the definition
of advisory — so ordering stays an option. The admission test for an intention is
whether a generator may decline to honour it.

**The corpus A/B nearly produced a phantom regression.** Diffing against
`corpus-ext2.txt`, captured earlier the same day, showed 18 warnings and 2
completeness messages "disappearing" — precisely the direction that hides real
defects, so it stopped the work for a while. They had not disappeared. **riddl-
models is a moving target** (CLAUDE.md says so, and I had edited it myself hours
earlier), so a capture from this morning is not a baseline for this afternoon.

Re-running the PREVIOUS binary (`../bin/riddlc` at rc.13) over the corpus at the
same moment gave the honest answer: warnings 863 → 863, completeness 1 → 1,
errors 0 → 0, deprecations 2 → 432, every non-deprecation line byte-identical.
**A baseline is a run, not a file.** If the inputs can move, capture both sides
back to back or the diff measures the wrong thing.

**Two documented traps bit anyway, which is an argument for re-reading them.**
Inserting the new enum between `@JSExportTopLevel("Connector")` and its case
class silently reattached the annotation — `cJVM` cannot see it, `cJS` can. And
`StreamingValidation` held `options.find(_.name == "persistent").get`, safe only
while persistence could come from nowhere else; the moment it could come from an
intention the `.get` threw and killed the entire streaming check with a Severe.
Widening where a value can come from turns every `.get` on the old source into a
landmine.

## Two implementations agreeing is not evidence they are right (2026-08-12)

`repository MachineRegistry as sink` with two inlets and no outlets was rejected:
*"its arity (0 outlets, 2 inlets) is void"*. The model was correct; riddlc was
wrong. Found not by the corpus but by a **synapify Domain Model Wizard run** —
the last remaining error in a generated model, which the repair loop would have
"fixed" by damaging something valid.

**The vocabulary had a hole and a catch-all hid it.** `shapeForArity` named
`sink` as exactly `(0, 1)` and `source` as exactly `(1, 0)`, so `(0, >=2)` and
`(>=2, 0)` — an ordinary fan-in drain and fan-out origin — matched no arm and
fell to `case _ => Void`. A gap in the shape vocabulary became a confident wrong
answer that validation then reported as fact.

**The comment promised a safety net that did not exist.** It read *"degenerate
arities fall back to Void rather than crashing; arity validation is performed by
a later pass"*. There is no later pass: `validateProcessorShape` IS the check,
and it consumes `arityShape`, so it inherited the wrong value. When a fallback
says "someone else will catch this", find the someone else before believing it.

**What made it invisible for so long: the parser enforced the same restriction
independently.** `streamletTemplate(Keyword.sink, maxInlets = 1)` and the
`(0, 1)` arm agreed with each other, so nothing looked inconsistent. But they
were not two checks confirming a rule — they were **two encodings of the same
assumption**, and agreement between them carried no information. Meanwhile A31's
own comment said the opposite: *"fan-in/out is modeled by declaring MULTIPLE
ports"*.

**The corpus could not have found it.** riddl-models is written against riddlc,
so nobody wrote a fan-in sink — it would have errored. A restriction that is
wrong makes its own counter-examples unwritable, which is exactly the class of
defect only a CONSUMER generating fresh models can surface. Both of today's real
finds came from consumers (riddlg's `when invariant X` crash, synapify's fan-in
sink); the corpus found neither.

Reid widened both shapes: a SINK is any pure drain and a SOURCE any pure origin,
whatever the port count. `shapeForArity` is now **total over non-negative
arities** and its final arm THROWS, because returning a plausible shape is
precisely how the old one turned a gap into a lie.

## I predicted the direction of a corpus change, and was wrong by 1120 (2026-08-12)

Two checks reported the same defect in different words: the pre-existing #17
(*"Event X is defined but no handler produces it"*) and a correlation-scoped twin
I had added the day before. Reid ruled: fix #17 properly, then delete the twin.

**#17 asked the question four ways wrong**, each a false-positive source: it
scanned only entity and state handlers IN ONE CONTEXT; counted only `send` and
`tell`, so `yield event X` — the canonical spelling in an event-sourced entity —
read as unproduced; matched by NAME, so two contexts declaring `Paid` silenced
each other; and was gated on the context having entities, which was a false
NEGATIVE that skipped whole contexts.

**So I predicted the rewrite would REMOVE warnings. It added 1120.** Corpus went
884 → 2004. That number was not a corpus finding; it was a bug in my own fix.

**`external context Foo` and `option external` are different things, and only one
of them is `hasOption("external")`.** The corpus writes the INTENTION form
(`Context.intention`, four values: Application/External/Gateway/Service), and my
exemption tested only the option. So every event declared in an `external
context` — exactly the blocks describing systems a model deliberately does not
implement — was reported as emitted by nothing. Testing both spellings took the
delta to **zero**: 884 messages, byte-identical to baseline.

**The lesson is about the order, not the bug.** I wrote "expected direction is
fewer warnings" into a BACKLOG entry as a reason the change was safe. Had I
trusted that and skipped the A/B, this would have shipped 1120 false warnings
across 189 models. Predicting a delta is fine; *citing the prediction as
evidence* is not. Measure, then explain the number you actually got.

Worth checking whether other `hasOption("external")` sites have the same gap —
`validateOnMessageClause` and `checkCompletenessPostProcess` both use it, and
neither consults `intention`.

**A test fixture that legitimately warns is not always worth "fixing".** Three
in-repo fixtures began warning once the false-negative gate was gone. Two were
goldens and simply gained a line. The third asserted `isOnlyIgnorable`, and both
attempts to satisfy it structurally made it worse: adding an entity pulled in the
entity-completeness rules (needs a state, a sink, a repository) and adding a
source pulled in the connector ones (outlet not connected). A focused fixture is
allowed to be an incomplete model — excluding the one expected message by name,
with the reason written down, beats inflating the fixture until it stops
complaining.

## The soak worked, and it caught me enumerating from memory (2026-08-12)

Four hours after `2.0.0-rc.12` shipped, riddl-generator reported that
`when invariant X` — A17's ask form — **aborts `ValidationPass`** with
`IllegalStateException: stateReadsIn has no arm for InvariantCondition`. That is
the first real soak result this branch has had, and it arrived only because a
consumer was exercising a form nothing in the corpus uses.

**The throw was correct. The enumeration was not.** These three functions —
`stateReadsIn`, `asksIn`, `countValueFailPoints` — deliberately end in a `throw`
rather than a catch-all, so an unhandled node fails loudly instead of silently
returning "nothing here". That design did exactly its job. What went wrong is
that I enumerated `BooleanExpression`'s subtypes **from the ones I happened to
see in nearby code** rather than from the sealed hierarchy, and then wrote a
message telling the next reader to *"decide whether it can contain a `get from
state` rather than assuming it cannot"* while having done precisely that
assuming.

**The lesson is mechanical, so it is worth stating mechanically:** when writing a
total dispatch over a sealed type, read the hierarchy — `grep "extends
BooleanExpression"` — and tick the arms off against it. `Value` is a union of 8,
`BooleanExpression` has exactly 5 members, and all three functions covered
everything except `InvariantCondition`.

**Two of the three gaps were older than the bug report suggested.** The report
attributed it to my rc.12 commit, which is right for `stateReadsIn` — but
`git tag --contains` puts `asksIn` in **rc.11** and `countValueFailPoints`
before **rc.1**. So it was one blind spot replicated across three siblings, two
of them latent for releases. riddlg hit it now only because its spec uses
`when invariant X` in an ENTITY handler, which reaches the newest of the three;
the saga path reaching `asksIn` is rarer and nothing had exercised it.

**Canary-check a regression test that is supposed to assert recursion.** The
obvious wrong fix here is `case ic: InvariantCondition => Seq.empty` — it stops
the crash and is still wrong. Tests that only assert "no longer throws" pass
against it. So the two tests that matter hide a `get from state` and an `ask`
inside the invariant's `with` operand, and I verified the property by stubbing
both arms and confirming those two go RED while the crash-only cases stay
green. Assert the behaviour that distinguishes the real fix from the plausible
one, then prove the assertion can fail.

## The corpus was arguing with itself, and the warning was wrong (2026-08-12)

Three statement-scope holes from ossum.tech: `set` accepted in a context handler
and a saga step, `get from state` accepted in a saga step and outside the owning
entity. All four re-probed live before any work — they still held.

**Measuring the corpus reframed the task.** It was filed as three keyword bans,
with an acceptance criterion braced for fallout "as the `as type T` tightening
did (202 errors across 186 of 187)". Measured: `set` in a context handler **0**,
in a saga step **0**, `get from state` anywhere **0**. The two rules that looked
expensive were free.

**What was NOT free was the rule nobody proposed.** `set` appeared 97 times in
REPOSITORY handlers, which would have made "set belongs to entities" break real
models. Reid knew why: riddl-models added them to silence *"contains only prompt
statements"*. The corpus was not evidence that repositories write state — it was
evidence that a riddlc warning was wrong, laundered through a workaround into
something that looked like usage. **When the corpus contradicts a rule you
believe, check whether an earlier diagnostic taught it to.**

So the fix was two-sided and had to land together: exempt repositories from the
warning (their on-clauses legitimately hold one `do` standing in for SQL), and
only then ban `set` there. Landing the ban alone would have re-created exactly
the pressure that produced the workaround. The warning also stopped saying
`prompt` — `do` is the canonical spelling, so it had been telling authors to look
for something their models did not contain.

**The corpus A/B is the part worth keeping.** Before: 884 messages, 0 errors.
After the riddlc change: 884 + exactly 97 errors, every other line byte-identical
once sorted (the first diff looked alarming and was pure output ordering). After
deleting the 97 workaround lines from riddl-models: **884 again, byte-identical
once line numbers are stripped**. Three measurements, each predicting the next.
The corpus test went 189/189 with `pendingModels` still `Set.empty`.

**A trap for the next scope rule.** `checkStatementScopes` is NOT the hook it
looks like — it is wired only to on-clauses and function bodies, never saga
steps, which is precisely why hole 2 existed. `validateStatement` is the one
every statement reaches, and for a saga step `parents.head` is the **Saga**,
because a SagaStep is a Leaf that `Pass.traverse` deliberately never pushes.

## A rule that could not be written told us the grammar was wrong (2026-08-12)

A70's last three rules are built, and the first of them changed the language
rather than being added to it.

**Building a check is a test of the design.** The rule read *"every
correlation's yielded record is handled by the referenced Repository's
handlers."* It cannot be written. `yields` took a `recordRef`, while a handler
clause takes a `messageRef` — the four real messages only (A9b) — so **no
repository handler could name what a correlation produced.** The 2026-08-11
ruling had already felt this and worked around it, inferring acceptance from "a
command that *holds* the record", and left "does *holds* mean a direct field or
any nested one?" open. That open question was the design failing, not a detail
awaiting a decision.

Reid's ruling deleted it: **`yields` names a COMMAND**, because a projector's
only output is a change to a repository and a repository is changed by handling
a command. The check collapses from a reachability search to `eq` on a resolved
`Type`. The same shape as the timeout ruling one day earlier — make the bad
state unrepresentable instead of diagnosing it.

**Worth remembering: the ruling's own corollary was the answer.** It said "a
correlation's output ought to be a command on the Repository" while the grammar
said `yields record`. When a design note and the grammar disagree, the note is
usually where the thinking got to and the grammar is where it stopped.

**Enforced in two places, deliberately.** The wrong KEYWORD dies in the parser
(`commandRef`, so `yields record R` does not parse); a `command` naming a
non-command is an Error in `ValidationPass`, the only place holding the resolved
referent — and a parse-time `error()` there would preempt the whole pass chain.

**Severity was Reid's call too:** the repository-accepts-it rule is a
**Completeness warning**, not the Error A70 specified. A repository missing the
handler is under-specified, not self-contradictory.

**A recorded baseline is not a measured one.** The handoff's JVM baseline of
2201 was stale; the tree actually measured 2205, so the +13 that first appeared
looked like 4 unexplained tests. Stashing and re-running gave the real number
and the delta came out at exactly +9. The per-platform split is the useful part:
JVM +9, JS +1, Native +8 — because `CorrelationTest` is `scala-jvm-native` (so
no JS) and `ProjectorTest` is abstract with concrete runners only in `JVMTests`
and `JSTests` (so no Native). **Three different deltas, all correct.** When they
do not reconcile, measure the baseline rather than adjusting the expectation.

## Correlations, and a design fixed by deleting the question (2026-08-11)

A70 built: `correlation <id> by <keys> yields command <C> is { <handler> }
times out after "<duration>" { <statements> }` inside a projector. Syntax,
resolution, all eight validation rules, and all four reflectivity surfaces
(prettify, BAST rev 12, JSON). (It read `yields record <T>` until 2026-08-12 —
see the entry above.)

**The design changed mid-plan, and the change is the lesson.** Planning surfaced
a question the committed design could not answer: what does a correlation with
no `timeout` mean? The first answer was a ruling — retained forever, the
implementation deals with it — which then needed a StyleWarning to make the cost
visible. Reid replaced that with a structural fix: make the timeout **mandatory
and syntactic**, dropping the `else` keyword in favour of `times out after`.

That deleted **three** planned diagnostics rather than implementing them —
forever-retention, no-timeout-in-scope, and a block that can never fire were all
consequences of optionality. **When a plan starts accumulating warnings about a
state, check whether the state should exist.** The same move also removed the
A10-style timeout inheritance from the Projector, since nothing is left to
default, so `RecognizedOptions` was untouched by the whole feature.

Promoting `timeout` out of metadata is the **entity-intentions argument again**
(rc.10): §4.2 calls options advisory, and a bound that MUST fire a block is not
advisory. Two precedents now point the same way; treat "this option drives hard
behaviour" as a smell that it should be grammar.

**Three things a new Branch node silently breaks**, all found by building this:

1. **`Containment.of`** (`AST.scala`) is an exhaustive match over `Branch` with
   no fallback — a missing arm is a runtime `MatchError`, not a compile error.
2. **`Pass.traverse`'s generic `Branch` arm walks `contents` only.** A
   Correlation's `timeoutStatements` is a FIELD, so it needed its own case
   BEFORE that arm — exactly the defect SagaStep was fixed for, where an
   unreached block resolves nothing and the model validates clean while naming
   definitions that need not exist.
3. **`PassVisitor.openContainer` ends in `case _: Definition => ()`**, so a new
   node falls through in silence rather than failing.

**Two pre-existing projector checks assumed the old shape** — folds in one
top-level handler, over a record the projector itself declares. A correlating
projector does neither, so each is now skipped when correlations are present. A
projector without them validates exactly as before.

**Writing the tests found a real gap.** The first test model named the
correlation and its record both `Fulfillment`, and got "Path reference
'Fulfillment' is ambiguous" — which says nothing about how to fix it. State has
an explicit same-name Error for exactly this; correlations now do too.

**A stale CLAUDE.md line cost a small decision.** It said `riddlc unbastify` was
"pending"; it is implemented (`UnbastifyCommand`, exercised over the whole
corpus by `RiddlModelsRoundTripTest`). That reasoning appears in the BAST commit
message and is wrong there. The decision it fed — checking in
`language/input/import/NotImplemented.riddl` as the fixture's source — stands on
its own: regenerating from a checked-in source beats recovering it from the
binary either way. Line corrected.

**What was deliberately NOT built**: the "yielded record is handled by the
referenced Repository" Error. A70 states the rule but not the mechanism, and
repositories in the corpus are commonly `{ ??? }` with no handlers, so a guessed
shape would have fired on correct models. Filed with the open design question
rather than implemented on a guess.

## 2.0.0-rc.11, and two ways a green run can certify nothing (2026-08-10) — DONE

The largest RC of the 2.0 line — 70 commits — shipped to all six channels.
Getting there took two false certifications, both green, both worthless.

**A warm sbt server ignores a changed `sbt.version`.** Reid bumped sbt to 2.0.6
for a critical vulnerability mid-cut. My certification had already run — clean,
all three platforms, every floor exceeded — under the sbt the server booted with.
The only signal is one line that scrolls past in a batch log: `[warn] sbt version
mismatch, using: 2.0.2, in build.properties: "2.0.6", use 'reboot' to use the new
value`. **`reload` does not fix it; only `shutdown`/`reboot` does.** The `/rc`
skill now shuts the server down and asserts `show sbtVersion` before certifying.
This is the same family as the testQuick and action-cache traps already recorded
here: a run that is green about the wrong thing.

**`git status` paths are relative to cwd.** The shell had wandered into
`language/src/test/scalajvm/python` to run the grammar validators, and stayed
there. So `git status --porcelain` reported `M project/build.properties` for the
NESTED stub in that directory, not the root pin — and I spent several turns
diagnosing a file I had not meant to touch, concluding a phantom "something is
rewriting this file" when `git checkout --` appeared not to work. It worked
fine; it was restoring a different file from a staged blob. **Use `git -C <repo>`
or absolute paths in release steps.** Two `build.properties` exist here and only
the root one pins the build.

**An unreleased tag is not a published tag.** I tagged rc.11, then found the sbt
problem before `gh release create`. Because every downstream channel triggers off
that release, nothing existed: no GitHub release, no Maven coordinates, no npm,
no Homebrew — verified by query, not assumed. So the "never retag" rule did not
apply and Reid had the tag deleted and the number reused. **The rule protects
consumers of published artifacts; check whether any exist before burning an RC
number.**

**Corrected in the skill:** the Homebrew RC path was marked "NOT yet exercised
end to end" — stale, since rc.9, rc.10 and rc.11 each updated only
`Formula/riddlc-rc.rb` while `riddlc.rb` stayed on 1.31.0.

**What shipped**, all verified against the registry rather than the logs: 20/20
Maven coordinates (and the four retired modules confirmed ABSENT), npm under the
`rc` dist-tag with `latest` unmoved, three release assets, the prerelease flag
set so the blog stayed silent. Certification under sbt 2.0.6: JVM 2177 / JS 704 /
Native 1473, zero skips, zero failures, all four grammar validators clean.

## Three fixes from two task files, and a near-miss (2026-08-10) — DONE

Closing out riddl-models' two 2026-08-10 tasks produced three changes and one
lesson that was nearly a disaster.

**The near-miss: I had the diagnosis backwards, and Reid's warning caught it.**
The saga task carried an undiagnosed observation — `expression in Saga 'S' … must
not be empty` alongside `let x = ask …`. I isolated it to `RiddlValue.isEmpty`
defaulting to `true` with `Call`/`Ask` not overriding it, filed it as "concrete
case classes are missing overrides", and was about to add those overrides. Reid
stopped me: *"Empty means there are no contents, it doesn't mean all the optional
fields are None,"* and *"this has bitten ME many times."*

He was right, and the contract says so **in the source I had already read**:
`AST.scala:98` documents `isEmpty` as *"non-containers are always empty"*. Every
`Value` except `LiteralString` is a non-container, so `isEmpty == true` is
CORRECT for them. Adding overrides would have redefined emptiness from
*contentless* to *present* — a different question, and the one traversal and
flatten depend on.

**The real bug was the CALLER asking the wrong question.**
`checkNonEmptyValue` is meaningful only for a `LiteralString`; eight of its ten
call sites in ValidationPass already guarded on exactly that, and two —
`let`'s expression and `set`'s value — passed an arbitrary Value and therefore
fired on correct code. The generalizable rule, now in CLAUDE.md § Emptiness:
**when a check misfires on correct code, suspect the question before you change
what is being asked.** Fixed at the two callers; the AST was not touched.

**A ruling that changed no behaviour.** Reid ruled that paths must not descend
through optional fields, extended to `*` and `+`. Testing first showed all three
ALREADY refused — the rule was the rule; what was missing was any way to learn
it, since the message claimed the name "was not found" when the name is right
there in the type. So the change was purely diagnostic. **Check whether the
behaviour you have been asked to implement already exists**; here it turned a
resolver change into a message change.

**The corpus is not a gate for everything, and saying which is part of the
result.** riddl-models has EIGHT `let` statements and 901 `set` statements, and
every one has a literal-string right-hand side — so it exercises only the path
the emptiness fix does not touch. "Corpus unchanged" was expected and proves
nothing there. Same shape as the `foreach` blindspot already recorded below.
The saga and cardinality fixes are likewise exact no-ops on the corpus (0 errors,
byte-identical per-model tally), which is a real result only because the fixtures
carry the actual coverage.

**Four of my own measurements were wrong before one was right**, all in the same
family — a measurement that reports "nothing" because it was built wrong:
- `sbt … | tail` buffers until sbt exits, so the log looked hung for ten minutes.
- A wait condition on `^\[error\]` matched riddlc validation output printed
  INSIDE passing tests, so I read a mid-run log and nearly reported an abort.
- The first corpus script counted `[missing]` when the message prints
  `[completeness]`, while `riddlc from <conf> validate` suppressed the rest —
  the corpus `.conf` files set `show-style-warnings = false`, silencing exactly
  the messages under investigation. It reported all-zeros.
- `grep "let "` counted 10,589 statements by matching **out**let and **in**let.
  The real number is 8.
**A measurement built to find something that finds nothing is a bug report about
the measurement**, not a result.

**Publishing: two ways to ship something unreproducible.** A warm sbt server
serves the version it resolved at startup, so `publishLocal` labelled new code
`2.0.0-rc.10-57-e012ebb9` — overwriting what that version already meant, which is
worse than a stale artifact. Then a tree with one uncommitted file produced
`…-64-3635a3f8-20260810-1624`, whose timestamp suffix is dynver saying no commit
can reproduce it. Both are now impossible: `scripts/publish-and-stage.sh` refuses
a dirty tree before sbt starts, runs `reload; publishLocal; riddlc/stage` in ONE
invocation so both halves move together, and verifies the staged binary's version
against `git describe`. Reid deleted both bad artifact sets from `~/.ivy2/local`.

## A "contradiction" that was an unfinished migration (2026-08-10) — DONE

A task file from riddl-models reported that two streaming checks contradicted
each other: Rule 5 advises putting an adaptor between an external context and a
processor in another context, and doing so then triggered `Sink 'X' is a sink
but has no upstream path from any source`. It offered three options and asked
riddl to RULE among them. I relayed that framing to Reid as "blocked on your
call". **He had made no call, and there was none to make** — that was the whole
lesson of the session.

**Read the file, then read the code, then believe the code.** `check-tasks`
says exactly this and I half-did it: I verified the file's line references and
the `Adaptor`-is-not-a-`Streamlet` type relationship, then carried its
CONCLUSION across unexamined. The tell was in my own output — a grep for
`trait Streamlet` returned nothing and I reported the conclusion anyway. That
empty grep was the finding: `Streamlet` is a concrete case class
(`AST.scala:4842`), one of six sibling `Processor` kinds, never the supertype
meaning "port-bearing thing". The unified processor model raised the CAPABILITY
into `Processor` and left the NAME on a leaf.

So there was no contradiction between two rules. There was one unfinished
migration: `StreamingValidation`'s graph stayed typed over that leaf, and
`collect { case s: Streamlet => s }` silently dropped five of six kinds. The
decisive evidence was inside the same file — `checkUnattachedOutlets` and
`checkPortletCardinality` read the `inlets`/`outlets` buffers, which
`validateInlet`/`validateOutlet` fill for EVERY owner. One half of the file
already treated an adaptor's ports as real when asking "is this connected?",
the other refused when asking "where does data flow?".

**A control isolates a cause; a reproduction only confirms a symptom.** Four
minimal models, one variable changed at a time. `adaptor` vs `processor as
flow` in the same position — same topology, ports and connectors, differing
only in processor KIND — pinned the cause to the type filter and nothing else.
The `entity` model (no external context, no adaptor, so Rule 5 absent
entirely) proved it was never adaptor-specific. The `flow` model found
something the task file had missed: the advisory clears only for an `Adaptor`
and reachability worked only for a `Streamlet`, so NO kind satisfied both.

**Three of my own measurements were false before one was true.** A `| tail`
on a background sbt run buffers everything, so the log stayed empty for ten
minutes and looked hung. A wait-condition of `^\[error\]` matched riddlc
validation output printed INSIDE passing tests, so I read a mid-run log and
nearly reported 4-of-7 modules as an abort. And the first corpus script counted
`[missing]` when the message kind prints `[completeness]`, while
`riddlc from <conf> validate` was suppressing the rest — the corpus .conf files
set `show-style-warnings = false`, so the run reported all-zeros for exactly
the messages under investigation. **All-zeros on a measurement built to find
something is a bug report about the measurement.**

**The corpus consequence was worth the measurement.** Errors 0 → 0, the 7
advisories unchanged, and 3 new completeness warnings. Two are repositories
whose chain traces back to an `application context … as router` with no
`Source`-SHAPED processor anywhere — literally true, and it raises a real
question (filed, not decided): should reachability demand a Source-shaped head,
or a node with no inbound edge? The third is a context ascribed `as sink` that
declares no ports; **`effectiveShape` honours the ascription over arity**, so
my claim to Reid that portless processors are bounded out as `Void` held only
for UNASCRIBED ones.

Fixed in `70b0f527a`. `ByIdentity` keys the graph (Reid's call) because
`Definition.equals` is structural and `loc` is what distinguishes two
same-named processors — the same trap `checkPortletCardinality` documents.
`ValidationOutput.streamlets` keeps its published `Seq[Streamlet]` type by
narrowing at the construction site: widening a buffer is not a licence to widen
a published type. Remaining `Streamlet`-narrowed sites in `AnalysisResult`,
`MessageFlowPass`, `DiagramsPass` and `StatsPass` are filed in BACKLOG, not
swept — `AnalysisResult.streamlets` is public API whose MEANING would change.

## Destructuring a mapping, and a census of one (2026-08-10) — DONE

`foreach` over a mapping bound one name to `Anything`, so the body could write
`e.whatever` and be believed. Two candidate fixes, both sound:

1. bind one name to a synthesized anonymous `{ key, value }` record;
2. bind two names, `foreach k, v in m`.

Reid took 2, and the reasoning is worth keeping because it generalizes: RIDDL
has **no generics**, so a named entry type in the predefined `Riddl` module
could not be typed against an arbitrary mapping's `from`/`to` — a standard
module holds concrete definitions, and this is inherently parameterized. Option
1 dodged that by staying anonymous (`collectionElementType` returns a
`TypeExpression`, not a `Type`), but two names need no type at all.

**Iterating keys and looking the value up was rejected on a fact, not taste.**
The `Value` union is `LiteralString | PromptValue | Constructor | ValueRef |
GetValue | BooleanExpression | Call | Ask` (AST.scala:2933) and nothing in it
indexes; `GetValue` is `get from <inlet|state>`, not a subscript. So binding
only the key would strand the value — the very defect just fixed, rebuilt on
purpose. That absence is now filed as its own feature (`<mapping|array> at
<index>`, BACKLOG § 2), because a mapping is otherwise write-only outside a
loop.

**Arity is strict both ways**, and the "one name over a mapping" case is the
load-bearing half: leaving it legal would leave the `Anything` hole open, which
is what the work was for. The corpus made that free — `foreach` and `mapping`
each appear **zero** times across riddl-models AND riddl-examples, so a
tightening that would normally need a deprecation cycle broke nothing.

That zero is the entry's real lesson. **Nothing in either corpus exercises
`foreach` at all**, which is why the loop-variable bug of 2026-08-09 had to be
reported by a docs writer rather than caught here, and why neither corpus can
be counted as a gate for this statement. The fixtures are the only gate, so
they carry the whole load — hence a test per reflection surface, each asserting
the second NAME rather than whole-model equality. A fixed-point check compares
two sides that can both be missing it, which is exactly how A57's binding
shipped broken for a commit.

**A silent hole fell out of it.** `foreach k, v` was the first thing in RIDDL
that ever needed to READ a mapping's `to`, and it did not work — except when it
did. The destructuring tests passed only because the fixture's `byId: mapping
from Integer to Line` sat beside an unrelated `lines: many Line`; the refMap is
keyed by PATH, so that one resolved occurrence covered for the mapping's own
missing one. Deleting the sibling field broke `v.sku`.

The cause was `case Mapping(_, from, _)` in `ResolutionPass` discarding the
value half, so `mapping from Integer to Nonexistent` validated CLEAN while the
same name in the key position errored correctly (`b307909b5`). Pre-existing and
unrelated to `foreach` — a wildcard in a pattern that quietly declined to do
half the work.

**Two lessons, and the second is the general one.** A test that passes because
of a neighbouring line is worse than one that fails, and the tell here was
cheap: the same assertion behaved differently in two fixtures that differed
only in an unrelated field. When that happens, isolate before celebrating — the
fixtures are now built so the mapping's value type is referenced ONLY by the
mapping, and the fix was verified by reverting it and watching two cases go
red. More generally, **adding the first consumer of a piece of data is when you
discover nobody was producing it**; expect that, rather than assuming the
existing surface works because it compiles.

Two mechanical notes worth reusing:

- **The positional parameter was deliberate.** `valueElement` went in as a
  required positional field, not a defaulted trailing one, so `-Werror` named
  all four stale pattern sites (`Finder`, two in ValidationPass, one test). A
  default would have compiled everywhere and silently dropped the name.
- **The `FORMAT_REVISION` 9→10 bump reddened `IncludeAndImportTest`** exactly
  as the HANDOFF warns. The documented recovery worked verbatim: the staged
  `~/Code/ossuminc/bin/riddlc` was still at revision 9, so `unbastify` recovered
  the fixture's source for the new build to re-emit. **Keep a last-revision
  binary until the bump is finished** — restaging first destroys the only tool
  that can read the old fixture.

---


## A restriction nobody chose (2026-08-09) — DONE

ossum.tech reported two `foreach` defects. The first was plainly a bug: the
loop variable was bound for the header's own check and nowhere else, so every
body that dereferenced the element was an Error, and `foreach` admitted only
bodies that ignored what they iterated. Fixed at `3b2af9049` by carrying the
element's **type** rather than its name — binding the name alone would accept
`line.nosuch` as readily as `line.sku`, which is the last-component-matching
defect A54 removed from `ValueRef` resolution generally, and no better for
being local.

The second is the one worth recording, and it is about **how to read a
diagnostic**.

`foreach line in field order.lines` was rejected with:

> 'foreach' field 'order.lines' must be a field of the enclosing entity's
> state, the handled message, or a function input

The reporter read that as "a considered restriction" and asked us to confirm
so they could document it. I read it the same way, traced it to
`foreachAllowedFields(parents).exists(_ eq field)`, established that the check
was *satisfiable in principle* by anchoring dotted paths at one of those three
roots, and brought Reid a three-option ruling about how far to widen it.

All of that was the wrong shape of question. Reid's correction: **cardinality
is the whole question.** Resolve the path, look at the type it lands on,
iterate iff it has cardinality. Where the field sits is the resolver's
business and the resolver has already answered. The allow-list went away
entirely (`ca495d67e`); it never earned its place.

**The lesson: a message that enumerates conditions may be enumerating intent,
or it may be enumerating the contents of a data structure.** This one listed
the entity state, the handled message and the function input because those are
what `foreachAllowedFields` happened to concatenate — not because anyone
decided depth was forbidden. It read like a rule because error messages are
written in the voice of rules. Before treating a restriction as a design
decision to be documented or negotiated, find the commit that introduced it
and the reason given; absent a reason, it is an artifact, and the question is
whether to delete it rather than how far to relax it.

The corollary for asking Reid anything: a question offering three ways to
preserve a mechanism presumes the mechanism. That presumption was the error,
and no amount of care inside the options would have surfaced it.

Two loose ends recorded rather than hidden: a `Mapping` is iterable but binds
its element to `Anything`, since RIDDL has no pair type to name the element
with, so members of the element go unchecked. A `Graph` binds the node type
and is fully checked. `ForeachValidationTest`'s third case was **reversed, not
deleted** — it asserted the Error this work removed, and the reversal is worth
reading in the diff.

---


## Three silent holes, and a test that checked nothing (2026-08-08) — DONE

Two task files from ossum.tech, both reproduced on the CURRENT build before any
work started. They had tested `2.0.0-rc.9-54`; ours was `rc.10-39`. The gap
changed nothing, but checking cost a minute and would have saved an afternoon
had it.

**Order mattered more than any individual fix.** The empty-`[severe]` reporting
defect was the smallest of the three and looked like the least important. Fixed
FIRST, it turned ossum.tech's silent failure into a named `ClassCastException`
with a file and line — converting the second bug from a hypothesis they had
inferred by reading into a fact confirmed by execution, before any of that work
began. Their own framing argued for it ("worth more than the specific fix");
they were right.

**`// no references` was false, and the comment is why it survived.**
`ResolutionPass.resolveInteractions` dismissed all three interaction containers
with that comment. The container carries none; its CONTENTS do. A step inside
`sequence`/`parallel`/`optional` was never resolved AND never validated — two
independent gaps, either sufficient — so a model could name definitions that do
not exist and validate green. A confident comment is how a hole stays open:
nobody re-derives what a comment already asserts.

**The recurring shape, now three for three.** `InteractionContainer` is a
`Container` but NOT a `Branch`, so the generic traversal cannot descend into it
— exactly like `SagaStep` (`a1bce0d50`) and `BASTImport` before it. When a node
holds children outside `contents`, or is a Container without being a Branch,
assume the traversal does not reach it and prove otherwise.

**A passing test that checked nothing.** `PathThroughFunctionTest` passed in
isolation and on JVM, and failed on NATIVE only, because `pc.options` is global
mutable state and a different suite ordering there had left `showStyleWarnings`
clear. `Messages.Accumulator` DROPS StyleWarnings when it is, so the message
list came back EMPTY — and five of the seven cases assert `mustBe empty` against
`justErrors`, which an empty list satisfies. Five tests were passing while
observing nothing; only the one assertion looking for a PRESENT string exposed
it.

Two lessons, the second sharper than the first:

- Pin options with `withOptions` whenever a diagnostic's visibility depends on
  them. Already documented at `PortletOptionTest.scala:22`, which had learned
  this and written it down — and which was read too late.
- **A canary does not catch this.** The canary proves a body EXECUTES; it says
  nothing about whether the body OBSERVES anything. An assertion of the form "no
  errors" is vacuous whenever the message set can be empty for unrelated
  reasons. Prefer at least one assertion per suite that requires something to be
  PRESENT.

**Verified before claiming their last criterion.** ossum.tech asked whether the
documented `parallel`/`optional` analysis actually runs on grouped steps. It
does — `UseCaseTracePass.walkSteps` recurses and `checkParallel` does the
cross-order check. We nearly told them it had been starved by the resolution
bug; it had not. `lookupOne` goes through the SYMBOL TABLE, not the refMap, so
that analysis was unaffected all along. The guess was plausible, wrong, and one
grep from being found out.

## `persistent` where there is no state to persist (2026-08-07) — DONE

`task/done/2026-08-06-persistent-should-error-on-gateway-context.md`. Misplacing
`option persistent` is now an Error, not a StyleWarning. Connector is the only
definition that takes it.

**The filed ask was smaller than the rule that came out of it, in both
directions.** riddl-generator asked for an Error on a *gateway* context. Checking
the premise first showed the task's central claim was wrong — it said the option
was "accepted like any other option", but it already warned, on every context
identically. So the change was never scope, only severity. Then Reid widened the
principle past gateways ("everything else has no state to persist") and, on being
shown that an Entity carries persistence as an INTENTION and a Repository is
persistent by implication, narrowed it again to Connector alone. The delivered
rule matches neither the task nor the first ruling; the task file records both
divergences rather than quietly satisfying the newest one.

**Severity is per-option, and that was the design decision.** `OptionSpec` gained
`severity`, defaulting to StyleWarning. The tempting move — make every
`validParents` violation an Error — was never ruled on and would be a
corpus-wide behaviour change. A test pins the boundary by asserting a misplaced
`auto-id` stays a warning; without it, a later "simplification" that drops the
field would pass every other test in the suite.

**Why this one is an Error at all** is worth keeping: a misplaced option is
usually just ignored, but `persistent` on a stateless definition ASSERTS
durability nothing can provide. Contrast A35's cross-boundary connector warning,
deliberately a warning because the in-memory downgrade is a legitimate deployment
choice. Severity tracks whether the model is saying something untrue, not how
annoyed we are.

**426 corpus uses rode on this option**, all on connectors — which is why the
follow-on (connector intentions replacing it) is sequenced in BACKLOG as add,
deprecate, migrate, remove. Changing severity was safe precisely because the
corpus was measured first, not assumed.

## The type-first aggregate is deprecated (2026-08-07) — DONE

`type X is command {…}` now emits one `[deprecated]`, code `type-first-aggregate`,
`autoFixable = true`. Removed in 3.0. Two commits: migrate the fixtures, then
deprecate.

**Where the deprecation fires is the whole design.** It is emitted in
`TypeParser.defOfType`, NOT in `aggregateUseCaseTypeExpression`. Only an
aggregate use case standing as the DIRECT type expression of a `type` definition
is the type-first spelling; the same expression reached through a FIELD's type
(`f: command { … }`) is a different construct and is deliberately untouched. Put
the deprecation one level lower and it would fire on both.

**A 305-site rewrite that moved no test counts is the proof it was a spelling
change.** JVM stayed at 260 suites/2074 tests and JS+Native at 243/2048 across
41 files. Three things did move, none of them meaning: two `.check` goldens
(they quote the offending source line AND encode its column span, so any rewrite
of that line shifts them) and two hardcoded token counts, because
`type X is command {` is five tokens and `command X is {` is four.

**Only 2 of the 4 fixtures carrying the form had goldens that noticed.** Predicting
which goldens shift by grepping fixtures over-predicts — a golden only moves if a
message actually points at a rewritten line.

**BACKLOG's numbers were wrong in both directions**, found by counting rather
than trusting: 158 occurrences, not 167; and it omitted 151 more embedded in
`.scala` test-fixture strings, which is nearly half the real work. Its
"9,337 kind-first declarations" is also unreproducible. The directional claim it
rested on — zero type-first in riddl-models and riddl-examples — is exact.

**`DeprecationCode.all` was missing `EntityOptionToIntention`**, defined since
rc.10 but never listed, so the "exhaustive migration report" its own doc promises
had silently omitted every entity option-to-intention deprecation. Found only
because adding a code meant reading the list.

## A green Scala suite says nothing about the Python grammar CI (2026-08-07)

The include-transparency commit (`564b17b3f`) shipped a **broken
`ebnf-grammar-validation` job**. Its two new include fixtures are fragments —
`entity Thing is {…}` at top level cannot parse standalone — and they were never
added to `ebnf_tatsu_validator.py`'s `INCLUDE_FRAGMENTS` allowlist, so the
validator exited 1.

**It was reported as green on all three platforms, and that was true and
irrelevant.** `tJVM`/`tJS`/`tNative` do not run the Python validators; CI runs
them as a separate job. So the evidence gathered never covered the thing that
broke. It surfaced a commit later only because the next task happened to touch
the grammar and thus ran them.

**Adding a `.riddl` fixture is a grammar-surface change, not just a test
change.** Any new fixture that is an include fragment or intentionally invalid
needs an allowlist entry, and the validators must be run —
`.venv/bin/python ebnf_tatsu_validator.py` — before calling the work verified.
The lesson generalises past this repo: "all tests pass" is a claim about the
tests you ran, and the gates that live outside the test runner are exactly the
ones a green run cannot speak for.

## A plan for work that was already done (2026-08-06) — CLOSED

BACKLOG § 1 carried "Carry source locations through the JSON surface" as **NOT
STARTED**, with a detailed plan called "executable as written" and sized LARGE
(own session). It shipped **eight days earlier**, on 2026-07-29, in
`84c1b5124` — on this branch the whole time. BACKLOG, the plan file, and
NOTEBOOK's own HANDOFF all repeated the claim, each citing the others.

**What caught it was checking the cheapest falsifiable number in the plan
before starting.** The plan sized the work as "~300 `At()` sites in
`JsonAstBuilder`". `grep -c 'At()'` returned **0** — because `Ctx` already
carries `source`/`current`/`locOf`, exactly what the plan proposed adding. From
there every other element was already present: `ContentEntry`, `LocationsDto`,
`$at`, `basis: origin|document`, and both stated verification tests
(`JsonRoundTripTest:388` and `:445`).

Had I started from the plan's narrative instead, the first hour would have gone
into re-deriving why the code did not look like the plan said.

**A plan file cannot notice the work happening.** Neither can a BACKLOG entry.
The repo already knows this about `task/` files — `/ossuminc-skills:check-tasks`
exists precisely to verify a sender's claims against the world — and the same
discipline was simply never extended to our own plans. It is now recorded at the
top of the plan file. The generalisable move is cheap: **pick the plan's most
specific factual claim and test that one first**, because a stale plan is
usually stale in a way a single grep exposes.

## A comment that prescribed the wrong fix (2026-08-06) — DONE

`task/done/2026-08-06-include-transparent-definitions.md`. Synapify asked for
`Contents.definitions` to descend `Include`/`BASTImport` like its 35 siblings.
It does now, with `directDefinitions` as the literal form.

**The lesson is about the instruction we left ourselves, not the accessor.**
`Contents.scala` carried a note naming three accessors that stay literal and
telling a future reader how to promote one: *"give it the transparent treatment
AND remove the caller's manual walk in the same change."* Followed literally,
that breaks imports. ResolutionPass's walk descends `Include` and deliberately
NOT `BASTImport`, because an imported definition must not resolve before an
explicit `flatten` — and `filterThroughWrappers` cannot express "includes but
not imports". There are **three** semantics; the note assumed two. So the walk
STAYS and reads `directDefinitions`, and the comment now says why it is a
permanent exception rather than unfinished work. A future-instruction comment is
a hypothesis about a change nobody has attempted — it deserves the same doubt as
a stale BACKLOG entry.

**Two false warnings fell out, both real.** `checkContents` told a container
whose content arrived entirely by include that it "should have content" —
reproduced, not reasoned: `domain d is { include "types" }` draws
`Domain 'd' in Root should have content` while having two types. And an include
contributing through a further include was reported as contributing nothing.
Nobody filed either; they surfaced only because the accessor they shared moved.

**`checkUniqueContent` got stricter and it cost nothing.** `type Thing` beside
an included `entity Thing` is now an Error. It was always ambiguous — the
resolver is include-transparent — merely invisible. Reid approved taking a
corpus hit; there was none, 189/189 riddl-models still validate clean.
**That zero needed its own measurement.** `Root2JsonCorpusTest` compares the
original AST against the re-parsed one, so an error appearing in BOTH leaves
parity at 100% — the suite is structurally incapable of noticing a new
always-on error. Validating the corpus directly with a staged `riddlc`, plus a
positive control on a colliding fixture to prove the search worked, is what
made the claim worth anything. Green suites answer the question they were
built to ask, and this was not it.

## Epic witnessing confirmed by riddl-examples (2026-08-05) — CLOSED

`task/done/2026-08-05-epic-witnessing-confirmed.md`. The residual question from
the yields-in-streamlets fix — does an epic step sending such a command to a sink
actually get witnessed? — is answered yes, with no second defect behind it. dokn
had no epics, which is why neither side could demonstrate it; it has one now.

**They ran a positive control before believing their own green**, misdirecting
the step to the wrong sink and confirming `UseCaseWitnessPass` fired, then
restoring it. That is the same discipline this repo calls canarying, arriving
unprompted from a consumer. It reproduced exactly here, on both the rc.9-54 and
rc.10-2 binaries. **A report that carries its own positive control can be closed
in one pass** — there was nothing left to doubt.

The substantive finding is a distinction worth stating: **it was SUBSTITUTING
`on other` for a named clause that left steps unwitnessed, never the clause
itself.** Ten `on other` clauses remain in dokn as trailing catch-alls beside
named clauses, and witnessing is satisfied. In the pass this is structural but
unstated — `UseCaseWitnessPass:117-123` matches `OnMessageLikeClause` and lets
everything else fall through `case _ => ()`, and `OnOtherClause` carries no `msg`
to resolve, so it contributes nothing to the witness index.

Also: they validated against the staged `~/Code/ossuminc/bin/riddlc`. A consumer
was actively depending on it at the moment I wrote that practice off as retired.

## 2.0.0-rc.10 shipped the entity-intentions work (2026-08-05) — DONE

Tagged at `fc4e54c1b`. 59 commits since rc.9 and the largest RC of the 2.0 line:
entity intentions, the four event-sourcing rules, implicit invariant scope and
`invariant X` as a boolean atom, accessors that see through `include`/`import`,
`requires`/`returns` in contents, `canContain`, and the `tNative` fix.

Two things worth keeping from cutting it.

**A floor can be wrong in the honest direction.** The `/rc` skill says a count
below the minimum is "a skipping bug to find, not a threshold to adjust" — and
rc.10 certified Native at 1339 against a floor of 1624. The rule was right and
still did not apply, because the rule assumes the metric means the same thing
either side of the comparison. Fixing `tNative` changed what "Native tests"
counts. The tell was per-row: the two rows that were ALREADY Native came back
bit-identical (723 and 21) while every row that CHANGED dropped, which is the
signature of JVM-only suites leaving, not of tests being skipped. **When a gate
fails, check whether the gate or its definition moved before doing either.** The
proof standard now lives beside the table so the next drop has to earn it.

**A shipped RC did not end the staged-binary practice — I assumed it had, and
Reid corrected it.** Having spent the day finding three artifacts that outlived
their reasons (the skip list, the `tNative` alias, a stale floor), I reached for
the same shape a fourth time and misapplied it: publishing rc.10 is ADDITIVE, and
says nothing about whether consumers still need locally built assets. They do —
2.0 is a long way from shipping, and the consumer repos work against
`publishLocal` output and `~/Code/ossuminc/bin/riddlc`, which continue unchanged.

The lesson is about the pattern, not the fact. "X outlived its reason" is a
conclusion that needs the same evidence as any other, and the evidence is
someone stating the reason is gone — not my noticing that a superficially similar
thing has changed. **Pattern-matching earned by three real findings is exactly
when it starts firing on the fourth case that does not fit.**

## Two consumer reports, both exactly right (2026-08-06) — DONE

`7e4c25b94`. synapify's option-picker report and riddl-models' external-context
report, closed together. Both reproduced to the number — synapify's 24 options
reconciled once `message_envelope` was accounted for, riddl-models' 139 unused
and 5 repository findings matched exactly.

**A consumer's diagnosis can be right and their proposed FIX still slightly
wrong.** synapify asked for a name-keyed deprecated list. That would have been
wrong: `consistent`/`available`/`transient` are deprecated on an Entity, where
they became intentions, and CURRENT on a Repository, which has none. Deprecation
is per (option, kind). They also asked whether `persistent` should join Entity's
list; it should not — it is registered for Connector and means something else,
and adding it would re-introduce the intention-as-option shape 2.0 removed.
**Implement the diagnosis, not the patch.**

**`parseAndValidate` silently discards parse-time messages**, because it uses
`TopLevelParser.parseInput` rather than `parseInputWithMessages`. A drift-guard
test asserting "every option the registry calls deprecated actually deprecates"
reported `aggregate` as undeprecated while riddlc plainly deprecates it. **The
test helper was the liar, not the compiler.** Any test asserting a deprecation —
they are emitted at PARSE time — must use `parseInputWithMessages`. This is the
same family as the false-green traps in CLAUDE.md, but inverted: a test that
fails for a reason that has nothing to do with the code under test.

**Measuring the corpus has a trap of its own.** Validating riddl-models through
its `.conf` files reports ZERO unused warnings, because every one sets
`show-usage-warnings = false`. I briefly concluded the report was wrong on that
basis. Validate the entry `.riddl` directly.

Every suppression shipped with the case that must STAY reported. A test proving
only that warnings disappeared cannot distinguish a targeted exemption from a
broken check — and this change removed 54 warnings, which is exactly the size of
mistake that looks like success.

## A57: the envelope binding, and a keyword that wanted not to exist (2026-08-06) — DONE

`4208946d2`. `on other as x [: <envelope>]`, with `Riddl.Envelope` and
`option message_envelope` landing just before it.

**The syntax question answered itself once we noticed the keyword carried no
information.** Reid was choosing between `as x: type Riddl.Envelope` and
`as x: message Riddl.Envelope`, uneasy about both — `message` is untrue (an
envelope is not a message) and `type` is correct only because it says nothing.
Testing showed RIDDL already accepts bare AND keyword-led type names in both
positions, so there was no consistency argument either way. **When a decision is
between two spellings of nothing, the thing to question is whether the syntax
belongs there at all.** Making the ascription OPTIONAL dissolved it: no keyword
to choose, and the two validation rules became the payoff for using the explicit
form rather than the price of using the feature.

**Prettify silently dropped the binding, and only a text assertion caught it.**
The rendering went on `OnOtherClause.format`, which reads correctly and is not
what the prettifier consults for a clause header — `openDef` reads
`Declaration.ascription`. So parse → prettify → re-parse produced a valid model
that had quietly lost `as env: Riddl.Envelope`. Two lessons:
1. **`Declaration` exists BECAUSE these two surfaces drift**, and its docstring
   says so. I read that docstring earlier in the same session and still put the
   code in the wrong place. Reading the warning is not the same as applying it.
2. **A round-trip test that asserts only AST survival would have passed**, since
   the AST was fine going in. It has to assert the emitted TEXT. Canaried by
   neutering `ascription`: both binding cases go red, the plain case stays green.

**A revision bump has a fixed tax.** Second `.bast` fixture regeneration in one
day (7→8 after 6→7). It is mechanical, but it fails the suite in `language`
BEFORE the modules whose tests you actually changed, so the `;` chain aborts and
the run looks like a much bigger problem than it is. Regenerate the fixture
first when bumping.

## A56: forwarding a bound message (2026-08-06) — DONE

`897b474bf`. `on p: command Ping is { tell p to entity F }` now works. Three
things worth keeping.

**A misleading error alternation cost a wrong diagnosis — mine.** `tell p` failed
with `Expected one of ("become" | "command" | "event" | "morph" | "query" |
"reply" | "result" | "yield")`, which mixes STATEMENT keywords with MESSAGE-KIND
keywords and so reads like `tell` is banned in that clause. Reid read it that
way; I had read it the other way. The settling move was neither argument but a
three-line experiment: `tell command C.Ping to entity C.F` in the SAME clause
parses with 0 errors, so `tell` is permitted and the operand was the problem.
**Test the alternation, don't read it** — fastparse aggregates the failure set at
the furthest position, which is not the same as "what is allowed here".

**A binding is necessary but not sufficient.** I had argued for `on other as x`
on the grounds that a catch-all cannot dead-letter what it catches. True, but the
blocker was the message-operand GRAMMAR, not the missing name — so the binding I
recommended would have been inert on arrival. Worth remembering when a feature is
justified by a use case: check that the use case actually becomes reachable, not
just less blocked. (A56 landing still does not unblock `on other as x`, for the
same reason one level down: the operand must RESOLVE to a message Type, and an
untyped catch-all binding has none.)

**`-Werror` did the design review.** Widening the operand union turned every
exhaustive match on `msg` into a compile error, which is how the four passes that
interpret an operand — validation, resolution, message-flow, diagrams — each got
an explicit decision instead of an accidental default. Two were substantive:
`operandMessageKind` had to become an `Option`, because a keyword-led ref carries
its kind syntactically while a binding's is only known once resolved and
`AggregateUseCase` has no "unknown" member — returning a wrong kind would have
silently mis-answered the event-sourcing rules. And `DiagramsPass` had to answer
that a bound operand contributes no `Reference` at all. **A widened union is a
question asked at every call site, and -Werror makes sure each one answers.**

Also: the `FORMAT_REVISION` 6→7 bump reddened `IncludeAndImportTest`, because the
checked-in `NotImplemented.bast` was written at revision 6. The fix has a trick
worth reusing — the STAGED binary was still at revision 6, so it could recover
the fixture's source (`unbastify`) for the new build to re-emit. Keep a
last-revision binary around when bumping.

## A skip list outlived its reason (2026-08-05) — DONE

Both corpus migrations (BACKLOG § 1d, § 1d2) turned out to be finished on the
consumers' side, and verifying that turned up a sixth member of this repo's
false-green family.

`RiddlModelsRoundTripTest.pendingModels` held 6 models excluded as "pre-existing
validation errors … redefine built-in type names", with the comment **"Remove
after fixing in riddl-models"**. They had been fixed. The suite reported
`succeeded 183, failed 0` and looked completely green while silently skipping 6
round trips. Emptying the set: **189 succeeded, 0 pending, 0 failed.**

The catch was not cleverness — it was refusing to accept two numbers that
disagreed. An independent sweep of all 189 `.conf` entry points with the staged
binary said zero errors everywhere; the skip list said 6 models had validation
errors. Both could not be true. **A skip carries a claim about the world, and
claims go stale exactly like the task files and NOTEBOOK notes this repo has
already been burned by.** An exclusion whose comment says "remove after X" is a
dated cheque — re-present it periodically rather than reading past it.

Method note, since it cost a re-run: I piped a background `sbt` to `tail -60`,
so the output FILE kept only the last 60 lines and the first two suites'
results were destroyed rather than merely unread. The `Suites: completed`
count is the required check (CLAUDE.md), and it cannot be done on a truncated
log. **Redirect the whole log; filter when reading, never when capturing.**

### The Native gate had never measured Native (same day)

BACKLOG § 1b said "Native in progress"; § HANDOFF said all three platforms green
at "Native 173/1318". Both were written honestly, and they could not both be
answering the same question. They weren't: the handoff number came from the
`tNative` alias, which runs the **JVM** rows for 5 of its 7 modules. It had never
been a Native measurement, so "Native green" had never been established — by
anyone, at any point.

Naming the seven real rows and running them: **176 suites / 1339 tests, zero
failures.** § 3 had predicted "a backlog of Native-only reds the moment they
run", and that was the stated reason the fix needed a plan. There are none. The
alias fix is now mechanical.

Two things worth keeping:

1. **The numbers were nearly identical** — 173/1318 vs 176/1339 — because the
   rows share their test sources; only the executing runtime differs. A wrong
   measurement that lands next to the right one is the hardest kind to notice,
   and averages and totals will not reveal it. Only asking *what ran* does.
2. **Scope was narrower than the § 3 entry implied**, which is why the reds never
   appeared: `cNative` already names all seven rows, so Native code has always
   COMPILED in CI. Only execution fell back to the JVM. The exposure was Native
   *runtime* behaviour in five modules — real, but much smaller than "five
   ungated modules" sounds. **State what a broken gate did still cover**, or the
   next reader over-estimates the risk and defers the cheap fix again.

CI inherits it (`scala.yml:97` runs `c$PLATFORM; t$PLATFORM`), so the Native
matrix leg has been reporting green off JVM rows.

## Two latent bugs surfaced by writing one test (2026-08-04) — DONE

`64b7b4134`. Three ossum.tech reports, filed while they documented the new
invariant semantics. The headline change is small — `invariant X` and
`invariant X with <expr>` are boolean atoms now, so `when not invariant X`
parses. The value was in what it dragged out.

1. **`require invariant X` had been corrupting BAST**, and nothing had ever
   noticed because nothing had ever round-tripped one. `writeRequireStatement`
   used `writePathIdentifier` (emits a leading `NODE_PATH_IDENTIFIER` tag)
   against `readPathIdentifierInline` (consumes none), so every byte after the
   path shifted by one — surfacing as "Invalid string table index" far
   downstream, exactly the hazard CLAUDE.md warns about. **Found by writing the
   round-trip test the NEW feature needed, not by looking for it.** The lesson
   is cheap to reuse: when adding a variant to a serialized construct,
   round-trip the EXISTING variants too — the new test is the first one they
   have ever had.
2. **Removing a guard revealed a bug it was masking.** The duplicate-`initial`
   check was gated on `states.sizeIs <= 1`, so adding a state hid the error.
   Removing that gate exposed a second defect: DEFAULTING counted states
   literally while VALIDATION counted them through includes, so an entity with
   one inline and one included state was defaulted as single-state and then
   validated as multi-state — riddlc auto-marking a handler and then reporting
   the author's own marked handler as the duplicate. **A guard that suppresses
   a check also suppresses evidence of everything downstream of it.**

Also: I reported a defect to Reid using the reporter's first framing ("the model
doc promises something the compiler rejects") and had to correct it after
verifying — A17 was satisfied all along by the BARE spelling, and only the
keyword-qualified one failed. **The reporter rewrote their own file to walk it
back before I acted.** Verify the report, not just the code it points at.

---

## Invariants apply implicitly now (2026-08-04) — DONE

`b33decf25`. Requested by riddl-generator: an invariant did nothing unless some
clause wrote `require invariant X`, so a model could carry a constraint that
read as enforced and was inert. Semantics are in
`ossuminc/RIDDL-Computational-Model.md` §15 (rewritten) — **that is the
authority; don't re-derive from here.**

**The design turn worth remembering.** riddlg's open question was "a pure block
cannot `send`, so a stateless processor cannot gather data", with three options,
all of which were about whether to permit effects. The answer was none of them:
an invariant DECLARES what it reads, and that declaration also decides where it
applies. Nothing became inexpressible and no new machinery appeared beyond one
optional clause.

The reasoning generalizes: **"does not mutate" is the wrong axis for anything
that runs before an effect.** A read-only query satisfies it while breaking the
four properties a precondition actually needs — synchronous, total,
deterministic, terminating. When something must run in a refusal window, check
those four, not purity-as-non-mutation.

**Four things worth keeping:**

1. **A severity has to match its sibling.** I made "invariant on a stateless
   processor" an Error while the analogous "declared but never applied" case was
   a Warning by Reid's ruling. Same defect — an inert invariant — so two
   severities was arbitrary, and the Error rejected an existing fixture
   (`module/mixed-module.riddl:14`) that had been inert under the old rule
   anyway. Downgraded. **When adding a diagnostic, find the nearest existing one
   and match it, or justify why not.**
2. **A test that encodes the old rule fails correctly.** `CompletenessTest`'s
   "warn when invariant is not referenced by require" went red because not
   warning is now right. Rewritten to assert the new rule rather than patched to
   pass — and the case it used to cover is now covered by a second case for the
   `requires <type>` form.
3. **Scala 3 sealed-hierarchy exhaustivity does the finding for you.** Adding
   `InvariantBlock` as a `RiddlValue` broke four unrelated matches in
   SymbolsPass/ResolutionPass/ValidationPass at COMPILE time. Adding it to
   `NonDefinitionValues` fixed all four — the same lever that worked for
   `Requires`/`Returns` the same day. New non-definition AST node ⇒ put it in
   that union.
4. **Two silent prettify defects surfaced only because a fixture had no
   metadata.** `doInvariant` never terminated its line, so a metadata-less
   invariant ran into whatever followed it — output that still re-parsed, which
   is why nobody noticed. Most fixtures carry `with { … }`, whose emission
   supplied the newline by accident. **A fixture without optional decoration is
   a different test than one with it.**

---

## `requires`/`returns` became content, and `???` came with them (2026-08-04) — DONE

The follow-on to the saga-comments fix below. Fixing that one made a comment a
legal saga definition, which promptly exposed a second defect it had been
hiding: a comment written ABOVE `requires` consumed the definitions slot and
`requires` was then rejected. The body grammar was
`[func_input] [func_output] {definitions}` — a fixed PREFIX — so the working
rule was "`requires`/`returns` must be the very first tokens of the body",
exactly where a reader wants a comment explaining them. `Function` had the
identical prefix and the identical defect.

`requires`/`returns` are now ORDINARY CONTENT: new `Requires`/`Returns` AST
nodes in `OccursInSaga`, `OccursInFunction` and `NonDefinitionValues`.
`Function.input`/`output` and `Saga.input`/`output` survive as derived
accessors returning `Option[TypeRef | Aggregation]` — **the exact type the
constructor fields had**, which is why the blast radius was 5 test sites rather
than the ~56 first estimated, and why riddlg and riddl-gen needed no change.

**Five things worth keeping:**

1. **Fixing a prefix means fixing the WHOLE prefix.** Moving the two clauses
   into the content list broke `requires X returns Y ???` — a shape in the
   corpus (`everything_full.riddl:72`) — because every container spells its
   body `undefined | definitions`, so `???` was an alternative to the whole
   list rather than a member of it. It worked before only because the clauses
   sat OUTSIDE that choice. `???` is now a content item too. The lesson: when
   you dissolve a fixed prefix, enumerate everything the prefix's position was
   silently permitting, or you narrow the language while thinking you widened
   it. 23 language tests went red before this was found.
2. **A grammar closure cannot bound cardinality — say so somewhere.** `rep`
   accepts `requires A requires B` happily. `checkRequiresReturnsCardinality`
   in `ParsingContext` enforces zero-or-one AFTER the parse, which is the only
   reason `Function.input`'s `headOption` is a complete answer rather than a
   silent truncation. Same shape as `checkForDuplicateIncludes` beside it.
3. **`Definition.equals` SKIPS Contents fields, so moving a field into
   contents removes it from equality.** `ContextValidationTest` asserted a
   whole `Function` with `mustBe`; after the move that assertion could no
   longer see `requires`/`returns` at all and would have passed with them
   missing. The clauses needed assertions of their own. **Any field-to-contents
   move has this consequence — check every `mustBe` on the container.**
4. **The printer got SIMPLER, not smarter.** `PrettifyVisitor` stopped emitting
   from the `input`/`output` accessors in `openFunction`/`openSaga` and now
   emits `doRequires`/`doReturns` as the contents are walked. Emitting from the
   accessors would have reimposed the very ordering the move removed — the
   clause first, the author's comment after it. **Order is now a property of
   the AST rather than of the printer.**
5. **A named field cannot carry a position, so JSON needed a content kind.**
   `FunctionDto.input` round-trips the VALUE fine and always did; it cannot say
   the comment came first. `$kind: "requires"`/`"returns"` entries now travel in
   the ordered `contents` array, the bucketed fields are read only when a
   document has no ordered contents (reading both would double the clause), and
   both are still written. All four reflective surfaces — parse, prettify, BAST
   (`FORMAT_REVISION` 3 → 4), JSON — now carry position, each with a test that
   asserts ORDER and not merely presence.

**Left open on purpose:** the node stays `TypeRef | Aggregation`. Reid's
`Option[TypeRef]` is the wanted end state but it means dropping the deprecated
inline aggregation, which is a language change with corpus fallout — filed in
BACKLOG § 2 with the verified cost.

---

## A saga body rejected comments because one rule skipped a shared alternative (2026-08-03) — DONE

`867ab0333`. Reported by riddl-generator: `//` between two saga steps was a
parse error whose message never mentioned comments.

`sagaDefinitions` was the only container in the VitalDefinition family that did
not lead with `vitalDefinitionContents` — Domain, Function, Epic and Processor
all do. Since `OccursInSaga` is `OccursInVitalDefinition | SagaStep`, `Type` and
`Comment` were ALWAYS legal saga contents; only the parse rule disagreed with
the AST it fed.

**Three things worth keeping:**

1. **Fix the omission, not the symptom.** Adding `comment` alone would have
   fixed the report and left `type` broken in the same place, for the same
   reason — which the reporter had not noticed. Restoring the shared
   alternative fixed both and made saga consistent with its siblings. When a
   rule is the odd one out, ask what ELSE the others get that it doesn't.
2. **A fixture in a skipped file is not coverage.** The obvious home for the
   new syntax, `language/input/full/context.riddl`, is **skipped by the TatSu
   validator as an include fragment** — the fixture would have looked like CI
   coverage and been none. Checking the validator's own output for a `✓` on the
   file is the only way to know. Same family as the false-green traps: verify
   the gate ran, don't assume it.
3. **A `rep(2)` that looks like a semantic guard usually isn't.** It reads as
   "a saga needs two steps", but the real rule is ValidationPass:2585, which
   counts `sagaSteps` and emits a proper Error with a suggestion. Relaxing the
   parser therefore lost no rule and UPGRADED the diagnostic — a parse failure
   at the wrong token became a validation message that says what is wrong.
   Worth checking for this shape before assuming a parser cardinality is
   load-bearing.

---

## Resolution was never slow; a case class hashed the whole file (2026-08-03) — DONE

Reported by synapify as "ResolutionPass takes 4.3s, likely algorithmic". It is
not algorithmic and it is not ResolutionPass. On the JVM, all ten analysis
passes together cost **0.78x one parse**, and Resolution resolves 3730
references in **44.6ms** — 12µs each.

The real defect: `StringParserInput` is a case class whose first field is
`data: String`, the entire text of a source file. `At` holds a
`RiddlParserInput`; `Identifier` and `Definition` hold an `At`;
`ReferenceMap.Key` holds a `Definition`. So the compiler-generated hashCode
chain meant **every refMap add and lookup hashed a whole source file** — twice
per `Definition.hashCode`, once via `id` and once via `loc`.

The JVM and Native never noticed, because both memoise `String.hashCode` into
the string object. A JS string cannot carry that field. Measured on a 139KB
source: 14ns (JVM), 1ns (Native), **181,187ns (Scala.js)**. Fix: memoise the
hash on the parser input — one field per FILE, nothing per node. Scala.js
`Definition.hashCode` went **384,016ns → 217ns (1,770x)**, now at parity with
the JVM.

**Four things worth keeping:**

1. **A platform asymmetry can masquerade as an algorithm.** Every hypothesis in
   the report — and ours — was about complexity: scope walking, candidate
   rebuilding, linear `Contents` scans. The tell was in the *ratios*, not the
   totals: parse cost 3.2x on Scala.js while Resolution cost 97x. Ordinary
   overhead is uniform; when one number is 30x the others on the same runtime,
   the runtime is doing something different, not the algorithm. **Get the
   cross-platform ratio before profiling anything.**
2. **We tested our favourite hypothesis and it was wrong.** ClassTag dispatch
   was the prime suspect for both of us. Measured, Scala.js runs it **5x faster
   than the JVM** (~21ns), which accounts for ~0.2% of the time. Had we "fixed"
   it we would have shipped a plausible refactor across 13 methods and moved
   nothing. The microbenchmark cost an hour and refuted it in one run.
3. **Case-class hashCode is a hazard on any node holding bulk data.** Nothing
   here was written badly; `At`, `Identifier` and `Key` are all ordinary case
   classes. The cost came from a field three layers away that nobody hashing a
   `Key` was thinking about. When a case class transitively reaches a `String`
   that is a *document*, its generated hashCode/equals are O(document).
4. **`sbt -batch` with several command arguments runs only the FIRST.** Seven
   `'module/testOnly *'` args ran `utils` alone, printed "All tests passed",
   and exited 0. Both the exit code and the word "passed" were honest about the
   14% that ran. Use one `;`-separated argument and **count the `Suites:
   completed` lines against the modules you asked for.** Recorded in CLAUDE.md
   beside the other false-green traps — this is the fifth known member of that
   family, which is itself the point: assume a green suite is evidence only when
   you know how much of it ran.

Verified after the change: JVM 248 suites / 2007 tests, JS 60 / 674, Native
149 / 1058 — **0 failures on all three**. `ParserInputHashingTest` (9 cases)
pins the contract, including `At.isEmpty`'s `source == RiddlParserInput.empty`
identity. Three benchmarks committed, none asserting a timing threshold (see
BACKLOG § 3 on why `BASTPerformanceBenchmark`'s ratio assert is a bad pattern).

**Still owed to synapify:** a before/after of *their* table. riddl cannot
produce it — the Scala.js `PlatformContext` is `DOMPlatformContext`, which loads
by `fetch`, so no riddl test reads reactive-bbq off disk under Node. They own
`AnalysisPassCostTest` and should re-run it against the new build. Said so
explicitly in the task file rather than leaving the criterion quietly unmet.

**Second filing.** The same problem was reported 2026-03-13 at 3.4s and moved to
`task/done/` with an empty Results section and no work done — no performance
commit exists in that file's history. Corrected in place. Its sibling from the
same minute had the identical empty-Results shape but a real fix behind it
(`367669016`), so the placeholder was never the tell; only `git log` was. **A
file's presence in `done/` is not evidence that its work happened.**

---

## `format` renders the declaration (2026-08-03) — DONE

`9c922e42e`. Reported by riddl-generator, whose six line citations were all
exact; task file in `task/done/` with the transcript.

2.0 put meaning into prefixes and suffixes — entity intentions, a context's
intention, `initial`, `yields`, `as <shape>` — and `format` rendered none of it,
so `aggregate consistent event-sourced entity Order` formatted as
`entity Order`. Streamlet was wrong the other way: it emitted `source Ingest`,
the spelling 2.0 deprecated, from the DERIVED shape, while the prettifier
normalizes the same definition to `processor Ingest as source`.

**The fix was never really about `format`.** `RiddlFileEmitter.openDef` already
held the correct composition; the defect was that a consumer could not reach it,
so riddlg had started re-deriving it. `AST.Declaration` is now the one
implementation and `format`, `openDef` and `openState` all use it.

**Two things worth keeping:**

1. **Prove sharing, do not assert it.** Neutralizing the shared prefix reddens
   the four `format` cases AND the prettify round-trip tests. That is what makes
   "one implementation" a fact rather than an intention — if they drift, a test
   fails first.
2. **This is the third instance of the same shape**, after the include walks and
   the ungated checks: knowledge that exists in one place, is unreachable from
   another, and gets re-derived by whoever needs it next. **When a consumer
   reports duplicating our logic, the fix is to expose ours — not to add a
   second copy on our side of the line.**

---

## BAST positions: recoverable, and honest when they are not (2026-08-03) — DONE

`bd9e0a705`. Reported by synapify, which cannot move AnalysisPass off the
Electron main thread without a redundant re-parse.

**The report's diagnosis was wrong in a way worth remembering.** It said BAST
cannot carry positions. BAST carries them fine: `BASTWriter.writeLocation`
delta-encodes the REAL offset, and `DeepASTComparison` already verified offsets
round-trip exactly. `At` has always derived line/col lazily from
`source.lineOf(offset)` — it stores only `(source, offset, endOffset)`.

The defect was that the reader attached a `BASTParserInput` whose line index is
SYNTHETIC (line L starts at L×10000) and then fed it real offsets, so anything
under offset 10000 landed on line 1 at col = offset. **Two halves of one
subsystem with contradictory contracts.**

Fix cost nothing structural: `positionsKnown` on `RiddlParserInput` (default
true, so `At.empty` still reports 1:1 and no golden moved), an optional
`sources` map on `BASTReader.read`, and deleting a cast to `BASTParserInput`
that made supplying a real source a ClassCastException. No format change, no
FORMAT_REVISION bump, no size increase, no filesystem access — which matters
because BAST is read on JS and Native.

**Three things worth keeping:**

1. **Ask what the code already does before believing it cannot.** The lazy
   derivation the "fix" would supposedly need was already `At`'s design; the
   offsets were already preserved. Only the source attachment was wrong.
2. **A confident wrong answer is worse than an absent one.** The old behaviour
   returned line 1 with a plausible column — good enough for a Problems pane to
   point at. Positions now report 0, which is unrepresentable as a 1-based
   position, so a consumer can detect it.
3. **`DeepASTComparison` had carried a comment explaining the wrong line/col as
   a difference in line breaks.** A defect notice written down and lived with,
   which is why this survived. Corrected.

---

## Two over-broad checks narrowed (2026-08-03) — DONE

`0dba8d26b` (Q2) and `30979985d` (Q3). Reported by riddl-examples and
riddl-models against the staged build; task files carry the transcripts.

- **Q2** — `checkYieldConformance` demanded a `yield` from clauses that cannot
  contain one. `StatementParser` grants `yieldStatement` to ProcessorKind
  Entity/Context/Repository only, so a sink handling a `yields`-declaring
  command had no satisfiable spelling: `on other` dodged the yield rule but left
  the epic step unwitnessed under A36. **Our regression** — `0054a8433` scoped
  the exemption to clauses that REFUSE, and a clause that FORWARDS is a third
  case nobody thought of. Now enforced only where a yield is legal.
- **Q3** — Completeness 4b demanded dispatch-to-entity from split/merge/flow,
  whose job is routing between ports. Restricted to `Sink` via `effectiveShape`.

**The pattern, stated once because it has now happened three times.** Both of
these, and the four `@JSExport` helpers before them, were not under-tested —
they were **ungated**. Completeness 4b had zero tests of any kind and could not
even be reached in the corpus, because its outer guard keys off `c.entities` and
every entity there lives in an include. Making the accessors include-transparent
woke it up, and the first thing it did was demand something no model could
provide. **When a check's guard depends on an accessor, ask what happens the day
that accessor starts returning things.**

First fully-clean matrix of the session afterwards: 19 rows, 3,946 tests, zero
failures, no external reds — riddl-examples landed its dokn migration and both
riddl-models suites pass.

---

## Accessors see through `include` (2026-08-03) — DONE

`2a8b87a6b`, `c98e33e5e`, `dbd350020`, `0d83caabc`. Reported by riddl-generator
against the staged build; task file in `task/done/` with the full transcript.

`context.entities` was empty whenever the entity was written in an included
file, while `context.repositories` in the same context answered normally — the
model giving different answers based only on which file the author typed into.
riddlg generated 582 files for reactive-bbq with no entity class among them, and
nothing failed. The 35 named accessors now use `filterThroughIncludes`.

**Four things worth keeping:**

0. **Provenance is riddl's business, not the reader's.** The first cut
   descended `Include` but not `BASTImport`, reasoning that importing a
   COMPILED artifact is a different claim from textual composition. Reid
   overruled it: a client asking what is in a processor wants the full list and
   does not care how each member got there. Both wrappers are descended; the
   method is `filterThroughWrappers`, not `...Includes`. Structure and
   resolution are untouched — see § 3 for the one loose end that leaves.
1. **The gap was never "untested", it was UNGATED.** riddl validates by
   traversing (`HierarchyPass`, `Finder`), so every internal test took that
   path; the accessor path that consumers use had no test at all. Four of the
   eight `@JSExport` consumer helpers — `getEntities` among them — had zero test
   references. Fixing transparency without adding
   `ConsumerReadsIncludedDefinitionsTest` would have reset the clock on the same
   bug. **Add to that suite whenever an accessor is added.**
2. **The compiler contained the same workaround it was being asked about.**
   Seven helpers in AST.scala were written `x.foo ++ x.includes.flatMap(...)`,
   which is exactly the "every consumer reimplements the recursion" complaint —
   and they double counted the moment the accessors worked. Collapsed.
3. **`recursiveFindByType` was riddlg's plan B, and still the wrong fix — but
   not for the reason first recorded.** The note said it would return nested
   sub-contexts' entities; contexts cannot nest, so that shape is a syntax error
   rather than a rare case, and for `Entity` under a `Context` the two agree
   exactly. riddlg filed the correction. The accurate rule is that the two
   ANSWER DIFFERENT QUESTIONS, and they diverge under a Domain (domains nest)
   and for `Type` under a Context (recursive find reaches types inside
   entities). Worth remembering as a habit: a caution that sounds right is still
   worth checking against the grammar before it goes in writing.
4. **No fixture and no `.check` golden moved.** Validation results are
   unchanged, which confirms the defect was purely on the consumer surface.
5. **Verified on all 19 rows, not the 6 that had changed.** After the import
   reversal only `language` and `passes` had been re-run, and the summary still
   quoted pre-reversal numbers for the rest. Reid caught it. Full matrix:
   3,934 tests, one pre-existing external failure. `commands` and `riddlc` were
   the ones that mattered — they carry the external-corpus suites, where a
   double count would show up as a diff in real model output rather than a unit
   assertion. **Re-run everything after a late reversal, not just the modules
   whose tests you expect to move.**

---

## Refusing a command discharges its `yields` contract (2026-08-02) — DONE

`0054a8433`. Reported by riddl-models against the staged build; task file in
`task/done/yields-conformance-forces-refusing-clauses-to-yield.md` with the full
verification transcript.

`checkYieldConformance` required EVERY `on command C` clause to yield C's
declared event. With R1 making `yields` mandatory on any command an
event-sourced entity handles, the ordinary shape — a command accepted in one
state and refused in the others — became unexpressible: each refusing clause had
to yield the success event it had just declined to produce. Fixed by exempting a
clause that refuses, using the `ErrorStatement || RequireStatement` predicate the
sibling check at :509 already had.

**Three things worth keeping:**

1. **The task file proposed keying on `error` alone; that would have fixed half
   of it.** A `require`-based refusal produced the identical error. Testing the
   proposal rather than implementing it is what caught this.
2. **The corpus could not reproduce it.** reactive-bbq reports zero such errors
   in its committed state, because riddl-models backed the conversion out when it
   failed. The defect only appears when you construct the condition. A corpus run
   is not a substitute for building the case.
3. **R1 and the wrong-type branch were deliberately left alone.** Refusing
   exempts a clause from having to yield, not from yielding correctly.

Residual gap (conditional refusals) is recorded in § 3 — it is the honest limit
of the predicate, not an oversight.

---

## Four defect fixes from riddl-models + riddl-vscode (2026-08-01) — DONE

**`@JSExport` on an overridden `toString` breaks ToPrimitive in Scala.js.** The
biggest catch of the batch. Interpolation compiles to JS `+`, so `s"...$loc..."`
threw `TypeError: Cannot convert object to primitive value` — crashing the whole
validation run on JS while the JVM passed. `At` and `URL` both carried it. JS
callers get `toString` from the prototype anyway, so the export bought nothing.
Guarded by `ToPrimitiveCoercionTest`, which is JS-only **by necessity**: on the
JVM every assertion in it passes regardless of the annotation, which is precisely
why it survived. Grep before adding `@JSExport` near any `toString`.

**A swallowed diagnostic hides the bug under it.** The JS `ExceptionUtils` shim
returned `Array.empty` ("can't get stack traces in JS"), so the pass runner's
catch-all rendered EVERY JS exception as a Severe with no text — a blank squiggle
on line 1 in an IDE. riddl-vscode read it as "a rule that lost its message" and
filtered it defensively. Fixing the shim FIRST is what made the crash
diagnosable; the stack trace naming `checkNonEmpty` appeared immediately. A
reporting path that can silently produce nothing is worse than no reporting path.

**The npm template overrides the sbt-ossuminc generator.** `riddlLib/js/
package.json.template` is what actually ships — proved by the published
description and `reactive-systems` keyword, which `build.sbt` does not declare.
The generator was already fixed upstream and it changed nothing. **Check which
source actually produced the artifact before fixing the one that looks right.**

**Two checks must agree on what a domain is.** rc.8's error-sink checks did not:
missing ran per-domain, uniqueness used a recursive find crossing nested `Domain`
boundaries, so a nested model could satisfy NEITHER. Fix: both use the sinks a
domain declares itself (descend through Include, stop at Domain), an ancestor's
sink satisfies a subdomain (nearest wins), and a domain with no processors of its
own is not asked at all.

**Incremental caching must cover everything it claims to validate.**
`IncrementalValidator` fingerprinted Contexts only, so a domain-level edit
changed nothing it could see and it served stale results — dropping real errors
while the user typed. Correctness beat speed: a domain-level change now forces a
full validation.

---

## Infix alternation `A | B` accepted (2026-08-01) — DONE

Accepted as a second spelling of `one of { A or B }`; **not** canonical.
PrettifyPass still emits the words, so a bar-written document normalises on its
next round trip. Both spellings produce the identical `Alternation`, which is
what makes accepting a second spelling safe at all.

**Why `infixAlternation` is tried FIRST, before `predefinedTypes`:** so the two
spellings behave the same. `one of { String or Integer }` today gives
`Path 'String' was not resolved` (alternation operands are type REFERENCES, not
predefined types — verified before choosing). Placing the infix rule later would
have made `String | Integer` a *parse* error pointing at the bar instead. It is
safe to try first only because it REQUIRES at least one `|` — lose that guard
and every lone type reference becomes a one-element Alternation.

`|` is otherwise used only for `described as` margin lines, a different context.

**Reflectivity checklist for a syntax addition** (all done here): parser + EBNF +
regenerated GBNF + a corpus fixture under `language/input/` so the CI TatSu
validator exercises it + a prettify round-trip test + all three rows.
Corpora re-validated: riddl-models 189/189, riddl-examples 9/9.

---

## GeneratorError + error-sink completed (2026-08-01) — DONE

`Operations` is **withdrawn** from the standard module; `HardError` is renamed
**`GeneratorError`** (the name should state the SOURCE — a generator produces
one). riddlg's bug report is the argument for the withdrawal in miniature: the
predefined sink's self-qualified path failed to resolve and the error pointed at
a file the modeller cannot see or edit. The standard library owes a generator the
SHAPE of a notification and a way to NAME its destination — nothing more.

**The unused warning is the design, not a defect.** `GeneratorError` has no
predefined receiver, so validating the module alone reports `Record
'GeneratorError' is unused`. Rather than tolerate it, `PredefinedTerminatorsTest`
asserts it EXACTLY and asserts the converse — a model declaring an error-sink
inlet of that type clears it. Canaried both ways.

**A missing error-sink is a MISSING warning, not COMPLETENESS.** `isIgnorable`
is `severity < CompletenessWarning`, so Completeness asserts structural
incompleteness (unfed inlets, unreachable sinks). "Has not said where hard errors
go" is the "has no author" family. Emitting it as Completeness turned **thirteen
unrelated suites red** for models that were otherwise fine — that was the tell,
and it is the reason to reach for `addMissing` here.

**An error-sink inlet must accept `GeneratorError`** — directly, or via an
alternation including it so a model can route its own error messages to the same
inlet. Otherwise Error: a generator has nothing it can send there.

**Vacuous-test trap, twice in one sitting.** Every error-sink case asserts an
ABSENCE, which a non-parsing fixture satisfies trivially. A `printf "%s"` with a
literal `\n`, then a bad alternation (`A | B` instead of `one of { A or B }`),
each produced green cases proving nothing; only the case asserting PRESENCE gave
it away. `ErrorSinkTest.messagesFor` now FAILS on "Expected one of" rather than
reporting on a model that never parsed. Worth copying wherever absence is
asserted.

**Repo-wide:** `sbt scalafmtCheck` is RED on HEAD independent of this work — 7
committed files reformat (6 in `commands`). Left alone rather than swept into
these commits.

---

## A10 fully registered + duration positivity (2026-07-31) — DONE

`retry` (Saga added), `undo-retry`, `failure-message` — names chosen by
riddl-generator after we held them rather than settling by implementing
first. **That was worth doing**: riddlg pushed back correctly on my
objection to reusing `retry`, pointing out `timeout` already has exactly
that one-concept-two-scopes shape.

`retry`'s optional second argument is a backoff duration, so the temporal
check is now INDEX-AWARE (`temporalArgIndex`) rather than "first argument
of a temporal option". `retry("3")` must stay valid; a bare `"3"` would be
flagged vague if the wrong index were read.

**Non-positive durations are errors**, with their OWN message — vague and
non-positive need different fixes ("state a unit" vs "state a magnitude").
`PT0S` needed care: the ISO-8601 path matches SHAPE only and never parses
a value, so positivity there is a non-zero-digit test. The shape carries
no sign, so zero is the only non-positive ISO case.

**Precedence is a generator contract riddlc does NOT enforce** — a step's
own `retry`/`timeout` wins, the saga's applies to steps without one, else
the A10 default. Recorded in the registry comment because writing it down
is the only way two generators agree.

---

## `activate` accepted as an acquisition verb (2026-07-31) — DONE

`button Checkout activate Confirmation` used to be a bare parse error.
`activate` is now accepted; the other fourteen aliases stay third-person
and their imperatives stay REJECTED, asserted by test so a widening must
be deliberate.

**A grammar change touches THREE artifacts, not two.** The parser
whitelist, `ebnf-grammar.ebnf`, AND `riddl-grammar.gbnf` — the GBNF is
generated by `ebnf_to_gbnf.py` and CI fails on drift. The incoming task
listed only the first two. `--check` was already failing before this
change, so the committed GBNF had drifted independently.

**Scope had a hidden coupling.** Pairing the whole vocabulary would have
required `select`/`choose`/`pick` in `UIVerbs.selectionVerbs` too — it
gates the choice-type check at `ValidationPass:2652` — or those inputs
would parse while silently skipping it. The narrow change avoids it.

---

## Option registry work for riddl-generator (2026-07-31) — DONE

Three riddlg tasks, shipping in 2.0.0-rc.5.

- **`available`/`consistent` widened to Repository.** Computational model
  §5.6: a Repository is a Processor, so its WRITE side is single-writer by
  default and `available` hands arbitration to the storage engine. The
  registry already had this exact shape for `transient`.
- **`timeout` widened to Saga** (Tools-To-Do-List Part A item 10). It is
  the third terminal condition of a `parallel` saga (§9.8) and had no
  expression in the language.
- **`compensate` DEREGISTERED** on Reid's ruling. The decisive fact is in
  the parser: `SagaParser.sagaStep` requires `reverted by`
  UNCONDITIONALLY, so a saga without compensation cannot be written and
  the option distinguished nothing. Its A10 citation was also wrong — A10
  asks for timeout/step-retries/undo-retries/error-string, not this. The
  registry now carries a DO-NOT-RE-REGISTER note with that history.

**Vague durations are now an ERROR.** A `timeout`/`delay` argument that
does not state a unit fails validation: `"30"` is ambiguous between
seconds and milliseconds. Uses `scala.concurrent.duration.Duration`;
ISO-8601 is matched BY SHAPE because `java.time.Duration.parse` is
JVM-only and would make riddlc behave differently per platform.

### The recurring hazard, stated once

Three separate tests this session asserted the ABSENCE of a warning and
passed for the wrong reason:

1. an invalid fixture that never parsed, so there were no messages at all;
2. `showStyleWarnings` being off — the accumulator DROPS StyleWarnings,
   and `pc.options` is global state other suites mutate, so a case passed
   alone and failed in the full suite;
3. `withClue` interpolating Messages — `Message.toString` is unsafe under
   Scala.js and `withClue` evaluates eagerly, so every case failed on the
   JS row while reporting, not asserting.

**A test asserting absence proves nothing until it has been canaried.**
Revert the change and confirm it goes red.

**`RecognizedOptionsTest` has a no-shrink ratchet** over the former
hand-written option lists. Move its baseline only with a reason recorded
inline — never to make a red run green.

---

## Portlet options were never validated — FIXED

`checkDefinition` validated metadata only under `case vd: VitalDefinition[?]`.
`Inlet`/`Outlet` are `Leaf`s, so their options were never checked:
`option zzznotanoption("x")` on an outlet was accepted in SILENCE while the
same typo on a vital definition drew a StyleWarning. Found by
riddl-generator while asking for the `lowering` option — which was the
smaller half of their report.

**Two constraints, both found by breaking tests, not by reasoning:**

1. **Narrow the arm to `Portlet`.** A broad `WithMetaData` arm
   double-validates every definition whose validator calls BOTH
   `checkDefinition` and `checkMetadata` — Constant, Adaptor, Schema and
   others do. Symptom: doubled FigmaRef message counts.
2. **Contents only.** `checkMetadataContents` is split out of
   `checkMetadata` so portlets are checked without inheriting "metadata
   should not be empty" or "should have a description". Routing the
   description check through the shared path made **14 suites** demand a
   description on every type and field.

**Test fixture gotchas** hit while writing the test, worth knowing:
`AbstractValidatingTest` is a FIXTURE spec, so every case body takes
`(td: TestData)` and builds inputs as `RiddlParserInput(src, td)`; an
`outlet` takes a MESSAGE type ref (`command X`), not `type X`; and the bare
shape keyword `flow F is {…}` **no longer parses** — it is
`processor F as flow is {…}`.

---

## CI: two build-gate defects found cutting rc.3 — BOTH FIXED

**1. `target/out` must NOT be cached.** Restoring sbt 2 build outputs into
a fresh checkout leaves sbt believing the meta-build is already built, so
`project/Dependencies.scala` never contributes its symbols and `build.sbt`
collapses with dozens of `Not found: V` / `Not found: Dep` plus an
`Append` ambiguity on a line nobody edited. The cascade points everywhere
except the cause.

**A cache written by a GREEN run is just as poisonous as a stale one** —
#2192 and #2193 restored #2191's successful cache and failed on
markdown-only commits. Dropping `restore-keys` first did NOT fix it; that
only made one run cold by accident. The rule that actually held: every
cold build passed, every cache-restoring build failed. Step removed
entirely; Coursier/ivy2 dependency caches are separate and fine.

**2. `set every Compile/doc/sources := Seq.empty` blanks
`Compile/sources` ITSELF.** `set every` does not respect the `Compile /
doc` scope prefix — sbt logs "Defining Compile / sources, Global /
sources and 75 others". Harmless until the empty-module guard landed,
then every module looked source-less and the guard fired on all of them.
Replaced with `With.NoDocs` on the JVM rows, where the intent is scoped
correctly and survives a workflow rewrite.

**Diagnostic that settles this class of failure fast:** compare
suite/test COUNTS between runs, and check whether the run was cold or
restored a cache. An exit code proves nothing; #2196 is trustworthy
because its counts rose by exactly the 19 tests added.

---

## SysLoggerTest flake — FIXED (was an intermittent CI red)

`SysLoggerTest` captures what SysLogger wrote to stdout by swapping the
**global** `System.out`. sbt runs a module's suites in PARALLEL, so any
other utils suite printing during that window landed in the capture and
the assertion failed on "random garbage". It failed CI run 30552046714
(JVM only; JS and Native fine) on a commit that touched nothing related.

Fixed with `utils / Test / parallelExecution := false`. **The suite's own
`SequentialNestedSuiteExecution` does NOT cover this** — it orders NESTED
suites and says nothing about siblings running concurrently. That
mislead is why the FIXME sat unresolved.

Three tests (severe/warning/info) had been **commented out** rather than
fixed. They are restored and pass: utils went 137 → 140 tests, clean
5 runs in a row.

Cost is negligible — utils' suites take seconds — and it beats a release
gate that fails at random.

**If another module ever grows a stdout-capturing test, it needs the same
setting.** The pattern to avoid is asserting exclusive ownership of a
global stream inside a parallel runner.

---

## sbt-riddl tasks were cached no-ops under sbt 2 — DONE

Reported by the riddl-models session. **`sbt riddlcValidate` reported
success on a corpus it never re-validated.** All six `taskKey[Unit]`
riddlc tasks return Unit, which sbt 2 caches, while the `.riddl`/`.conf`
files they read are discovered INSIDE the body by `resolveConfs` and so
are never declared inputs. Editing sources, `clean`, and deleting
`target/` all failed to invalidate it. First run in a fresh checkout
executes, so CI looked fine while local development silently stopped
checking.

Fixed by wrapping all six in `Def.uncached` — the same lever the compile
hook already used. `riddlcDownload`/`riddlcBinary` were already opted
out.

**The scripted test now runs, breaks the model, and runs again**,
asserting the second run FAILS. The old test invoked each task exactly
once, and a first invocation always executes — which is precisely why
this shipped. Proven by reverting the fix: scripted then fails at
`-> riddlcValidate` with "Command succeeded but failure was expected".

**`bastify` tolerates semantic errors** — given an unresolved path it
parses and writes a BAST with exit 0. Only a model that cannot PARSE
makes it fail, so the bastify assertion needs its own
`changes/unparseable.riddl`. Do not reuse the validate fixture.

Scripted fixtures are checked against riddlc **1.23.0** as well as the
local build, because `sbt-test/.../build.sbt` pins `riddlcVersion`
1.13.0 rather than using the local one.

Two claims in the task file were wrong and are corrected in its Results:
`riddlcDownload`/`riddlcBinary` were never cached, and riddl's own CI
does NOT use `riddlcValidate` (scala.yml runs `sbt-riddl/scripted`; the
corpus is checked by `RiddlModelsRoundTripTest`).

---

## Third-party license notices in `riddlc info` — DONE

`riddlc info` now ends with a one-line-per-project attribution block,
grouped by license, and every distribution ships
`THIRD-PARTY-NOTICES.txt` with the full texts. Both the file name and
`https://ossum.tech/riddl/licenses/` are printed.

**The list is a hand-maintained CONSTANT**
(`utils/.../ThirdPartyNotices.scala`) — not generated, and not read from
a file. Only the JVM build has a filesystem; the Native binary has no
resources at all, and the same text must render in Scala.js. So **it
goes stale silently when dependencies change.** `ThirdPartyNoticesTest`
pins the shape (80 columns, every license group, both links) but CANNOT
know a dependency was added. Regenerate it whenever deps change:

- JVM truth is the staged `riddlc/universal/stage/lib` (what ships).
- JS/Native from `<mod>/Runtime/fullClasspath`.
- Licenses from each artifact's POM in `~/Library/Caches/Coursier`,
  walking to the parent POM when the child declares none.
- **Do NOT take the copyright holder from `<developer>`** — that is the
  first committer, not the holder. For Apache projects it is the ASF;
  read `META-INF/NOTICE` from the jar, which Apache-2.0 §4(d) requires
  be reproduced anyway.

Notices print LAST, below the JVM/OS lines. That ordering lives in
`InfoCommand`, not `InfoFormatter`, so `InfoFormatter.formatBuildInfo`
(no notices) exists alongside `formatInfo` (with them). Calling
`formatInfo` from `InfoCommand` buries the block mid-output — the bug
`InfoCommandTest` now guards.

### Two build defects this surfaced — BOTH NOW FIXED

Writing the notices exposed two dependencies that were shipping by
accident. Both are fixed, and the notices lost a line each.

1. **ScalaTest/Scalactic shipped on JS and Native.**
   `Dep.scalatest_nojvm`/`scalactic_nojvm` were added WITHOUT `% Test`
   at three sites — utils JS, utils Native, language Native — so a test
   framework was a runtime dependency of the native binary and the JS
   bundle (16 artifacts each). Now `% Test`. **`testkit` was left
   alone on purpose**: its MAIN sources use ScalaTest because it is a
   test kit exporting test helpers, so compile scope there is correct,
   and blanket-scoping it would break the published `riddl-testkit`.
   Nothing in the riddlc/riddl-lib chain depends on testkit, so it does
   not leak into the distributions.
2. **logback-core (EPL-1.0 / LGPL-2.1) arrived via
   `airframe-json` → `airframe-log`.** Excluded in
   `Dependencies.scala`. JVM distribution went 21 → 20 jars.
   **This was treated as a runtime risk, not a no-op**: airframe-log
   can bind logback reflectively, so a missing class would surface only
   at runtime. Verified with `riddlc info` AND a real
   `riddlc validate` — airframe-log falls back to `java.util.logging`.

**riddl now carries NO copyleft dependency.** Everything is Apache-2.0,
MIT or BSD-3-Clause. `ThirdPartyNoticesTest` asserts the ABSENCE
(`must not include "logback"` / `"LGPL"` / `"ScalaTest"`) so a
regression fails the build rather than quietly re-adding an obligation.

Certified from clean, tri-platform: tJVM 541, tJS 213, tNative 396,
zero failures.

Filed `ossum.tech/task/publish-riddl-license-page.md` for the web page.
**The URL is compiled into riddlc**, so it cannot be silently
redirected.

---

## Corpus-blocking bug reports from riddl-models/riddl-examples — DONE

Four reports arrived while release/2 was being finished, all filed against the
staged `riddlc`. All four fixed, each with a regression test; task files carry
their results in `task/done/`.

| Report | Cause | Fix |
|---|---|---|
| prettify drops `updates repository` | `RepositoryRef` is a Reference in contents, so the visitor never saw it | `ef4945d14` |
| schema types "unused" | `Schema` refs live in FIELDS, not contents; no resolver case at all | `508403875` |
| `show … to …` unvalidatable | parser hardcoded the empty relationship the validator rejects | `46384b252` |
| MessageFlowPass false positives | nested `send`/`tell` resolved only their `Constructor` operand | `9042c17d6` |

**One pattern explains three of them.** Anything in `contents` that is not a
`Definition` — a Reference, a `Comment` — is invisible to the generic machinery,
and every pass has to reach for it by hand. Comments (#70) were the first
instance, `RepositoryRef` the second, and a `Schema`'s field-borne refs the third.
Expect the next one to have the same shape.

**`quietly` is the instrument for resolution-without-policing.** Populate the
refMap so downstream passes can look references up; do NOT start reporting
references that have never been checked. Nested `send`/`tell` use it, as A55's
`ValueRef` does. Ignoring this took `RiddlModelsRoundTripTest` from 106 failures
to 180 in one change, when a strict schema resolution began rejecting clauses
nothing had ever validated.

**`of <name> as type <T>` is STRICT, by ruling.** A path landing on an Entity is
an error even though it parses: the syntax says `type`, so it must be a type.
This surfaced 202 errors across 186 of 187 riddl-models — nearly the whole corpus
— and the ruling was held with those numbers in hand. The corpus is being
corrected. `SchemaUsageTest` pins the semantics.

**`when prompt("…")` (`7147a0039`).** A54 made a bare `"x"` a literal and
`prompt("x")` an AI-evaluated value, but a `when` condition could only be written
as a bare string and `prompt(...)` did not parse there at all. It does now and
the bare form is deprecated. Carried through all six surfaces; BAST condition
flag 4, FORMAT_REVISION 1 -> 2. `matchGuard` needed nothing — it never accepted
a string.

**A fifth of the same family: a body-less `state` lost its metadata**
(`d576ce59c`). `closeState` called `closeDef` only when the state had contents,
and `closeDef` emits the closing brace AND the metadata — so `state X of record
R with { … }`, having no brace to close, lost its `briefly` and `described as`
too. 41 states across 41 corpus files, 82 missing warnings, blocking
canonicalisation. Audited every other closer: all call `close(x)`
unconditionally, so this was the only guarded one.

**`IncludeHygieneTest` now fails DETERMINISTICALLY in a full `passes` run** while
passing in isolation. Proven independent of the prettify fix by stashing that
change and re-running. It is the known `PlatformContext.withOptions` defect —
no try/finally, so a throwing test poisons global options for later sequential
suites. Intermittent before, consistent now, so CI will see it. Unfixed.

**Two tests no longer fetch `dokn.riddl` over the network** (`aecf4392e`).
`RiddlParserInputTest` asserted byte offsets into a file in *another repository*,
so migrating riddl-examples to 2.0 broke it — catching a correct migration, not a
defect, and aborting a coverage run. It now reads
`language/input/parser-input/offsets.riddl` through a local file URL. The JS
`TopLevelParserTest` parses an inline model. `RunRiddlcOnRemoteTest` is left
alone: the remote IS its subject.

## #70 — the JSON round trip is a real fidelity check now — DONE

The AST<->JSON round trip was checked for IDENTITY — `root -> json1 -> root ->
json2`, asserting `json1 == json2`. That is a good check of determinism and
re-readability and a **useless** check of fidelity: a construct the serializer
drops is missing from both sides, so identity holds over the wreckage. It was
holding over 242 lost nodes.

**Two new checks, both in `Root2JsonFixturesTest`** (JVM, walks
`language/passes/riddlc/commands input`, 109 fixtures of which 84 are standalone
models):

1. identity, as before;
2. a **census** — count every node by kind, including metadata, in the original
   and the re-parsed tree, and require them equal. This is what catches a
   dropped construct.

Both name their skips and assert a floor on how many models were actually
compared, so neither can go vacuously green.

**What the census found, and what was fixed:**

- **25 definitions** (types, fields, schemas, authors, streamlets, inlets, a
  connector). The AST unions had widened in release/2 and the DTOs never
  followed: a domain gained connectors and repositories, every processor became
  port-bearing so an entity may own streamlets, a root may carry a top-level
  author. `JsonifierPass` now records which children each parent consumed and
  reports the rest as `droppedKinds` — that guard found all five gaps in one
  run and will find the next one the day a union widens again.
- **45 metadata nodes**, 31 of them block descriptions. Rich metadata rode on
  seven DTOs; RIDDL lets any definition carry it. Every definition DTO carries
  it now.
- **171 comments.** `Comment` is a `RiddlValue`, not a `Leaf`, so it arrived at
  `processValue` (which did nothing) rather than `processLeaf` — invisible to
  the pass AND to its own drop guard.

**Known gap, pinned at exactly 3 occurrences:** a comment opening a `group`
body. The parser puts it in the group's contents, but `AST.OccursInGroup` is
`Group | ContainedGroup | Input | Output` and admits no `Comment`, so there is
no legal way to rebuild it. `Contents` is an opaque `ArrayBuffer`, which is how
the parser gets away with it at runtime. **The parser and the AST union
disagree — that is an AST bug, not a serializer bug.** Fixing it means widening
the union, which touches BAST and prettify. Awaiting a decision.

**`Root2JsonCorpusTest` had been passing without running.** It drove off the 187
checked-in `.bast` files in `../riddl-models`, which are at `formatRevision` 12
against a current 25; `Header.isValid` rejects a mismatch, every read failed,
every failure was silently skipped, and both assertions reduced to `0 mustBe 0`.
It now drives from the `.conf` entry points' sibling `.riddl` sources and
reports parse failures rather than skipping them. It is EXPECTED RED until the
corpus is migrated (standing policy), alongside `RiddlModelsRoundTripTest`.
This is a fourth member of the "suite passes without running" family catalogued
in CLAUDE.md — and the first one where the vacuum was caused by a data file
going stale rather than by test-framework misuse.

## JSON fidelity ratchet — DONE (reached zero)

Follow-on from #70. `Root2JsonFixturesTest` gained a third check —
**prettify agreement**, `root2RiddlSource(root0) == root2RiddlSource(root1)` —
which sees lost or reordered FIELDS that neither `json1 == json2` (blind to
anything dropped on both trips) nor the node census (counts nodes by class) can.
It landed as a ratchet at 63 divergent fixtures and was driven to **0**; the
`DivergentCeiling` constant is gone and the suite asserts `divergent mustBe
empty`. JSON is now RIDDL's fourth fully-reflective surface, alongside prettify,
BAST and the parser.

**The mandate that shaped it:** reflectivity is not a metric to improve, it is a
binary property. `root -> JSON -> root` recovers the EXACT AST, *including the
order of definitions within their parent*. A ceiling above zero is a standing
statement that the surface is not reflective.

**What was actually wrong, in the order fixed:**

| Cause | Ratchet |
|---|---|
| aggregate flavour — `type X is {…}` returned as `record X is {…}` | 63 → 62 |
| **source order** — per-kind buckets, the dominant cause | 62 → 49 |
| `String` bounds rendering | 49 → 24 |
| TypeRef keyword on ports and inputs | 24 → 14 |
| on-clause `from`; group/input/output alias | 14 → 12 |
| metadata order (`with { … }` buckets) | 12 → 7 |
| `briefly`'s position; `refJs` not writing the alias | 7 → 2 |
| a use case's steps interleaved with its comments | 2 → 0 |

**The shape of the fix.** A container's children travel in ONE ordered
`contents` array of `$kind`-tagged entries, and a `with { … }` block's entries
in `metadata.items`. The per-kind buckets are still READ (so older documents
load) but never written; `parseJsonWithMessages` reports a `Deprecation` naming
the containers that used them.

**Traps worth remembering — all four are the same shape, a second code path
that quietly disagrees with the first:**

- **upickle tags sealed hierarchies.** Making the DTOs extend a `sealed trait`
  silently added `$type` to every object in the schema; the round trip still
  agreed with ITSELF, so the fixtures test stayed green at 85/85 and only the
  hand-authored `json-examples` caught it. `ContentDto` is a Scala 3 UNION —
  which is also how the AST models the same idea — so derivation is untouched
  and exhaustivity is still checked.
- **Hand-written codecs drop new fields.** `RecordDto.comments` and
  `RefDto.keyword` were both added to the case class and both went on being
  dropped, because `writeTypeExpr` and `refJs` are hand-written rather than
  derived. Anything in `JsonModel`'s manual codec section needs the field added
  in two places.
- **The tag key is `$kind`, not `kind`.** `OnClauseDto` and `SchemaDto` carry a
  `kind` FIELD of their own and `ujson.Obj.from` keeps the last of a duplicate
  pair, so the tag silently overwrote the data.
- **`withContents` overwrote a container that built its own.** A use case reads
  its steps straight off the node rather than from the scope stack, and the
  generic attach-the-kids step replaced them with just the comments.

**The wrappers are represented too, and `NotRepresented` is GONE.** `Include`
and `BASTImport` are ordered content entries carrying their already-loaded
contents nested, so read-back still needs no I/O and stays Native-safe. Two
traversal notes: `Pass.traverse` deliberately does NOT push a scope for an
`Include` (its children belong to the enclosing container), so `JsonifierPass`
overrides `traverse` to give it one; `BASTImport` uses the `openBASTImport` /
`closeBASTImport` hooks `HierarchyPass` already provides. The census now counts
every kind with no exclusions and reports `lossy=0`.

`ULIDAttachment` is represented as an ordered metadata item. Note `metaOf`'s
"is this metadata empty?" guard had to learn about the ordered items, or a block
containing ONLY a kind with no bucket of its own vanished entirely.

**Still open:**

- `Root2JsonCorpusTest` red by standing policy (2 of 189 external models fail to
  re-parse: `reactive-bbq.riddl`, `fund-accounting.riddl`).
- **`Nebula` stays deferred, and the ledger's reason is now the honest one:** it
  is not a child of any container and never appears in a `Root`, so it is not a
  fidelity gap at all. Representing it would mean a new top-level document shape
  (a `parseJsonNebula`), which is a feature, not a reflectivity fix.
- ~~**`attachment ULID is "…"` appears NOT TO PARSE**~~ — **FIXED since, and
  this note was stale.** Re-checked 2026-08-03: `CommonParser.scala:360` now
  factors `attachment` out and alternates the BODIES
  (`ulidAttachmentBody | namedAttachmentBody`), so there is nothing to backtrack
  over, and `language/input/attachments.riddl` is a fixture covering all three
  forms. `riddlc parse` on it reports 0 errors. Left visible rather than deleted
  because a stale "known bug" is worse than none — it sends the next reader
  chasing something already repaired.

## Two parser fixes found by the JSON work — DONE

**`attachment ULID is "…"` could not be parsed at all.** `Keywords.keyword` ends
in a cut — `P(key ~~ &(isNotKeywordChar))./` — so once the `attachment` keyword
matched, the enclosing `|` in `metaData` could not backtrack and whichever
attachment rule came first won outright. The general rule was first, so
`ulidAttachment` was unreachable and the ULID form failed where a mime type was
expected, having never been tried. Reordering would only have broken the other
two the same way; the prefix had to be FACTORED so the keyword is matched once,
ahead of the choice. `bastImport` in the same file was already factored exactly
this way, with a comment describing the identical hazard.

The construct had **no fixture and no test anywhere**, which is how a documented
piece of syntax stayed unreachable unnoticed. Both added:
`language/input/attachments.riddl` covers all three forms for the CI grammar
validators, and `MetaDataTest` gains two cases — including an ordinary
attachment NAMED `ULID`, which proves the branches backtrack against each other
rather than the first winning.

**`state X is <recordRef>` is deprecated.** `of` is the canonical 2.0 spelling.
`is` was also accepted and — since `is` is itself optional — so was nothing at
all, which left one keyword doing two jobs in a single production: `stateBody`
already uses `is` to introduce the BODY, as every other definition does. The old
spellings still parse (they are used throughout the suite and the external
corpus) and now emit a `deprecation`. `StateRecordIntroTest` pins both halves —
that they still parse, and that they say so. No `.check` golden moved, because
every fixture already writes `of`.

## A55 — optional local name binding for the on-clause message — DONE

`on foo: command Foo { … }` binds an optional local name to the handled
message. The `:` is ordinary **type ascription** — the same rule as
`let x: T = …` and a field declaration `p1: String` — so the parser reuses
`HandlerParser.maybeName`, the very combinator the `from <name>: <origin>`
clause already uses. `when` is untouched.

**Slice 1 (done).** `binding: Option[Identifier]` on `OnMessageLikeClause`
and on both concrete nodes. It sits **immediately after `from`, without a
default**, because `@JSExportTopLevel` requires defaulted parameters to be
trailing and `contents`/`metadata` are defaulted. All four reflection
surfaces carry it: prettify, BAST (no new node tag — the existing on-clause
sub-discriminators 2 and 4 grew a field; `FORMAT_REVISION` 22 → 23, and the
checked-in `NotImplemented.bast` fixture's header was bumped to match), JSON
(`OnClauseDto.binding` is **defaulted** so pre-A55 JSON still reads), and the
EBNF/GBNF grammars plus a corpus fixture.

**Adjacent bug fixed in slice 1:** prettify never emitted an on-clause's
`from [<name>:] <origin>` clause at all, so `on command C.DoIt from di:
context C` silently lost its origin on **every** round trip. It is emitted
now, in the same `openDef` slot the binding uses.

**Slice 2 (done): `ValueRef` is resolved by the RESOLVER, not by hand.**
A54 had `ResolutionPass` skip `ValueRef` outright and matched only
`path.value.last` in validation against three in-scope field sources — so
`garbage.nonsense.conditionRed` validated whenever `conditionRed` was a
field of the handled message. The leading components were never examined.
That hole is a symptom of the duplication, and it closes as a consequence
of removing it.

- The three `case _: ValueRef => ()` opt-outs are gone. A ValueRef is now
  queued and resolved in `postProcess` (its value-scope anchors are reached
  THROUGH other references, and the pass visits definitions in source
  order, so a handler written above the state it reads must not lose).
- Only the ANCHOR choice differs from an ordinary reference: the on-clause
  BINDING, else a field of the handled message / entity state / function
  `requires` input, else the ordinary `findAnchor` route (which covers
  qualified paths like `GState.active` and bare `constant` names). The rest
  is `resolvePathFromAnchor`'s existing walk — no new traversal machinery.
- **`let`-locals stay LEXICAL.** A `let` is not a Definition and is
  statement-ORDERED (visible only after its declaration, shadowed by inner
  blocks), which the symbol table cannot model. They stay threaded by
  `checkStatementScopes`; everything else goes through the refMap. The
  ValueRef walk therefore runs QUIETLY (`ResolutionPass.quietly`) — only
  validation knows whether a failure is real, so it owns the diagnostic.
- **A `let`'s type is now INFERRED from its expression** when it has no
  `let x: T = …` annotation, which is what makes `let bar = foo; bar.a`
  work. `validateForeachCollection`'s "has no declared type" complaint is
  reworded to "no declared or inferable type" and fires less often.
- `whenValueRefCategory`, `validateComparand`, `comparandCategory`,
  `matchSubject*` all read the refMap now; `valueAllowedFields` and
  `constantOf` are deleted (the resolver does that lookup).
- Warnings: a local shadowing an outer definition → Warning; a binding
  colliding with a field of the message/state → Warning (legal: bare `foo`
  is the binding, `foo.foo` is the field); a local name not BEGINNING with
  a lowercase letter → StyleWarning (camelCase stays legal).

**Latent bug found and fixed:** `findMatchingCandidate`'s on-clause arm was
guarded by `omc.msg.id.nonEmpty`, but `Reference.id` is a reference's
OPTIONAL LOCAL NAME (what `from di: context C` sets) and no `MessageRef`
ever carries one — so the arm was **unreachable**. The intended test is
`omc.msg.nonEmpty` (a non-empty pathId), and it is what lets an
`on foo: command Foo` binding walk `foo.someField`.

## #60 Slice 2 — the predefined `Riddl` standard module — DONE

Two terminators, available to EVERY model with no `import` and no author
declaration: **`BottomlessPit`** (a `sink` that consumes everything and
emits nothing) and **`ForeverEmpty`** (a `source` that never produces).
They exist because under the unified streaming model every port is the
endpoint of exactly one connector (A31), so every outlet must terminate
somewhere and every inlet must be fed.

- **The module is readable RIDDL, not hand-built AST.**
  `language/.../PredefinedModule.scala` holds the source in a string
  constant, parses it once via `TopLevelParser.parseString`, and caches the
  resulting `Module` singleton (so `eq` comparisons are meaningful).
  Streamlets and the `Drain` type (`type Drain is Anything`) live DIRECTLY
  in the module — `ModuleContents` is the wide `NebulaContents` union, so
  no domain/context wrapping.
- **The seam is the SYMBOL TABLE, not the AST.** `SymbolsPass.postProcess`
  seeds `predefinedSymTab`/`predefinedParentage` — **two NEW maps on
  `SymbolsOutput`, deliberately separate** from `symTab`/`parentage`.
  Lookups consult the user's tables first and fall back to the predefined
  ones. Consequences: (a) `AnalysisResult.domains/streamlets/…`,
  `UseCaseWitnessPass`, and `foreachOverloadedSymbol`, which all ENUMERATE
  `parentage`/`symTab`, still see only the user's model (the first cut
  seeded the shared maps and broke `AnalysisPassSpec`); (b) a user
  definition with a colliding name WINS structurally, with no ambiguity
  and no message.
- **Non-injection is the invariant.** The module never enters
  `Root.contents`. A terminator-free model's AST, prettify, BAST bytes and
  JSON are unchanged — asserted directly (BAST bytes before-passes ==
  after-passes; same for JSON).
- **Exemptions** (all by REFERENCE IDENTITY against the singleton, never by
  name): A31 port cardinality; unattached-port; isolated-streamlet;
  source→sink and sink←source reachability (reaching `BottomlessPit`
  TERMINATES a pipeline; `ForeverEmpty` ORIGINATES one); handler
  completeness (Empty/PromptOnly). Plus a general rule in
  `validateConnector`: a port whose type is `Anything` is compatible with
  every other type, which is what lets one drain absorb any message type.
- **EBNF drift found and fixed:** `streamlet` was
  `source|sink|flow|merge|split|router|void` — missing `processor`, so a
  `processor` written directly in a *module* parsed with fastparse but not
  with the published grammar. Added `| processor`; GBNF regenerated
  (296 rules, all validators pass). `riddl_grammar.lark` was left alone —
  it has no `processor` rule at all and is separately stale.
- **Grammar coverage:** `language/input/predefined/riddl-standard-module.riddl`
  is a verbatim copy of the constant so the CI TatSu/GBNF validators scan
  it; `PredefinedModuleSourceTest` (JVM) fails if the two drift.

## #60 Slice 1 — `Anything` replaces `Abstract` — DONE

The dual of `Nothing` already existed as the `Abstract` predefined type
(`isAssignmentCompatible = true`, plus a both-directions special case in
the `TypeExpression` base). Renamed the AST node to `Anything`; `Abstract`
survives as a deprecated *input* spelling and a deprecated *Scala* alias.

- **AST** — `case class Anything(loc)`; `@deprecated type Abstract =
  Anything` + `@deprecated val Abstract: Anything.type = Anything`, so
  `Abstract(loc)` AND `case Abstract(loc)` both still compile downstream
  (test in `TypeExpressionTest`). Nothing internal touches the alias, so
  `-Werror` stays quiet.
- **Parser** — `otherPredefTypes` accepts both; `Abstract` yields an
  `Anything` node plus exactly ONE `deprecation(...)` (same mechanism as
  `reply`→`yield` / `prompt`→`do`).
- **Prettify/JSON** — both key off `getClass.getSimpleName`, so output is
  now `Anything` for free. JSON still ACCEPTS `"Abstract"` on input and
  normalizes it to `"Anything"` on output.
- **BAST — the tag did NOT change** (`TYPE_REF`/subtype 99), so the wire
  format is identical and `FORMAT_REVISION` stayed at **21**. Guarded by a
  `BASTRoundTripTest` case asserting `Anything` and `Abstract` sources
  produce byte-identical BAST.
- **Fixture** — `language/input/full/domain.riddl` now has one `Anything`
  type and one `Abstract` type, so the CI TatSu run covers both arms.

**Found along the way:** `passes/.../prettify/RiddlFileEmitterTest.scala`
is `abstract` with **no concrete subclass anywhere** — it compiles but has
never run. Worth wiring up (or deleting) separately.

## Session 2026-07-26 — Unified streaming processor model (A37/A31/A32/A6)

Shipped a large release/2 change (27 commits, pushed to origin at
`4af86d67`) unifying the processor model, plus the earlier A9b tail and
a deprecation-logging fix.

**What changed & why.** Every `Processor` (context/entity/projector/
repository/adaptor + a new generic `processor` keyword) can now declare
inlets/outlets and an optional `as <shape>` ascription; shape is
otherwise derived from arity. The old `source/sink/flow/merge/split/
router` keywords are deprecated aliases (synonyms: cascade/fanin/
broadcast/fanout). Contexts gained an optional intention prefix
(`application|external|gateway|service`). Validation added A31
one-connector-per-port, `as`/arity check + omitted-shape nudge, A37
intention rules, option deprecations, and A6 `tell` reachability. All
reflection surfaces updated (Prettify, BAST `FORMAT_REVISION` 10→11,
JSON, EBNF+GBNF). Executed as an 18-task plan via subagents + a
whole-branch review. Design docs live in `docs/superpowers/{specs,plans}/
2026-07-26-unified-streaming-processor-model-*`.

**What went wrong (root causes worth remembering).**
- I added the `Intention` enum *between* `@JSExportTopLevel("Context")`
  and `case class Context`, orphaning the annotation onto the enum →
  `cJS` broke. Root cause: I verified AST edits with `cJVM` only.
  Lesson: AST/`@JSExport` edits need `cJS`+`cNative`, not just `cJVM`.
- `test`/`tJVM` resolve to **`testQuick`**, which incrementally SKIPPED
  language/passes after a fix (ran 0 tests) even with the `ac` cache
  cleared — a false "green". Root cause: testQuick dependency tracking,
  not the action-cache. Lesson: for a real full run use
  `language/testOnly *` / `passes/testOnly *`, not `test`.
- Parser `warning()`/`deprecation()` were silently dropped on a
  *successful* parse (`parseRule` only returned the message buffer on
  fastparse failure). Fixed by threading parse messages via
  `PassInput.parseMessages` → `PassesResult.additionalMessages`; a
  final review caught that the first fix only covered `parseAndValidate`
  (not `parse`/`stats`/`bastify`) and it was extended.

**Unfinished / awaiting others.**
- **#56 corpus migration** (riddl-models, riddl-examples) to the new
  syntax — the external worker is actively migrating (commands tests
  went 51→232 passing during the session). Task drops filed this
  session (see `../riddl-models/task/`, `../riddl-examples/task/`).
- A37 **rule 3** (UI-groups-only-in-application) is scoped to
  explicitly-declared non-application intention; tighten to also fire on
  intention-less contexts AFTER the corpus is migrated to mark UI
  contexts `application`.
- A6 tell-reachability is direct-connector only (transitive deferred);
  it will warn broadly on legacy connector-less `tell` — expected.

**Method worth reusing.** Subagent-per-task with file-based briefs/
reports (kept controller context lean across 18 tasks), a git-ignored
ledger at `.superpowers/sdd/progress.md` for compaction recovery, and a
final whole-branch review subagent → one fix pass. Each task verified
green before commit.

---

## Deferred — blocked on prerequisites (do NOT start yet)

### #45 — `put`/`get` UI-boundary statements — DEFERRED (out of order)
Prereqs first: the UI / application (output-input-triplet) model must land
before this makes sense. Design as discussed, for when it's unblocked:
- `put <value> to output <outputRef>` (push to a UI Output from an on-clause);
  `get`: `let <id> = get from input <inputRef>` (pull, its own statement kind,
  not an extension of LetStatement). Revive `put`/`get` keywords (they were
  storage statements long ago, removed when repository sufficed).
- Completes the boundary-statement census: send/tell = streaming, call =
  function, put/get = UI.
- **can-fail**: both can fail (put when UI absent/headless, get when input
  unset). Model via a `def canFail: Boolean` on `Statement` (default false;
  true for Send/Tell/Put/Get) — one source of truth shared with #12.
- **Witnessing** (greenfield — no existing use-case-realization validation):
  a `ShowOutputInteraction` witnessed by a `put` to that output; a
  `TakeInputInteraction` by a handler on the input's message type OR a `get`
  referencing it. Emit CompletenessWarning for an unwitnessed step.
- Open question for when unblocked: which handler statement-sets may contain
  put/get.

### #12 — Single failure point per saga do-block — DEFERRED
Blocked on the **complete can-fail census**. A saga step's do-block is
all-or-nothing (undo assumes all-or-none happened); warn when it contains > 1
potential failure point — a statement-kind count. Census: send, tell, call,
yield, put, get CAN fail; let, set, when, match, foreach CANNOT.
**Why deferred, not done-partial:** `call` doesn't exist, `yield` (≈ reply) is
TBD, `foreach` is new, and put/get are behind #45. Shipping with only
send/tell would give false "single failure point" passes that flip to warnings
later — worse than waiting. Build once, correctly, after the census statements
exist (via the shared `Statement.canFail`).

## Connectors at Domain scope (2.0) — DONE, internal green

Branch `release/2`. Mirrors the Repository-at-Domain-scope feature.
- **AST**: `OccursInDomain += Connector`; `Domain extends
  WithConnectors[DomainContents]`.
- **Parser**: `DomainParser.domainDefinitions += connector` (StreamingParser
  already mixed in). EBNF `domain_content += connector`; GBNF regenerated.
- **Validation** (reworked `StreamingValidation.checkConnectorPersistence` →
  `checkConnectorPlacement`; the old version `require(false)`-crashed on a
  connector with no context — would have crashed on domain-scoped ones):
  resolve each end's context AND domain (`domainOf` = first Domain in
  `parentsOf`), take the connector's own scope from
  `symbols.contextOf(connector).isEmpty`, then:
  1. ends in different domains → ERROR (domain-analysis failure; terminal).
  2. domain-scoped + ends in same context → ERROR (over-scoped).
  3. context-scoped + ends cross contexts → ERROR (under-scoped).
  4. domain-scoped + cross-context + not `persistent` → **CompletenessWarning**
     (so AI can adapt) — durability at a context boundary can be model
     correctness, not just deployment.
  Existing "remove persistent when same-context" warning preserved verbatim.
  Rules are conservative: only fire when BOTH ends resolve (no false positives
  on unresolved refs).
- **Reflection**: prettify (`doConnector`) + BAST (`writeConnector`/
  `readConnectorNode`) already generic; proven by `ConnectorDomainScopeRoundTripTest`
  (prettify) + a `BASTRoundTripTest` case.
- **Tests**: 5 rule cases in `StreamValidatorTest` + 2 round-trips + EBNF↔
  fastparse parity (`language/input/domain-connector.riddl` +
  `ConnectorScopeFileTest` + TatSu). **Internal suite: 0 failures.**
- **External**: riddl-models has cross-context connectors at context scope
  (`reactive-bbq`'s 3, plus more) → correctly caught as under-scoped ERRORs
  (feature working on real data). Committed `90fdae10`, pushed; CI run
  30176269811 is red **only externally** (JS green; JVM/Native red with **0
  non-external failures**, 46 connector-scope errors across riddl-models).
  Goes green once riddl-models `main` is conformed — tasks dropped in
  `../riddl-models/task` + `../riddl-examples/task`.

## Explicit `initial` marker on states & handlers (2.0) — DONE, tri-platform green

Branch `release/2`. Roadmap item #14. An optional `initial` keyword before
`state`/`handler` makes the starting state (and the live-after-morph handler)
explicit and refactor-safe under reordering; unmarked models keep the
first-declared semantics.

- **AST**: `isInitial: Boolean = false` added (last field, after metadata) to
  both `State` and `Handler`. Defaulted + last → `@JSExportTopLevel` safe.
- **Parser**: the marker is discovered *after* the full set is parsed so the
  "first one is initial" default is position-based, not lexical. `EntityParser`
  helpers `markFirstHandlerInitial` (per-state) and `defaultEntityInitials`
  (marks first State if none marked; if a single state, marks the first
  entity-scope Handler) rebuild the first-of-type via `.copy(isInitial=true)`
  when none is explicitly marked. **Cut-collision trap** (same shape as the
  handler-kinds `on` cut): `Keywords.initial` cuts, so a plain `initial`
  alternative can't backtrack into `state`/`handler`. Fixed with the
  non-cutting `Keywords.maybeInitial: P[Boolean]` (`(kw ~~ &(isNotKeywordChar))
  .!.?` — the `.!` is required; `.?` on a Unit parser yields Unit, not Option).
- **Validation** (`ValidationPass`): >1 `initial` handler in a state → ERROR;
  >1 `initial` state in an entity → ERROR; if the entity has ≤1 state, >1
  `initial` entity-scope handler → ERROR. Each with a `suggestion`.
- **Reflection (all 3 surfaces)**: BAST writer/reader U8 flag on State+Handler,
  `FORMAT_REVISION` 7→8; Prettify emits `initial ` prefix (`PrettifyVisitor`
  openState, `RiddlFileEmitter.openDef` for Handler) — emitted even on the
  defaulted-first so the round-trip is AST-preserving; JSON `StateDto`/
  `HandlerDto` gained `isInitial`, wired in `JsonifierPass` + `JsonAstBuilder`.
- **EBNF↔fastparse parity**: `["initial"]` on the `state`/`handler` rules; GBNF
  regenerated (263 rules, validator PASS); fixture
  `language/input/initial-marker.riddl` parsed by both fastparse
  (`InitialMarkerFileTest`) and TatSu.
- **Tests**: `InitialMarkerTest` (4 validation cases), `InitialMarkerRoundTripTest`
  (prettify RT), BAST + JSON round-trip cases extended, `InitialMarkerFileTest`
  (parity). `KeywordsTest` 144→145. **Tri-platform: JVM 1067 pass / 0 internal
  failures; tJS + tNative green.** (The lone JVM external failure is the
  pre-existing connector-scope conformance in riddl-models' `reactive-bbq`,
  documented above — unrelated to this feature.)

## A9 — Function/Saga `requires`/`returns` as named type refs — DONE

Branch `release/2`. Roadmap item **A9** (first of the 37-task release/2 queue,
see the To-Do list). `requires`/`returns` on `Function` and `Saga` now take a
**named `TypeRef`** (any type — so `type Age = Integer; function F is { requires
Age returns Age }` works for unary/nullary fns) instead of only an inline
aggregation. Field type is the union `Option[TypeRef | Aggregation]`.

- **Not aggregate-restricted** (user ruling): `TypeRef`, not a
  Message/Record-only ref. The `AggregateRef` hierarchy cleanup the user also
  wants (make `MessageRef` = the 4 real messages, reparent `RecordRef` under a
  new `AggregateRef`) is a **separate, filed task** (queue item **A9b**, #54) —
  A9 uses `TypeRef` and doesn't touch the ref hierarchy. Audit finding for A9b:
  no abstract `case _: MessageRef` matches exist, but records DO flow through
  `MessageRef` via the `messageRef` parser (feeds send/tell/on/morph/reply) +
  BAST/JSON, so the reparent changes those statements' semantics — hence its own
  task.
- **Non-breaking**: inline `requires { … }` still parses/validates but emits a
  **new `Messages.Deprecation` kind** (severity 3, shown with warnings, never
  blocks; `addDeprecation` helper; `justDeprecations` filter). External corpus
  does NOT break (deprecation is a Warning). Advisory migration tasks can be
  dropped in riddl-models/riddl-examples later.
- **Gap closed**: `validateSaga` and the resolution pass's Saga case never
  touched input/output before — A9 wires both (new `SagaValidatorTest`).
- **Reflection (all surfaces)**: parser `funcInput`/`funcOutput` →
  `(aggregation | typeRef)` (widen each branch to the union — fastparse `|`
  otherwise infers the LUB `RiddlValue`); resolve via `resolveATypeRef`;
  validate via `checkTypeRef` (+ deprecation on inline); Prettify emits
  `ref.format`; **BAST** discriminator byte (0=ref,1=agg) + `FORMAT_REVISION`
  **8→9**; **JSON** new `ArgDto(ref, fields)`. EBNF `func_input`/`func_output`
  → `( aggregation | type_ref )`, GBNF regen (263 rules), TatSu ✓ on new fixture
  `language/input/requires-returns-ref.riddl`.
- **Tests**: `FunctionValidatorTest` (+2), `SagaValidatorTest` (new),
  `RequiresReturnsRefFileTest` (parity), `RequiresReturnsRoundTripTest`
  (prettify), BAST + JSON round-trip cases. `everything.check` / `saga.check`
  regenerated (saga validation gap now fires field warnings on the inline form).
  Ledgers updated: `MESSAGE_SUGGESTIONS.md`, `JSON_COVERAGE.md`.

## A26 — Pure-only functions — DONE

Branch `release/2`. Roadmap item **A26** (2nd of the 37-task queue). A `Function`
body must be pure: it may not write entity state (`set`/`morph`/`become`),
`send`/`tell`, or `reply`. `require`/`error` (refusal) and pure computation
(`let`/`when`/`match`/`prompt`/`code`) stay legal. Enforced at **parse time**
(user's choice) so purity is structural — effect statements can't enter a
function's AST (which A23 relies on).

- **One-file change** in `StatementParser.scala`: `messagingStatements` and a new
  `setStatements` helper reject `send`/`tell`/`set` for `ProcessorKind.Function`
  with `Fail.opaque` messages; the `statement` dispatcher's new `Function` case
  appends a `morph`/`become`/`reply` `Fail.opaque` after `base`.
- **Parser gotcha (memory'd):** the ban must SUBTRACT inside `base` (mirror the
  `ActivationClause` pattern) or APPEND after base. Do NOT prepend
  `keywordAlt ~/ Fail | base` — it breaks `functionDefinitions`' `rep`
  termination at `}` so even valid pure functions fail to parse. Cost me a few
  iterations; see [[statement-restriction-parser-pattern]].
- **Deferred:** "may not READ entity state" — structurally undetectable today
  (function conditions/lets/expressions are opaque `LiteralString`s; no
  structured field reads exist). Needs A17/A28 first. **`put`/`get`** join the
  ban when A45 lands (flagged on task #47).
- **No reflection changes** (parser restriction; no new AST/construct) — no
  prettify/BAST/JSON/EBNF. **Impact nil:** 0 external and 0 internal `.riddl`
  function bodies used effects; the only internal fix was
  `FunctionValidatorTest`'s "simple function" (used `set` → made pure with `let`).
- **Tests:** new `PureFunctionTest` (reject each of set/send/tell/morph/become/
  reply; accept prompt/require/let/error; assert the pure-function message).

## In-flight: Handler kinds per processor (2.0) — DESIGN LOCKED, WIP

**Branch**: `release/2`. One combined change (user's choice). Uncommitted
WIP. **STATUS (this session): feature BUILT + verified; JVM suite 35→5
failures, all understood as test-expectation updates (not logic bugs).**

### Done + verified
- **AST** (#12): `OnEventClause`, `OnActivationClause`, `OnPassivationClause`
  added; both event/message nodes now share a new sealed trait
  `OnMessageLikeClause` (has `msg`/`from`) so resolution/flow/dep/diagram/
  validation treat them uniformly. `StatementsSet` record refactor
  (`ProcessorKind` × `ClauseRestriction`). **Fastparse trap fixed**: the
  ban parsers MUST be `def`s not `val`s (a `P[T]` is a parsing *run*; a
  val executes at its definition position and corrupts the alternation —
  caught by verification, 14 failures → 0).
- **Parser** (#13): `HandlerParser` dispatches on `set.processor` — no
  ProcessorParser changes needed (processors already pass the right set).
  `on event` and `on <msg>` are ONE parser (`onMessageOrEventClause`) that
  branches on the parsed ref type via `flatMap` (needed because
  `Keywords.on` cuts, so two `on …` alternatives can't backtrack); event
  bodies parse under `forEvent` (bans require/error). Projector ref set
  rejects command/query/record; non-entity rejects activate/passivate;
  activation bodies ban send/tell/reply/morph/become. `eventRef`/
  `resultRef` de-privatized. Keywords `activate`/`passivate` +
  `on activate`/`on passivate`. **EBNF updated. GBNF NOT yet regenerated.**
- **BAST** (#14): writer/reader sub-kinds 4=Event,5=Activate,6=Passivate;
  `FORMAT_REVISION` 6→7; `BASTWriterPass` traversal cases added (the
  explicit per-subtype match would else silently drop them).
- **Prettify** (#14): `OnEventClause` added to `RiddlFileEmitter.openDef`
  msg-format special case; activate/passivate/init/term/other all emit via
  `id.format`.
- **JSON** (riddlLib, a 3rd serialization surface): JsonifierPass +
  JsonAstBuilder handle event/activate/passivate.
- **Validator** (#15): removed the now-dead projector command/query
  WARNING (parse error supersedes); added adaptor "missing `on other`" →
  ERROR (generalizable later). All `OnMessageClause` collect-sites that
  should count events broadened to `OnMessageLikeClause` (restores
  pre-change behavior — events *were* OnMessageClause); kind-filtered
  (command/query) sites left precise.
- **Reflection tests** (#16): 6 new HandlerTest parse/parse-error cases
  (JVM+JS); `HandlerKindsRoundTripTest` (prettify, JVM+Native);
  `BASTRoundTripTest` handler-kinds case. **All green** (12 + 6).
- **Corpus fixes so far**: context.riddl, SharedValidationTest,
  SharedAdaptorTest (×2), everything_full.riddl, adaptor-direction.riddl,
  commands/adaptors.riddl (error→prompt in on-event; add on-other).

### DONE since (all green)
- **5 JVM expectation updates** applied: PassTest opens 57→58 + values
  25→26; StatsPass categories 23→25 (new "On Event" + "On Other"), All.count
  22→24, numStatements 7→8; HandlerValidatorTest string "OnMessageClause"→
  "On Event" (OnEventClause renders via its `kind` override);
  KeywordsTest 142→144. Both `.check` files regenerated (handler-types
  projector converted command→event + repurposed; adaptor-direction down to
  the 2 direction errors — my `error` in on-other counts as executable so the
  "only prompt" warnings correctly vanished).
- **EBNF** updated (`ebnf-grammar.ebnf`): `on_clause` now includes
  `on_activate_clause` / `on_passivate_clause` / `on_event_clause` (the last
  reuses the existing `event_ref`; `on_message_clause` stays a superset since
  EBNF is context-free and can't express the projector/statement bans).
  **GBNF** regenerated (263 rules) from it + `gbnf_validator.py` PASSED.
- **EBNF↔fastparse parity PROVEN** (not just asserted): added corpus fixture
  `language/input/handler-kinds.riddl` exercising `on activate`/`on passivate`/
  `on event`/`on other`. It parses under BOTH the TatSu EBNF validator
  (`ebnf_tatsu_validator.py`, run locally in a venv — 67/87, 0 unexpected
  failures) AND fastparse (new `HandlerKindsFileTest`). Before this, no
  input-dir file used `on activate`/`on passivate`, so those new EBNF rules
  were unexercised.
- **tJVM: green** except the 16 external `RiddlModelsRoundTripTest` cases
  (see below). **tJS: green (63/63).** **tNative: green** (one further count
  shift, `VisitingPassTest` values 24→25 — same `on other` node; fixed and
  confirmed on both Native and JVM).
- **Cross-repo tasks dropped**: `../riddl-models/task/` and
  `../riddl-examples/task/` (`2026-07-25-handler-kinds-2.0-conformance.md`).

### The only remaining red is EXTERNAL (expected, coordinated)
`commands/…/RiddlModelsRoundTripTest` round-trips the **external**
`../riddl-models` checkout. 16 models fail: **48** adaptor handlers lack
`on other`; **6** `on event` clauses use `require`/`error`. Per the "fix
internal, drop tasks external" directive these are fixed in riddl-models
(task dropped), NOT here. This test goes green once riddl-models `main` is
updated. JVM-only test (scalajvm), so it does not affect tJS/tNative.

### Clean-from-scratch certification (the incremental runs were NOT enough)
sbt 2's action cache (`~/Library/Caches/sbt/v2/ac`) keys test results on the
compiled classpath but is BLIND to fixtures a test READS AT RUNTIME — so the
`tJVM`/`tJS`/`tNative` aliases served stale passes for fixture-reading tests
after I edited `.riddl`/`.check` files, and only ran ~30 of ~125 suites.
`clean` does NOT fix this (global cache). Forcing a real run
(`rm -rf ~/Library/Caches/sbt/v2/ac`, keep `cas`) surfaced two INTERNAL
failures the incremental runs had masked — both now fixed:
  - `TokenParserTest` everything_full token count 401→407 (my `on other`).
  - `JsonCoverageGuardTest` — added `OnEventClause`/`OnActivationClause`/
    `OnPassivationClause` rows to `JSON_COVERAGE.md`.
Full fresh results: **JVM 1031+ / 0 internal fail, JS 409 / 0, Native 370 /
0 + nativeLink OK.** (See memory `sbt2-action-cache-fixture-blindspot`.)

### External corpus (was a moving target — now fixed)
`RiddlModelsRoundTripTest` (commands) + `Root2JsonCorpusTest` (riddlLib) read
the LIVE `../riddl-models` checkout. A concurrent instance completed the
dropped task (58 `on other` added, 4 `require`/`error` removed; 187/187
validate clean, all `.bast` regenerated), so those tests now PASS
locally: `RiddlModelsRoundTripTest` 0 fail (was 16); `Root2JsonCorpusTest`
181/181 reparsed, 96.8% byte-identical (over the 95% bar).

### Regression tests (comprehensive — added so we never revisit)
- **Parse matrix** (`HandlerTest`, 16 cases): parse `on activate`/`on
  passivate`/`on event`; reject `on command`/`on query`/`on record` in a
  projector; ACCEPT `on event`/`on result` in a projector; reject
  `require` AND `error` in `on event`; reject `on activate` AND
  `on passivate` outside an entity; reject ALL of send/tell/reply/morph/
  become in `on activate` (parser now bans all five uniformly with the
  "side-effect-free" message).
- **Validation** (`SharedAdaptorTest` +2, `HandlerValidatorTest` +1):
  adaptor handler missing `on other` → ERROR; with `on other` → clean;
  entity `on activate`/`on passivate`/`on event` validate with no
  spurious errors.
- **Reflection**: prettify RT (`HandlerKindsRoundTripTest`), BAST RT
  (`BASTRoundTripTest` case), JSON RT (`JsonRoundTripTest` case), JSON
  ledger (`JsonCoverageGuardTest`), EBNF↔fastparse parity
  (`HandlerKindsFileTest` + `language/input/handler-kinds.riddl` +
  TatSu validator).

### DONE — committed + CI green on all platforms
Handler-kinds landed as one commit (`66af0752`). `release/2` pushed; `scala.yml`
(workflow_dispatch) is **green on JVM + JS + Native** (run 30172643158), which
also confirms the external riddl-models/riddl-examples fixes in a clean CI env
(RiddlModelsRoundTripTest / Root2JsonCorpusTest download the pushed `main`).

### CI / sbt 2 fixes required on release/2 (were pre-existing, cache-masked)
Dispatching `scala.yml` on release/2 surfaced three sbt-2 CI issues that
`main` never hit (main restores plugins/deps from cache and hadn't re-fetched
in ~18 months). Fixed in commits after the feature commit:
1. **Plugin-resolution 401** — under sbt 2 the global `~/.sbt/2/github.sbt`
   credential is NOT applied to meta-build (plugin) resolution, so fetching
   sbt-ossuminc 3.0.3 from GitHub Packages returned 401. **NOT** a permission
   gap: the automatic `GITHUB_TOKEN` reads the public package fine (direct
   `curl -u x-access-token:$TOKEN` → 302). Fix: restore
   `credentials += Credentials("GitHub Package Registry","maven.pkg.github.com",
   "x-access-token", sys.env.getOrElse("GITHUB_TOKEN",""))` in
   **`project/plugins.sbt`** (the meta-build) — as pre-1.4 revisions had.
   GitHub Packages Maven requires auth even for PUBLIC packages (401 to
   anonymous); only the Container registry allows anonymous.
2. **sbt-2 CLI** — a single quoted multi-command arg is parsed as ONE command
   line; the old `sbt clean cJVM tJVM` fails ("Expected whitespace"). Use the
   `;`-list form: `sbt "; clean; cJVM; tJVM"`. Fixed in scala.yml + coverage.yml.
3. **dynver** — shallow `actions/checkout` breaks `git describe` ("No names
   found"). Add `fetch-depth: 0` + `fetch-tags: true`.

**Goal**: `HandlerParser` stops assuming one handler grammar and offers a
family of handler kinds per processor. Three features on that spine:
- **Projector = event-only**: `projectorHandler` offers event + result
  clauses only; `on command`/`on query` in a projector is a **parse
  error**. Remove the existing ValidationPass projector command/query
  warning.
- **Entity `on activate` / `on passivate`**: new per-rehydration /
  per-eviction lifecycle clauses, entity-only, distinct from once-ever
  init/term. Statements banned: outbound messaging (send/tell/reply/
  morph/become) — activation must be side-effect-free.
- **Adaptor `on other` completeness**: an adaptor handler with no
  `on other` clause is a validation **ERROR** (adaptors only for now;
  bring the user a proposed generalization list before extending to other
  processor kinds).

**Locked design decisions** (from the interview):
- **`on event` becomes its own node `OnEventClause` EVERYWHERE** (not just
  projectors). Its statement set bans `require`/`error` at parse time —
  "events must always be accepted" is structural. `on event` moves OUT of
  `OnMessageClause` (which now covers command/query/result/record only).
- New AST nodes: **`OnEventClause`** (has a `msg` ref like OnMessageClause),
  **`OnActivationClause`**, **`OnPassivationClause`** (lifecycle, no ref,
  like OnInit/OnTerm). All extend `OnClause`. **DONE.**
- **`StatementsSet` is now a record** `(processor: ProcessorKind, clause:
  ClauseRestriction = Unrestricted)` with companion vals preserving the old
  `StatementsSet.X` call sites, and `.forEvent`/`.forActivation`.
  `ClauseRestriction`: `Unrestricted | EventClause (bans require/error) |
  ActivationClause (bans send/tell/reply/morph/become)`. Composition lives
  in `anyDefStatements`/`statement`. **DONE + compiles.**
- **Message-kind restriction stays STRUCTURAL, not in the record**: distinct
  `onEventClause` (uses `eventRef`) vs `onMessageClause` (non-event refs);
  the projector's result-only need is a local `allowedKinds` param on
  `onMessageClause`. (User agreed after discussion.)
- Reflection: 3 new nodes ⇒ BAST tags + **`FORMAT_REVISION` 6→7**, prettify
  emission, EBNF/GBNF, and per-node parse+validate+prettify-RT+BAST-RT
  tests. Plus parse-error tests (command/query-in-projector; require/error-
  in-event; activate/passivate-outside-entity; outbound-msg-in-activate).
  Fix internal corpus; **drop correction tasks in `../riddl-models/task`
  and `../riddl-examples/task`**.

**Ripple finding (good news)**: `passes` compiles clean adding the nodes —
the `VisitingPass` dispatch handles `OnClause` **generically** via
`openOnClause`/`closeOnClause`, so NO per-pass hooks are needed. But a clean
compile does NOT mean BAST/prettify handle the new nodes (fallthroughs) —
those need explicit emission, proven by round-trip tests, not the compiler.

**Remaining (concrete)**:
1. Parser: `onEventClause`; split `onMessageClause` (non-event refs +
   `allowedKinds`); `onActivationClause`/`onPassivationClause` + `activate`/
   `passivate` keywords; `projectorHandler`/`entityHandler`/`defaultHandler`;
   wire `ProcessorParser` per processor. EBNF + regen GBNF.
2. BAST: 3 node tags, `FORMAT_REVISION` 6→7, writer + reader.
3. Prettify: emit the 3 clauses (per-node keyword in `PrettifyVisitor`).
4. Validator: remove projector warning; add adaptor on-other ERROR.
5. Tests (full reflection matrix + parse-error cases); corpus fixes;
   cross-repo task drops. Full tri-platform + EBNF/GBNF green.

---

## sbt 2.0 / sbt-ossuminc 3.0 / Scala 3.9 — the riddl 2.0 baseline

**Branch**: `release/2` (created from `feature/sbt2-migration`, which
was off `development`). This is the **riddl 2.0** baseline. **Status
(2026-07-24): locally verified end-to-end.** A full
`sbt "; clean; tJVM; tJS; tNative"` passes from scratch — all modules
compile and all tests pass on JVM/JS/Native, 0 failures. Publishing,
the sbt-riddl scripted test, and the EBNF/GBNF validators all pass.
`main` has been merged in (format rectification + 1.30/1.31 option
registrations). Not yet merged back to `main`.

### Scala 3.9.0-RC4 (adopted 2026-07-24)
riddl 2.0 now targets **Scala 3.9.0-RC4** (was 3.8.4; RC1 verified
first, then bumped to RC4). Verified: all three platforms compile and
**all tests pass, 0 failures** on the RC — scala-js and scala-native
both publish 3.9.0-RC4 toolchains. Findings:
- **The lever is `With.Scala3.configure(version = Some(V.scala))` per
  module**, NOT `V.scala` / `scalaVersion :=` alone (sbt-ossuminc's
  `With.typical` pins its 3.8.4 default and is applied *after* the
  module `scalaVersion` setting, so that setting is a no-op). The
  README's `With.Scala3(version=…)` shorthand does not exist.
- **One code change needed**: `DefinitionValidatorTest` passed a
  `Seq[Message]` where `Messages` (= `List[Message]`) is required;
  3.9.0-RC4's stricter implicit search (scala/scala3#25910/#26210) no
  longer accepts it (3.9 is *correct* — Seq ⊅ List; 3.8.4 was lenient).
  Fixed with `.toList` (valid on both versions) — **not** a Scala bug,
  so no Scala Center report warranted.
- **When 3.9.0 final ships**: bump `V.scala` 3.9.0-RC4 → 3.9.0 and
  re-grep the `scala-3.9.0-RC4` path segments in CI/sonar/Dockerfile/
  CLAUDE.md.

### What shipped (committed on the branch)
1. **Source restructure** — all 7 cross modules moved (pure `git mv`)
   from `shared/jvm/js/native/jvm-native` to the projectMatrix flat
   layout: `src/{main,test}/{scala,scalajvm,scalajs,scalanative,
   scala-jvm-native}`. Python validators → `language/src/test/scalajvm/python`.
2. **Meta-build** — sbt 1.12.3→**2.0.2**; sbt-ossuminc 1.4.0→**3.0.3**;
   dropped scala-xml scheme / jsdom / bloop / tracked `metals.sbt`;
   `Dependencies.scala`: no portable-scala import, `V.scala=3.8.4`,
   `%%%`→`%%`.
3. **build.sbt** — projectMatrix `CrossModule(…, V.scala)`; `pDep`
   per-row deps; `jvmNativeSrc(dir)` wires `scala-jvm-native` onto the
   JVM+Native rows (utils/language/passes); removed tasty-mima +
   coveralls; fixed the `commandsNative = riddlLib_cp.native` bug.
4. **sbt-riddl plugin** — Scala 3 / sbt 2: `Setting[?]`,
   `PathFinder.get()`, **`Def.uncached`** on the File-/CompileAnalysis-
   returning tasks (`riddlcDownload`, `riddlcBinary`, the
   validateOnCompile hook — required, else the plugin won't compile);
   dropped With.Scala2 + `scalaVersion:=2.12.20`.
5. **CI/tooling** — corrected to the real sbt-2 `target/out/…` layout
   (see CLAUDE.md "Target-path layout"); `~/.sbt/1.0`→`~/.sbt/2`;
   dropped coveralls; release.yml checkouts get `fetch-depth:0` +
   `fetch-tags:true`; sonar/Dockerfile updated.
6. **Test/validator path fixes** — several tests + the python
   validators hardcoded old `…/jvm/src/test/resources` /
   `…/shared/src/main/…` paths; updated to the new layout.

### Key facts learned in verification
- **Run sbt with `sbt --server …`, not the default.** The sbt 2 CLI
  uses the `sbtn` native thin client, which talks to a **detached**
  server (`--detach-stdio`) — piped stdout comes back **empty** and the
  build looks hung. `sbt --server <cmds>` runs in the foreground with
  attached stdout. Don't trust `--server`'s exit code; grep the log.
- **Project IDs are clean** (`utils`, `utilsJS`, `utilsNative`, …) —
  the sbt-ossuminc `defaultAxes` fix (shipped 3.0.1) works.
- **scalajs-stubs** is JVM-only (`_3`); sbt-ossuminc **3.0.3** auto-adds
  it (`% provided`) to the JVM+Native rows of JS-targeting modules, so
  no consumer dep is needed. (3.0.0/3.0.1 did not — an interim manual
  workaround was removed once 3.0.3 landed.)
- **`riddlLib/js/{package.json.template,types}` did NOT need moving** —
  `riddlLibJS/npmPrepare`/`fullLinkJS` published the npm tgz fine.
- **DocSite / dropped `import scala.collection.Seq`** — load-time issue
  from 3.0.0 already fixed on the branch; build loads clean.

### Known non-blockers (deferred)
- **scalafmt**: earlier `scalafmtCheck` flagged ~327 files — pre-existing
  drift because this branch was based on `development`, *behind* `main`'s
  "Rectify code format style" commits. **Resolved** by merging `main` into
  this branch (2026-07-24) to pick up the rectification, then running
  `sbt scalafmt` / `sbt test:scalafmt` for the migration's own new files.
- **scaladoc**: `Compile/doc` fails on `@JSExport*`-annotated modules
  (`provided` scalajs-stubs isn't on the doc classpath). Non-fatal —
  `publishLocal` published every artifact anyway, and the release CI
  already disables doc (`set every Compile/doc/sources := Seq.empty`).
  Pre-existing category, not a migration regression.
- **Degradations accepted on sbt 2**: coveralls, TASTy-MiMa,
  sbt-idea-plugin, stable sbt-paradox. Regular binary MiMa + scoverage
  retained. A Scala-3/sbt-2 `sbt-riddl` requires sbt-2 consumers.

### CI caveat
CI path edits match the locally-observed `target/out/…` layout but are
only truly confirmable in a real CI run. The Dockerfile still installs
the sbt 1.10.7 launcher (honors `build.properties`; confirm on first
image build).

### Local dev note
Move `~/.sbt/1.0/github.sbt` → `~/.sbt/2/github.sbt` before building.

---

## Current Status

**Last Updated**: 2026-07-24

`main` is at **1.31.0**, clean and pushed. All work now lands
directly on `main` (no `development` branch — see CLAUDE.md
"Branch Strategy"). The 1.24.0 provide-tips work described in
earlier notebook revisions shipped long ago; see CLAUDE.md
"Validation Specifics" for its durable design notes.

**Two releases shipped this session:**

- **1.30.0** — registered `protocol`, `event_catalog_version`,
  `sql_dialect`, `sql_table` as recognized options, plus a
  repo-wide formatting rectification (below).
- **1.31.0** — registered `backstage_owner`,
  `backstage_lifecycle`, `backstage_type`, `confluence_space`,
  `confluence_parent`.

Nine generator-metadata options are now registered in total.
The pattern, and how to pick `validParents`, is documented in
CLAUDE.md under "Validation Specifics" — read that before adding
the next one. The recurring gotcha: `KnownOptions.*` lists have
**no consumers**; only `RecognizedOptions.registry` affects
validation.

**Formatting baseline is now clean.** `sbt scalafmt` +
`sbt test:scalafmt` were run across every module (227 files:
123 main, 104 test) and both `scalafmtCheck` and
`test:scalafmtCheck` now pass. Before this, ~227 files were
out of conformance, which meant a real formatting regression
was invisible in the noise. **Keep it clean** — if a diff
starts showing dozens of unrelated reformatted files again,
something has drifted. Note `sbt scalafmt` formats *main*
sources only; test sources need `sbt test:scalafmt`.

## JSON Input Method — phased roadmap

Branch `feature/json-input-method` (task:
`json-to-ast-input-method.md`). Adds `RiddlLib.parseJson(json,
origin)` + `validateRoot(root)`: a JSON document is mapped onto the
AST correct-by-construction (defaults applied), then validated /
prettified by the existing machinery. JSON is an alternative input
method for programmatic/AI generation. Lives in
`riddlLib/shared/.../json/` (`JsonModel`, `JsonAstBuilder`); uses
upickle 4.0.2 (`%%%`, Native-safe). Schema: `JSON_INPUT.md`.
Coverage ledger: `JSON_COVERAGE.md`.

**Goal:** eventually cover the *entire* RIDDL language,
incrementally; hardest constructs last, none silently dropped. The
ledger + a Phase-9 guard test keep coverage honest.

**Status: all 9 phases complete.** Every RIDDL definition, type
expression, statement, and interaction is expressible in JSON;
`JsonCoverageGuardTest` enforces that the ledger stays complete as
the AST evolves. 28 `JsonInputTest` cases pass on JVM + JS; Native
compiles. Remaining work is review/merge and any follow-on polish
(e.g. extending `metadata` beyond the four primary containers if
desired). Found+fixed a pre-existing prettify bug along the way
(Output double-rendered its verb alias).

| Phase | Scope | Status |
|---|---|---|
| 1 | Core DDD: domain/context/entity/type/field/state/handler/on-clauses/invariant/author/messages + common type exprs + `do` statement | **done** |
| 2 | Remaining type expressions (SI units, time, collections, URI/Blob/…, SpecificRange) + Constant/User/Enumerator values (Method→P3, Term→P9) | **done** |
| 3 | Full statement language + Function + Method | **done** |
| 4 | Adaptor/Streamlet/Inlet/Outlet/Connector/Relationship/Projector/Repository+Schema | **done** |
| 5 | Saga/SagaStep | **done** |
| 6 | Module + deep nesting | **done** |
| 7 | Epic/UseCase + interactions (the hardest tier) | **done** |
| 8 | Group/Input/Output/ContainedGroup | **done** |
| 9 | Metadata (Description/Terms/options/attachments/AuthorRef/Comment) + automated coverage guard test | **done** |

Deferred (out of scope, documented in the ledger): Include /
Import / BAST — file-reference mechanisms incompatible with a
self-contained, no-I/O, Native-safe JSON document.

Decisions: hand-written upickle ReadWriter for the polymorphic
`typeExpression` (cardinality wrapper vs `kind` tag); `Option`
encoded null-or-value via a custom `AttributeTagged` pickler; a
named `Record` maps to a RecordCase aggregate (a real RIDDL
`record`) so `state … of record X` resolves.

## Open Tasks in `task/`

**(none)** — queue is empty as of 2026-07-21.

Five riddl-generator option-registration tasks were closed out
this session and moved to `task/done/`: `register-protocol-option`
(completed in a prior session), `register-event-catalog-version-
option`, `register-sql-options`, `register-backstage-options`,
`register-confluence-options`. Each has its results appended,
including the reasoning behind any judgment call.

More of these are likely as riddl-generator grows generators.
They are near-mechanical; follow the CLAUDE.md recipe.

Completed task files live in `task/done/` (gitignored, local
hygiene only).

## Active Work Queue

1. **riddl-models validation errors** (handed off) — see
   `../riddl-models/TASK-fix-validation-errors.md`. As of last
   check, 45 of 186 entry points fail `riddlc validate`. Error
   categories: ambiguous path refs, `briefly` outside `with {}`,
   unresolved `EmailAddress`/`Year` types, decimal fractional
   parts. After fixes, add riddl-models EBNF validation to
   `.github/workflows/scala.yml` (mirror the riddl-examples
   pattern).
2. **TypeScript AST declarations** (low priority) — full AST
   hierarchy (Domain, Context, Entity, …) remains opaque on the
   JS side. Public RiddlAPI methods are declared. JS consumers
   are expected to use the facade, not the raw AST.
3. **Housekeeping** (low priority, none blocking):
   - **Delete the `development` branch** (local + remote) and
     `old-development`. `development` is fully contained in
     `main` (0 ahead, 39 behind as of 1.31.0), so nothing is
     lost. Deferred pending an explicit go-ahead.
   - **Fix `.claude/skills/ship/SKILL.md`** — it still prescribes
     the GitFlow pre-flight (fast-forward `main` from
     `development`) and post-release merge-back. Both contradict
     current policy; they were skipped for 1.30.0 and 1.31.0.
   - **Delete the stray `help` git tag.** Almost certainly a
     typo'd `git tag help`. Harmless, but it sorts to the top of
     `git tag --sort=-v:refname` and so leads the tag list when
     working out the latest release.
   - **Verify the `unset GITHUB_TOKEN` guidance for `gh`.** Both
     CLAUDE.md files and the ship skill say to unset the token so
     `gh` falls back to keychain credentials. In the 1.30.0 /
     1.31.0 sessions that produced "please run gh auth login" —
     the keychain was not reachable — and `gh` worked only with
     `GITHUB_TOKEN` **set**. May be specific to a sandboxed tool
     environment rather than the interactive shell; worth
     confirming which before trusting the documented advice.

## Blocked

(none)

## Scheduled

| Date     | Task |
|----------|------|
| At 2.0.0 final | **Drop `RIDDL_MODELS_BRANCH: release/2` from `scala.yml`** once the riddl-models 2.0 corpus merges to its `main`. The override exists because `RiddlModelsRoundTripTest` falls back to downloading a branch zip when there is no local checkout, and riddl-models `main` still holds 1.x models — so CI failed on the 2.0 grammar while local runs (reading the developer's `release/2` checkout) passed 189/189. The default in the test is `main`, so deleting the line is the whole fix. |

The CodeQL v3 → v4 upgrade that sat here is DONE — `upload-sarif@v4` went in
with the Node 20 action sweep, well ahead of its December 2026 deadline.

---

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

### AIHelperPass Design Rationale

(Shipped 1.22.0; impl at
`passes/shared/.../passes/ai/AIHelperPass.scala`.)

Distinct from ValidationPass: produces `Tip` messages (severity 0,
"soft" proactive guidance), works on incomplete models (no
ResolutionPass dependency), and rewrites resolution errors into
actionable Tips on a second path. Tip categories: Completeness,
Pattern, BestPractice, Relationship, Documentation. Entry points
exposed cross-platform via `RiddlLib.analyzeForTips` /
`analyzeSourceForTips`; `riddlc advise` is the CLI surface.

### Path-Identifier Usage Tracking (1.23.1)

`ResolutionPass.resolvePathFromAnchor` now records each anchor
and non-terminal intermediate component of every path
identifier as a **path usage** of `parents.head`. Storage is
in parallel maps `usesInPath` / `usedInPathBy` on `UsageBase`
(separate from the existing `uses` / `usedBy` to keep
`Usages.getUsers` semantics unchanged for downstream callers).

Filters:
- Self-references skipped via `user ne use`.
- Ancestor-qualified self references (e.g.,
  `state AState of fooBar.fields` inside `entity fooBar`)
  are filtered by `parents.exists(_ eq anchor)` — internal
  qualification does not count as external usage.

`UsageResolution.checkUnused`:
- Suppresses `is unused` when either direct or path usage
  exists.
- Emits a new `CompletenessWarning` for Types only when usage
  is path-only (addressable by path but never declared as a
  field's / state's type).

Public API on `Usages`: `isUsedInPath(d)` and
`getPathUsers(d)`. Tests in `UsageTest.scala` cover
positive / negative / mixed / scope / regression cases.

### EOF Brace Crash in Error Reporter (1.23.3)

Two `require` calls in `RiddlParserInput.annotateErrorLine`
were over-strict for parse failures at EOF — when a closing
`}` is missing, the failure's `At.endOffset` lands one past
the line range computed by `lineRangeOf`, the requires threw
`IllegalArgumentException`, and the outer `runMain` catch
surfaced it as `[severe] Exception Thrown` instead of a
normal `[error]` with file/line context. Removed the
requires; downstream slicing already clamps via `Math.min`.

Regression fixtures at `language/input/riddl-bad/{badDomain,
badEntity}.riddl` (3 opens / 2 closes by design) + a
`TopLevelParserTest` case lock this in. `badEntity.riddl` is
listed in `INCLUDE_FRAGMENTS` of both `ebnf_tatsu_validator.py`
and `ebnf_validator.py` so the EBNF-grammar CI job skips it.

### Native Scaladoc Race (1.23.4 mitigation)

Scala 3.8.3 scaladoc has a race in
`dotty.tools.scaladoc.renderers.Resources.allResources` that
crashes intermittently when multiple `doc` tasks run in
parallel under `publish`. It cost two releases (1.23.2 never
shipped; 1.23.3 needed a hand recovery). Workaround in
`build.sbt`: `passesNative` and `riddlLibNative` set
`Compile / doc / sources := Seq.empty` so the Native scaladoc
task is a no-op. JVM and JS scaladoc are unaffected. If the
race ever bites another Native module, apply the same one
line to its `.nativeSettings`.

### Scala.js Validation Performance

(Shipped 1.11.0; mostly resolved.)

Four-phase optimization made interactive validation practical for
the browser playground:

- **ParentStack** is now a class that caches its `toParents`
  (toSeq) result — replaces the previous type alias.
- ValidationPass micro-opts: single-pass handler classification,
  combined SagaStep validation, `recursiveFindByType` cache.
- `ValidationMode.Quick` skips `checkStreaming` and
  `classifyHandlers` in postProcess for interactive feedback.
- `IncrementalValidator` caches messages per-Context using FNV-1a
  fingerprints (`validator.reset()` forces full recheck).

ossum.ai's playground still uses a tiered Web Worker strategy for
very large models (BAST-loaded models skip validation since they
were pre-validated at build time).
