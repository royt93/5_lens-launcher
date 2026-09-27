#!/usr/bin/env bash
# Pre-commit secret scanning check
# Scans staged files for common signing keys, passwords, and tokens.
set -euo pipefail

STAGED_FILES=$(git diff --cached --name-only --diff-filter=ACM || true)

if [ -z "$STAGED_FILES" ]; then
    exit 0
fi

FAILED=0

# 1. Ban forbidden binary/keystore file extensions
for file in $STAGED_FILES; do
    case "$file" in
        *.jks|*.keystore|keystore.properties)
            echo "ERROR [SEC-001]: Attempting to commit prohibited key file: $file" >&2
            FAILED=1
            ;;
    esac
done

# 2. Ban common plaintext credential assignments in staged diffs
SUSPICIOUS_PATTERNS=(
    "ANDROID_RELEASE_STORE_PASSWORD\s*="
    "ANDROID_RELEASE_KEY_PASSWORD\s*="
    "KS_PW\s*="
    "BEGIN PRIVATE KEY"
    "BEGIN RSA PRIVATE KEY"
)

for pattern in "${SUSPICIOUS_PATTERNS[@]}"; do
    MATCHES=$(git diff --cached -S"$pattern" --name-only || true)
    if [ -n "$MATCHES" ]; then
        for f in $MATCHES; do
            # Allow examples/docs
            if [[ "$f" =~ \.example$ ]] || [[ "$f" =~ \.md$ ]] || [[ "$f" =~ check-secrets\.sh$ ]]; then
                continue
            fi
            echo "ERROR [SEC-001]: Found potential secret matching '$pattern' in $f" >&2
            FAILED=1
        done
    fi
done

if [ "$FAILED" -ne 0 ]; then
    echo "Commit rejected by scripts/check-secrets.sh. See SEC-001." >&2
    exit 1
fi

echo "Secret check passed."
