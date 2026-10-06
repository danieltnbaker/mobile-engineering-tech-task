# MOB-247 — Prioritised fix tasks

Prioritised remediation plan for the UserHub release candidate, derived from the review against
`TICKET_MOB-247.pdf`.

> **Why this plan is framed the way it is.** This repo is a useful microcosm: the individual defects
> are small, but together they touch data accuracy, cross-platform consistency, release hygiene, and
> the gap between what we claim and what we shipped. The *technical* ordering below is sound and largely
> unchanged, but the **emphasis** is deliberately on the things that carry downstream and partner-facing
> cost. See `ENGINEERING_PLAN.md` for the systemic changes (process gates, ways of working) that stop
> this class of defect recurring at scale — the repo fixes alone do not.

## What each finding signals beyond the code

| Theme | What this repo shows | Tasks |
|---|---|---|
| **Data accuracy & product feel** | Fabricated feed timestamps (P0-4), divergent cross-platform time strings (P0-3), misleading error states (P1-1) — inaccurate data and inconsistent feel across platforms | P0-3, P0-4, P1-1 |
| **Release hygiene** | Debug logging + a secret shipped to `master` (P0-2); "green" never ran iOS (P1-5); tests that pass while core behaviour is broken (P1-4) — the exact profile of a defective build reaching a partner | P0-2, P1-4, P1-5 |
| **Downstream / commercial cost** | A data-loss bug in a "delete with undo" (P0-1) is a shrug internally and a trust event with an external partner. Each task states the *downstream* cost, not just the code smell | all P0 |
| **Claim vs reality** | The README confidently claims things the code contradicts (undo, shared logic, Clean Arch). Over-claiming internally is the same habit that over-claims to the people depending on the answer | P2-5 + practice in ENGINEERING_PLAN.md |
| **Consistency as the template** | This becomes the template later features copy; divergent per-platform logic (P0-3) and an inverted architecture (P1-3) are what a shared, owned definition of "done" prevents | P0-3, P1-3 |
| **Resilience under pressure** | At scale with time-critical commitments, you can't afford to debug fabricated data or silent deletes live. Rigor up front is what makes the demanding moments survivable | execution order + CI gates |

**Prioritisation method.** Tasks are ordered by two factors, in this order:

