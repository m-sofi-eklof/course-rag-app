# CLAUDE.md

@AGENTS.md

Claude Code doesn't natively read AGENTS.md yet, so this file exists purely
to import it — the `@AGENTS.md` line above pulls in the full project memory.

Edit AGENTS.md, not this file. Keeping one source of truth is the point:
Codex, Cursor, and most other agents read AGENTS.md directly; this file just
bridges Claude Code to the same content so nothing drifts out of sync.

Claude-specific note: when in plan mode, propose the plan for the *current
phase only* (see phased build plan in AGENTS.md) — don't plan the whole
project at once.
