# Domain Docs

This repository uses a single-context documentation layout.

## Canonical locations

- Domain context: `CONTEXT.md`
- Architectural decisions: `docs/adr/`

## Agent behavior

Skills that need project or domain understanding should:

- Read `CONTEXT.md` before making architectural decisions
- Consult `docs/adr/` for historical decisions and constraints
- Prefer extending existing terminology over introducing competing vocabulary

## Repository layout

```text
CONTEXT.md
docs/
  adr/
```

If the repository evolves into a monorepo or develops multiple bounded contexts, migrate to a multi-context layout using a root `CONTEXT-MAP.md`.
