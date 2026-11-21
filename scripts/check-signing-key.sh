#!/usr/bin/env bash
# Fail early, and legibly, when the release signing key cannot sign or cannot be found.
#
#   scripts/check-signing-key.sh              checks the secret keyring (publish.yml, after
#                                             setup-java imported MAVEN_GPG_PRIVATE_KEY)
#   scripts/check-signing-key.sh --public FPR checks the published key Maven Central looks up
#                                             (the release skill, before the tag is pushed)
#
# Why: v0.3.0's publish failed 36 seconds into Maven with "gpg: no default secret key: No secret
# key", which reads like an empty keyring. The key had expired; gpg's default-key selection
# skips expired keys and then reports that it found none (#114). After the key was replaced,
# Central rejected every signature because keyserver.ubuntu.com answered inconsistently while
# the new key propagated; keys.openpgp.org served it reliably by fingerprint.
#
# A signing key is usable when it is not expired, revoked, disabled or invalid. One expiring
# within WARN_DAYS (default 30) passes with a warning. KEY_URL overrides the lookup URL for
# --public, so the test can point it at a file:// key instead of the network.
set -euo pipefail

warn_days="${WARN_DAYS:-30}"
now="$(date +%s)"
tmp="$(mktemp)"
trap 'rm -f "$tmp"' EXIT

if [ "${1:-}" = "--public" ]; then
    fpr="${2:?usage: check-signing-key.sh --public <fingerprint>}"
    url="${KEY_URL:-https://keys.openpgp.org/vks/v1/by-fingerprint/$fpr}"
    if ! curl -sf "$url" -o "$tmp"; then
        echo "::error::$url did not serve key $fpr. Maven Central would reject every signature: upload it to keys.openpgp.org and retry."
        exit 1
    fi
    gpg --show-keys --with-colons "$tmp" 2>/dev/null > "$tmp.colons" || true
    record=pub
    what="published key $fpr"
else
    gpg --list-secret-keys --with-colons 2>/dev/null > "$tmp.colons" || true
    record=sec
    what="secret keyring"
fi
trap 'rm -f "$tmp" "$tmp.colons"' EXIT

awk -F: -v rec="$record" -v now="$now" -v warn="$((warn_days * 86400))" -v what="$what" '
    $1 == rec && $12 ~ /[sS]/ {
        expired = ($7 != "" && $7 <= now)
        if ($2 ~ /[erdin]/ || expired) {
            printf "unusable signing key %s: validity=%s%s\n", $5, $2, (expired ? ", expired" : "")
            next
        }
        usable++
        if ($7 != "" && $7 - now < warn) {
            printf "::warning::signing key %s expires in %d days\n", $5, int(($7 - now) / 86400)
        }
    }
    END {
        if (usable == 0) {
            printf "::error::no usable signing key in the %s. An expired key makes gpg report \"no default secret key\"; extend or replace it (see #114).\n", what
            exit 1
        }
        printf "%d usable signing key(s) in the %s\n", usable, what
    }' "$tmp.colons"
