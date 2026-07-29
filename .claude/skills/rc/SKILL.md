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
| Maven Central / GH Packages | `1.32.0` | `1.32.0-rc.1` (SemVer sorts it BELOW the release) |
| npm | `latest` dist-tag | **`rc`** dist-tag — `npm publish --tag rc` |
| Homebrew | `Formula/riddlc.rb` | **`Formula/riddlc@rc.rb`** |

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

### 3. npm

```bash
sbt riddlLibJS/npmPublishNpmjs   # MUST carry --tag rc
```

`npm publish` sets `latest` by DEFAULT regardless of the version string, so an RC
without `--tag rc` becomes what a plain `npm install @ossuminc/riddl-lib`
resolves to.

Blast radius if it happens: `^`/`~` ranges do NOT match prereleases, so pinned
dependents are unaffected; only fresh unversioned installs are exposed. Recover
with `npm dist-tag add @ossuminc/riddl-lib@<last-stable> latest`.

### 4. Homebrew — **`riddlc@rc`**

**Built on both sides** (riddl `0850570c9`, homebrew-tap `50b2f28`).

- `release.yml`'s `update-homebrew` job branches on
  `github.event.release.prerelease` and sends `client_payload[formula]` of
  `riddlc@rc` or `riddlc`.
- The tap has `Formula/riddlc@rc.rb` (class `RiddlcAtRc`) with `conflicts_with`
  in both directions, and `update-formula.yml` routes on that payload field,
  defaulting to `riddlc` when absent and rejecting any other value before it
  becomes a file path.

NOT yet exercised end to end. On the FIRST RC, confirm the dispatch touched only
`Formula/riddlc@rc.rb` and left `Formula/riddlc.rb` alone before announcing it.

Why a separate formula: Homebrew's `devel` block is deprecated and removed, and
there is no prerelease flag. A versioned formula plus `conflicts_with` is what
Homebrew's own maintainers recommend; `brew upgrade` then tracks each line
independently. The formula NAME is the "experimental" marking — users opt in with
`brew install ossuminc/tap/riddlc@rc`. (homebrew-core forbids unstable versions,
but that governs the official tap, not ours.)

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
- Publishing npm without `--tag rc`.
- Announcing the first RC before confirming the dispatch left `Formula/riddlc.rb`
  untouched.
- Retagging an existing RC instead of cutting the next one.
- Staging a native binary without `reload`, so it reports a stale commit.
