import logging
import uuid

from .db import execute, insert_many
from .r2_client import download_pdf
from .pdf_parser import extract_pages
from .chunker import chunk_document
from .embedder import embed_texts

log = logging.getLogger(__name__)


def _vec_str(embedding: list[float]) -> str:
    """Format a float list as a pgvector literal: '[0.1,0.2,...]'"""
    return "[" + ",".join(str(x) for x in embedding) + "]"


def _claim_job():
    """
    Atomically claim one pending job via UPDATE...RETURNING with SKIP LOCKED.
    Returns (job_id, document_id) or None if no pending jobs exist.
    """
    return execute(
        """
        UPDATE ingestion_jobs
        SET status = 'processing', updated_at = now()
        WHERE id = (
            SELECT id FROM ingestion_jobs
            WHERE status = 'pending'
            ORDER BY created_at
            LIMIT 1
            FOR UPDATE SKIP LOCKED
        )
        RETURNING id, document_id
        """,
        fetch_one=True,
    )


def _get_document(document_id):
    """Returns (id, course_id, storage_key) or None."""
    return execute(
        "SELECT id, course_id, storage_key FROM documents WHERE id = %s",
        (document_id,),
        fetch_one=True,
    )


def _set_document_status(document_id, status: str):
    execute("UPDATE documents SET status = %s WHERE id = %s", (status, document_id))


def _set_job_status(job_id, status: str, error: str | None = None):
    execute(
        "UPDATE ingestion_jobs SET status = %s, error_message = %s, updated_at = now() WHERE id = %s",
        (status, error, job_id),
    )


def process_next_job() -> bool:
    """
    Claim and process one pending job.
    Returns True if a job was found (regardless of success/failure),
    False if the queue was empty (caller should sleep before retrying).
    """
    row = _claim_job()
    if row is None:
        return False

    job_id, document_id = row
    log.info("Claimed job %s for document %s", job_id, document_id)

    try:
        doc = _get_document(document_id)
        if doc is None:
            raise ValueError(f"Document {document_id} not found in DB")

        doc_id, course_id, storage_key = doc
        _set_document_status(document_id, "processing")

        # Download
        log.info("Downloading %s from R2", storage_key)
        pdf_bytes = download_pdf(storage_key)

        # Parse
        pages = extract_pages(pdf_bytes)
        log.info("Parsed %d pages from document %s", len(pages), document_id)

        # Chunk
        chunks = chunk_document(pages)
        log.info("Created %d chunks from document %s", len(chunks), document_id)

        # Embed (batched)
        embeddings = embed_texts([c.text for c in chunks])

        # Insert all chunks in one transaction — atomic: either all land or none do
        rows = [
            (
                str(uuid.uuid4()),
                str(doc_id),
                str(course_id),
                chunk.text,
                _vec_str(embedding),
                "pdf_text",
                chunk.page_number,
            )
            for chunk, embedding in zip(chunks, embeddings)
        ]
        insert_many(
            """
            INSERT INTO chunks
                (id, document_id, course_id, content, embedding, source_type, page_number)
            VALUES (%s, %s, %s, %s, %s::vector, %s, %s)
            """,
            rows,
        )
        log.info("Inserted %d chunks for document %s", len(rows), document_id)

        _set_document_status(document_id, "ready")
        _set_job_status(job_id, "done")
        log.info("Job %s completed successfully", job_id)

    except Exception as exc:
        log.error("Job %s failed: %s", job_id, exc, exc_info=True)
        try:
            _set_job_status(job_id, "failed", str(exc))
            _set_document_status(document_id, "failed")
        except Exception as update_exc:
            log.error("Failed to update job/document status after failure: %s", update_exc)

    return True  # A job was dequeued (even if it failed) — don't sleep
