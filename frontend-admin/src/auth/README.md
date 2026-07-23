# SAFY Admin Mock Authentication

This frontend-admin authentication flow is a mock implementation for routing and UI validation only.

- It does not call a backend login API.
- It does not issue or validate JWT, access tokens, or refresh tokens.
- It stores only `isAuthenticated` and `email` in `sessionStorage`.
- It never stores the password.
- Replace `AuthProvider.login` with the backend authentication API when the real admin login API is ready.

Test-only account:

- Email: `admin@safy.kr`
- Password: `safy1234`
