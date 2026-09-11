# Engineering Notebook: RIDDL

Records open work, blockers, and design nuances that future AI
sessions need to know. Release history lives in git tags and
GitHub release notes — don't reproduce it here.

## HANDOFF

Orientation for a session with no memory of this work. **Open work is in `BACKLOG.md`**;
durable facts are in `CLAUDE.md`; what a change TAUGHT us is in this NOTEBOOK's body.
Ask `git` for branch, tree and unpushed span — never trust a written answer to those.

### READ THIS FIRST — CLAUDE.md was split on 2026-09-11

It had reached 195,688 chars, past the 150k point where it stops being loaded in full.
Now 135,724, with two companions **that are as authoritative as the parts that stayed**:

- **`docs/claude/language-constructs.md`** — per-construct reference: syntax, AST shape,
  BAST tags, `FORMAT_REVISION` numbers, JSON keys, design rationale (A70, instance
  identity, A20, A55/A57, quiescence, `send … at`, `empty`, intentions, literals).
- **`docs/claude/build-and-api.md`** — adding a riddlc command, NPM, import vs include,
  writing a Pass, RiddlLib/RiddlAPI, workflows.

**The split rule: reference moved, traps stayed.** Anything whose failure mode is "not
knowing you needed to look" — Total Dispatch, the false-green family, `isEmpty`, the BAST
hazards, the corpus floor, FALSE ZERO, the parser CUT hazard — is still in CLAUDE.md.
**If you need a fact from a companion that you had no reason to look for, move it back.**
Line-number citations into CLAUDE.md from before this date are stale; two were repaired
(`BACKLOG.md:83`, `NOTEBOOK.md:619`), the rest were checked and there are no others.

### Build state — verified 2026-09-11 (afternoon) by running

**`2.1.1` is released** (tag on `20e72732d`), Scala 3.9.0 final. **BAST `FORMAT_REVISION` 24**
(unchanged by [1.25]: no AST-shape change).

