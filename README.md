# Course RAG Study Assistant

A portfolio project: a multimodal RAG app for chatting with course
material (lecture videos, slide PDFs, textbook PDFs), built to practice
using AI coding agents (Claude Code and Codex) effectively.

## Stack

React + TypeScript (frontend) · Java/Spring Boot (backend/orchestrator) ·
Python/FastAPI (ingestion worker) · Postgres + pgvector (metadata +
embeddings, single DB) · Cloudflare R2 (object storage) · hosted APIs only
for transcription/vision/embeddings/chat (Anthropic and/or OpenAI). Budget
target: under $15/month. Full rationale in `AGENTS.md`.

## Repo layout

```
course-rag-app/
├── AGENTS.md              canonical project memory (read by Codex natively)
├── CLAUDE.md               thin @AGENTS.md import for Claude Code
├── backend/                Java Spring Boot — not built yet
├── worker/                 Python ingestion worker — not built yet
├── frontend/                React + TS — not built yet
├── docker-compose.yml       local Postgres+pgvector only
└── .env.example
```

## Status

Scaffolding only. No app code yet — see the phased build plan in
`AGENTS.md`. Phase 1 (text-only PDF RAG, single course) is next.

## Working on this repo

1. Read `AGENTS.md` first — it has the full spec, locked-in RAG design
   decisions, DB schema, and phased plan.
2. `docker compose up -d` for a local Postgres+pgvector instance, copy
   `.env.example` to `.env` and fill in API keys.
3. Start Phase 1. Confirm the plan before implementing.
