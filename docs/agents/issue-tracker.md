# Issue Tracker

This repository tracks work items as local markdown files inside the repository.

## Location

Issues live under:

```text
.scratch/<feature>/
```

Example:

```text
.scratch/config-sync/001-reader-validation.md
```

## Expectations

- One markdown file per issue
- Filenames should be concise and descriptive
- Issues should contain enough context for asynchronous implementation
- Related research, notes, and implementation plans may live alongside the issue

## Agent behavior

Skills that create or manage issues should:

- Create markdown issue files under `.scratch/`
- Preserve existing issue structure and naming conventions
- Update issue files in place when refining scope or status
- Avoid introducing additional issue-tracking systems unless explicitly requested

## Suggested issue structure

```md
# Title

## Problem

## Desired outcome

## Constraints

## Notes
```
