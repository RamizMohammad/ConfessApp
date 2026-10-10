# Confess security — setup, controls, testing

The server is the source of truth for every security decision. Nothing on the
phone can be fully hidden or made tamper-proof (the API URL, the x-api-key and
the app's code can all be read from the APK), so client measures only raise the
cost of attacks; the server re-checks everything.

## 1. secrets.properties (app)

| Key | What | Example |
|---|---|---|
| `CERT_PINS` | SPKI pins for the API host(s), comma-separated, **at least two** (current key + a backup: the CA intermediate or root, or a spare key) | `sha256/AbC…=,sha256/XyZ…=` |
| `DEBUG_ALLOW_PROXY` | `true` = debug builds skip pinning (inspect your own traffic). Never affects release. | `false` |
| `CLOUD_PROJECT_NUMBER` | Google Cloud project number with the Play Integrity API enabled. `0`/empty = no verdicts sent. | `123456789012` |
| `SIGNING_CERT_SHA256` | Allowed signing certificates (Play Console → App integrity → app-signing key SHA-256; add your upload key if you sideload release builds). Empty = check off. | `AA:BB:…` |

Get pins for your server's chain (run where the server is reachable):

```bash
HOST=confess.api.mohammadramiz.in
openssl s_client -connect $HOST:443 -servername $HOST -showcerts </dev/null 2>/dev/null \
 | awk '/BEGIN CERT/{n++} {print > "c" n ".pem"}'
for f in c1.pem c2.pem c3.pem; do [ -s $f ] || continue
  sed -n '/BEGIN/,/END/p' $f > x.pem
  echo "$(openssl x509 -in x.pem -noout -subject)"
  echo "  sha256/$(openssl x509 -in x.pem -pubkey -noout | openssl pkey -pubin -outform der | openssl dgst -sha256 -binary | base64)"
done
```

Pin the **intermediate** (and add the root or a second intermediate of the same CA
as backup). Let's Encrypt rotates leaf keys on every renewal unless you reuse the
key, so pinning only the leaf will lock users out.

## 2. Server environment

| Variable | Rollout value | After every install has v5.1 |
|---|---|---|
| `REQUEST_SIGNING` | `report` | `enforce` |
| `ACCEPT_LEGACY_TOKENS` | `true` | `false` |
| `PLAY_INTEGRITY_MODE` (sign-in) | `report` | `enforce` |
| `PLAY_INTEGRITY_SENSITIVE_MODE` (delete account, password lock, biometrics) | `report` | `enforce` |
| `PLAY_INTEGRITY_CREDENTIALS` / `_PATH` | service-account JSON with Play Integrity access (falls back to Firebase credentials) | |
| `ANDROID_CERT_SHA256` | app-signing certificate SHA-256 (also used by assetlinks.json) | |
| `PLAY_INTEGRITY_REQUIRE_PLAY_RECOGNIZED` | `false` while testing sideloaded builds | `true` |
| `SECURITY_AUTO_BLOCK`, `SECURITY_BLOCK_THRESHOLD`, `SECURITY_WINDOW_SECONDS`, `SECURITY_BLOCK_SECONDS` | defaults 20 points / 10 min → 1 h block | |
| `PASSWORD_MAX_ATTEMPTS`, `PASSWORD_REVOKE_AFTER` | 5 / 15 | |

Do not switch `REQUEST_SIGNING` back to `off` once apps have seen signed
responses: v5.1 refuses unsigned answers after that (it treats them as tampered).

Migrations: `0004_auth_sessions`, `0005_user_roles`. Admin: `UPDATE users SET role='admin' WHERE alias='…';`

## 3. Controls

**Network** — HTTPS only; system CAs only in release (`network_security_config.xml`);
API certificate pinned (OkHttp `CertificatePinner`); redirects not followed;
no failover on TLS/pin/signature failures.

**Sessions** — 15-min access tokens; rotating refresh tokens (reuse of an old one
revokes the session); 30-day sliding / 180-day absolute lifetime; server-side
session table (revocation, device list, sign out others); sign-out revokes the
server session; password change signs out other devices; reset / brute force
signs out all devices.

**Requests** — every signed-in call is HMAC-signed with a per-session key
(method, path+query, time, nonce, body hash); nonces are single-use (replays are
rejected) and timestamps must be within ±5 min (the app follows the server
clock). Responses are signed back; the app drops answers whose signature fails.

**Integrity** — Play Integrity verdict at sign-in/registration and for sensitive
actions, verified server-side (nonce, package, age, Play recognition, signing
certificate, device integrity). Re-signed APKs are refused at launch
(`AppSignature`) — a cheap first line; the server's certificate check via Play
Integrity is the one that can't be patched out.

**Storage** — tokens, signing key and cached profile encrypted with AES-256-GCM
under an Android Keystore key; excluded from cloud backup and device transfer;
older plain-text sessions are migrated and wiped. FLAG_SECURE privacy setting.

**Release build** — R8 shrink + obfuscation, flattened packages, verbose/debug/info
logs stripped, source file names removed, not debuggable; debug HTTP logging only
logs headers, with credentials redacted.

**Backend** — API key + JWT (HS256 pinned) + live session on every call;
resource ownership checks on every object (posts, comments, notifications, push
tokens, sessions, likers); RBAC for admin routes (`users.role`); per-route rate
limits (Redis, per user/IP); password-lock lockout with escalation; strict input
validation and body caps (chunked too); HTML stripped from user text; reset
tokens hashed, single-use (row-locked), 1 h; docs/OpenAPI off in production;
generic 500s.

**Monitoring** — `security_event` log lines without secrets; per-IP and per-user
scores → automatic blocks; Telegram alerts for high-severity events and blocks;
admin endpoints to list/lift blocks and revoke a user's sessions.

## 4. Test plan

Automated (server): `pytest -q` — 46 tests incl. refresh rotation/reuse,
revocation, forged/`alg=none` JWTs, signature enforce/report, replay, stale
timestamps, response signatures, lockout, auto-block, integrity verdict rules +
enforcement + step-up + nonce reuse, IDOR, RBAC, reset-link race, body caps.
Automated (app): `./gradlew test` — RequestSignerTest (server-generated vectors).

Manual (release build, real device):

1. **Interception** — Burp/mitmproxy with its CA installed as a *user* CA →
   every call fails with "connection couldn't be verified"; nothing is shown.
   Debug build with `DEBUG_ALLOW_PROXY=true` → traffic visible (expected).
2. **Pinning** — set a wrong pin → all API calls fail; restore.
3. **Replay** — (debug + proxy, server `REQUEST_SIGNING=enforce`) repeat a
   captured request → 401 `BAD_SIGNATURE`; edit a body byte → 401.
4. **Response tampering** — edit a response body in the proxy → the app drops it.
5. **Token theft** — copy the refresh token, refresh with it from curl, then let
   the app refresh → the session is revoked; the app returns to sign-in.
6. **Sign out** — sign out, then reuse the old access token with curl → 401.
7. **Repackaging** — `apktool d` → `apktool b` → sign with another key → launch
   shows "This copy of Confess was changed"; with the launch check patched out,
   sign-in fails server-side when `PLAY_INTEGRITY_MODE=enforce`.
8. **Decompilation** — jadx on the release APK: obfuscated names, no
   `Log.d`, no server secrets (only URL, x-api-key, web client id, pins — all public by design).
9. **Integrity** — on an emulator / unlocked-bootloader device with
   `PLAY_INTEGRITY_MODE=enforce` → sign-in refused (403 `INTEGRITY_FAILED`);
   `report` → allowed, `security_event integrity_failed` logged.
10. **IDOR / abuse** — with another user's post/comment/session ids → 404/403;
    hammer an endpoint → 429, then an automatic block → 403 `BLOCKED`.
11. **Backups** — `adb backup` / device transfer contain no session data;
    copying the prefs file to another device doesn't sign in.

## 5. Remaining risks

- Anything in the APK can be read, and a determined attacker on a device they
  control can hook the app at runtime. Server enforcement limits what that gains them.
- Play Integrity depends on Google; outages are treated as "unavailable" and
  don't block users (by design). Classic requests have a daily quota.
- A crash between the server rotating a refresh token and the app saving it
  makes the next refresh look like reuse → that one session is revoked and the
  user signs in again.
- The x-api-key identifies the app but is not a secret.
- PixelVault's delete endpoint is unauthenticated: keep it on a private network.
