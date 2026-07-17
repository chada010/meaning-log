# JWT jjwt migration and notification drawer close button

Date: 2026-07-17
Branch: `feat/jjwt-notification-drawer-close`
Base: latest `origin/main` (`be976b5`)

## Scope

- Task A: migrate backend JWT signing and verification to jjwt 0.12.6 without changing HTTP behavior.
- Task B: keep the existing notification Drawer/store and add a header close control with mobile coverage.
- Do not change the database, frontend routing, CORS, or the `JWT_SECRET` environment variable.
- Do not access production, push, merge, or deploy.
- Commit Task A and Task B separately, in that order.

## TODO

- [x] Review `JwtService`, `JwtAuthenticationFilter`, `AuthService`, configuration, and existing tests.
- [x] Add `JwtService` characterization tests before changing its implementation.
- [x] Add jjwt 0.12.6 dependencies and migrate JWT generation/verification.
- [x] Verify malformed, tampered, expired, and wrong-key tokens return 401 through the authentication chain.
- [x] Run `./mvnw test` and commit Task A.
- [x] Review the notification Drawer, its store, styles, and frontend test conventions.
- [x] Add the header close/back icon button calling `store.closeDrawer()`.
- [x] Add the smallest close interaction test; defer 360px, 390px, and 412px layout checks to a browser.
- [x] Run `npm test`, `npm run type-check`, and `npm run build` and commit Task B.
- [x] Use `scripts/start-local.ps1` for any local runtime validation; hand off missing `.env` or occupied ports instead of claiming success.

## Status

- Task A implementation complete. JWT/Auth targeted tests: 32 passed.
- Full backend suite passed against local MySQL: 148 tests, 0 failures, 0 errors, 0 skipped.
- Local frontend, backend, MySQL, Redis, and RabbitMQ are running and healthy from this Worktree.
- Task B implementation and real-browser mobile validation are complete. Frontend tests: 29 passed; type-check and build passed.

## Merge review fixes

- [x] Add a fixed legacy three-part HS256 fixture and verify jjwt compatibility.
- [x] Verify non-HS256 tokens are rejected explicitly.
- [x] Increase the notification close target to at least 44 by 44 pixels.
- [x] Keep one close interaction test without treating jsdom widths as layout validation.
- [x] Inspect 360px, 390px, and 412px layouts in a real browser during local validation.
- [x] Re-run JWT targeted tests, `npm test`, `npm run type-check`, and `npm run build`.
- [x] Autosquash the fixes back into the original Task A and Task B commits.

## Local validation

- Started the project from this Worktree with `scripts/start-local.ps1`; frontend returned 200, backend health was `UP`, and MySQL, Redis, and RabbitMQ were healthy.
- Full backend suite passed after loading the Worktree's local environment for the Maven process: 148 tests, 0 failures, 0 errors, 0 skipped.
- Logged in locally as the existing `test` account, then successfully called `/api/auth/me` and `/api/notifications/unread-count` with the returned JWT.
- Verified an existing JWT returned 401 after temporarily incrementing the local test user's `token_version`; restored the original value immediately afterward.
- Executed the real `/api/auth/reset-password` flow with a one-time code injected only into local Redis, without calling the mail sender. The old JWT returned 401, the temporary new password logged in successfully, and the original password hash plus `tokenVersion` were restored and verified afterward.
- Chrome 150 real-browser checks passed at 360px, 390px, and 412px: the close button was visible at 44 by 44 pixels, did not overlap the heading or mark-all control, caused no horizontal overflow, and closed the Drawer through a real coordinate click.
- The browser check exposed and fixed the pre-existing non-matching `.notification-drawer .el-drawer` selector; the root has both classes, so it now uses `.notification-drawer.el-drawer` and the mobile `92vw` width applies.
- Frontend verification after the selector fix: 11 test files / 29 tests passed, type-check passed, and production build passed.
