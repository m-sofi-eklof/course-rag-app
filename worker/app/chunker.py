"""
Structure-aware chunker: page boundary = primary unit, semantic splitting within page.

Strategy:
  1. If a page fits within MAX_TOKENS, emit it as one chunk.
  2. If longer, split on paragraph breaks (\n\n) first, then sentence boundaries.
  3. If a single sentence still exceeds MAX_TOKENS (e.g. long URLs, code blocks,
     tables with no punctuation), hard-split it by token count as a last resort.
  4. Merge pieces up to MAX_TOKENS with a short overlap tail from the previous
     chunk — within the same page only. Never cross page boundaries, which would
     break page-number citation accuracy.
"""
import re
from dataclasses import dataclass

import tiktoken

from .pdf_parser import PageText

MAX_TOKENS = 450
OVERLAP_TOKENS = 80
_ENC = tiktoken.get_encoding("cl100k_base")


@dataclass
class Chunk:
    page_number: int
    text: str


def _count(text: str) -> int:
    return len(_ENC.encode(text))


def _split_sentences(text: str) -> list[str]:
    parts = re.split(r"(?<=[.!?])\s+", text)
    return [p.strip() for p in parts if p.strip()]


def _hard_split(text: str) -> list[str]:
    """
    Last-resort fallback: slice a text that is still over MAX_TOKENS after
    sentence splitting (e.g. a very long URL, code block, or table row with no
    punctuation) into fixed-size token windows. No overlap is added here —
    the caller's normal overlap logic handles continuity between the resulting pieces.
    """
    tokens = _ENC.encode(text)
    return [
        _ENC.decode(tokens[i : i + MAX_TOKENS])
        for i in range(0, len(tokens), MAX_TOKENS)
    ]


def _chunk_page(page: PageText) -> list[Chunk]:
    text = page.text

    if _count(text) <= MAX_TOKENS:
        return [Chunk(page_number=page.page_number, text=text)]

    # Split into paragraphs, then sentences, then hard-split any remaining giants
    paragraphs = [p.strip() for p in re.split(r"\n\n+", text) if p.strip()]
    pieces: list[str] = []
    for para in paragraphs:
        if _count(para) <= MAX_TOKENS:
            pieces.append(para)
        else:
            for sentence in _split_sentences(para):
                if _count(sentence) <= MAX_TOKENS:
                    pieces.append(sentence)
                else:
                    # Single sentence exceeds MAX_TOKENS — hard-split by token count
                    pieces.extend(_hard_split(sentence))

    # Merge pieces into chunks, prepending an overlap tail from the previous chunk
    chunks: list[Chunk] = []
    current: list[str] = []
    current_tokens = 0
    overlap_tail = ""

    for piece in pieces:
        piece_tokens = _count(piece)

        if current_tokens + piece_tokens > MAX_TOKENS and current:
            # Emit the current chunk
            body = " ".join(current)
            chunk_text = (overlap_tail + " " + body).strip() if overlap_tail else body
            chunks.append(Chunk(page_number=page.page_number, text=chunk_text))

            # Compute overlap: last OVERLAP_TOKENS of the emitted chunk text
            encoded = _ENC.encode(chunk_text)
            if len(encoded) > OVERLAP_TOKENS:
                overlap_tail = _ENC.decode(encoded[-OVERLAP_TOKENS:])
            else:
                overlap_tail = chunk_text

            current = [piece]
            current_tokens = piece_tokens
        else:
            current.append(piece)
            current_tokens += piece_tokens

    # Emit the final chunk
    if current:
        body = " ".join(current)
        chunk_text = (overlap_tail + " " + body).strip() if overlap_tail else body
        chunks.append(Chunk(page_number=page.page_number, text=chunk_text))

    return chunks


def chunk_document(pages: list[PageText]) -> list[Chunk]:
    """Chunk all pages; page boundaries are never crossed."""
    all_chunks: list[Chunk] = []
    for page in pages:
        all_chunks.extend(_chunk_page(page))
    return all_chunks
