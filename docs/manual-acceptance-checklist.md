# Manual Acceptance Checklist

## Purpose

This checklist is used after structural refactors to verify that core user flows still behave correctly at runtime.

Use it when:

- a major frontend page has been split
- a major backend controller/service has been split
- SSE flow handling has been consolidated
- chat or AI workflow internals have been reorganized

## Pre-check

Before running the scenarios below, confirm:

- frontend starts successfully
- backend starts successfully
- database and Redis are available
- AI provider config is valid
- you can log in with a working account

Recommended startup order:

```bash
# backend
cd meaning-log-backend
./mvnw spring-boot:run

# frontend
cd meaning-log-frontend
npm run dev
```

## 1. Login and session basics

Expected goal:
confirm the auth baseline was not broken by refactor side effects

Steps:

1. Open the login page
2. Log in with a valid account
3. Refresh the page
4. Navigate between home, new log, and companion chat
5. Log out

Expected result:

- login succeeds
- page refresh keeps the logged-in state
- route switching does not force unexpected logout
- logout clears the session and returns to the unauthenticated state

## 2. Home quick-create flow

Expected goal:
confirm the `HomeView.vue` split did not break quick entry behavior

Steps:

1. Open the home page
2. Enter a short quick note in the quick-create textarea
3. Save it
4. Verify the new item appears in the log list
5. If the “补充细节” button appears, click it

Expected result:

- save succeeds
- the log list refreshes correctly
- the new log appears with the expected date
- follow-up edit navigation still works

## 3. Home filters and list actions

Expected goal:
confirm the home list composable split did not break list state and item actions

Steps:

1. Search by keyword
2. Filter by AI tag
3. Filter by date
4. Toggle favorite-only
5. Clear filters
6. Toggle favorite on an item
7. Open detail from list
8. Open edit from list
9. Delete a non-critical test item

Expected result:

- every filter applies correctly
- clear resets the list to the expected state
- favorite toggle updates list state immediately
- detail/edit navigation still works
- delete succeeds and the list refreshes

## 4. Home AI drawer flow

Expected goal:
confirm the home AI drawer still works after `HomeView.vue` and backend service splits

Steps:

1. Open AI drawer from an existing log
2. Confirm prior messages load if the log already has chat history
3. Send a refine request
4. Wait for the preview result
5. Apply the preview
6. If undo is available, undo once

Expected result:

- drawer opens normally
- selected log context is correct
- message history loads correctly
- preview returns successfully
- apply updates the log AI content
- undo restores the previous AI version

## 5. New log and edit log flow

Expected goal:
confirm ordinary CRUD still works after backend controller/service restructuring

Steps:

1. Open the new log page
2. Create a log with title, content, mood, and optional image
3. Open the created log
4. Edit the log
5. Save changes

Expected result:

- create succeeds
- image upload still works if configured and used
- edit succeeds
- saved content matches what was submitted

## 6. Trial flow

Expected goal:
confirm trial flow was not broken by SSE support extraction

Steps:

1. Open the trial page while unauthenticated
2. Enter trial content
3. Trigger AI trial analysis
4. After result appears, go to register or login
5. Complete the auth flow
6. Check whether the pending trial content is persisted into the account

Expected result:

- trial analysis succeeds
- trial result renders correctly
- handoff to register/login still works
- post-auth trial persistence still works as before

## 7. Companion chat stream

Expected goal:
confirm `XiaojiChatController` and `XiaojiChatService` split did not break general chat streaming

Steps:

1. Open the companion chat page
2. Start a new general chat
3. Send a message
4. Observe stream output
5. Refresh or revisit the session

Expected result:

- a session is created correctly
- stream output arrives progressively
- completion occurs normally
- saved history is still available after revisit

## 8. Log AI stream

Expected goal:
confirm log AI stream still works after SSE consolidation

Steps:

1. Choose a log that is valid for AI refinement
2. Trigger log AI stream flow
3. Wait for progressive output
4. Confirm final completion event handling
5. Confirm final preview or persisted result behavior

Expected result:

- stream opens successfully
- chunk output arrives progressively
- no premature disconnect occurs
- final state matches previous behavior

## 9. Report generation and report chat stream

Expected goal:
confirm report workflow still works after controller/service split and SSE support extraction

Steps:

1. Open report-related UI
2. Generate a date-based report
3. Observe stream output
4. Confirm final report is saved
5. Open the generated report
6. Enter report chat
7. Trigger report refine stream

Expected result:

- report stream opens normally
- report completion saves the report
- saved report can be reopened
- report chat history works
- report refine stream behaves correctly

## 10. Regression markers to watch closely

If any of the following happens, treat it as a likely refactor regression:

- SSE request opens but never completes
- SSE stream completes without sending the expected final event
- AI preview returns but apply/undo stops working
- log/chat/report message history disappears unexpectedly
- trial handoff no longer persists content
- favorite/delete/list refresh state becomes stale
- opening a page after refresh loses expected session state

## Execution record template

Use this format while checking:

```text
Date:
Tester:
Frontend commit/worktree state:
Backend commit/worktree state:

[Pass/Fail] Login and session basics
Notes:

[Pass/Fail] Home quick-create flow
Notes:

[Pass/Fail] Home filters and list actions
Notes:

[Pass/Fail] Home AI drawer flow
Notes:

[Pass/Fail] New log and edit log flow
Notes:

[Pass/Fail] Trial flow
Notes:

[Pass/Fail] Companion chat stream
Notes:

[Pass/Fail] Log AI stream
Notes:

[Pass/Fail] Report generation and report chat stream
Notes:
```
