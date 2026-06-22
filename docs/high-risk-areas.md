# High-Risk Areas

This appendix lists areas that must be handled as controlled zones until later refactor phases.

## 1. SSE Streaming Flows

Affected areas include:

- `meaning-log-backend/src/main/java/com/chad/meaninglog/controller/MeaningLogController.java`
- `meaning-log-backend/src/main/java/com/chad/meaninglog/controller/XiaojiChatController.java`
- `meaning-log-backend/src/main/java/com/chad/meaninglog/controller/PublicTrialController.java`
- `meaning-log-frontend/src/api/stream.ts`

Risks:

- request lifecycle mismatch
- incomplete persistence timing
- inconsistent `done` event handling
- frontend and backend protocol drift

Rules:

- Do not create new streaming variants by copying existing endpoints.
- Do not change event naming or completion semantics casually.
- Prefer containment and shared helpers before behavior changes.

## 2. Authentication and Token Lifecycle

Affected areas include:

- `meaning-log-backend/src/main/java/com/chad/meaninglog/security/`
- `meaning-log-backend/src/main/java/com/chad/meaninglog/service/AuthService.java`
- `meaning-log-frontend/src/stores/authStore.ts`
- `meaning-log-frontend/src/router/index.ts`
- `meaning-log-frontend/src/api/http.ts`

Risks:

- silent auth regressions
- inconsistent session invalidation
- redirect loops
- token persistence drift between frontend and backend assumptions

Rules:

- Do not introduce new auth state locations unless there is a migration plan.
- Do not change token invalidation behavior without explicit verification.
- Keep auth error handling centralized rather than page-specific.

## 3. AI Prompt and JSON Contracts

Affected areas include:

- `meaning-log-backend/src/main/java/com/chad/meaninglog/client/OpenAiClient.java`
- `meaning-log-backend/src/main/java/com/chad/meaninglog/service/AiService.java`
- `meaning-log-frontend/src/api/logs.ts`

Risks:

- prompt drift
- invalid JSON output assumptions
- inconsistent response parsing
- hidden coupling between prompt shape and frontend preview/apply flows

Rules:

- Do not fork prompt logic into multiple files without a contract boundary.
- Do not change AI response shape unless all consumers are reviewed together.
- Prefer one provider contract path per interaction mode.

## 4. Trial Flow Handoff

Affected areas include:

- `meaning-log-backend/src/main/java/com/chad/meaninglog/controller/PublicTrialController.java`
- frontend trial-related views, auth flow, and local storage handoff logic

Risks:

- inconsistent transition from anonymous trial state to persisted user data
- duplicated pending-draft handling
- unclear ownership of cleanup timing

Rules:

- Do not add alternate trial persistence paths.
- Keep handoff semantics explicit and documented.
- Treat local storage keys used by trial flow as controlled contract keys.

## 5. Image Storage and Image Delivery

Affected areas include:

- `meaning-log-backend/src/main/java/com/chad/meaninglog/service/MeaningLogService.java`
- `meaning-log-backend/src/main/java/com/chad/meaninglog/repository/LogImageRepository.java`
- `meaning-log-backend/src/main/resources/schema.sql`

Risks:

- database growth
- slow list/detail assembly
- persistence consistency on update/delete
- accidental API payload bloat

Rules:

- Do not expand image behavior inside unrelated services.
- Do not add new image persistence behavior without checking update/delete consistency.
- Any future storage redesign must be handled as a dedicated migration effort.

## 6. Environment-Specific Security and Infra Config

Affected areas include:

- `meaning-log-backend/src/main/resources/application.properties`
- `meaning-log-backend/src/main/java/com/chad/meaninglog/security/SecurityConfig.java`
- `meaning-log-frontend/.env.production`

Risks:

- production and development behavior mixing
- hardcoded origins
- placeholder secrets being treated as defaults
- deployment-specific assumptions leaking into source

Rules:

- Do not add more environment branching into business code.
- Document any new infra or deploy assumption immediately.
- Future changes here should converge toward explicit environment ownership, not more inline exceptions.
