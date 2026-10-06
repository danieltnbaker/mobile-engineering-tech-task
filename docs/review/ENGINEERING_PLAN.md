# Engineering plan — beyond the repo

This document answers a different question from `FIX_TASKS.md` and `FOUND_NOT_FIXED.md`. Those fix
*this repo*. This sets out how I'd change **the plan, and the way the team works**, based on what the
MOB-247 candidate reveals about how it was built. The repo is treated here as *evidence* — a single
build rarely matters, but the habits that produced it do.

**Core thesis.** Every defect in this review is individually cheap to fix. The reason they shipped
*together*, in a build the team wanted to release this week, with a README confidently claiming the
opposite — that is the part worth addressing. The engineer is clearly capable (clean modules, Compose
Multiplatform, a working offline cache). The gaps are in judgement, communication, the quality bar,
and process — not raw ability. So the plan below is deliberately weighted toward *system* changes, not
more code review.

---

## 1. Technical decisions carry business consequences

**What the repo shows.** The "delete with undo" fires the server delete immediately, so Undo silently
loses data (`FIX_TASKS.md` P0-1). Viewed purely technically that's a view-model nuance. Viewed in
context it's "we would delete a record a partner's tester expected to keep, in a build we handed them,
and then have to explain it." Same code, different lens — and the second lens is the one that protects
revenue and relationships.

**What changes in the plan.**
- **Every ticket carries a one-line statement of real-world impact**, written by the engineer before
  review — a forcing function to think past the diff to who is affected and what it costs. The P0 tasks
  in `FIX_TASKS.md` already model this: each states the downstream cost, not just the code smell.
- **The business shape of major integrations is briefed into the team, not held above it.** Capable
  senior engineers make better calls when they know what a given partner is actually buying, and what a
  slipped or defective release costs — so those facts travel with the work.
- **"Adding features earns nothing" becomes an explicit value.** The brief says it; the README still
  foregrounds polish (shimmer, dark mode) while core data is fabricated. We reward judgement about what
  matters, not output volume.

---

## 2. Communicating the true state of the work

**What the repo shows.** The README asserts strict inward Clean Architecture, "nothing finalised until
the undo window closes," and times "computed entirely in shared code" — each contradicted by the code.
Over-claiming in a README is the same habit that over-claims in a room full of people depending on the
answer. The weakness isn't the solution; it's the honest communication of its state.

**What changes in the plan.**
- **Claims must be evidenced.** A README or PR that says "done on both platforms" points at the run
  that proves it. Here `./gradlew test` never exercised iOS (`FIX_TASKS.md` P1-5), so that claim was
  never backed. Declining to accept unevidenced claims internally builds the habit that external
  audiences can trust.
