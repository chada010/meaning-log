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
- [ ] Review the notification Drawer, its store, styles, and frontend test conventions.
- [ ] Add the header close/back icon button calling `store.closeDrawer()`.
- [ ] Add the smallest interaction test and verify 360px, 390px, and 412px layouts.
- [ ] Run `npm test`, `npm run type-check`, and `npm run build` and commit Task B.
- [ ] Use `scripts/start-local.ps1` for any local runtime validation; hand off missing `.env` or occupied ports instead of claiming success.

## Status

- Task A implementation complete. JWT/Auth targeted tests: 30 passed.
- Full `./mvnw test` attempted; 8 integration errors require local MySQL on `localhost:3306`.
- Local runtime handoff required because `.env` is missing and Docker Desktop is unavailable.
- Pending: Task B after the Task A commit boundary.
