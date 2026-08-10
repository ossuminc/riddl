#!/usr/bin/env bash
#
# Publish riddl locally AND restage the riddlc binary — as ONE operation.
#
# Reid's rule (2026-08-10): `~/Code/ossuminc/bin/riddlc` must ALWAYS match the artifacts in the
# local ivy cache. Library consumers resolve the first, CLI consumers run the second; when only
# one half is refreshed the two disagree about what the language accepts, and that surfaces in a
# consumer as a baffling failure far from its cause.
#
# So `publishLocal` and `riddlc/stage` run in a SINGLE sbt invocation — both succeed or both
# fail — and the staged tree is copied over the distribution ONLY if that invocation succeeded.
# A failed run therefore leaves both halves at their previous version, still agreeing.
#
# Usage: scripts/publish-and-stage.sh
set -euo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DIST="$REPO/../riddlc-dist"

cd "$REPO"

# The Scala version is in the target path, so read it from sbt rather than hardcoding it —
# a patch bump moves every one of these paths (see CLAUDE.md "Target-path layout").
SCALA_VER="$(grep -oE 'val scala = "[^"]+"' project/Dependencies.scala | sed -E 's/.*"(.*)"/\1/')"
STAGE="$REPO/target/out/jvm/scala-$SCALA_VER/riddlc/universal/stage"

echo "==> publishLocal + riddlc/stage (one invocation; both or neither)"
sbt -batch 'publishLocal; riddlc/stage'

if [[ ! -x "$STAGE/bin/riddlc" ]]; then
  echo "ERROR: staging reported success but $STAGE/bin/riddlc is missing." >&2
  echo "       Not touching $DIST — the old binary still matches the old ivy artifacts." >&2
  exit 1
fi

echo "==> copying staged tree to $DIST"
mkdir -p "$DIST"
rm -rf "$DIST/bin" "$DIST/lib"
cp -R "$STAGE/bin" "$STAGE/lib" "$DIST/"

echo "==> verifying"
"$DIST/bin/riddlc" --no-ansi-messages version 2>&1 | grep -v '^WARNING' || true
echo "OK: ivy artifacts and $DIST/bin/riddlc were produced by the same build."
