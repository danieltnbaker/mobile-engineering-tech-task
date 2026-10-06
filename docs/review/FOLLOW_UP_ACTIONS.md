# Follow-up actions — out-of-band items not completed in code

Some remediation steps cannot be completed by a code change on this branch: they are destructive to
shared history, require credentials/ops access, or depend on infrastructure outside the repo. They are
recorded here with concrete plans so nothing is lost after the remediation pass.

Status legend: **OPEN** (not started) · **BLOCKED** (needs access/decision) · **DONE**.

---

## FU-1 — Purge the leaked GoRest token from git history  ·  OPEN / BLOCKED (needs owner + force-push approval)

**Context.** P0-2 (commit `c8644e6`) removed the hardcoded token from the working tree and switched to
an injected token. That stops the secret shipping *going forward*, but the credential still exists in
**git history** and can be recovered by anyone with repo access.

**Exact exposure (verified):**
- Introduced in commit `02a7a5b` ("Ktor client and GoRest endpoints"), file
  `data/src/commonMain/kotlin/com/userhub/data/remote/ApiConfig.kt`, constant `API_TOKEN`.
- Present in every commit from `02a7a5b` up to (not including) `c8644e6`.
- Absent from the current working tree (confirmed: no match in `*.kt`).
- The token value is a 64-char hex GoRest access token. (Do not paste the value into tickets/logs;
  reference it as "the token in `02a7a5b:ApiConfig.kt`".)

**Why it's not done here.** Rewriting history requires a force-push to a shared branch (`master`),
which is destructive and coordinated — out of scope for an automated code change and gated on owner
approval per the repo's git-safety rules.

**Plan (do FU-2 rotation FIRST — see below — then purge):**
1. Freeze: tell contributors to pause pushes to `master`; note the current `master` SHA.
2. Purge the blob from history with a supported tool:
   - Preferred: `git filter-repo`
     ```
     git filter-repo --path data/src/commonMain/kotlin/com/userhub/data/remote/ApiConfig.kt --invert-paths
     ```
     then re-add the current (clean) file; **or** use a replace-text run to redact just the secret:
     ```
     echo '<TOKEN_FROM_02a7a5b>==>REDACTED' > ../replacements.txt
     git filter-repo --replace-text ../replacements.txt
     ```
   - Alternative: BFG Repo-Cleaner — `bfg --replace-text replacements.txt`.
3. Force-push the rewritten history (`git push --force-with-lease` to all affected branches/tags) with
   owner sign-off.
4. Have every contributor re-clone or hard-reset; delete stale forks/PR branches that still carry the
   blob.
5. Invalidate caches that may retain the old objects (CI mirrors, artifact caches, code-host "blame"
   caches where applicable).

**Acceptance:** `git log -S '<token>' --all` returns nothing; the token no longer resolves at
`02a7a5b:…/ApiConfig.kt`.

**Dependencies:** must happen *after* FU-2 (rotate first so the exposed value is already useless if a
copy was taken).

---

## FU-2 — Rotate the GoRest credential  ·  OPEN / BLOCKED (needs GoRest account access)

**Context.** Any secret committed to a repo must be treated as compromised regardless of later removal.
The token was in history (FU-1) and in logs (`LogLevel.ALL`, fixed in P0-2), so it may have been
captured.

**Why it's not done here.** Requires authenticated access to the GoRest account at
`https://gorest.co.in` — an ops/account action, not a code change.

**Plan:**
1. Log in to gorest.co.in and **revoke** the exposed token.
2. Generate a new access token.
3. Store it outside source control and expose it as the `GOREST_API_TOKEN` environment variable (the
   value `provideApiToken()` now reads on both platforms):
   - Local/dev: shell profile or an untracked `.env` consumed by the run configuration.
   - CI: a masked secret/variable injected into the build and test environment.
   - Release/signing pipelines: the platform secret store (e.g. Keychain/Secrets Manager), never a
     plist or gradle property committed to the repo.
4. Verify a read + write call succeeds with the new token; confirm the old token now returns 401.

**Acceptance:** old token rejected by GoRest; app functions with the new token sourced from the
environment; no credential in source or history (with FU-1).

**Priority:** HIGHEST of the follow-ups and a prerequisite for FU-1 ordering.

---

## FU-3 — Add the CI secret-scan gate that makes FU-1/FU-2 non-recurring  ·  OPEN

**Context.** Removing and rotating the secret fixes the incident; the *gate* prevents the next one.
Referenced by P0-2 and `docs/review/ENGINEERING_PLAN.md` (release-process section).

**Why it's not done here.** No CI workflow exists in this repo to attach it to; standing up CI is a
separate infrastructure task.

**Plan:**
1. Add a pre-merge CI job that fails on committed credentials — e.g. `gitleaks` or `trufflehog`,
   plus a lightweight custom check for high-entropy string literals in `*.kt`.
2. Add a pre-commit hook (e.g. `pre-commit` + `gitleaks`) so it's caught locally before it ever lands.
3. Pair with the release-config lint from the plan: fail the build if a verbose Ktor `LogLevel`
   (e.g. `ALL`/`BODY`) is used in a release variant.

**Acceptance:** a PR introducing a fake secret or `LogLevel.ALL` in release is rejected by CI.

---

## FU-4 — SQLDelight migration for the new `first_seen_at` column  ·  OPEN

**Context.** P0-4 (commit `951eecc`) added a NOT NULL `first_seen_at` column to `user_cache` at schema
v1. For a fresh install and for the test suite this is fine, but an app already on a device with the
old schema would fail to open the DB without a migration.

**Why it's not done here.** The GoRest cache is disposable for this exercise, so no migration was
required to meet the ticket; a real rollout needs one.

**Plan:**
1. Bump the SQLDelight schema version and add a `.sqm` migration that `ALTER TABLE user_cache ADD
   COLUMN first_seen_at INTEGER NOT NULL DEFAULT 0` (or backfill from `cached_at`).
2. Enable SQLDelight schema verification/`verifySqlDelightMigration` in the build.
3. Add a migration test asserting an old-schema DB upgrades cleanly and existing rows get a sensible
   `first_seen_at`.

**Acceptance:** upgrading from the pre-P0-4 schema succeeds with no data loss; migration test passes.

---

## Summary

| ID | Action | Status | Blocked on | Order |
|----|--------|--------|-----------|-------|
| FU-2 | Rotate GoRest credential | OPEN | GoRest account access | 1st |
| FU-1 | Purge token from git history | OPEN | Owner + force-push approval | 2nd (after FU-2) |
| FU-3 | CI secret-scan + release-config gate | OPEN | CI exists | Parallel |
| FU-4 | SQLDelight migration for first_seen_at | OPEN | — | Before next release on existing installs |

These items were deliberately **not** attempted as code changes because they are destructive to shared
history, require credentials, or depend on infrastructure outside this repository. Each is actionable
with the plan above.
