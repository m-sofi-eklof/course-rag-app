# AGENTS.md — Multi-Course Multimodal RAG Study Assistant

This is the canonical project memory file. It's written in the open AGENTS.md
standard so it's read natively by Codex, Cursor, Copilot, Gemini CLI, and most
other coding agents. Claude Code reads it too, via the `@AGENTS.md` import in
`CLAUDE.md` — don't duplicate this content there, edit it here only.

## What this is

A portfolio project: a RAG app for chatting with course material (lecture
videos, slide PDFs, textbook PDFs). Deliberately polyglot stack to demonstrate
full-stack + ML pipeline skills across a job interview. Not a startup, not
production scale — clean, explainable, interview-talkable code over
premature scaling.

## Tech stack (fixed, don't deviate without discussing first)

- Frontend: React + TypeScript
- Backend/orchestrator: Java (Spring Boot) — owns auth, course/document CRUD,
  file upload to R2, ingestion job creation, chat endpoint, source lookup
- Ingestion/ML worker: Python (FastAPI or a simple polling worker) — owns
  nothing user-facing. Polls `ingestion_jobs`, claims pending jobs, does
  transcription/parsing/embedding, writes chunks back, updates status.
  Keep it stateless and narrow — resist the urge to add worker-owned
  business logic.
- Database: PostgreSQL + pgvector — single DB for metadata AND embeddings.
  Do not introduce a separate vector DB.
- Object storage: Cloudflare R2 (S3-compatible, no egress fees)
- ML: hosted APIs only (Anthropic and/or OpenAI). No self-hosted models,
  no GPU dependency, ever.
- Job queue: none. A Postgres `ingestion_jobs` table with a status column
  (`pending`/`processing`/`done`/`failed`), polled every few seconds. Do not
  add Redis/RabbitMQ — it's not needed at this scale and it's not the point
  of the exercise.

## Budget constraint

Total infra cost must stay under **$15/month**. Prefer free tiers
(Supabase/Neon for Postgres, Vercel for frontend, R2 free tier) plus one
cheap Digital Ocean droplet ($4-6/mo) running Java + Python via Docker
Compose. Every design decision — including RAG pattern choices below —
should be weighed against this budget, not just against "best practice."

## Core product behavior

1. User creates/selects Courses. Each course is an isolated namespace —
   documents and chat only ever reference material within the selected
   course.
2. Within a course, user uploads: lecture videos (audio extracted +
   transcribed, video track not analyzed), lecture slide/notes PDFs,
   textbook PDFs (large, mixed text/scanned/diagrams).
3. Ingestion pipeline (async, one background job per uploaded file):
   - Video → extract audio → hosted Whisper API → chunk transcript with
     timestamp metadata (so answers can cite "Lecture 3, 14:32")
   - PDF → parse text page-by-page; detect embedded images/figures; for
     each image, call a hosted vision-capable LLM to produce a plain-text
     description so it's retrievable like any other chunk
   - All extracted text is chunked, embedded, and stored in pgvector with
     metadata: `course_id`, `document_id`, `source_type`, `page_number` OR
     `timestamp`, `chunk_text`
4. Chat: question scoped to current course → similarity search over that
   course's chunks → prompt with retrieved context → LLM call → answer.
5. Source viewing: every answer shows which chunks grounded it — clicking a
   source shows the originating PDF page or video timestamp/excerpt.

## RAG design decisions (locked in — see reasoning log in docs/ if you need
the "why", but these are decided, don't re-litigate per phase)

**Chunking:** structure-aware primary boundaries first — respect page
breaks for PDFs and timestamp segments for transcripts, so citation
granularity stays clean — then semantic splitting *within* each boundary
for chunk sizing. Not pure semantic chunking across the whole document
(breaks page/timestamp citation), not naive fixed-token chunking (worse
retrieval coherence).

