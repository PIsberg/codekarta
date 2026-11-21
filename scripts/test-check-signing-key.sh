#!/usr/bin/env bash
# Exercises check-signing-key.sh against throwaway keyrings, so the check that guards a release is
# itself checked on every build. No network: --public is pointed at file:// URLs.
set -uo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
check="$here/check-signing-key.sh"
work="$(mktemp -d)"
trap 'gpgconf --homedir "$work/gnupg" --kill all >/dev/null 2>&1; rm -rf "$work"' EXIT
export GNUPGHOME="$work/gnupg"
mkdir -p "$GNUPGHOME" && chmod 700 "$GNUPGHOME" 2>/dev/null || true
gen() { # gen <uid> <expire> [extra gpg args...]
    gpg --batch --pinentry-mode loopback --passphrase '' "${@:3}" --quick-gen-key "$1" rsa2048 sign "$2" 2>/dev/null
}
url_of() { # a file:// URL curl can open; native Windows curl needs a drive path, not /tmp
    local p
    if p="$(cygpath -m "$1" 2>/dev/null)"; then echo "file:///$p"; else echo "file://$1"; fi
}
fpr_of() { gpg --list-keys --with-colons "$1" | awk -F: '$1 == "fpr" { print $10; exit }'; }

failures=0
expect() { # expect <exit-code> <output-fragment> <description> -- <command...>
    local want="$1" fragment="$2" desc="$3"; shift 4
    local out code
    out="$("$@" 2>&1)"; code=$?
    if [ "$code" -ne "$want" ] || ! printf '%s' "$out" | grep -q -- "$fragment"; then
        echo "FAIL: $desc (exit $code, wanted $want; output: $out)"; failures=$((failures + 1))
    else
        echo "ok:   $desc"
    fi
}

expect 1 "no usable signing key" "an empty keyring fails" -- "$check"

# Created in the past with a one-day expiry, so it is expired now: the v0.3.0 failure.
gen "Expired <expired@example.invalid>" 1d --faked-system-time 20200101T000000
expect 1 "expired" "a keyring holding only an expired key fails, naming expiry" -- "$check"
gpg --armor --export "Expired <expired@example.invalid>" > "$work/expired.asc"
expect 1 "no usable signing key" "a published expired key fails" -- \
    env KEY_URL="$(url_of "$work/expired.asc")" "$check" --public "$(fpr_of 'Expired <expired@example.invalid>')"

gen "Soon <soon@example.invalid>" 10d
expect 0 "expires in" "a key expiring within 30 days passes with a warning" -- "$check"

gen "Forever <forever@example.invalid>" never
gpg --armor --export "Forever <forever@example.invalid>" > "$work/forever.asc"
expect 0 "usable signing key" "a published key with no expiry passes" -- \
    env KEY_URL="$(url_of "$work/forever.asc")" "$check" --public "$(fpr_of 'Forever <forever@example.invalid>')"
expect 1 "did not serve" "a key the lookup cannot find fails" -- \
    env KEY_URL="$(url_of "$work/missing.asc")" "$check" --public 0000000000000000000000000000000000000000

[ "$failures" -eq 0 ] && echo "check-signing-key.sh: all cases pass" || { echo "$failures case(s) failed"; exit 1; }
