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

Incremental runs lie. They have hidden a CI-gating grammar failure and a test
that only passed against stale classes, both in one afternoon. There is no
shortcut here.

```bash
sbt clean
sbt "cJVM; cJS; cNative"
sbt tJVM      # separately — a failure in one aborts a chained run
sbt tJS
sbt tNative
```

Then every validator CI gates on:

```bash
cd language/src/test/scalajvm/python
.venv/bin/python ebnf_tatsu_validator.py     # MUST report zero "Unexpected failures"
.venv/bin/python gbnf_validator.py
.venv/bin/python validate_external_riddl.py --repo ../../../../../riddl-examples
.venv/bin/python validate_external_riddl.py --repo ../../../../../riddl-models
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

NOT yet exercised end to end. On the FIRST RC, confirm the dispatch touched only
`Formula/riddlc-rc.rb` and left `Formula/riddlc.rb` alone before announcing it.

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

### 5. Stage the binary

```bash
sbt "reload; riddlcNative/nativeLink"
```

`reload` first: a long-running sbt server computes dynver's version and the
`gitCommit` BuildInfo key at PROJECT-LOAD time and does not re-read git when HEAD
moves. Confirm `riddlc info` reports the RC version before copying to
`~/Code/ossuminc/bin/riddlc`.

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

Then run `/ship` as normal, and move the npm `latest` pointer:

```bash
npm dist-tag add @ossuminc/riddl-lib@1.32.0 latest
```

## Red flags

- Certifying incrementally instead of from clean.
- Reading `92/113` as "21 failures" — or ignoring a non-empty Unexpected list.
- Omitting `--prerelease`, so the workflows treat it as a stable release.
- Publishing npm by hand — the workflow does it, and a manual publish makes it
  fail with a 409.
- Announcing the first RC before confirming the dispatch left `Formula/riddlc.rb`
  untouched.
- Retagging an existing RC instead of cutting the next one.
- Staging a native binary without `reload`, so it reports a stale commit.
