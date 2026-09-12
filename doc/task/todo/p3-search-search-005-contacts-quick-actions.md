# SEARCH-005 — Contacts search + quick call/message

| Field | Value |
|---|---|
| Type | `new` |
| Status | `todo` |
| Priority | `P3` |
| Evidence | `idea` |
| Epic | Search expansion (owner-picked 2026-09-12) |
| Estimate | 8 |
| Risk | High |
| Dependencies | None (owner sign-off required before implementation) |

## Context and evidence

Owner-picked idea: search should also surface contacts and let the user call/message them
directly. This is the highest-risk item in the search-expansion epic — confirmed during
investigation that the app currently declares no `READ_CONTACTS` permission and has no
contacts-provider query code anywhere in `app/src/main/java`.

## User story

As a user, I want to find a contact from the launcher search and call/message them without
opening a separate contacts or dialer app.

## Acceptance criteria

- [ ] `READ_CONTACTS` requested at first use of contact search only, with a clear rationale.
- [ ] Contacts results appear only after grant; denial falls through to app-only search, no crash.
- [ ] Call/message action uses standard `Intent.ACTION_DIAL`/`ACTION_SENDTO` (never a direct
      `ACTION_CALL`, which needs an even more sensitive permission and isn't required for this
      use case).
- [ ] Contacts data is never cached/persisted by this app beyond the single query result set —
      matches the existing "no unnecessary persistence" pattern used for other PII-adjacent data.

## Implementation notes

Owner action required before this proceeds: Play Console permissions declaration update and a
privacy-policy revision (contacts access), same publisher-side gate pattern already tracked for
`SEC-001`/`VIP-001`. Do not start implementation before that sign-off exists — flag explicitly
during backlog triage.

## Verification and Definition of Done

- [ ] Unit tests: contact query result → ranked result mapping, permission-denied path.
- [ ] Widget/UI: contact result row renders correctly, call/message intents fire correctly.
- [ ] Integration: real `ContactsContract` query against a test device with sample contacts.
- [ ] Smoke on designated device: search a real contact, place a call, send a message.
- [ ] No new lint/build failures; owner + Play Console sign-off recorded in this file's evidence
      before moving to `inprogress`.