**`../bin/riddlc` is `2.1.1-33-dd3c2d80`, dated Sep 10 — now well behind HEAD and it contains
NEITHER `handler-duplicate-special-clause` NOR the [1.25] port rules.** Nothing was published
or staged outside the repo on 2026-09-11, deliberately (Reid's call). The in-repo JVM stage at
`target/out/jvm/scala-3.9.0/riddlc/universal/stage/bin/riddlc` IS current and is what the
corpus census used. Confirm with `../bin/riddlc --no-ansi-messages version` against
`git describe --tags --long`.

**Ten language/validation changes since 2.1.1**, the first eight in that binary and the last
two NOT: `on quiescence <window>`; `send … at <instant>`; `stream-graph-cycle` as an infinite
MESSAGE loop; `msg-tell-crosses-unrelated-domains`; [5.7]; `saga-no-timeout` non-blocking;
`saga-step-no-tell` an Error; `ask` both ways; `handler-duplicate-special-clause`; and
**[1.25] — implied adaptor ports ABOLISHED, a missing port is a Missing warning for every
processor kind, rules abstain on the side they cannot read, an endpoint naming an adaptor is
`ref-wrong-kind`** (2026-09-11, see the entry below).

### Corpus — 1290 findings after [1.25], from a verified ZERO before

191 entry points, riddl-models @ `6d9e7ce8`. All attributable; three rows want Reid's ruling
(21 `source` streamlets that receive, 388 consume-only adaptors written `as flow`, 102 external
contexts that `yield` with no outlet). Table and reasoning in the 2026-09-11 (later) entry.
riddl-models has NOT yet been told — the Results in `task/done/` and a task drop are the
handoff; see "In flight".

### In flight

**[1.25] is DONE and committed (`2c2b8d5b2`, `b41886bc0`, `05f95348a`; CM `2927731` in the
ossuminc repo). NOT pushed at the time of writing — Reid's call, because the push turns CI
red:** `RiddlModelsRoundTripTest` gates on corpus Errors and riddl-models has 388 `as flow`
ascriptions to DELETE and 26 endpoints to repoint first (their task drop:
`../riddl-models/task/2026-09-11-implied-ports-abolished-26-endpoints-388-ascriptions.md`).
Task drops also in `../ossum.tech/task/` and `../riddl-generator/task/`. Three task files
closed to `task/done/` with Results. **One NEW task file is untriaged:**
`task/2026-09-11-external-context-unhandled-command.md` — arrived mid-session, not read.

### Traps a fresh session would hit

- **Missing warnings are DROPPED when `showMissingWarnings` is off, and `pc.options` is global
  test state.** A suite asserting one goes red only in the FULL run. Pin
  `pc.withOptions(CommonOptions.default)`.
- **Report AFTER measuring, not before.** The "23 endpoints" was a stale, unrecorded count; the
  real number was 26 and rising. Re-measure anything older than a day against the branch.
- **Endpoint paths name the PENULTIMATE segment** — `outlet <Context>.<Adaptor>.<Portlet>`.
- **A corpus census must capture the MESSAGE line, not just the header** — the kind is on the
  second line of every riddlc diagnostic.
- **A bare single-segment portlet name in a fixture is ambiguous** the moment two processors
  declare it; qualify the path.
- **A backtick in ANY model-authored shell string is command substitution** — see the parent
  `../CLAUDE.md`.

### Certainty

Verified by running this session: BEFORE census 0/191 (calibrated); AFTER census 1290;
JVM `utils` 148, `language` 76/760, `passes` 269/1828, `testkit` 2, `riddlLib` (JSON corpus
191/191 identity AND parity), `riddlc`; `tJS` green (111/438/317/1/149); Native `language`
72/745 and `passes` 266/1818; `cNative` for all. Both new suites canaried. **Known red:**
`commands`' `RiddlModelsRoundTripTest`, 171/191 models, all at "validate original" on the
414 new Errors — by construction until riddl-models migrates. **Not run:** `commands`/
`riddlLib`/`riddlc` on Native (CI covers them; the corpus row will be red there too).

**Run `/ossuminc-skills:check-tasks` in the new session** — triage is the driver's call.

## 2026-09-11 (later) — [1.25] landed: nothing is implied, a missing port is a STUB

riddl-models answered the feasibility measurement within hours (`task/2026-09-11-implied-ports-
the-correspondence-holds-for-80-percent.md`): our grep had looked only on the adaptor's own side,
the covering alternation is usually the FAR entity's `<Entity>Event`, 845 of 1056 port-less
adaptors can name an existing type, and they will declare every port themselves — inlets by
script, ~350 outlets by hand. That killed materialisation (the compiler authors nothing) and
left the abolition. Design negotiated with Reid in one sitting, five points:

1. Ports are declared or absent — for ANY processor. `Adaptor.arityShape`, `ImpliedEnd`,
   `resolveConnectorEnd`'s adaptor arm, AR9's implied typing and its ambiguity Error are gone.
2. A side the handlers NEED and the definition lacks is INCOMPLETE — a **Missing** warning,
   `???`'s kind. Reid: *"missing is missing, that's incomplete"*; not a Deprecation, because an
   omission is not a spelling. Named from the handlers: *"handles X, Y but declares no inlet"*.
3. **A rule abstains on the side it cannot read** — inlet rules wait for the inlet, outlet rules
   for the outlet, both-rules for both. Reid sharpened this from my "everything abstains".
4. A connector endpoint naming an adaptor is `ref-wrong-kind` again. Reid asked *"are you sure
   there are 23 … that isn't syntactically legal and never was"* — re-measured: **26**, the
   session that reported 23 never recorded how, and riddl-models' own campaign was writing new
   ones (`reactive-bbq/corporate/domain.riddl:34`, `from outlet MenuManagement.ToPrintingService`
   — the ADAPTOR). Their reply says *"our connectors name a qualified portlet path throughout"*;
   26 do not, and riddlc had been telling them it was fine.
5. Adaptors are not special: no shape carve-out, `as merge` legal, ascription rule untouched.

**Two scope rulings by AskUserQuestion**: the check is on EVERY processor kind (it absorbed the
entity-only 4h/4i block), and sender-side abstention is symmetric (a port-less entity that tells
draws one Missing warning, not that plus `msg-tell-target-unreachable`). **One departure from
Reid's pick, flagged in the plan and approved**: he chose "retire `entity-no-inlet`/`-outlet`";
I kept them emitted for entities because synapify's `EmitterConformanceTest.scala:160,166` keys
on them and a published code means the same thing forever. One rule, two spellings, documented
at the enum.

**The Missing/Completeness question, asked by Reid and settled by not merging.** The only
load-bearing difference is RANK: `isIgnorable`/`isActionable` (`Messages.scala:46-47`) split
exactly between Missing (2) and Completeness (4). Merging would be a rename-and-keep-both under
the compat policy — two names for one kind, not one kind. What went in instead is the admission
test, in `Messages.scala`: *Missing = the author OWES something unwritten; Completeness = the
written things do not CONNECT.* The rank oddity (a `???` body below an unused type) is [5.8].

**Corpus A/B — 191 entry points, riddl-models @ `6d9e7ce8`, JVM stage `2.1.1-40`.** BEFORE:
**0 diagnostics** (they closed their inbound campaign at zero the same morning; calibrated on a
28-warning probe). AFTER, 1290 across 186 models, every one attributable:

| rule | count | what it is |
|---|---:|---|
| `stream-processor-no-inlet` | 759 | 734 Adaptor (the migration population), **21 Source**, 3 Projector, 1 external Context |
| `stream-ascribed-shape-mismatch` | 388 | ALL `Adaptor … as flow` deriving **sink**: inbound adaptors that only consume (`do`), written `as flow` when the outlet was implied |
| `stream-processor-no-outlet` | 112 | **102 external Contexts that `yield`**, 6 Adaptor, 4 Repository |
| `ref-wrong-kind` | 26 | the endpoints naming an adaptor (list in the task Results) |
| 5 graph findings | 5 | in the same 4 models as the 26; their connectors no longer resolve |

Three of those rows need Reid's eye, and none is a bug in the check: **the 21 Sources** are
`streamlet X as source` with `on event …` clauses that `send` to their outlet — they RECEIVE
with no inlet, which nothing ever checked; they are flows in disguise. **The 388** are true by
the rule as ruled (the arity is knowable: (0,1) is a sink) but are 3/8 of the corpus's adaptors;
the fix is `as sink` or writing the tell and its outlet, and riddl-models' own reply says
roughly a quarter of these will get an outlet. **The 102 external contexts** yield events with
no outlet — the shape riddl-models' campaign was already giving `outlet <Ctx>EventsOut` to; not
exempted by default, per the plan, so Reid rules with a number.

**Things that bit.** (a) Missing warnings are DROPPED by the accumulator when
`showMissingWarnings` is off, and `pc.options` is global state other suites mutate: three suites
went red in the full run and green alone until every Missing assertion pinned
`pc.withOptions(CommonOptions.default)`. (b) The corpus census captured only the header line,
so "by kind" was a list of file:line — the kind is on the NEXT line. (c) `transmittedTypes`
named tells/forwards/sends and not yields, so 102 messages said "transmits messages" — caught
by the census, not by a test; pinned now. (d) A bare `outlet Out` in a fixture resolved to the
ADAPTOR's `Out` two processors away — CLAUDE.md's single-segment ambiguity trap, again.

Both new suites canaried: predicates disabled → 14 positives red, 13 negatives/returns green;
endpoint arm restored → the 2 wrong-kind cases red. `ImpliedPortTypeTest` deleted (it pinned
the mechanism); its one surviving behaviour, AR5 resolving a `let`-bound tell, moved to
`PortAbstentionTest`.

## 2026-09-11 — a ruling survived, its mechanism did not

Reid ruled: abolish implied adaptor ports. The plan was materialise-and-deprecate — put the
implied ports into the AST after resolution so `proc.inlets` is the single answer every rule
already reaches for, deprecate the port-less spelling, let prettify write the declarations out.
It would have turned riddl-models' ~2570-declaration migration into a formatting run.

**Then the measurement killed the mechanism.** `Inlet.type_` is a `TypeRef` — a reference to a
NAMED type — so a generated port can only point at a type that already exists. Of 411 port-less
adaptors, 355 have real clauses; only **165** handle exactly one type, **190** handle two to
five, and exactly **1** has a derivable outlet. All 355 contain `do` and `error`; one contains
`tell`. They are unimplemented stubs written in prose. **The information required to generate
the declarations is not in the models** — AR9's derivation was built to CHECK a type, and
generation needs a type to NAME.

So the ruling stands and the implementation is paused, with the numbers sent back to
riddl-models: their claim that this is *"a large mechanical migration … not a research problem"*
is the premise the ruling rests on, and it is false. **Not a corpus-cost objection — cost is
never a gate here — a feasibility one.** [1.25].

**Three of my own errors on the way there, all the same family: I reported before I measured.**
I wrote "the 31 `as source` adaptors stop erroring" (they don't — one that handles messages gets
a materialised inlet and still contradicts). I reported "zero refuse-everything adaptors are
wired" from a probe that found zero adaptor names among 1,379 endpoints *including* adaptors
that certainly are wired — the endpoint syntax is `outlet <Context>.<Adaptor>.<Portlet>`, so the
adaptor is the PENULTIMATE segment and my probe read the last. And I raised an alarm that 47% of
adaptors had no derivable port type, having conflated "refuses everything" (551) with
"port-less" (411); the overlap is 5. **Only the calibration caught the second one** — the habit
this repo already mandates for exactly this shape, and the reason the third got caught before it
reached Reid.

**Also, from reading one corpus body: a handler may declare at most one of each SPECIAL
on-clause.** Reid spotted two `on other` clauses in one handler — two catch-alls with no rule for
which runs — validating clean. Only `on quiescence` had the rule; `on other`, `on init`,
`on term`, `on activate`, `on passivate` were unchecked. `handler-clause-shadowed` could never
have caught it, and the quiescence check's own comment already said why: it collects
`OnMessageLikeClause` only, because a special clause names no message to key on. **The gap was
recorded next to the fix for one kind and left open for five** — the same shape as `error` being
made terminal while `terminate` was not. Quiescence keeps its own id and message: same rule,
different reason (a second idle clock, not a second residual-message policy).

## 2026-09-10 — silence taught a false rule, and the corpus paid for it in a day

riddl-models found `ask` validated for the far end's BEHAVIOUR and not at all for a CHANNEL: in
an adaptor touched by no connector, `tell` drew two Errors and `ask` drew nothing. **The damage
was not the missed mistake, it was the rule the silence taught.** They read "0 errors", concluded
*"wiring is simply irrelevant to it"*, wrote it down, and were about to apply it to 363 sites.
**"It validates" is the evidence modellers use** — a validator silent where the language has a
rule is not neutral, it is actively wrong.

Reid ruled both legs. The reply leg is the one I framed badly: I quoted his own earlier *"setting
up a reply actor … is the generator's concern"* back at him as evidence the reply path might be
the generator's too. His answer draws the line that makes it obvious — **the MECHANISM is the
generator's, the PATH is the model's.** A reply actor, a future, a correlation id are lowering
choices; whether an answer can physically get back is not one. **When a ruling says a mechanism
is free, that is not a statement about whether the thing it carries must exist.**

**The implementation was small because the question was already answered elsewhere.**
`checkAskReachability` is `checkTellReachability` asked twice over the same
`connectorAdjacency` graph, same exemptions, ~40 lines. I deliberately did NOT add the
"admitting portlet" check the task also asked for: `checkInletsAreReceived` and
`checkTellDeliverability` already ask it, and a third would report one fault twice. Recorded as
`[~]` in the task's criteria with an invitation to send a probe, rather than silently dropped.

**Timing decided the value, and it nearly slipped.** When I triaged this the corpus had ZERO
`ask query` sites and I argued the checks should land before the 363 were authored. By the time
I finished — one day — riddl-models had authored **75 across 42 models**, and the census found
**59 errors in 26 models** where a day earlier it would have found none. **Re-measure a corpus
claim at the END of the work, not just at triage**: mine went from "zero, greenfield" to "59
findings" while I was building the thing that measures it.

**They had built their own guard, and it disagrees with ours.** `sbt ac` in riddl-models exists
because riddlc had no check — and yet the question leg failed 40 times against 19 for the reply
leg. If their guard covered the outbound leg, those two facts do not sit together. I put that in
the task drop as a question rather than assuming the 40 are simply unmigrated: **a rule that
disagrees with a careful modeller's own guard deserves a second look from our side too.**

## 2026-09-09 — I asked which way to relax a rule that wanted tightening

riddl-generator reported that `saga-no-timeout` blocks code generation and asked for it not to.
Reid ruled that; the same task asked, without assuming, about `saga-step-no-tell`, which sits in
an identical position. I framed that as "should it also stop blocking?" and offered two options,
both of which relaxed it. Reid's answer went the other way:

> that's illegal … why are you wondering about how it generates code when it wouldn't? … if that
> situation does not raise an error in riddlc, it should. Every saga step must tell something ELSE
> to do something or there is, by definition, no action, which makes the step moot and pointless.

**The framing error is the lesson, not the outcome.** I reasoned from SYMMETRY — same fixture,
same severity, same consumer — and never asked the prior question of whether the rule was
correctly severe in the first place. The generation question I was weighing does not even arise:
riddlg's first gate is a model with nothing above a StyleWarning, so an illegal model never
reaches the generator. **When two rules look like twins, check that BOTH are right before asking
which way to make them agree.**

What actually separates them, and is now written in both: an absent timeout has a defensible
default (one hour, which riddlg records as an invention); an absent ACTION has none, and an
`AI FILL` hole marks code as MISSING rather than substituting a value. That is the bar written
into `RuleId.nonBlocking`.

**The simplest fix for the first half was wrong, and its own test said so.** riddlg proposed
demoting `saga-no-timeout` to a StyleWarning. That would have asserted the message "does not
change the MEANING of the model" — while the rule exists because the absent bound decides when
compensation fires — and, concretely, **every riddl-models `.conf` sets
`show-style-warnings = false`, so the whole corpus would have stopped seeing it**, against the
task's own acceptance criterion. I found that by making the change and watching the test go red:
its helper sets exactly that flag. So the exception went in `RuleId.nonBlocking` with
`Message.isGenerable` consulting it, and the per-kind ladder stayed honest. **"Most simply" in a
task file is the sender's proposal, not the ruling.**

**Seven fixtures moved and none of them justified weakening the rule.** All were sagas used as
scaffolding for tests about something else (initiate/terminate legality, a module round trip, an
invariant value walk, a JSON round trip). Repairing them means giving the step a real `tell`.
Two traps, one general and one specific:

- **`terminate` is TERMINAL, so an appended tell is unreachable** — the tell has to LEAD the
  block. The first repair failed for exactly this and the error named the right cause.
- **A `$stepOne` I wrote inside a `//` comment in an INTERPOLATED Scala fixture interpolated**,
  injecting multi-line statements into the comment and producing a parse error whose message
  ("expected sagaContent") pointed nowhere near it. **Prose inside an s-interpolated fixture
  string is code.**

Corpus cost of the Error: **zero**, all 6 riddl-models sagas already tell — measured with an
instrument calibrated on a known-positive first, since the first corpus model I tried for
calibration was fully clean and therefore calibrated nothing.

## 2026-09-09 — [3.8] had mostly evaporated before anyone worked it

Reid closed it: inside one context a connector may run from an adaptor's outlet to a contained
entity's inlet, because *"the adaptor is part of the context (its boundary) and therefore enjoys
the same privilege as other processors in that context."* No code changed — behaviour already
matched.

**Two thirds of the item had been answered by a change that landed three days AFTER it was
filed.** [3.8] was raised 2026-09-03 asking whether an adaptor's connectors must be
context-to-context; A103 landed 2026-09-06 and made the cross-context half an Error on its own,
leaving only the intra-context case — which ran head-on into the standing 2026-08-18 ruling that
intra-context wiring needs no ceremony. I found that by RUNNING the cross-context shape against
the staged binary (two Errors, one per end) rather than reasoning from the item's text. **A
backlog item filed before a large change is a snapshot; re-measure it against the branch before
working it**, exactly as `check-tasks` says to do for an incoming task file. The same discipline,
one repo inward.

**The item cited a green test as evidence, and that evidence was empty.** It pointed at
`SharedAdaptorTest`'s "allow wrapper adaptations", which carries the exact shape — but its
assertions are `domain.isEmpty must be(false)` and that the adaptor's id is `PaymentAdapter`. It
never asserts the absence of a boundary error, so a rule that started rejecting the shape would
have left it green and the "it PASSES today" note would have gone on reading as protection.
**Citing a passing test is not citing an assertion** — read what it asserts before treating it as
a pin. The privilege now has a real one, with the cross-context shape as its negative control.

**Where the ruling had to land is worth noting**: the CM's A103 paragraph said the adaptor is
"boundary surface, not content" of its context, which invites exactly the wrong inference here.
It now says explicitly that this is a claim about what CROSSES the boundary and nothing more —
being the boundary does not make an adaptor a stranger to its own context.

## 2026-09-08 (later) — [5.7]: two rules disagreeing about the same node

Reid ruled the tail rule follows the loop rule: a handler-less processor is a TAIL whatever its
shape, where it had been a tail only with no outlets. One line of code; the value was in finding
the principle that makes the two rules one rule.

**The reconciliation the backlog item guessed at was wrong, and the right one is simpler.** [5.7]
framed it as *"consistent only if a completeness question may extend benefit of the doubt that an
Error may not"* — a rule about SEVERITY. It is not: **an opaque processor lets NO rule assert what
it does with a message.** The loop rule may not claim the message comes back; the tail rule may not
claim it goes on. Severity never entered it, and the arity the processor happens to bear is
evidence for neither. Once stated that way there is nothing left to reconcile.

**Both of the item's factual predictions were wrong**, and I checked instead of repeating them.
It said `sink-reach.check`'s fixture would move — it does not, because that fixture declares no
Source, so Check 2 never runs on it; its golden is byte-identical. It said corpus population was
unmeasured — it is **zero**, across all 189 entry points. **A backlog item's predictions are the
author's notes at filing time, not findings**; the closing entry should say which survived contact.

**Calibrating the zero mattered and I nearly skipped it.** I had just been burned by an
uncalibrated zero in the same session, so I built a known-positive (a source into a ports-only
flow), ran it through BOTH the plain `validate` path and a corpus-style `.conf`, and confirmed the
rule id appears in each. Only then was "0 across 189 models" evidence rather than a null result
from a grep that could never have matched.

**The staged binary was the "before" instrument.** It predates this change, so counting the rule's
corpus population with `../bin/riddlc` measured the OLD behaviour without stashing or rebuilding
anything — the A/B was free. Worth remembering: after a restage the staged binary stops being
available for that, so take the before-count while it still is.

## 2026-09-08 — two rules were prescribing each other's refusal

riddl-generator reported an adaptor in `Shop` telling a processor in `Corp` — top-level
siblings — with no legal spelling. Reid ruled it a modelling error whose DIAGNOSTIC was the
defect: *"the error shouldn't say 'add a connector', it should say put both domains into a
common parent domain and add a connector between them."* Landed as
`msg-tell-crosses-unrelated-domains`.

**The report described a two-sided vise; it was three-sided, and the third side is what made
it riddlc's bug rather than a bad model.** Declare the far inlet and A6 demanded a connector;
add the connector and `stream-crosses-domains` refused it; **drop the inlet and AR5 refused
that**. Only with the third arm is it true that NO edit works — with two arms it still reads
as "pick the other spelling". I found it by testing the third arm rather than accepting the
report's framing, and it also handed over the mechanism: AR5 forced the far inlet to exist,
which removed A6's `target.inlets.isEmpty` exemption. **When a report says "both arms fail",
ask what the third arm is before believing the shape of the problem.**

**The two rules had been prescribing each other's refusal, and only reading BOTH revealed
it.** `stream-crosses-domains`' suggestion said *"model the communication with an adaptor and
messaging rather than a direct stream connector"* — exactly the shape A6 rejected. Fixing A6
alone would have left the contradiction intact facing the other way, with an author sent
round the same loop by a different sentence. **A contradiction between two rules has two
sites; a fix that touches one is half a fix.**

**The negative control that mattered was the REMEDY, not the absence of the error.** Asserting
that the new message appears proves the rule fires; it says nothing about whether the advice
leads anywhere — and "advice leading nowhere" was the entire complaint. So the test applies
the remedy (both domains under a parent, plus the connector) and asserts **0 errors**, and I
ran the same thing through the binary. Had I only pinned the message, I could have shipped a
better-worded dead end and called it fixed.

**Scoped to a re-diagnosis on purpose.** The new rule fires only where the old one already
did, so error surface is unchanged by construction — no model that passed now fails, and the
corpus is provably unmoved (355 / 190/190, the pre-change baseline) rather than measured-and-
hoped. The residual is stated rather than hidden: a target with no inlet still reports the
inlet first.

**I filed a language question that CLAUDE.md had already answered twice, and Reid retired it
the same day.** His remark — *"a `tell` is only relevant within a context (same process
boundary)... but it could be a `send`"* — read to me as a possible language change, so I filed
it as [5.8] rather than acting on it. Right instinct, wrong diagnosis. His clarification: **a
`tell` is semantically IDENTICAL to a `send`; the remark was about ability to LOWER, not
ability to MODEL.** [5.8] is deleted, its number retired.
Both halves of that were already written down, in this repo, in words: CLAUDE.md's 2026-08-18
entry says *"`tell` is no exception: it is the same operation as `send` unless a generator can
lower it more efficiently while keeping RIDDL's semantics"*, and the 2026-09-07 loop-rule entry
directly above records the same correction — *"semantically identical to `send` at the model
level, only riddlg's lowering may make them direct"*. **I had read that entry the same session.**
The trap already named in this HANDOFF is *check CLAUDE.md before justifying a design decision*;
the sharper form is that it applies to filing a question too. **A remark that seems to open a
language question is first a prompt to look up what the language already says** — an unnecessary
backlog item is not free, it presents settled semantics to the next session as unsettled.