**Embedding model:** `text-embedding-3-small` (OpenAI), 1536-dim. Cheap,
good quality, keeps pgvector index size reasonable on a small droplet.
`voyage-3-lite` is an acceptable swap if OpenAI embedding cost becomes a
concern. Do not use `text-embedding-3-large` — not worth the cost/storage
at this scale.

**RAG patterns, in priority order:**
1. Query routing by source type (transcript / pdf_text / pdf_image) — this
   is the core multimodal differentiator of the app. Implement in Phase 3+
   once multiple source types exist.
2. Corrective RAG (grade retrieved chunk relevance before generating;
   fall back to "not covered in this course material" instead of
   hallucinating) — implement once Phase 1 retrieval is working, it's cheap
   and directly addresses a real failure mode of course-scoped retrieval.
3. Adaptive routing by query complexity (simple lookup vs. comparative
   question) — nice-to-have, add if time allows.
4. HyDE — optional toggle for vague/conceptual queries, not the default
   path. Costs an extra LLM call per use.
5. Self-RAG — explicitly out of scope. Needs a fine-tuned model to be done
   properly; not feasible with hosted APIs only.
6. GraphRAG — explicitly out of scope for the build. Good "how would you
   extend this" interview answer, too expensive to build against a
   <$15/mo budget (entity/relation extraction over every chunk).
7. Agentic RAG (full agent-driven multi-step retrieval loop) — explicitly
   not used. Single-course, single vector store, roughly one retrieval hop
   per question doesn't need it. Know why it's not needed if asked.

## DB schema (starting point — confirm/refine in Phase 1, don't restructure
later without good reason)

- `courses (id, name, created_at)`
- `documents (id, course_id, filename, type [video|pdf], storage_url,
  status, created_at)`
- `chunks (id, document_id, course_id, content, embedding vector,
  source_type [transcript|pdf_text|pdf_image], page_number,
  timestamp_seconds)`
- `ingestion_jobs (id, document_id, status, error_message, created_at,
  updated_at)`
- `chat_messages (id, course_id, role, content, created_at)` — optional
- `chat_sources (message_id, chunk_id)`

## API surface (Java backend, rough draft)

- `POST /courses` / `GET /courses` / `GET /courses/{id}`
- `POST /courses/{id}/documents` (multipart → R2, creates document + job row)
- `GET /courses/{id}/documents` (list with ingestion status)
- `POST /courses/{id}/chat` (question in, retrieval + LLM answer + source
  chunk refs out)
- `GET /chunks/{id}` (fetch a chunk's context for the source viewer)

## Phased build plan — build and demo incrementally, do NOT skip ahead

1. **Text-only PDF RAG, single course.** Upload a text-based PDF, parse +
   chunk + embed, chat against it, show sources by page number. No video,
   no images yet. Get end-to-end plumbing working first.
2. **Multi-course support.** Course creation/switching, scoping retrieval
   to the selected course.
3. **PDF image handling.** Detect embedded images, vision LLM descriptions,
   store alongside text chunks. Implement query routing by source type here.
4. **Video transcription.** Audio extraction, Whisper API, timestamp-based
   chunking and citation.
5. **Polish.** Job status UI, better source viewer (inline PDF preview /
   audio jump-to-timestamp), ingestion error handling.

At the start of each phase: confirm the plan for that phase before writing
code. At the end of each phase: it should run end-to-end and be demoable
before moving to the next one.

## Constraints and conventions to respect throughout

- Python worker: stateless, narrow, ingestion-only. No user-facing logic.
- Hosted APIs only for all ML tasks. No self-hosted models, no GPU deps.
- Postgres+pgvector over a separate vector DB.
- Deployable on: Vercel (frontend), one small DO droplet via Docker Compose
  (Java + Python), Supabase/Neon free tier (Postgres), R2 free tier
  (storage). No hardcoded local-only assumptions — use env vars.
- This is a portfolio project: code should be clean and interview-talkable,
  but don't over-engineer for scale that will never be needed.
- Before implementing anything non-trivial: state the plan, wait for
  confirmation, then implement in small, independently-verifiable steps.
