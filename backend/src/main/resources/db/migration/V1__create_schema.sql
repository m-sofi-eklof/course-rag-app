CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE courses (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    name       VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE documents (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    course_id   UUID        NOT NULL REFERENCES courses(id),
    filename    VARCHAR(255) NOT NULL,
    type        VARCHAR(50)  NOT NULL DEFAULT 'pdf',
    storage_key TEXT         NOT NULL,
    status      VARCHAR(50)  NOT NULL DEFAULT 'uploaded',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE ingestion_jobs (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id   UUID        NOT NULL REFERENCES documents(id),
    status        VARCHAR(50) NOT NULL DEFAULT 'pending',
    error_message TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE chunks (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id       UUID        NOT NULL REFERENCES documents(id),
    course_id         UUID        NOT NULL REFERENCES courses(id),
    content           TEXT        NOT NULL,
    embedding         vector(1536),
    source_type       VARCHAR(50) NOT NULL DEFAULT 'pdf_text',
    page_number       INTEGER,
    timestamp_seconds INTEGER,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- HNSW index: no pre-training required, works well on small datasets
CREATE INDEX chunks_embedding_idx ON chunks USING hnsw (embedding vector_cosine_ops);
