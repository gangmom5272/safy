# SAFY Admin Authentication

The admin console authenticates against the backend auth API.

- `POST /auth/admin/login` takes `{ loginId, password }` and returns an Access Token
  plus a `SAFY_REFRESH_TOKEN` HttpOnly cookie.
- The Access Token is kept in memory only (`src/api/accessToken.ts`); it is never
  written to `localStorage` or `sessionStorage`.
- On reload, `AuthProvider` restores the session with `POST /auth/token/refresh`
  followed by `GET /users/me`.
- A `401` on any request triggers a single refresh-and-retry in the axios
  interceptor; if the refresh fails the session is cleared.
- Only `OPERATOR` and `SUPER_ADMIN` accounts with status `ACTIVE` may enter the
  console (`canUseAdminConsole`).
- The password is never stored.

Accounts are provisioned on the backend — a first `SUPER_ADMIN` from
`SUPER_ADMIN_LOGIN_ID` / `SUPER_ADMIN_PASSWORD`, and operators through
`POST /admin/operators`.
