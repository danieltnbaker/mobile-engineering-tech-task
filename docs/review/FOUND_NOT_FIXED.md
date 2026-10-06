# MOB-247 — Found but deliberately not fixed, and why

Review of the UserHub release candidate against `TICKET_MOB-247.pdf` and the candidate brief.
This is the brief-mandated "short list of what you found and did not fix, and why."

**How to read this list.** Every item was found and verified during review. The decision *not* to
fix it (in the time-boxed review window) is a deliberate prioritisation call, justified on two axes:

- **Ticket-core?** — Does it break an acceptance criterion in MOB-247, or is it peripheral?
- **Commercial sense** — What does fixing it buy us *now*, given this repo's real purpose is to
  become the **template every later feature is copied from**, versus the cost/risk of touching it?

The guiding principle: fix what is cheap, safe, and either breaks a core acceptance criterion or
would propagate a bad pattern into every future feature. Defer what is correct-enough for a sandbox
exercise, risky to change under time pressure, or out of scope for this ticket.

---

## A. Found and fixed (for contrast — see FIX_TASKS.md for ordering)

Not part of this document, but noted so the "not fixed" decisions below are understood in context.
The fixes I prioritised are the ones that (a) break a core MOB-247 acceptance criterion and (b) are
cheap and low-risk: delete-with-undo correctness, the fabricated "added X ago" timestamps, the
shared relative-time divergence, and the committed token + `LogLevel.ALL` privacy leak.

---

## B. Found but deliberately NOT fixed

### B1. Clean Architecture dependency direction is inverted (`domain` depends on `data`)
**What:** `domain/build.gradle.kts` declares `api(project(":data"))`. Use cases import
`com.userhub.data.repository` and return Ktor's `HttpResponse`, so Ktor leaks into domain and
presentation. The README's "dependencies point inward" diagram is the reverse of reality.

**Ticket-core?** Partially. The ticket asks for "Clean Architecture… with a clear domain / data /
presentation separation." The *separation* exists (three modules); the *dependency rule* is wrong.
No user-facing acceptance criterion fails because of it.

**Why not fixed now:** This is the single most *structurally* important finding, but it is also the
**highest-risk, highest-effort** change — it means inverting the dependency, introducing a domain-owned
repository interface and domain models, and moving the Ktor/`HttpResponse` types out of use cases.
That touches every layer and every test. Doing it hastily in a review window risks breaking a
currently-green build for a change that produces **no visible behaviour difference**. 

**Commercial reasoning:** It is the *right* first scheduled piece of work precisely *because* this repo
is the template — but it deserves a proper, reviewed PR, not a rushed edit. Documented as the top
architectural task in FIX_TASKS.md rather than fixed blind. Deferring it costs nothing today; the
cost only accrues once features start getting copied from this foundation.

---

### B2. `isMinifyEnabled = false` on the release build type
**What:** `composeApp/build.gradle.kts` ships the release variant with no R8/minification or shrinking.

**Ticket-core?** No. MOB-247 says nothing about release packaging; the DoD only requires the Android
app to build and run and the iOS framework to link.

**Why not fixed now:** Enabling R8 on a Compose Multiplatform + Koin + Ktor + SQLDelight app without
curated `-keep` rules is a reliable way to introduce *runtime* crashes (reflection, serialization)
that won't show up in a build or the current unit tests. It needs device/QA validation we don't have
in-window.

**Commercial reasoning:** Zero commercial value for a throwaway sandbox directory, and real risk if
done carelessly. It belongs in the "productionisation" workstream *after* the foundation is agreed,
with proper keep-rules and a smoke test — captured as a backlog task, not a review fix.

---

### B3. `TimeProvider.nowEpochMillis()` adds the local UTC offset to an epoch value
**What:** `nowEpochMillis() = systemEpochMillis() + utcOffsetMillis()`. Epoch milliseconds are by
definition UTC; adding the offset corrupts the value.

**Ticket-core?** No, and currently invisible: the only consumer is the relative-time label, which is
computed against the *same* corrupted "now", so the error cancels out in the subtraction.

**Why not fixed now:** It's latent, not active. Fixing `TimeProvider` in isolation is sensible, but it
is entangled with B-items in the FIX_TASKS list (the fabricated timestamps and the shared formatter):
once we stop fabricating `addedAt` and compute times correctly, `TimeProvider` should be simplified at
the same time. Fixing it standalone now would be churn against code that's about to change anyway.

