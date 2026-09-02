# Shamela MCP Live Contract

Verified against `https://shamela.link/mcp` on 2026-09-02.

## Transport

- MCP endpoint: `https://shamela.link/mcp`
- Transport: stateless Streamable HTTP over `POST`
- Unauthenticated MCP requests return HTTP 401 with JSON-RPC error data.
- `GET` and `OPTIONS` on the MCP endpoint return HTTP 405.
- Bearer tokens are supplied with the standard `Authorization` header.
- The protected-resource metadata is published at
  `https://shamela.link/.well-known/oauth-protected-resource/mcp`.

## OAuth

- Issuer: `https://shamela.link/api/auth`
- Authorization endpoint: `https://shamela.link/api/auth/oauth2/authorize`
- Token endpoint: `https://shamela.link/api/auth/oauth2/token`
- Dynamic client registration endpoint: `https://shamela.link/api/auth/oauth2/register`
- Revocation endpoint: `https://shamela.link/api/auth/oauth2/revoke`
- Native clients may use authorization code plus refresh token grants.
- Public clients use token endpoint authentication method `none`.
- PKCE method: `S256` only.
- Requested scopes: `openid profile email offline_access`.
- Resource audience: `https://shamela.link/mcp`.

The registered MyVault client is a public native client. Its client identifier is
not a secret. No client secret is embedded in the application.

## Android Integration

MyVault uses AppAuth for Android for the browser authorization flow, PKCE,
authorization response validation, token exchange, and refresh handling. The
serialized AppAuth state is encrypted with an Android Keystore AES-GCM key before
being written to app-private preferences. It is excluded from Android platform
backup and from MyVault's manual backup format.

The redirect URI is `com.myvault.app:/oauth2redirect/shamela`.

## Runtime Evidence

- The live authorization request redirected to Shamela's real sign-in page.
- A new Shamela account was accepted by the live service.
- Shamela then required email verification before it would issue authorization.
- No CAPTCHA or two-factor prompt was encountered before the email-verification
  gate.
- The first blank custom tab was caused by an emulator Chrome native-library
  startup failure. A clean emulator/browser restart rendered the same OAuth
  request correctly; the MyVault OAuth request was unchanged.

## Authenticated MCP Result

Authenticated discovery was completed from the installed MyVault debug app on
the Android emulator after the account was activated.

- Negotiated protocol: `2025-11-25`
- Server: `shamela` version `1.3.0`
- Response transport: `text/event-stream`
- Session header: none; the server behaved statelessly
- Server capabilities: `tools` and `resources`, both with `listChanged`
- Discovered tools: 34
- Pagination: supported by the client; the current catalogue was returned
  without a remaining cursor
- Complete tool definitions, input schemas, output schemas, annotations, and
  server instructions: `docs/SHAMELA_MCP_TOOL_CATALOG.json`
- Sanitized catalogue SHA-256:
  `e3d13719ed941155d92e2a53465a6c2f215ba1dbae8c5af807bde0cf3e7c8225`

The application sent `notifications/initialized` after negotiation and includes
`MCP-Protocol-Version` on subsequent requests. It accepts both JSON and SSE and
does not assume one response type.

## Live Tool Inventory

All 34 advertised tools were annotated `readOnlyHint=true` and
`destructiveHint=false`:

1. `shamela_search_pages`
2. `shamela_search_titles`
3. `shamela_search_books`
4. `shamela_search_authors`
5. `shamela_get_page`
6. `shamela_get_toc`
7. `shamela_get_book`
8. `shamela_get_author`
9. `shamela_list_categories`
10. `shamela_resolve`
11. `shamela_get_pages_range`
12. `shamela_get_book_section`
13. `shamela_get_citation`
14. `shamela_search_quran`
15. `shamela_get_aya`
16. `shamela_get_tafseer_of_aya`
17. `shamela_get_books_for_hadith`
18. `shamela_list_downloaded_books`
19. `shamela_get_book_parts`
20. `shamela_get_page_services`
21. `shamela_search_phrase`
22. `shamela_search_hadith`
23. `shamela_health`
24. `shamela_search_exact`
25. `shamela_search_boolean`
26. `shamela_root_stats`
27. `shamela_books_by_period`
28. `shamela_list_tafsirs_for_aya`
29. `shamela_get_tafseer_texts`
30. `shamela_guide`
31. `shamela_suggest_download`
32. `shamela_verify_quote`
33. `shamela_scan_consensus`
34. `shamela_research_scope`

## Harmless Live Invocation

`shamela_health` was called with an empty argument object through the same
authenticated Android client. It completed successfully and returned structured
status `ok`, server version `1.3.0`, 8,598 downloaded books, 3,190 authors, 41
categories, 7,605,947 indexed page documents, and a 5-of-5 readable-book spot
check. The call took less than three seconds together with initialization and
tool discovery in the live instrumentation run.

## Live Research Calls

The production `ShamelaResearchProvider` was exercised against the authenticated
endpoint with bounded, read-only calls on the Android emulator:

- Keyword `الاستواء`: 11,557 hits; first bounded page returned in 338 ms.
- Exact phrase `الاستواء معلوم`: 1,006 hits; returned in 340 ms.
- Book-scoped keyword in book `7798` (Tafsir al-Tabari): 10 hits; returned in
  365 ms.
- Surrounding context for book `7798`, page id `9129`: returned in 321 ms with
  printed page `13/ 411` and an author-body passage.
- Full server-formatted citation for the same page: returned in 316 ms as
  `ابن جرير الطبري (ت ٣١٠هـ). تفسير الطبري. ج ١٣، ص ٤١١.`

The server also explicitly reported unavailable edition, publisher, and city
fields. MyVault does not fill those gaps. No HTTP 429 response was observed in
this small sequential acceptance run; the test intentionally did not generate
load to probe an unknown rate limit.

Search results use separate `snippet_body`, `snippet_foot`, and
`snippet_comment` fields, with `matched_in` as an array. The Android normalizer
keeps each non-empty section as a distinct source so editor footnotes cannot be
presented as the book author's own words.
