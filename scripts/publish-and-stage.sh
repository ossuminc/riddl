#!/usr/bin/env bash
#
# Publish riddl locally AND restage the riddlc binary — as ONE operation.
#
# Reid's rule (2026-08-10): `~/Code/ossuminc/bin/riddlc` must ALWAYS match the artifacts in the
# local ivy cache. Library consumers resolve the first, CLI consumers run the second; when only
# one half is refreshed the two disagree about what the language accepts, and that surfaces in a
# consumer as a baffling failure far from its cause.
#
# So `publishLocal` and `riddlcNative/nativeLink` run in a SINGLE sbt invocation — both succeed or
# both fail — and the binary is installed ONLY if that invocation succeeded. A failed run therefore
# leaves both halves at their previous version, still agreeing.
#
# The binary is the NATIVE one, copied from the target tree as a REAL FILE (Reid, 2026-08-12) —
# the same shape as `bin/riddlg`. It is deliberately NOT a symlink and NOT the JVM launcher:
#
#   Until 2026-08-12 this script wrote a JVM stage tree into `../riddlc-dist` and never touched
#   `bin/riddlc` at all. That path happened to work only because of a hand-made symlink created
#   on 2026-08-09, a day before this script existed and invisible to git (`bin/` is ignored). So
#   the rule above was enforced NOWHERE: the script verified `riddlc-dist/bin/riddlc` and printed
#   OK, which was true and said nothing about the path the rule names. Break or replace that
#   symlink — as installing a real binary does — and the script would have gone on reporting
#   success while `bin/riddlc` froze. Verify the path you promise, not a path near it.
#
# Usage: scripts/publish-and-stage.sh
set -euo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BIN="$REPO/../bin"

cd "$REPO"

# The Scala version is in the target path, so read it from sbt rather than hardcoding it —
# a patch bump moves every one of these paths (see CLAUDE.md "Target-path layout").
SCALA_VER="$(grep -oE 'val scala = "[^"]+"' project/Dependencies.scala | sed -E 's/.*"(.*)"/\1/')"
NATIVE="$REPO/target/out/native0.5/scala-$SCALA_VER/riddlc/riddlc"

# What is published must correspond to a COMMIT on this branch, or the build cannot be
# reproduced from the history (Reid, 2026-08-10). sbt-dynver marks a dirty tree by appending a
# `-YYYYMMDD-HHMM` timestamp to the version, which is the symptom; refusing to build from a dirty
# tree is the cure. Checked BEFORE sbt runs, so a dirty tree costs nothing.
if [[ -n "$(git status --porcelain)" ]]; then
  echo "ERROR: the working tree has uncommitted changes, so the version would carry a dirty-tree" >&2
  echo "       timestamp and this build could not be reproduced from any commit. Commit first." >&2
  git status --short >&2
  exit 1
fi

# sbt-dynver resolves the version ONCE and a running sbt server keeps serving that value, so a
# publish from a warm server silently labels new code with the version the server started at --
# overwriting what an existing version means, which is worse than a stale artifact. `reload` in
# the SAME invocation re-reads it.
#
# `git describe` renders the commit as `-g<hash>` with a variable-length hash; dynver drops the
# `g` and uses 8 characters. Compare on that normalised form -- an earlier version of this check
# compared the two raw strings and cried wolf over a perfectly good build.
#
# AT an exact tag, dynver emits the BARE tag (`2.0.0-rc.11`) while `git describe --long` always
# appends `-0-g<hash>`. Staging a release candidate is exactly that case, so special-case it or
# the check fails on the one build most worth getting right.
DESCRIBE="$(git describe --tags --long)"
if [[ "$DESCRIBE" =~ ^(.+)-0-g[0-9a-f]+$ ]]; then
  EXPECTED="${BASH_REMATCH[1]}"
  echo "==> at tag $EXPECTED exactly (distance 0)"
else
  EXPECTED="$(printf '%s' "$DESCRIBE" | sed -E 's/-g([0-9a-f]{8})[0-9a-f]*$/-\1/')"
fi
echo "==> expecting version $EXPECTED (from git describe, tree clean)"

echo "==> reload + publishLocal + riddlcNative/nativeLink (one invocation; both or neither)"
sbt -batch 'reload; publishLocal; riddlcNative/nativeLink'

if [[ ! -x "$NATIVE" ]]; then
  echo "ERROR: the link reported success but $NATIVE is missing." >&2
  echo "       Not touching $BIN/riddlc — the old binary still matches the old ivy artifacts." >&2
  exit 1
fi

echo "==> installing the native binary at $BIN/riddlc"
mkdir -p "$BIN"
# `rm -f` first: if a previous arrangement left a SYMLINK here, `cp` would write THROUGH it and
# quietly update whatever it pointed at instead of this path.
rm -f "$BIN/riddlc"
cp "$NATIVE" "$BIN/riddlc"
chmod +x "$BIN/riddlc"

echo "==> verifying"
# riddlc prefixes its output with a log level; strip that and any whitespace before comparing.
ACTUAL="$("$BIN/riddlc" --no-ansi-messages version 2>&1 |
  grep -v '^WARNING' | sed -E 's/^\[[a-z]+\][[:space:]]*//' | tr -d '[:space:]')"
echo "    installed binary reports: $ACTUAL"
if [[ "$ACTUAL" != "$EXPECTED" ]]; then
  echo "ERROR: version mismatch -- expected '$EXPECTED', got '$ACTUAL'." >&2
  echo "       The build did not pick up the current commit, so these artifacts would be" >&2
  echo "       labelled with a version that already means something else. Stop an already-" >&2
  echo "       running sbt server and re-run." >&2
  exit 1
fi
# Verifying THE INSTALLED PATH is the whole point; see the header.
if [[ -L "$BIN/riddlc" ]]; then
  echo "ERROR: $BIN/riddlc is a symlink; it must be a real file so this check means something." >&2
  exit 1
fi
echo "OK: ivy artifacts and $BIN/riddlc were produced by the same build, at $ACTUAL."
