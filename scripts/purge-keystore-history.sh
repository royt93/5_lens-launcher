#!/usr/bin/env bash
# SEC-001 step 5 — local history purge. Roy runs this himself; it never pushes.
#
# What it does:
#   1. Refuses to run on a dirty working tree.
#   2. Backs up every branch/tag/remote-ref to a git bundle next to the repo.
#   3. Runs git filter-repo to (a) drop app/keystore.jks from every commit and
#      (b) redact the KS_ALIAS/KS_PW plaintext that's still sitting in old
#      gradle.properties revisions (see keystore-history-redact-expressions.txt).
#   4. Re-adds the "origin"/"upstream" remotes filter-repo strips for safety, and
#      stops. It does NOT force-push — that's a separate, coordinated, irreversible
#      step (doc/task/SEC-001_OWNER_CHECKLIST.md step 5 last two boxes).
#
# Usage: scripts/purge-keystore-history.sh --yes-i-have-backed-up-and-notified-everyone
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"

if [ "${1:-}" != "--yes-i-have-backed-up-and-notified-everyone" ]; then
    echo "This rewrites local git history (all branches). Read doc/task/SEC-001_OWNER_CHECKLIST.md" >&2
    echo "step 5 first, make sure every other clone owner has been told, then re-run with:" >&2
    echo "  scripts/purge-keystore-history.sh --yes-i-have-backed-up-and-notified-everyone" >&2
    exit 1
fi

if ! command -v git-filter-repo >/dev/null 2>&1; then
    echo "git-filter-repo not found. Install it first: brew install git-filter-repo" >&2
    echo "(or: pip3 install --user git-filter-repo)" >&2
    exit 1
fi

if [ -n "$(git status --porcelain)" ]; then
    echo "Working tree is dirty. Commit or stash first." >&2
    exit 1
fi

ORIGIN_URL=$(git remote get-url origin 2>/dev/null || true)
UPSTREAM_URL=$(git remote get-url upstream 2>/dev/null || true)

BUNDLE="../$(basename "$(pwd)")-backup-$(date +%Y%m%d%H%M%S).bundle"
echo "Backing up full history (all refs) to $BUNDLE ..."
git bundle create "$BUNDLE" --all

echo "Running git filter-repo ..."
git filter-repo --force \
    --path app/keystore.jks --invert-paths \
    --replace-text scripts/keystore-history-redact-expressions.txt

# filter-repo drops all remotes as a safety measure; put back what was there.
[ -n "$ORIGIN_URL" ] && git remote add origin "$ORIGIN_URL"
[ -n "$UPSTREAM_URL" ] && git remote add upstream "$UPSTREAM_URL"

cat <<EOF

Done locally. Backup bundle: $BUNDLE

Before you force-push (doc/task/SEC-001_OWNER_CHECKLIST.md step 5, last two boxes):
  - Verify: git log --all --oneline -- app/keystore.jks   (should be empty)
  - Verify: git log --all -p -- gradle.properties | grep -i KS_   (should show only REDACTED)
  - Run gitleaks locally against the rewritten history before trusting it.
  - Force-push is a separate manual step, on every affected branch, only after everyone
    with a clone has been told to re-clone (not fetch/merge) afterwards.
EOF
