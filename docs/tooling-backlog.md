# Tooling Backlog

This backlog defines the automation work that should follow the Phase 2 documentation baseline.
These items are intentionally not implemented in Phase 2.

## Priority 1: Minimum Automation

- Frontend lint setup
  add ESLint for Vue 3 + TypeScript with rules focused on duplication, unused code, and oversized files where practical.
- Frontend formatting setup
  add Prettier with a minimal shared config.
- Backend formatting or style checks
  add Spotless, Checkstyle, or a similarly lightweight baseline for Java formatting and basic structure rules.
- Frontend validation command
  add an explicit `type-check` script so type validation does not rely only on build.
- Basic CI
  run frontend validation and backend validation on every PR or main branch push.

## Priority 2: Review Enforcement

- Commit or PR template
  include ownership layer, affected risk areas, and validation run fields.
- Optional pre-commit hooks
  add lightweight local checks only after commands are stable and fast.
- Documentation check habit
  ensure env vars and scripts are updated when code changes require them.

## Priority 3: Refactor Support

- Complexity reporting
  add a lightweight way to flag oversized files during review.
- Duplication visibility
  add rules or reports that help catch copied API, controller, and service logic.
- Test coverage reporting
  start with visibility for core backend flows before enforcing thresholds.

## Suggested Rollout Order

1. Add explicit validation commands.
2. Add lint and format rules with low false-positive risk.
3. Add CI for existing validation commands.
4. Add review templates and optional hooks.
5. Add stronger reporting for complexity and duplication.

## Success Criteria

- A contributor can tell which command to run before opening a PR.
- Reviewers can see whether baseline validation passed.
- The repo starts blocking obvious entropy increases automatically.
- Tooling supports the documented baseline instead of replacing it with unrelated style noise.