1. **Ticket-core first** — does it break a MOB-247 *acceptance criterion*? A feature that silently
   does the wrong thing is worse than one that is merely imperfect, because this repo is the
   **template every later feature is copied from** (the ticket's actual purpose). A wrong pattern here
   is multiplied across the roadmap — and, at scale, into partner-facing builds.
2. **Commercial sense** — within each tier, order by (impact × likelihood) ÷ (effort × risk), where
   *impact* is explicitly weighted by **downstream/partner exposure**, not just internal cost. A
   defect that would ship in a partner test build outranks one that only ever hurts us internally.

Priority tiers:
- **P0 — Blocks sign-off.** Breaks a core acceptance criterion, or is a security/privacy defect.
  Must be fixed before this can be the template.
- **P1 — Should fix before template adoption.** Correctness/robustness issues that will propagate.
- **P2 — Backlog.** Real but non-blocking; schedule when the relevant surface becomes product.

Each task states the ticket link, why it matters commercially, effort/risk, and acceptance.

---

## P0 — Blocks sign-off

### P0-1 · Delete-with-undo must defer the server delete until the undo window elapses
- **File:** `composeApp/.../presentation/UserFeedViewModel.kt` (`onDeleteConfirmed`, `onUndo`)
- **Broken AC (Scope 3):** "The deletion is **not final until the undo window has elapsed**. Taking
  Undo leaves the record intact — the user is still there on the next load."
- **What's wrong:** The server `deleteUser` fires **immediately** via `GlobalScope.launch`. "Undo" only
  re-inserts the row into the local list; it never restores the server record. After Undo, the user is
  gone on the next real load. Behaviour is the exact inverse of the requirement.
- **Fix shape:** Remove the row from the UI optimistically, but **schedule** the network delete after
  the undo window (tie it to the snackbar duration, launched in `viewModelScope`). If Undo is taken,
  cancel the pending delete — never call the API. Surface delete failures.
- **Commercial reasoning:** This is a data-loss bug in the headline feature. If copied as the template
  pattern, every "delete with undo" in the product destroys records that users thought they'd kept.
  **At scale this is the kind of defect that surfaces in a partner's test cycle and costs us a release
  slot** — a defective build reaching an external partner. Highest-impact, must-fix.
- **Effort/Risk:** Medium / Low. Confined to one view model.
- **Acceptance:** New view-model test proving (a) Undo cancels the API call entirely, (b) no-Undo
  finalises exactly one delete after the window. Replace `GlobalScope` with `viewModelScope`.

### P0-2 · Remove the committed API token and the full request/response logging
- **Files:** `data/.../remote/ApiConfig.kt` (`API_TOKEN`), `data/.../remote/HttpClientFactory.kt`
  (`level = LogLevel.ALL`)
- **Broken requirement:** Not an AC, but a security/privacy defect and a bad template pattern. Git
  commit `3b2c7d7 "Turn on request logging while debugging the add flow"` shows the logging was a
  debug leftover that shipped.
- **What's wrong:** (a) A GoRest write token is hardcoded in source and in git history. (b)
  `LogLevel.ALL` logs the `Authorization: Bearer …` header and all user PII (names, emails) to
  stdout/logcat.
- **Fix shape:** Move the token out of source (build config / injected secret) and scrub it from
  history or rotate it. Set logging to `LogLevel.NONE` for release (or `HEADERS`/`INFO` for debug with
  the auth header sanitised).
- **Commercial reasoning:** Secrets-in-repo and PII-in-logs are exactly the habits we must *not*
  stamp into the template. **A debug artifact reaching `master` is the process gap behind defective
  builds reaching partner testing** — the fix is cheap, but the real deliverable is the CI gate (see
  ENGINEERING_PLAN.md) that makes it impossible to ship again. High reputational/compliance downside if
  propagated.
- **Effort/Risk:** Low / Low.
- **Acceptance:** No credential in source; release build logs no auth header or PII; token rotated.
- **Residuals (out-of-band):** the code fix removes the secret going forward, but the token still
  exists in git history and must be rotated at GoRest. Both are tracked with step-by-step plans in
  `FOLLOW_UP_ACTIONS.md` (FU-1 history purge, FU-2 credential rotation, FU-3 CI secret-scan gate).

### P0-3 · Relative times must be genuinely shared, not divergent per-platform
- **Files:** `domain/.../time/RelativeTimeFormatter.kt` (`expect`) + `.android.kt` / `.ios.kt`
  (`actual`)
- **Broken AC (Scope 1):** "Relative times are **computed in shared code rather than per platform**."
- **What's wrong:** It's an `expect`/`actual` with **two different implementations** that produce
  different strings for the same input — e.g. 90 min → Android "1 hour ago" vs iOS "90 minutes ago";
  ~25 h → Android "1 day ago" vs iOS "Yesterday". The exact opposite of the criterion. The shared test
  only covers inputs < 60 min, so the divergence is invisible to the green suite.
- **Fix shape:** Delete both `actual`s; make `formatRelativeTime` a single pure function in
  `commonMain`. One implementation, one behaviour, both platforms.
- **Commercial reasoning:** This finding is also the clearest *process* lesson — a green build gave
  false confidence. Fixing it both meets the AC and demonstrates the correct shared-logic pattern the
  template exists to establish.
- **Effort/Risk:** Low / Low.
- **Acceptance:** Single `commonMain` implementation; new tests crossing the 60-min, hour, and day
  boundaries; identical output asserted for the same input.

### P0-4 · Stop fabricating "how long ago they were added"
- **File:** `composeApp/.../presentation/UserUiMapper.kt` (`toUiModels`)
- **Broken AC (Scope 1):** "Each row shows … **how long ago they were added**, expressed relatively."
- **What's wrong:** GoRest's user object has no `created_at` (confirmed in `UserDto`). The label is
  **invented** as `now − index × 5 minutes` — the feed shows fictional recency unrelated to reality.
- **Fix shape:** Decide a defensible source of truth and say so in the README. Options: (a) if the API
  genuinely exposes no creation time, show a real attribute instead of a fabricated time and adjust the
  copy; or (b) persist a real "first seen" timestamp in the SQLDelight cache when a user is first
  observed and compute the relative label from that. Do **not** synthesise per-index times.
- **Commercial reasoning:** Shipping fabricated data in the primary list is a trust problem and, as a
  template, teaches "make up data when the API lacks a field." Must be honest.
- **Effort/Risk:** Medium / Low–Medium (option b touches the cache schema).
- **Acceptance:** Row timestamps trace to a real value; no index-based synthesis; README states the
  chosen source.

---

## P1 — Fix before this becomes the template

### P1-1 · Distinguish "offline" from "server/other error" in the repository
- **File:** `data/.../repository/UserRepositoryImpl.kt` (`getUsers`)
- **Related AC (Scope 1):** "If the feed cannot load, the user gets an explanation and a way to retry,
  **with loss of connectivity called out specifically**."
- **What's wrong:** `catch (Exception)` treats *everything* — 4xx, 5xx, serialization errors, bugs — as
  "offline". With a non-empty cache, a server outage is silently shown as a successful (stale) feed;
  with an empty cache, every failure becomes the "No Internet" screen. The app cannot tell offline from
  broken, yet the AC asks to call out connectivity *specifically*.
- **Fix shape:** Catch connectivity exceptions (e.g. `IOException`) distinctly from HTTP/other errors;
  model `UsersResult` to carry a generic error state separate from `NoInternet`; surface stale cache as
  explicitly stale, not as fresh success.
- **Commercial reasoning:** Misleading error states generate false support tickets and mask real
  outages. The template should model failure honestly.
- **Effort/Risk:** Medium / Low.
- **Acceptance:** Tests for (a) connectivity failure → NoInternet, (b) HTTP 500 with cache → explicit
  stale/error, (c) HTTP 500 without cache → generic error (not "No Internet").

### P1-2 · Harden pagination metadata parsing (remove the `!!`)
- **File:** `data/.../remote/GoRestApi.kt` (`fetchLastPageUsers`)
- **Related AC (Scope 1):** "page determined from the API's pagination metadata."
- **What's wrong:** `headers["x-pagination-pages"]!!.toInt()` throws NPE if the header is missing or
  renamed. Also two round-trips where one would do (first body discarded).
- **Fix shape:** Null-safe read with a sensible fallback (e.g. page 1) when the header is absent; fold
  the metadata read into a single request where possible.
- **Commercial reasoning:** A hard crash on a feed load, dependent on a third-party header, is a
  reliability risk the template must not carry. Cheap.
- **Effort/Risk:** Low / Low.
- **Acceptance:** Test with the header absent (no crash; defined fallback) and present (correct page).

### P1-3 · Invert the Clean Architecture dependency so domain owns its contracts
- **Files:** `domain/build.gradle.kts` (`api(project(":data"))`), use cases returning
  `io.ktor…HttpResponse`, repository interface location
- **Related requirement (Technical direction):** "Clean Architecture … clear domain / data /
  presentation separation."
- **What's wrong:** `domain` depends on `data`; Ktor's `HttpResponse` leaks into domain and
  presentation. Dependencies point *outward*, contradicting the README and the stated architecture.
- **Fix shape:** Move the `UserRepository` interface and domain models into `domain`; have `data`
  depend on `domain` and implement it; replace `HttpResponse` return types with domain result types.
  `data → domain`, `presentation → domain`.
- **Commercial reasoning:** This is the whole point of the ticket — the foundation. But it is the
  **highest-effort, highest-risk** change with **no visible behaviour difference**, so it ranks below
  the P0 correctness/security fixes and is scheduled as its own reviewed PR (see FOUND_NOT_FIXED.md
  B1). Doing it right once prevents every future feature inheriting the inversion.
- **Effort/Risk:** High / Medium.
- **Acceptance:** `domain` has no dependency on `data` or Ktor; use cases return domain types; full
  suite green; README diagram matches reality.

### P1-4 · Close the test blind spots that let these defects pass
- **Files:** `composeApp/.../presentation/UserFeedViewModelTest.kt` (+ new), time-formatter tests
- **What's wrong:** No test asserts the undo-window delete semantics (the P0-1 bug), none covers
  `onUserCreated`, and the time test never crosses the hour/day boundaries (hiding the P0-3 divergence).
- **Fix shape:** Add the tests described in P0-1, P0-3, P0-4 acceptance rows; add an `onUserCreated`
  test; clean up the `ExperimentalCoroutinesApi` opt-in warnings while here.
- **Commercial reasoning:** The green suite gave false confidence. The template's test *strategy* is as
  much a deliverable as its code; it must catch the cases that actually fail.
- **Effort/Risk:** Low–Medium / Low.
- **Acceptance:** New tests fail against today's code and pass after P0 fixes; `./gradlew allTests`
  (incl. iOS) considered in CI, not just `test`.

### P1-5 · Verify and wire the iOS test target into the definition of "green"
- **Evidence:** `./gradlew test` runs only JVM/Android variants; no iOS test results are produced.
  `iosSimulatorArm64Test` / `allTests` are separate tasks the brief's command never invokes.
- **Broken DoD:** "Acceptance criteria met **on both platforms**"; "./gradlew test passes." The common
  tests have only ever run on the JVM — "green on both platforms" is unverified by the stated workflow.
- **Fix shape:** Run `allTests` (or `iosSimulatorArm64Test`) in CI; document the real command in the
  README; this is also what surfaces the P0-3 divergence automatically.
- **Commercial reasoning:** "Green on both platforms" must be *true*, not assumed. The template defines
  what "done" means for every later feature.
- **Effort/Risk:** Low / Low.
- **Acceptance:** CI runs iOS tests; README's build/test instructions reflect the command that actually
  validates both platforms.

---

## P2 — Backlog (non-blocking)

| # | Task | File | Why deferred |
|---|------|------|--------------|
| P2-1 | Simplify `TimeProvider` (remove UTC-offset corruption) | `domain/.../time/TimeProvider*` | Latent; bundle with P0-3/P0-4 time work |
| P2-2 | Preserve master-detail selection across rotation (`rememberSaveable`) | `composeApp/.../ui/DirectoryScreen.kt` | UX polish, not an AC |
| P2-3 | Collapse pagination to a single request | `data/.../remote/GoRestApi.kt` | Efficiency; fold into P1-2 |
| P2-4 | Decide R8/minify + keep-rules for release | `composeApp/build.gradle.kts` | Out of ticket scope; needs QA |
| P2-5 | Correct README claims to match fixed code | `README.md` | Trailing deliverable; edit in the PR that makes claims true |

---

## Recommended execution order (one short sprint)

1. **P0-2** (token + logging) — minutes, removes the security/privacy leak immediately.
2. **P0-3** (shared time) — small, and unblocks the honest timestamp work.
3. **P0-1** (delete-undo) — the data-loss headline bug.
4. **P0-4** (real timestamps) — honest feed data.
5. **P1-1 / P1-2** (error honesty + pagination safety) — robustness, cheap.
6. **P1-4 / P1-5** (test gaps + iOS CI) — lock the fixes in and make "green" mean both platforms.
7. **P1-3** (dependency inversion) — its own reviewed PR; the architectural foundation.
8. **P2-5** (README) — correct the docs in the same PR as the code they describe.

Rationale for the order: security first (cheapest, highest downside), then the core acceptance-criteria
correctness bugs cheapest-first, then robustness, then lock-in via tests, then the big structural PR,
and finally the docs — so we never ship a README that describes code we haven't written yet.

**Leadership note on sequencing.** The order deliberately front-loads the items that reduce
**partner-facing release risk** (the leak, then the data-accuracy and data-loss bugs), and treats the
test/CI work (P1-4, P1-5) as a *gate*, not a chore: once it's in place, the class of defect that
produced this candidate cannot silently reach a partner build again. The repo fixes close *these*
bugs; the process changes in `ENGINEERING_PLAN.md` are what stop the *next* set. Fixing the code
without changing the process would leave the underlying habits untouched.
