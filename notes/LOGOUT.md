# Spontaneous Logout Bug — Vaadin + Spring Security + Push

## History

This bug has appeared multiple times. It was most recently triggered by
the Spring Boot 3.5.10 -> 3.5.11 upgrade, but the underlying issue is
architectural and can resurface with any dependency change that alters
session/cookie handling timing.

## Symptom

Users are spontaneously logged out a few seconds after logging in,
typically after sending a chat message (which triggers background
LLM processing and push UI updates). The browser is redirected to
the login page in a loop.

## Root Cause

Spring Security's `HttpSessionRequestCache` creates phantom sessions
that overwrite the authenticated JSESSIONID cookie.

The chain of events:

1. **Stale requests arrive with invalid JSSESSIONIDs.** During development,
   the server is frequently restarted. The browser has active push (long-poll)
   connections from the previous server instance. When the new server starts,
   these stale requests arrive with a JSESSIONID that doesn't exist on the
   new server.

2. **Spring Security creates a phantom session.** When an unauthenticated
   request is rejected, `ExceptionTranslationFilter` calls
   `HttpSessionRequestCache.saveRequest()`, which calls `request.getSession()`
   (without `false`). This creates a brand new empty HTTP session.

3. **Tomcat sets a Set-Cookie header.** The new session causes Tomcat to add
   `Set-Cookie: JSESSIONID=<new-phantom-id>` to the response. For async
   push responses, this happens during async completion — invisible to
   servlet filters and even Tomcat Valves.

4. **The phantom cookie overwrites the authenticated one.** The browser
   receives the Set-Cookie and replaces its authenticated JSESSIONID with
   the phantom one. All subsequent requests use the phantom session (which
   has no authentication), causing a redirect to login.

5. **The loop repeats.** The login redirect creates yet another session,
   and any remaining stale push responses overwrite it again.

## The Fix

One line in `SecurityConfiguration.java`:

```java
http.requestCache(cache -> cache.requestCache(new NullRequestCache()));
```

This tells Spring Security to never save the request in a session when
redirecting to login. Since `HttpSessionRequestCache.saveRequest()` was
the code path creating phantom sessions, disabling it prevents the
entire chain.

**Why this is safe for Vaadin:** The request cache exists so that after
login, the user is redirected to the page they originally requested.
Vaadin is a SPA — the entire app loads at `/`. There is no meaningful
URL to save and restore, so `NullRequestCache` has no user-visible
impact.

## What Didn't Work (and Why)

These approaches were tried during diagnosis and are documented here
to prevent re-investigating them:

- **`vaadin.close-idle-sessions: false`** — Already the default behavior;
  doesn't address the cookie overwrite mechanism.

- **Changing push transport** (LONG_POLLING vs WEBSOCKET vs WEBSOCKET_XHR) —
  The bug manifests with all transports. WEBSOCKET_XHR made it worse because
  every reconnection attempt is an HTTP request that can trigger session creation.

- **`spring.threads.virtual.enabled: false`** — Virtual threads were a red
  herring. The bug reproduces identically with platform threads.

- **Servlet filter wrapping the response** (`HttpServletResponseWrapper`
  suppressing Set-Cookie) — Doesn't work because Tomcat sets JSESSIONID
  at the connector level via `Response.addSessionCookieInternal()`,
  bypassing the servlet response wrapper entirely.

- **Servlet filter wrapping the request** (overriding `getSession()` to
  return null on push endpoints) — Partially effective for push endpoints
  but doesn't catch session creation on other endpoints (UIDL sync, static
  resources, forwarded requests).

- **Tomcat Valve** — Can't catch Set-Cookie headers added during async
  response completion. The Valve's `invoke()` returns before the async
  push response is committed.

- **Disabling Vaadin dev tools / live-reload** — No effect.

## Diagnostic Tools

- `test-session-browser.mjs` — Playwright-based browser test that logs in,
  sends chat messages, simulates tab switching, and monitors for phantom
  JSESSIONID cookie changes. Requires Playwright (`cd /tmp && npm install
  playwright && npx playwright install chromium`). Run with
  `cd /tmp && node /path/to/test-session-browser.mjs`.

## Key Insight

The phantom cookie is set during **async response completion** of long-poll
push connections. This makes it invisible to all synchronous server-side
instrumentation (servlet filters, Tomcat Valves). The fix must prevent
session creation at the source, not try to intercept the cookie after
the fact.

## Related Vaadin Issues

- https://github.com/vaadin/flow/issues/4072 — Push reconnects after session expiration
- https://github.com/vaadin/flow/issues/7323 — Spring Security logout triggers timeout with push
- https://github.com/vaadin/flow/issues/19649 — Push sessions don't expire/free resources
