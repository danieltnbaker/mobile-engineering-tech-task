# Investigation — "No internet connection" when adding a user

**Status:** Investigated, root cause confirmed. **Fix: not yet applied** (documented here first).
**Severity:** High — the headline "Add a user" feature is unusable, and the error misdirects the user.
**Affects:** Android and iOS identically (shared code path).

---

## 1. Symptom

On both the Android emulator and the iOS simulator, opening the FAB, entering a valid name and email,
and submitting shows:

> "No internet connection. Please check your network and try again."

…even though the device clearly has connectivity (the feed loads, and a browser on the same device
reaches the internet).

---

## 2. Reproduction and evidence

The feed uses `GET /users` (no auth required); add-user uses `POST /users` (auth required). Replicating
the exact requests the app makes against the live API:

```
# 1) POST /users WITHOUT an Authorization header  (what the app currently sends)
$ curl -s -w "HTTP %{http_code}\n" -X POST https://gorest.co.in/public/v2/users \
    -H "Content-Type: application/json" \
    -d '{"name":"Test User","email":"test_user_1234@example.com","gender":"male","status":"active"}'
{"message":"Authentication failed"}
HTTP 401

# 2) GET /users WITHOUT auth  (what the feed does)
$ curl -s -o /dev/null -w "HTTP %{http_code}\n" https://gorest.co.in/public/v2/users
HTTP 200

# 3) connectivity sanity
$ curl -s -o /dev/null -w "reachable, HTTP %{http_code}\n" "https://gorest.co.in/public/v2/users?page=1"
reachable, HTTP 200
```

**Conclusion from evidence:** the network is up (200). The POST returns **HTTP 401
"Authentication failed"** because it carries no `Authorization` header. The feed works because reads
are unauthenticated. So the "No internet" message is categorically wrong — it is an **auth failure**,
not a connectivity failure.

---

## 3. Root cause — two independent defects that combine

### Cause A — the write request has no token at runtime (regression introduced by the P0-2 fix)
Before the security fix, the token was hardcoded in `ApiConfig.API_TOKEN`, so every request (including
writes) was authenticated. P0-2 (commit `c8644e6`) correctly removed the hardcoded secret and switched
to an injected token sourced from the `GOREST_API_TOKEN` environment variable via `provideApiToken()`:

- `createHttpClient` only adds the `Authorization` header when the token is non-empty:
  ```kotlin
  if (authToken.isNotEmpty()) { header("Authorization", "Bearer $authToken") }
  ```
- On a normally-launched app (emulator/simulator/device), **no `GOREST_API_TOKEN` environment variable
  is present**, so `provideApiToken()` returns "", no auth header is sent, and writes get 401.

This is the correct *security* behaviour (fail safe / read-only without a credential), but P0-2 did not
provide a **runtime delivery mechanism** that actually reaches the running app. An environment variable
is readable by unit tests and CI, but not by a GUI app launched normally — so the demo lost its write
capability. In other words: P0-2 fixed the leak but left the app with **no way to obtain a token at
runtime**.

### Cause B — every non-success response is mislabeled as "No internet" (pre-existing; this is P1-1)
`AddUserViewModel.submit`:

```kotlin
val response = addUser(name, email)
if (response.status == HttpStatusCode.Created) {        // 201
    onCreated(response.body())
} else {
    _state.value = AddUserState(
        errorMessage = "No internet connection. Please check your network and try again."
    )
} // ...
catch (e: Exception) {
    _state.value = AddUserState(errorMessage = "No internet connection. ...")
}
```

Two problems:
1. Ktor's `expectSuccess` defaults to `false`, so a 401 does **not** throw — it returns a response with
   `status == 401` and falls into the **`else`** branch.
2. Both the `else` branch (any non-201: 401, 422 validation, 500…) and the `catch` block (any
   exception, including genuine connectivity loss) produce the **same** "No internet" message.

So the app cannot tell apart: a real offline state, an auth failure, a validation rejection, or a
server error — and labels all of them "No internet." The same flattening exists on the read side in
`UserRepositoryImpl.getUsers` (`catch (Exception)` → treated as offline), which is the broader P1-1
finding.

### How A and B combine
A produces a 401 where the app previously got 201; B then renders that 401 as "No internet." Either
alone would be a bug; together they make a working network look like an outage on the primary feature.

---

## 4. Why it looks platform-specific but isn't

The logic is entirely in shared `commonMain` (`AddUserViewModel`, `createHttpClient`, `provideApiToken`
contract). Both platforms resolve an empty token the same way and run the same mislabeling branch, so
the symptom is identical on Android and iOS — consistent with a shared-code defect, not a
platform-integration one.

---

## 5. Impact

- **Functional:** "Add a user" (MOB-247 Scope 2) does not work in any normally-launched build. Its
  acceptance criteria ("on success the new user is visible at the top"; "if the submission does not
  succeed, the user is told what went wrong") cannot be met — the user is told the *wrong* thing.
- **Diagnostic:** the message actively misdirects. A user or a partner tester would check their Wi-Fi,
  not suspect a missing credential. This is the "accuracy of what we tell the user" quality issue in
  miniature.
- **Validation gap:** no test covered a non-201 add-user response, so the mislabeling was invisible to
  the green suite (`AddUserViewModelTest` only exercises the 201 happy path).

---

## 6. Fix options (for the follow-up change — not applied yet)

Two things must change; they map to existing plan items.

**For Cause A — deliver the token to the running app (choose one):**
- **Preferred:** a gitignored `local.properties` entry (e.g. `gorest.api.token=…`) read at build time
  into a generated `BuildConfig`/`buildkonfig` field, which `provideApiToken()` returns. Keeps the
  secret out of source control (and out of git history) while actually reaching the app. CI/release
  supply the value from their secret store.
- Alternative: inject at app start from the platform secret store. Heavier for a sample app.
- Must be paired with FU-2 (rotate the leaked token) from `FOLLOW_UP_ACTIONS.md`.

**For Cause B — classify failures honestly (this is P1-1 in `FIX_TASKS.md`):**
- Distinguish connectivity exceptions (e.g. `IOException`) from HTTP status outcomes.
- Map 401/403 → an auth/configuration error ("Can't add users — the app isn't configured with an
  access token"), 422 → surface the validation message, 5xx → a server error, genuine I/O failure →
  the offline message.
- Add tests for each branch (offline, 401, 422, 500, 201) so the behaviour is pinned.

**Acceptance for the eventual fix:**
- With a valid token configured, adding a user returns 201 and the user appears at the top of the feed
  on both platforms.
- With no token, the user sees an accurate configuration/auth message — never "No internet."
- With the network actually down, the user sees the offline message.
- Tests cover all four outcomes.

---

## 7. One-line summary

The network is fine; the POST gets a **401** because the P0-2 security fix removed the hardcoded token
without giving the running app a way to obtain one, and the pre-existing error handling (P1-1) reports
*every* non-success — including that 401 — as "No internet connection." Fix = deliver the token at
runtime (gitignored build config) **and** classify errors honestly.
