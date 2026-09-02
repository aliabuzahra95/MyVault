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

Authenticated MCP initialization and `tools/list` remain pending until the
account activation link is opened.