- **Match the message to the audience.** A standard I'll coach and model: the same finding framed for a
  commercial counterpart ("the defect is fixed and here's the guard"), for product ("data-accuracy risk
  closed, timeline holds"), and for engineers ("deferred delete in `viewModelScope`, cancellable on
  undo"). Engineers step into higher-stakes conversations with me first, then on their own.
- **Named ownership.** Each significant integration and each strategic initiative has an engineering
  owner who can speak to it end-to-end, rather than a diffuse "the team."

---

## 3. The quality bar: feel, non-functional behaviour, and data accuracy

**What the repo shows.** The feed shows **fabricated** "added X ago" times (P0-4) — inaccurate data in
the primary surface. The relative-time strings **differ between Android and iOS** (P0-3) — an
inconsistent feel across platforms. Errors all report as "offline" (P1-1) — the app misrepresents its
own state. All three pass as "working" and still fall short. Functional-but-not-good-enough is a
recurring risk worth designing against.

**What changes in the plan.**
- **A written definition of "done" that includes non-functional behaviour and data accuracy**, not
  just "compiles and the happy path runs": cross-platform consistency, honest error/empty/offline
  states, and data provenance (nothing synthesised presented as real) become explicit checklist items.
- **Design/UX review as a gate for user-facing surfaces**, so "feel" is assessed deliberately rather
  than hoped for.
- **Data and analytics accuracy treated as first-class.** The fabricated-timestamp bug is the
  archetype: any value shown or recorded must trace to a real source, and that is reviewed.

---

## 4. Release process and rigor (highest leverage)

**What the repo shows.** A debug-logging change ("Turn on request logging while debugging the add
flow", commit `3b2c7d7`) and a hardcoded token shipped straight to `master` (P0-2). The suite was
green while the headline feature was broken (P1-4). A tired engineer and one missed check is all it
takes for a defective build to reach a partner's test cycle, or for a release to be swallowed by
fixing the last one's bugs. **This is where I'd invest first and hardest**, because rigor here prevents
the recurrence of every other category.

**What changes in the plan.**
- **A CI gate before anything merges to the release branch:**
  - `./gradlew allTests` including **iOS**, not just `test` — closes the P1-5 blind spot.
  - **Secret scanning** that fails the build on committed credentials (P0-2).
  - A check that **debug settings** (e.g. `LogLevel.ALL`, verbose logging) cannot ship in a release
    variant.
  - **Static analysis / detekt** and a required-reviewer rule — no self-merge to release.
- **A release checklist and a release train.** A predictable cadence only works if "code complete"
  means tested and the branch is always shippable: trunk stays releasable, feature work sits behind
  flags, and we end the pattern of a release held hostage by the previous version's defects.
- **Partner-build hardening.** Any build destined for an external partner passes a pre-flight checklist
  (config, logging, secrets, on-device smoke test) owned by a named person. A defective build reaching
  a partner should require several gates to have failed, not one.
- **Tests must assert the behaviour that matters.** Generalising P1-4: coverage is not the metric;
  catching the real failure modes is. Reviews reject tests that pass while the feature is wrong.

---

## 5. Consistency across a distributed team

**What the repo shows.** This repo becomes the template every later feature copies — yet it contains
divergent per-platform logic (P0-3) and an architecture that contradicts its own README (P1-3). That's
what a distributed team produces without a shared, owned, enforced definition of how we build:
locally-reasonable, globally-inconsistent work.

**What changes in the plan.**
- **Codify the standard; don't re-litigate it per PR.** Dependency direction, shared-logic rules, and
  test strategy live in a short, living engineering handbook and are **enforced by the build** where
  possible (module dependency rules, lint). Consistency you can rely on is consistency the toolchain
  guarantees.
- **Deliberate rituals for a distributed team.** Regular architecture/design reviews, visible decision
  records (ADRs), and paired work on the *template-defining* pieces, so alignment on the most strategic
  work happens by design rather than by chance.
- **Reserve in-person time for where it compounds** — kickoffs for major initiatives and the
  foundational architecture calls — rather than routine delivery that distributed work handles well.
- **This repo becomes the first worked example** of the new standard, fixed *with* the team as the
  reference the handbook points to.

---

## 6. Sustaining pace without self-inflicted drag

**What the repo shows.** At large scale, with time-critical external commitments, you cannot afford to
be debugging *fabricated data* or a *silent delete* in the middle of a live escalation. What makes a
demanding pace sustainable is that the fundamentals are boringly reliable, so the hard hours go to
genuinely hard problems rather than self-inflicted defects.

**What changes in the plan.**
- **Front-load rigor precisely so the demanding moments are survivable.** The CI gates and release
  discipline above aren't bureaucracy — they're what lets a small team move fast under pressure without
  shipping a defective build late at night.
- **Spike, don't simmer.** I'll personally own partner-facing issues and the awkward-hour calls — that
  comes with the role — but I'll protect the team from *recurring* firefighting by removing its causes,
  so intensity rises around real commercial moments and then recedes rather than becoming the baseline.
- **Clear escalation and on-call ownership** for partner-facing incidents, so owning an issue end-to-end
  is a defined responsibility rather than an ad-hoc scramble.

---

## How the plan is sequenced

1. **Week 1 — stop the bleeding and close the gate.** Land the P0 repo fixes (data-loss, fabricated
   data, security leak) *and* stand up the CI gate (iOS tests, secret scan, release-config lint). The
   gate matters more than any single fix because it prevents recurrence.
2. **Weeks 2–3 — codify the standard.** Fix the architecture (P1-3) *with* the team as the reference
   implementation; write the short engineering handbook and the release checklist; correct the README
   to match reality.
3. **Ongoing — change the operating rhythm.** Impact statement on tickets, named owners for partner
   integrations, communication coaching, distributed-team rituals, and a protected release train.

**The one-line version:** the engineers are good; the *system* around them currently lets good
engineers ship a build like this one and believe it was ready. I'd fix the build this week, but the
durable work is the gate, the standard, and the commercial and communication habits that make "ready"
mean the same thing to the team, to product, and to the partners who depend on us.
