# Development Baseline

## Purpose

This document defines the minimum development baseline for Meaning Log.
It is designed for AI-assisted development first, while remaining usable by human reviewers.

This baseline exists to stop the codebase from getting messier before large refactors begin.
It does not try to solve every style issue up front.

## Core Principles

- Preserve behavior before pursuing elegance.
- Do not add new work to overloaded files without first making a minimal split.
- Reuse existing concepts before creating parallel APIs, DTOs, stores, or services.
- Keep each file easy to place within one layer and one responsibility.
- Prefer documented rules first, then automate them in later phases.

## Layering Rules

### Frontend

- `views/` only owns page composition, route context, and page-level orchestration.
- Complex business logic must move into `composables/` or feature-scoped `modules/`.
- `components/` should contain reusable UI or view-local presentation blocks, not page-owned request orchestration.
- `api/` only owns request functions, transport concerns, and API-facing types.
- `stores/` only owns cross-page or session-level state. Page-private workflows must not be pushed into a global store.
- View-model shaping for a page should not be mixed into raw API types when the page needs derived state.

### Backend

- `controller/` only owns HTTP protocol concerns: params, auth principal, status codes, response shape, and endpoint wiring.
- `service/` must be split by role:
  application services own business orchestration;
  capability services own focused concerns such as AI, image handling, auth flows, SSE coordination, or rate limiting.
- `repository/` only owns data access and persistence queries.
- `client/` only owns external provider interaction and provider-specific request/response handling.
- `entity/` only represents persistence state.
- `dto/` only represents request and response contracts.

## Model Boundary Rules

- Backend `Entity` classes must not be exposed as API responses.
- Request DTOs, response DTOs, and persistence entities must remain distinct types.
- Frontend API-facing types must be separated from page-only display state when a page needs additional derived fields.
- New response shaping must happen in dedicated DTO mapping or view-model mapping, not inline across many callers.
- New storage keys, environment variable names, and reusable message strings must be centralized instead of redefined per file.

## Complexity Thresholds

These thresholds are hard guardrails for future changes.
Existing files that already exceed them are considered frozen for direct feature growth.

### Frontend thresholds

- View files:
  recommended under 220 lines;
  above 300 lines cannot receive new feature logic until a minimal split is done.
- Component files:
  recommended under 180 lines;
  above 260 lines require a split plan before adding behavior.
- API files:
  recommended under 160 lines;
  above 220 lines require extraction by feature or transport concern.
- Stores:
  recommended under 160 lines;
  above 220 lines require state and workflow responsibilities to be separated.

### Backend thresholds

- Controllers:
  recommended under 220 lines;
  above 300 lines cannot take additional business flow logic.
- Services:
  recommended under 220 lines;
  above 300 lines cannot take additional responsibilities.
- External client classes:
  recommended under 220 lines;
  above 300 lines require provider protocol and prompt/schema concerns to be separated.

### Required action when a file is over the limit

- Do not append the new feature directly.
- First extract the smallest coherent unit:
  UI block, request adapter, mapper, orchestration helper, focused service, or capability helper.
- The first extraction must reduce future coupling, not just move lines around.

## AI Collaboration Rules

- AI-generated changes must identify a single owning layer before code is added.
- Do not let AI create a second version of an existing concept with a slightly different name.
- Do not let AI copy a similar controller, page, or service and then patch differences into it.
- Before adding constants, prompts, storage keys, or error messages, check whether a centralized location already exists.
- High-risk areas must not gain new variants until they have a shared abstraction.
- Any AI-generated change should be explainable in one sentence:
  what layer it belongs to, what responsibility it owns, and what existing entrypoint it extends.

## High-Risk Areas

The following areas are controlled zones until later refactor phases:

- SSE streaming flows
- Authentication and token lifecycle
- AI prompt and JSON contract handling
- Trial flow persistence handoff
- Image storage and image response handling
- Environment-specific security and infrastructure configuration

Use the high-risk appendix for handling rules before touching these paths.

## Configuration Rules

- Development defaults, production configuration, and sensitive placeholders must be clearly separated.
- Business logic must not hardcode environment-specific domains, origins, or infrastructure behavior.
- CORS, JWT, AI provider config, mail config, and SQL initialization policy must converge toward dedicated configuration ownership.
- New environment variables, scripts, or deploy assumptions must be documented when introduced.

## Minimum Quality Gates

These are the minimum checks required before merging future code changes.
They are partly manual for now and will be automated later.

### Current validation entrypoints

- Frontend type-check:
  `npm run type-check`
- Frontend build:
  `npm run build`
- Backend validation:
  `./mvnw test`

### Frontend

- The frontend must pass the existing build entrypoint.
- The frontend must pass the dedicated `npm run type-check` entrypoint before build verification.
- New page logic must not be added directly into already overloaded views.
- New API types must not be expanded indefinitely inside one catch-all file.

### Backend

- The backend must pass compile or core test execution through the Maven wrapper workflow used by the project.
- Controllers must not receive new business orchestration.
- Services must not accumulate unrelated concerns.
- New external integrations must go through a dedicated client or focused capability service.

### Documentation

- New env vars, scripts, entrypoints, and important project constraints must be recorded in repo docs.
- Runtime assumptions must not live only in tool prompts or chat context.

## Change Review Baseline

Every meaningful change should be checked against these questions:

- Did the change add work to an already overloaded file?
- Did it introduce a parallel concept instead of reusing an existing one?
- Did it expand coupling across layers?
- Did it introduce environment-specific behavior into business code?
- Did it touch a controlled high-risk area without adding reuse or abstraction?

Use the review checklist appendix for the full review sheet.

## How Later Phases Use This Baseline

### Phase 3: low-risk cleanup

- Allowed: constant extraction, dead file cleanup, repeated utility consolidation, naming cleanup, documentation alignment.
- Not allowed: behavior changes disguised as cleanup.

### Phase 4: structural refactor

- First targets:
  `HomeView.vue`,
  `MeaningLogController.java`,
  `MeaningLogService.java`.
- Those refactors must follow this document's layer and complexity rules instead of inventing structure during implementation.
- Once these refactors are completed, record the resulting ownership boundaries and remaining duplication before starting deeper optimization.

### Controlled exceptions

- High-risk areas may receive only local containment work until shared abstractions are defined.
- Prompt contracts, auth flow, and streaming behavior should be stabilized before deeper redesign.