**Vocabulary, on Reid's correction mid-task:** never "channel" for a **Connector**. Use the
RIDDL definition name for structural elements in every user-facing string. Fixed in the new
messages and in the surviving unreachable suggestion, which had said *"so the told message has
a channel"* since it was written.

**A red test found while certifying was NOT mine, and I proved that before saying so.**
`FindTypeVocabularyTest` had been failing on `main` since yesterday's `on quiescence` landed —
the kind was never added to `FindPredicates.knownKinds`, so `find -type on-quiescence` rejected
a correct query. Confirmed by stashing my work and re-running, then fixed in its own commit.
**`git stash` is the cheap way to turn "probably pre-existing" into a fact.**

## 2026-09-07 (night) — the loop rule was too general, not the idiom wrong

BACKLOG [5.6] lived for a few hours. Reid's reading: `stream-graph-cycle` had been stated as "no
per-type ring of connectors", but the thing to prevent is an INFINITE MESSAGE LOOP — an `on X`
clause that transmits X, whose message can travel the network back to an `on X` clause that
transmits it again. The unity of type across outlet, inlet AND on-clause is the test; a ring whose
emitting clause is `on quiescence` or `on command Book` cannot be re-entered by what it sends, so
the schedule-to-yourself connector was never a loop. No exemption needed — a correct rule already
excludes it. Landed as `checkMessageLoops`, replacing `checkStreamCycles`.