**Commercial reasoning:** Bundle it with the time-handling fix (FIX_TASKS #2/#3) so we pay the review
cost once. Fixing it alone buys no behaviour change today.

---

### B4. Master-detail selected-user state is lost on rotation / config change
**What:** `DirectoryScreen` holds `selectedUser` in `remember { mutableStateOf(...) }` rather than
`rememberSaveable`, so rotating the device clears the detail selection.

**Ticket-core?** No. The adaptive-layout criteria require width-driven list/detail — which works. State
survival across rotation is not an acceptance criterion.

**Why not fixed now:** It's a genuine UX polish item but not a correctness or AC failure, and
`UserUiModel` isn't trivially `Saveable` (it wraps a DTO), so a correct fix means a saver or holding
only the id. Small, but out of the "fix what breaks the ticket" scope for the window.

**Commercial reasoning:** Low impact for the exercise; worth doing when the detail pane becomes real
product surface. Backlog, not blocker.

---

### B5. Two network round-trips in `fetchLastPageUsers` (first response body discarded)
**What:** The method GETs page 1 only to read the `x-pagination-pages` header, discards that body,
then GETs the last page. Two calls where the pagination metadata could be read from the single call
that also carries data.

**Ticket-core?** The *outcome* (last page shown) meets the criterion; the inefficiency doesn't violate
an AC. (The unsafe `!!` on the header is a *separate* correctness item and **is** scheduled — see
FIX_TASKS — because it can crash. The redundant round-trip itself is only an efficiency concern.)

**Why not fixed now:** Separating the crash risk (scheduled) from the efficiency (deferred) keeps the
scheduled fix small and reviewable. Collapsing to one request is a nice-to-have optimisation that can
ride along with the pagination hardening.

**Commercial reasoning:** One extra request against a sandbox has negligible cost. Fold the
optimisation into the pagination-hardening task rather than spending separate review effort.

---

### B6. `saveUsers` clears the whole cache on every write (last-page-only offline cache)
**What:** `SqlDelightUserLocalDataSource.saveUsers` does `clearAll()` then re-inserts. The offline
cache therefore only ever holds the most recently loaded page.

**Ticket-core?** No — this is actually **correct** for MOB-247. The criterion is explicitly "the most
recently loaded page stays readable when offline," and "pagination beyond the last page" is out of
scope.

**Why not fixed now:** It is not a defect against this ticket; "fixing" it would be adding scope the
ticket excludes.

**Commercial reasoning:** Deliberately left as-is. Noted only so a future reader doesn't mistake the
intentional single-page cache for a bug when multi-page browsing is eventually built.

---

### B7. README overstates the implementation (architecture, undo, shared time, error handling)
**What:** The README asserts strict inward Clean Architecture, "nothing finalised until the undo window
closes," relative times "computed entirely in shared code," and bulletproof error handling — each of
which the code contradicts (see FIX_TASKS).

**Ticket-core?** The DoD requires a README that "covers the architecture and how to build and run." It
does; the inaccuracies are about *claims*, not build instructions.

**Why not fixed now:** The README should only be corrected *after* the code is fixed — otherwise we'd
be rewriting it twice. Once the undo, timestamp, and shared-formatter fixes land, the README becomes
true with minimal edits. Editing prose now, ahead of the code, would create a misleading document in
the interim.

**Commercial reasoning:** The documentation is a trailing deliverable. Correct it in the same PR that
makes its claims true, so we never ship a README that lies about the shipped code.

---

### B8. Test opt-in compiler warnings (`ExperimentalCoroutinesApi`)
**What:** `UnconfinedTestDispatcher` usage emits opt-in warnings in the two view-model test files.

**Ticket-core?** No. Warnings only; the suite is green.

**Why not fixed now:** Cosmetic. Adding `@OptIn` is trivial but carries no risk or reward and is noise
against the substantive findings.

**Commercial reasoning:** Clean it up when those test files are next touched (they will be, to add the
missing undo/created tests in FIX_TASKS). No separate effort warranted.

---

## C. Summary table

| # | Finding | Ticket-core? | Fixed? | Primary reason deferred |
|---|---------|--------------|--------|-------------------------|
| B1 | domain → data dependency inversion | Partial (arch) | No | High risk/effort, no behaviour change; needs reviewed PR |
| B2 | `isMinifyEnabled=false` | No | No | Out of scope; R8 without keep-rules risks runtime crashes |
| B3 | `TimeProvider` epoch corruption | No (latent) | No | Bundle with time-handling fix to avoid churn |
| B4 | Detail selection lost on rotation | No | No | UX polish, not an AC; defer to real product surface |
| B5 | Double round-trip in pagination | No (efficiency) | No | Fold into pagination hardening task |
| B6 | Last-page-only offline cache | No (correct) | No | Intentional and correct for this ticket |
| B7 | README overstates implementation | No (claims) | No | Fix with the code it describes, not before |
| B8 | Test opt-in warnings | No | No | Cosmetic; clean up when tests are next edited |

**Overall stance:** everything deferred is either (a) correct as-is for this ticket, (b) risky to
change under time pressure for no behavioural gain, or (c) best bundled with a scheduled fix to avoid
reviewing the same area twice. Nothing deferred breaks a MOB-247 acceptance criterion — the items that
*do* are all in FIX_TASKS.md.
