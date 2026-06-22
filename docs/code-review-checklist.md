# Code Review Checklist

Use this checklist for all cleanup and refactor work after Phase 2.

## Change Summary

- What user-facing behavior is expected to stay the same?
- What file or module is the primary owner of this change?
- Does this change touch a high-risk area listed in `docs/high-risk-areas.md`?

## Layer Ownership

- Is the responsibility placed in the correct layer?
- Did the change keep `views` out of reusable business logic?
- Did the change keep controllers out of business orchestration?
- Did the change keep repositories limited to persistence concerns?
- Did the change avoid leaking entities directly into API responses?

## Complexity Control

- Does the change increase the size of an already overloaded file?
- If a file is already above the baseline threshold, was a minimal split done first?
- Did the change reduce or increase cross-file coupling?
- Did it add a second place for the same concept to live?

## Reuse and Duplication

- Was an existing API, service, DTO, helper, or constant reused where possible?
- Did the change duplicate SSE, auth, AI, image, or error-handling logic?
- Did it create a near-copy of another page, controller, or service?

## Configuration and Safety

- Did the change add hardcoded environment behavior into business code?
- Were new env vars, origins, keys, or scripts documented?
- Did it expand a controlled high-risk area without an abstraction plan?
- Did it change any auth, trial, AI contract, image, or streaming behavior unintentionally?

## Quality Gates

- Can the relevant frontend or backend validation entrypoint still pass?
- Are new constraints, assumptions, or module entrypoints documented?
- Can another engineer explain the owning layer and responsibility of this change in one sentence?

## Decision

- Approve if the change reduces entropy or at least does not increase it.
- Reject or request changes if the patch grows overloaded files, duplicates concepts, or hides environment-specific behavior inside business logic.