**Three rulings I had asked for went the other way, and each is recorded in the code.** I proposed
that a handler-less pass-through processor continues the walk (it does not: it does not validate
and passes nothing through — `isStreamTail`'s benefit of the doubt is a completeness posture, and a
loop is an Error); that `tell`/`forward` are direct edges (they are not: semantically identical to
`send` at the model level, only riddlg's lowering may make them direct); and I had not accounted
for union-typed portlets at all, which Reid added — Y-typed ports carry X, an `on Y` clause
handles X, an `on Z` clause emitting X loops nothing.

**Two red runs were the fixtures, not the check, and both are traps worth naming.** A bare
`outlet o` is AMBIGUOUS the moment two processors declare an `o`: single-segment paths search the
whole symbol table, the resolver records nothing, and the walk simply has no edge — the check
"passed" every positive case by finding nothing. The old connector-only rule never noticed because
it read connector paths, which are qualified. Found by instrumenting the resolver's `send` arm,
not by reading: three rounds of reasoning about refMap keys were all wrong. And an on-clause names a
message KIND — `on type Y` does not parse; an alternation of events is handled as `on event Y`.

**Canary and cost.** Disabling the check reddens exactly the seven positive cases. Corpus: the
old rule found nothing at 190/190 and the new one is strictly narrower, so zero movement is
expected — measured after restage, below.

## 2026-09-07 (evening) — temporal semantics: `on quiescence` and `send … at`

riddl-models measured that RIDDL could name a deadline but not act on one: 13 reminder
commands wired to nothing, 5 carrying a countdown field only a timer-holding sender could
compute, 18 of 18 time-caused events raised by a command nothing could send. Reid ruled both
requested features in (`68118db0f` the clause, `e0bf248b3` the scheduled send; CM §18.1, §4.5,
§20.7). Arithmetic stays rejected. The rulings are in CLAUDE.md; what the build taught:

**The plan's "deliberately unchanged" list contained a claim that was false, and only the test
caught it.** I wrote that a scheduled `send` of the yielded event "still discharges `yields`".
It does not — nothing a `send` does has settled a declared response since rc.19, and CLAUDE.md
says so in bold. The `SendAtTest` case asserting discharge went red for the right reason, and the
fix was to the TEST and to the comment I had already put in `ValidationPass` restating the wrong
rule. **An "unchanged" list is a list of claims about the current language; check each against
the rule, not against memory of it.** A wrong sentence in a code comment beside a correct check
would have taught the next reader the retired rule.

**The header value resolves under the CLAUSE.** `on quiescence Grace` with a non-Duration
constant was not rejected because `valueTypeExpr(vr, parents, …)` looked the ValueRef up under
the handler, while ResolutionPass had recorded it under the clause it was processing. Same fix
as the correlation's timeout block (`c +: parentsAsSeq`), same discovery route — a negative test
that stayed green.

**Search for the existing helper before writing the sibling.** `isDurationTypeExpr` shipped in
the morning following aliases with no cycle guard; `underlyingTypeExpr` sat 7,500 lines below
with exactly the `eq` visited list CLAUDE.md prescribes. Both alias walks now share
`aliasFreeTypeExpr` (aliases only, never cardinality — `TimeStamp?` is not an instant).

**Three fixture facts that cost a run each:** `let when = …` does not parse, because `when` is a
keyword; `send` does not parse in a function body, so a test that hides an `initiate` inside the
instant to prove the value walks see it has to live in a correlation fold instead; and
`ZonedDateTime` is spelled `ZonedDateTime(UTC)` — a quoted zone and a bare `ZonedDateTime` both
fail to parse.

**The committed `NotImplemented.bast` must be regenerated FROM ITS OWN DIRECTORY.** Run from the
repo root, `bastify` produced 115 bytes with 48 differences (the origin path is interned); from
`language/input/import/`, 93 bytes with exactly one byte changed — the revision. The stage
launcher (`target/out/jvm/scala-3.9.0/riddlc/universal/stage/bin/riddlc`) is the tool; `set
riddlc/Compile/run/baseDirectory` fails with "URI is not absolute".

**The restaged binary found what the suites could not: the ruled idiom trips a ruled Error.**
"Schedule to yourself" needs a connector from your outlet to your own inlet; `stream-graph-cycle`
reports a self-loop as a cycle of one. Neither rule is wrong on its own, no test covered the
pair, and the corpus has no population to show it — it surfaced only because I validated the
fixture I had just written with the binary I had just staged. Filed as BACKLOG [5.6] for Reid;
the fixture schedules to a downstream sink meanwhile. Third instance of the HANDOFF trap above
in one week: **a new rule's tests measure the rule, never its interaction with rules already
present.**

**Corpus movement: zero, as an additive feature should have.** 190/190 round trip, 191/191
JSON identity, TatSu 119/142 (+1 for `send-at.riddl`). That is the expected shape — a construct
no model uses cannot move the corpus — and it is evidence about the corpus, not about the rules.

## 2026-09-07 (later) — the direction advisory rewarded the unmigrated shape

riddl-models: `adaptor-direction-advisory` fires on every A103 outbound adaptor — own event
handled, far command produced through a `let` and a `send` — and stays quiet on the unmigrated
`on command Far.X { do "..." }` placeholders. Four findings, heading for ~674. Verified on the
current binary against the corpus (6 findings in 6 models, all the migrated shape) and against a
one-file reproduction. The advisory read only the `on`-clauses' handled types; it now counts a
reference to the target context anywhere in the adaptor — handled types, transmitted operands via
`clauseOperandType`, `let` ascriptions, declared portlet types.

**The reproduction did not reproduce, and that was the second bug.** My first probe used a
qualified referent, `to context D.NotificationService`, and the advisory stayed silent; the same
model with a bare `to context NotificationService` warned. The advisory resolved the referent with
`resolvePath(referent, adaptor's parents)`, but `ResolutionPass` records an adaptor's `referent`
under the ADAPTOR — the trap `hasAdaptorFor` already documents — so a qualified referent never
resolved and the advisory (and the direction-kind Errors sharing its block) silently skipped.
**When a check does not fire on a case you built to trip it, the first suspect is that the check
did not run.**

**A test that is green because the check never ran is vacuous, and I wrote three of them.** The
qualified-referent "should not fire" cases passed before the fix for exactly that reason. The
bare-referent variant is the one that was red, and it stays in the suite so the positives mean
something.

**Fixing the lookup woke a dormant rule.** `adaptor-inbound-wrong-message` (an inbound adaptor
handles the far context's events and results, not its commands) had been skipped for every
qualified referent too. It ran on one A103 fixture of mine whose inbound adaptor handled the far
context's COMMAND, and on nothing in the corpus (190/190). The fixture was wrong, not the rule; it
now handles the far context's event. Same lesson as AR5: resolving something you previously
missed exposes rules that were only ever half-running.

## 2026-09-07 — AR9: an implied port has a type, and AR5 was fighting AR6

riddl-generator, lowering A103, found that a connector between two implied adaptor ports was
never type-checked: `validateConnector` compared two DECLARED portlet types and I had
deliberately skipped the implied case in the permissive half. Landed as `fab94f996`.

**Verifying the task exposed a contradiction in what I shipped the day before.** I probed the
exact shape AR6 demands — outbound adaptor telling `to context Ful` over a connector into Ful's
inbound adaptor — with the tell written as a constructor, and AR5 rejected it: "no inlet on
Context 'Ful' admits Command 'Receive'". AR5 looked only at the far context's OWN inlets; AR6
had just made the far ADAPTOR the required landing point. Two rules, one legal shape, no
spelling satisfying both. It was invisible in the corpus for one reason: every adaptor tell
there is `let ship: type T = prompt(...)` then `tell ship to context C`, and AR5 never resolved
a `let`. The operand resolution AR9 needed would have reddened every correct pair at once.
**Resolving an operand you previously ignored can expose a rule you already shipped.**

**`on other { error "..." }` is a refusal, not acceptance.** First implementation used
`receivesMessageType` for "does the far adaptor accept this type", and two AR9 tests stayed
green with the implementation absent — because every fixture's adaptor ends with `on other {
error "unexpected" }`, which that helper counts as handling everything. Right for a delivery
question ("does something receive it"), wrong for a type question. `adaptorAccepts` is the
stricter sibling: a specific clause admitting the type, or an `on other` that does something
other than `error`. The corpus writes the refusal idiom in every adaptor; without the
distinction AR9 would have proven nothing corpus-wide.

**The SOURCE decides what a wire carries.** riddlg had worked this out to reach 0 `NOT
DELIVERED`: preferring the receiver's handled type typed an outlet as `OrderSubmitted` for an
adaptor that demonstrably sends `ReceiveTicket`. Adopted as written, including "several
distinct told types is an ambiguity, never the first taken" — `adaptor-implied-outlet-
ambiguous`, reported once by `validateAdaptor`.

**The task's own corpus prediction was already stale.** It named three or four mirrored adaptor
pairs in reactive-bbq; riddl-models' A103 migration had since deleted the outbound halves and
wired context-owned outlets straight into the inbound adaptors, which carry what those adaptors
handle. What remains is ONE true positive: `MenuReleaseEvent Distribution`
(`reactive-bbq.riddl:53`) carries the three MenuRelease events from a declared alternation
outlet into `FrontOfHouse.FromMenuManagementItems`, which handles the MenuItem events. Two
adaptor families, one wire crossed. Corpus 189/190; filed for riddl-models.

Numbers: `passes` 1747 (+12, 7 red before), Native 1735, JS 317; `language` 757; `riddlLib`
157; corpus 189/190.

## 2026-09-06 — A103: the adaptor IS the boundary, both halves in one day

riddl-generator filed two task files: the PERMISSIVE half (AR1/AR3/AR4/AR5, permissions only)
and the ADAMANT half (AR2/AR6/AR8 and the ascription Error), sequenced so the corpus could
migrate between them. Reid ruled: do both now; riddl-models and riddl-generator catch up from
the restaged binary. Landed as `a819670d7` then `1a434ced5`. Durable statement in `CLAUDE.md`
§ Validation Specifics; CM §§7.2, 7.7, 8.1 were patched by riddl-generator's session first.

**One abstraction, seven sites.** Seven checks each called `resolvePath[Outlet]`/`[Inlet]` on a
connector end. Making an adaptor a legal endpoint through all seven independently would have
been the "one derivation written seven times" defect this notebook keeps recording. So
`ConnectorEnd` (`DeclaredEnd` | `ImpliedEnd`) with `connectorFrom`/`connectorTo`, and every site
asks it. The one place I had to think per-site was what an implied port MEANS to each check:
no type (skip agreement; carry nothing on the cycle graph), one port (cardinality one), not
declared (never "unconnected"). Writing those four answers down was the design; the code
followed.

**The unattached-port check had a latent pairing bug the new shape exposed.** It collected
(outlet, inlet) PAIRS, so a connector whose one end failed to resolve dropped its OTHER end from
the in-use set and reported a perfectly connected port as unconnected. Harmless while both ends
always resolved or neither did; with implied ends, a false warning on exactly the shape being
introduced. Each side is collected independently now.

**The CM example did not parse.** riddl-generator's patch spelled the endpoint `to adaptor
ToBillingSystem`, but the same task recorded Reid's choice of NO grammar change, so the
accepted spelling is `to inlet Billing.ToBillingSystem` with the path resolving to the adaptor.
Corrected the CM rather than adding syntax the ruling declined. A spec author writing the
example they WISH parsed is a shape to watch for.

**A permissive half that stays green needs the count of what it will later break.** All 31
`as source` adaptors in the corpus declare exactly one outlet; with implied ports they derive
`flow`, so the EXISTING ascription check would have reddened all 31 on the permissive commit.
The count came from `grep -A6` on the declarations, before any code. The fix was a
declared-only reading accepted for adaptors only, removed again by the adamant commit two hours
later — short-lived scaffolding that let the two commits be bisected separately.

**AR8 found eleven of our own fixtures doing the thing.** Entities and repositories publishing
on their context's outlet, or on a sibling `source`'s, because that was the shortest way to
get a `send` into a test. Same edit riddl-models will make ~200 times: give the sender its own
outlet. And a bare outlet name that collides with another processor's outlet of the same name
resolves ambiguously — `CompletenessTest`'s `out` versus the source's `out` cost one extra
round; name the new port distinctly.

**Corpus at `bb17dd6ec` after both halves: 9 of 190 red, all on these rules.** 206
`stmt-outlet-not-owned`, 32 `as source`, 14 + 11 bypasses, 1 far end without an admitting
inlet. Not a rule to soften; filed in both task files' Results.

## 2026-09-04 (later) — a chain ends where its message is consumed; and no cycles

riddl-models' task: A6 turned every terminal sink in the corpus into a flow (a log that records
to its repository must own the outlet it writes on), so `stream-source-reaches-no-sink` fired 42
times on definitions nobody touched, and no wiring could clear it. Reid ruled; landed the same
day. Durable statement in `CLAUDE.md` § Validation Specifics and CM §8.1; this is what it taught.

**The rule was the same defect as 2026-08-14, at the other end of the same walk.** That day
`75a791682` stopped asking for a Source SHAPE at a chain's head because reactive-bbq entered its
graph through an application context fed by users. The tail side kept asking for a Sink SHAPE
for three more weeks, and it took A6 to make the asymmetry bite. When a rule is fixed at one end
of a symmetric structure, look at the other end the same day.

**"Same type" was the design decision, and Reid made it, not me.** I proposed "any send/tell/
forward disqualifies" as the strict, safer reading. Reid chose: a processor that receives an
event and sends a `Persist` COMMAND has consumed the event. The consequence is that a transform
(E in, F out) ends E's chain and starts F's, and F's fate is then guarded by the unconnected-
outlet check alone. Weaker than the strict reading, deliberately, and recorded so nobody
"tightens" it back.

**The sender's diagnosis of MY code was wrong, and verifying it first saved the wrong fix.** The
task file's second cause — "the walk stops dead at a context inlet" — read plausibly and would
have led to teaching the BFS to traverse contexts. The graph is per processor; it already does.
A ten-line probe settled it before any design. Same lesson as A6's "not implemented" report on
2026-09-02: a sender describes what they observed, in the mechanism they imagine.

**The cycle check had to be per TYPE or it condemned every request/response pair.** The first
sketch was a processor-level DFS. FrontOfHouse sends Kitchen a command; Kitchen sends FrontOfHouse
an event: a loop of processors, no loop of messages. Grouping edges by what the outlet carries
(alternation members expanded) makes the rule say what Reid meant — a message must not be able
to return to a processor it passed through — and it fires on **zero** corpus models.

**Four of our own fixtures were self-loops**, an outlet wired to its own inlet as a lazy way to
get a connector into a test: `dokn.riddl` and three in `StreamValidatorTest`. The suite surfaced
three; a sweep found the fourth. **BSD `grep -E` silently ignores backreferences** — my first
sweep "found nothing" for exactly that reason and I nearly reported it clean. Use perl.

**A `???` body needs no exemption from the tail rule**, which I had planned to add: `???`
replaces the whole body, so no inlet is declared and no connector can reach it.

**And `cd` in a Bash call persists into the next one.** A TatSu run left the shell in the Python
directory; the next `sbt -batch` booted a brand-new sbt project THERE (`project/build.properties`,
`target/`, its own server) and reported four modules "exit 1" that never ran. Absolute paths.

Numbers: corpus round trip 157 -> 190 of 190; `passes` 1703 (+9, canaried), `language` 757,
`riddlLib` 157, TatSu 118/141.

## 2026-09-04 — a full clean-cache regression, and a guard that had scanned nothing since sbt 2

Reid asked for the complete suite from a clean state overnight. Procedure and numbers are in
HANDOFF § Certainty. Two things worth keeping.

**`ESMSafetyTest` had been CANCELED on every run since the sbt 2 upgrade, locally and in CI.**
It globbed `riddlLib/js/target/scala-*/riddl-lib-opt/main.js`, the sbt 1 per-module layout;
sbt 2 writes `target/out/sjs1/scala-<ver>/riddl-lib/riddl-lib-opt/main.js`. The suite's
`assume(bundle.isDefined)` turned "bundle not found" into a cancel rather than a failure —
correct when the bundle has genuinely not been built, and indistinguishable from "looking in
the wrong place". So the one test that keeps `import 'x'` patterns out of the shipped npm
bundle (the ESM-shim rewrite hazard in CLAUDE.md § JS) had not read a bundle in weeks, while
`Tests: succeeded 156, failed 0, canceled 1` read as green. **A `canceled 1` in a summary
line is a question, not a footnote** — same family as `Suites: completed N` being short.
Fixed to glob the sbt 2 tree, taking the NEWEST `main.js` because stale `scala-3.9.0-RC4`
and `-RC6` directories still sit beside `scala-3.9.0` and a directory-listing order would
have picked one of them silently. Re-run: it scanned the 11.7 MB bundle and passed.
**And wired into CI**: the JVM row never builds a bundle, so even the fixed test would
cancel there; the JS row already runs `riddlLibJS/fullLinkJS` for release artifacts, and now
runs the scan right after it, against the exact bundle that ships.

**The 33-model corpus red is precisely the filed set.** Classified every diagnostic across
the 33 failures rather than trusting the count: 511 unreachable + 27 adaptor-target, zero
other messages, identical failure sets on JVM and Native. That is the whole of the red;
nothing in riddl needs fixing for it, and the migration is riddl-models' (their task file
is still open). Not softened, per the standing rule.

Also seen and filed rather than fixed: `scripts/pack-npm-modules.sh` has the same stale
path and cannot have worked since the upgrade — BACKLOG [5.5]. The scripted test's
`[error] … 'Missing' was not found` lines are its OWN broken-model fixture asserted with
`->`; the test passed.

## 2026-09-03 (later) — I inverted a rule CLAUDE.md states in terms

riddlg reported that A6 accepted a delivery from a sender with NO OUTLET AT ALL, over a
connector starting at the sender's CONTEXT. They were right, and the cause was mine:
`9d3c69aba` counted ancestor contexts as reachability origins, and I defended it in the
commit message with *"the context is the port at the boundary"*.

**`CLAUDE.md:1397` says the opposite, in terms**: *"An entity cannot publish on its
context's outlet … the FIRST step is the entity's own outlet and no context-level port
substitutes for it."* Same paragraph I had read while writing the A6 work — I took the
"intra-context needs no ceremony" half and inverted the half above it.

**The lesson is not "read CLAUDE.md".** It is that I wrote a JUSTIFICATION for the origins
decision, which is exactly the moment to check whether the thing being justified is already
ruled on. A design note that reads persuasively is the strongest signal that it is
load-bearing and therefore worth verifying — I was confident enough to explain it, and the
explanation was a paraphrase of a rule that says the reverse.

**Two days, three rules, and each broke the one before.** A6 became an Error (2026-09-02),
which created a contradiction with `stream-crosses-domains` (fixed 2026-09-03), and A6's own
origins decision was wrong from the start (fixed the same day). Every one of them shipped
green with canaries and corpus measurement. **What none of that measures is interaction
with rules already present**, and this is now the second instance in two days.

**A test caught a design error in the adaptor rule that no amount of reading would have.**
My first version forbade an adaptor sending to its OWN outlet — which is how an adaptor
emits at all. `SharedAdaptorTest`'s wrapper fixture went red and was right to: the rule
forbade the very shape it exists to require. An in-repo fixture failing is evidence about
the rule, not just about the fixture.

**Corpus: 33 of 190 models, 538 findings, and the shape matters more than the number.** 412
of the 511 reachability findings have a PROJECTOR as sender — one repeated pattern, not 412
independent defects, and the migration task says so. Reporting "511 errors" without that
breakdown would have made a day's mechanical work look like a crisis.

**riddl-models pre-empted us**: they migrated all 35 reactive-bbq adaptor tells to
`to context` at `b4b07053` before the rule landed, which made the report's own "29 of 35"
stale by the time I acted on it. Checked rather than repeated — the check-tasks discipline
earning itself twice in one day.

## 2026-09-03 — the contradiction my own rule created

A6 becoming an Error left reactive-bbq's `Corporate -> Restaurant` tell with **no legal
spelling**: omit the connector and A6 errors, add it and `stream-crosses-domains` errors.
riddl-models found it, fixed 12 of the 13 sites, and filed the thirteenth back.

**This is the trap CLAUDE.md names — "two checks forming a demand no legal spelling
satisfies" — and I shipped one the day before without noticing.** Worth sitting with: the
A6 work was careful about its own blast radius (corpus census, canaries, an exemption
adopted on evidence) and none of that care asks *what other rule does this now collide
with*. A correctness rule can be right in isolation and still make a legal model
unwritable, and nothing in the test suite or the corpus sweep detects that — the corpus
showed 13 errors, not 13 errors of which one is unfixable.

**Reproduce both halves before believing a reported contradiction.** I rebuilt the
sibling-domain probe from scratch rather than reuse theirs, and confirmed prettify emits
zero files on a validation error. Their report was accurate in every particular, which is
worth knowing precisely because I checked rather than assumed.

**Ruled for siblings, implemented as common ancestor.** The rationale is relatedness, not
depth: nothing separates `Corporate.Finance -> Restaurant.FrontOfHouse` from `Corporate ->
Restaurant`. `domainOf` returned the NEAREST enclosing domain, which cannot express that
distinction at all; `domainChain` walks the ancestry and intersects **by identity**, since
`Definition.equals` is structural and `contains` would fake a shared ancestor between two
distinct same-named domains.

**The message had to move with the rule.** It said "crosses a domain boundary", which is
now false — crossing is what is permitted. A diagnostic whose text outlives its rule is
worse than one that never existed, because it teaches the wrong model.

**A test that pinned the old behaviour was INVERTED, not deleted.** `StreamValidatorTest`'s
cross-domain case used siblings under a common parent — exactly the permitted shape. Kept
pointing the other way, as the `reply` un-deprecation was, so the record shows the rule
once ran differently. Rejection coverage moved to a suite that can express top-level
domains, which `parseAndValidateDomain` cannot — a constraint that is itself why the old
fixture used siblings.

**Verified the fix by BUILDING the fix on a copy**, not by reasoning that it would work:
4006 definitions / 1 error became 4009 / 0 errors, 0 warnings. That also caught something I
had warned about and got wrong in the reassuring direction — I said the new connector would
draw a persistence nudge; it does not, because I wrote it `persistent`. The recipe handed
back to riddl-models says so explicitly, since they would otherwise hit it.

## 2026-09-02 — a check that answered a weaker question, and an exemption adopted on evidence

Two riddlg tasks. The CM one was documentation; the A6 one is the interesting record.

### The check was not missing, and the reported hypothesis was wrong

riddlg reported six unreachable `tell`s validating clean, and guessed the warning was
unimplemented or blind to the `to context` form. Both plausible, both wrong:
`msg-tell-target-unreachable` existed, ran, was in the binary they used, and fired
correctly on that exact form in isolation — I calibrated on a positive case before
trusting the zero, which is what stopped me chasing their hypothesis.

**It asked whether ANY connector reached the target.** Every target that anything feeds
satisfies that. **Reachability is a question about a PAIR and `collectedTells` stored one
half of it** — so the check could not have answered it, whatever its predicate said. Their
own connector analysis contained the answer (*"none joins any of the six pairs"*); it just
drew the wrong conclusion about the cause. **When a report names a cause, check the cause
separately from the symptom** — the symptom reproduced exactly and the cause was elsewhere.

### The exemption is the durable methodological point

Making it an Error cost **37 in-repo fixture failures**, and my first instinct was to warn
Reid that the blast radius exceeded the six reported sites. Then the corpus census came
back: **1 model of 190**. My framing had been wrong, and I said so and continued rather
than leaving a bad number standing.

Wiring the two shared fixture files cleared 9. That still left 22 minimal fixtures needing
bespoke channels — at which point exempting *a target that declares no inlet* became worth
testing, on the precedent `checkInletsAreReceived` already set (its no-handlers exclusion,
where "fixture churn went from 7 to zero" was recorded as EVIDENCE the existing diagnostics
covered those cases).

**The adoption criterion was the corpus, not the churn**, and this is the part to keep: the
exemption kept **all 13** reactive-bbq sites while taking fixtures from 22 to 3. Had the
corpus gone quiet it would have been wrong at any fixture cost, and that sentence is now in
the code so the next reader can check the reasoning rather than the outcome.

### A pinning test that would have proved nothing

The test pinning that exemption FAILED first, usefully. Its first draft used a `???` body —
which the standing `???` ruling exempts from the *companion* check too, so both were silent
and "silent" demonstrated nothing. A real body makes the companion diagnostic reachable.
**An exemption test must show the other check firing, or it is indistinguishable from the
rule not running at all.** The code comment now states the limit instead of overclaiming
"already diagnosed".

### Smaller things worth keeping

- I wrote an intra-context exemption and removed it: it contradicted the 2026-08-18 ruling
  that a `tell`'s connector should exist. Checking a ruling before shipping a plausible
  simplification is cheaper than the alternative.
- Three attempts at literal-block replacement failed on non-unique anchors before I switched
  to a positional patcher. **Repeated failure of the same technique is the signal to change
  tools, not to retry more carefully.**
- Four node counters moved by exactly +3 and a token count by exactly +34, attributed
  (3 comments + 5 + 5 + 21) rather than bumped to whatever the run printed. Two independent
  traversals agreeing on a delta is evidence about the tree; a count edited to match output
  records nothing.

## 2026-09-01 — a workaround that expired when someone else acted

riddl-models dropped a task saying their `release/2` had merged and been deleted, so we
could remove `scala.yml`'s `RIDDL_MODELS_BRANCH: release/2`. Priority "Low — CI is green
either way."

**By the time we read it, CI was not green either way — it was broken.** Deleting the
branch is what broke it: `git clone --branch release/2` now fails outright, and the step
has no `continue-on-error`. The last green run predated their deletion by hours.

**The durable shape: a workaround whose expiry condition is ANOTHER REPO'S ACTION turns
into a failure at the moment they act, and we are not the ones who trigger it.** That is
precisely the case that has to be tracked on our side — and it was not. The 2026-08-27
task file we sent *told them* it was "filed in riddl's backlog as scheduled work". It
never was. It lived in two workflow comments saying *"REMOVE this line when riddl-models
merges to `main`"* — a note to whoever happens to open that file. **A promise made to
another repo is not tracking.** Filed and closed as [3.7] so the lesson has a home.

**There were three pins, not the one the task named.** The env var (`:34`), its use in the
clone (`:107`), and an INDEPENDENT `ref: release/2` in the `ebnf-grammar-validation` job's
`actions/checkout` (`:330`) — a different mechanism, added later, for the same reason.
Doing what the task literally asked would have left CI red and looked like the fix had
failed. `grep -rn "release/2" .github/` is what settles it, not the name in the request.

Verified rather than asserted: a fresh default-branch clone plus the EBNF validator gives
**191/191**, which is exactly what that job runs. Corpus floors (189/190) hold at 191.

**Their numbers needed two corrections**, both small: they predicted 240
`stream-processor-keyword` deprecations (it is **242**, matching what we measured before
shipping [5.1] and the 242 `streamlet` declarations now in their corpus), and named `main`
as `022705ea` (now `866b59b9`, two doc-only commits later).

**Genuinely still open, and ours:** no published tag carries `streamlet` — `2.0.0` predates
[5.1] by 9 commits — so anyone validating that corpus with a *released* riddlc sees 242
deprecations. Our own CI is unaffected (it builds riddlc from the branch under test). They
asked for a nudge when such a tag exists; that is a release decision, not backlog work.

## 2026-08-31 (later) — [5.1]: `processor` becomes `streamlet`

The first item filed under the never-delete rule, and a good demonstration of it: two
spellings, one parser, one AST node, and the old one keeps working indefinitely.

**What made it more than a keyword swap:**

- **The alternation had to be FACTORED.** `Keywords.keyword` ends in a cut, so
  `Keywords.streamlet | Keywords.processor` as two separate parser branches would let
  whichever matched first win outright and make the other unreachable — the hazard
  `bastImport` and `ulidAttachment` already document. One `.!` capture across both, then
  branch on the captured text.
- **The shape-keyword deprecation had to move too.** It said *use `processor X as flow`*
  — pointing an author from one deprecated keyword at another. Nine `.check` goldens
  carried that string.
- **The dual dispatch bit again, and was caught by design this time.**
  `AST.Streamlet.format` and `RiddlFileEmitter.openDef` are the same decision written
  twice; the round-trip suite canaries BOTH, and reverting either reddens it. That is the
  same pattern that, twenty-four hours earlier, had a golden test pinning
  `EntityReferenceTypeExpression.format`'s WRONG copy.
- **`RuleIdTest` rejected my first code, correctly.** `processor-keyword` has no known
  subject; `processor` is not in the closed vocabulary. `stream` already was, so
  `stream-processor-keyword` needed no new subject. The guard did exactly what it exists
  for: a rule that fits no subject needs a subject added, never an exemption. The old
  spelling was never published, so it left the append-only ledger rather than being
  retired.

**A corpus count inflated by prose — the second time in two days.** A loose grep scores
455 uses in riddl-models, but only 242 are declarations — the rest sit inside string
literals, 195 of them the one shape `error "Unexpected message for processor X"`. Real declarations: **242 in riddl-models, 28 in riddl-examples, 14 here.**
Yesterday the same mistake ran the other way, reporting 13 uses of `reference to` that
were all prose. **Grep the declaration SHAPE, never the bare word** — a keyword that is
also an ordinary English word will always be quoted somewhere in a corpus.

Migration task files dropped in both corpora. Nothing breaks: it is a deprecation, and
the rule carries a constant fix, so `validate --fix --fix-rule stream-processor-keyword`
does the whole thing.

## 2026-08-31 — five post-2.0.0 tasks, and a rule that now binds all of them

`/ossuminc-skills:check-tasks` triaged five requests from riddlg and synapify. Four were
small; the fifth reshaped how entity instances are referenced. Before any of them, Reid
set the constraint they all had to satisfy: **from 3.0 onward a deprecated name is never
deleted** — rename freely, deprecate freely, but the old spelling keeps working forever.
That is now `CLAUDE.md` § Backward Compatibility rule 3, and `[5.1]` (deprecate
`processor` in favour of `streamlet`) is the first item filed under it.

**Every one of the five was BIGGER than the report said it was.** That is the reusable
observation: a reporter names the instance that bit them, and the instance is a sample.

- `find -shape` rejected nothing — but neither did `-intention`, `-cardinality` or
  `-option`. Four selectors, one missing validation helper. The intention spelling was
  also wrong in a second place, so `normalizeIntention` had to be applied to the
  validator AND the predicate, not one of them.
- Four keywords were reported missing from the tokenizer tables. **Seventeen were.** The
  wanted fix — `StringIn(Keyword.allKeywords*)` — does not compile, because `StringIn` is
  a fastparse macro requiring literal constants. A behavioural drift guard
  (`KeywordTableDriftTest`) is the available substitute for a derived table.
- `noANSIMessages` was reported ignored. It was **honoured, in the wrong scope window**:
  the option was set outside the block that rendered. A parameter that "does not work"
  and a parameter read at the wrong moment look identical from outside.
- The invariant applier looked itself up under the wrong refMap key. `Pass` pushes the
  ON-CLAUSE as parent of its statements, so a handler-keyed lookup missed. Fixed with an
  ordered fallback rather than by guessing which parent is canonical.

### The fifth: one concept, five spellings, two AST nodes that disagreed

`Id(X)`, `Id(entity X)`, `reference X`, `reference to X`, `reference to entity X` all mean
*a pointer to an instance of entity X*, and they produced TWO nodes that had made
OPPOSITE decisions about the same question — `UniqueId` kept the disambiguating keyword,
`EntityReferenceTypeExpression` discarded it.

The cost was not aesthetic. Every addressing question riddlc asks is keyed on `UniqueId`,
so a field typed `reference to entity E` was **not usable as a `tell` address** despite
denoting exactly that. Canarying the fix reproduced riddlg's complaint verbatim on a field
literally typed that way.

Parsing `reference` INTO `UniqueId` fixes all six sites at once, where a common supertype
would have meant widening six matches and hoping none was missed. **`Id` is not
deprecated and neither is `UniqueId`** — Reid had to correct me on that wording: `Id` is
the permanent canonical SYNTAX, `UniqueId` the permanent AST node, and only the
`reference` spelling is deprecated. **Deprecating a syntax and deprecating an AST node are
different acts** and it is worth keeping the sentence unambiguous about which one is meant.

**Two collateral defects, both of a shape this repo keeps meeting:**

1. `DiagramsPass.getTypeReferences` had an arm for `EntityReferenceTypeExpression` and
   **none for `UniqueId`**. Switching the node would have dropped those usage edges with
   no error — a thinner graph and nothing to notice. It also turns edges ON for ~315
   pre-existing bare `Id(X)` fields that had never produced one; ruled a deliberate
   widening.
2. `EntityReferenceTypeExpression.format` returned `entity X`, which does not re-parse as
   a reference type — **and a golden test pinned it.** The emitter keeps its own correct
   copy and prettify routes through that, so the wrong one was never exercised. Third
   instance here of a dispatch written twice where only the correct copy runs. A golden
   test proves a string is STABLE, never that it is RIGHT.

**I also had to correct my own count.** I reported 13 corpus uses of `reference to`; there
are **zero** — all 13 grep hits are prose (`briefly "Opaque reference to a Document"`).
Grepping a syntax keyword that is also an ordinary English word needs the shape around it,
not the word.

**A withdrawn request worth recording.** Reid asked for the keyword to be a non-optional
enum rather than a string. Two tensions surfaced: it reverses a documented decision (the
keyword is stored AS WRITTEN so prettify is byte-exact), and under the day-old
never-delete rule a signature change is exactly what is now forbidden. Withdrawn on those
grounds. **Flagging the tension was the whole value — the request was reasonable and the
reasons it fails were not visible from where it was made.**

## 2026-08-27 (later) — four backlog items, and two that were already done

**The most useful thing this round produced was a correction to the backlog, not code.**
Asked what `[2.8]` was about and why "the UI model" blocked it, I checked instead of
explaining — and `[2.7]` and `[2.8]` were both **already built**. `PutStatement` exists
(A45), `GetValue` takes `InputRef | StateRef`, `Call` exists, `Statement.canFail` exists
with its own suite, and the A12 single-failure-point check is live in `ValidationPass` and
is MORE complete than the filed design (it counts embedded `Call`/`GetValue` in value
expressions, recurses through nested bodies, and skips the count when a step contains an
`ask`).

**I filed both the day before, graduating them VERBATIM out of NOTEBOOK's "Deferred —
blocked on prerequisites (do NOT start yet)" section during the prune.** That is this
repo's own documented failure — *a plan cannot notice the work happening* — and the cheap
test it prescribes would have caught it: take the entry's most specific factual claim and
check THAT first. "Revive the `put`/`get` keywords" was falsifiable by one grep.
**The rule that generalizes: a DEFERRED item is exactly the kind that goes stale
invisibly, because nobody re-reads a section headed do-not-start.** Verify before
graduating one, not after.

**`[1.22]`'s entry was wrong about its own subject, and the ruling is the interesting
part.** It read as one bar drawn in the wrong place — `isActionable` at CompletenessWarning
letting Missing and Usage through. Reid: these are TWO questions that must keep
disagreeing. `isActionable` asks *is this worth attention*; generability asks *can a
generator emit correct code*. Missing and Usage are precisely where they part, and the
reasoning is per-kind rather than a severity convenience: **unused definitions are cruft a
generator would emit as dead code, and what is MISSING cannot be generated at all.** So
`isActionable` is untouched — consumers key off it — and `isGenerable` was added beside it.

**That ruling then decided an unrelated severity, which is worth noticing.** A27's
portability warning (`[1.21]`, required since the item was written and never built) is a
**StyleWarning**, because under the new bar anything higher would make every use of the
`code` hatch non-generable — the hatch would block the generation it exists to serve. Same
unsatisfiable-demand trap as the discard-sink exemption and the adaptor advisory, reached
from a completely different direction. `CodePortabilityTest` pins it with a
*stays generable* case, which is exactly what a well-meaning severity bump would break.

**Coverage is presence, not sufficiency, and the report has to say which.** The scaladoc
work took AST.scala 81% -> 100% and Pass.scala 76% -> 100%; 98 of 370 declarations (26%)
still carry a single-line doc. Reporting "100%" without that second number would be true
and misleading. Note also that `language/doc` is the gate an invalid `[[link]]` trips — a
comment-only change compiling is not evidence the docs build.

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
- **Scala 3.9.0 final** adopted 2026-08-27, after RC1 -> RC4 -> RC6; rc.26 itself was
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
