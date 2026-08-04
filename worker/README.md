# worker/ — Python ingestion worker

Owns: nothing user-facing. Polls `ingestion_jobs`, claims pending jobs,
does transcription/parsing/embedding work, writes chunks + embeddings back
to Postgres, updates job status.

Not created yet — placeholder. Keep this service stateless and narrow per
AGENTS.md — resist adding any business logic here that belongs in the
Java backend.
