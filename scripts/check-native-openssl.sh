#!/usr/bin/env bash
# Assert that a macOS Native binary links libcrypto from exactly one openssl major.
#
#   usage: scripts/check-native-openssl.sh <binary> <major>
#
# Exits non-zero (and says why) if the binary links libcrypto from any other openssl
# keg, or from none. Used by release.yml so that the published riddlc and the Homebrew
# formula's `depends_on "openssl@<major>"` cannot silently disagree, as they did in 2.5.0.
set -euo pipefail
bin="${1:?usage: $0 <binary> <major>}"
major="${2:?usage: $0 <binary> <major>}"

links=$(otool -L "$bin" | grep -E 'libcrypto|libssl' || true)
echo "libcrypto/libssl linked by $bin:"
echo "${links:-  (none)}"

if [ -z "$links" ]; then
  echo "ERROR: $bin links no libcrypto; expected openssl@$major" >&2
  exit 1
fi
wrong=$(echo "$links" | grep -v "/openssl@${major}/lib/" || true)
if [ -n "$wrong" ]; then
  echo "ERROR: $bin links a libcrypto that is not openssl@$major:" >&2
  echo "$wrong" >&2
  echo "The Homebrew formula declares openssl@$major; fix the pin in release.yml." >&2
  exit 1
fi
echo "OK: links openssl@$major only"
