from dataclasses import dataclass
import fitz  # PyMuPDF


@dataclass
class PageText:
    page_number: int  # 1-based
    text: str


def extract_pages(pdf_bytes: bytes) -> list[PageText]:
    """Return one PageText per non-blank page, preserving page numbers for citations."""
    doc = fitz.open(stream=pdf_bytes, filetype="pdf")
    pages = []
    for i, page in enumerate(doc):
        text = page.get_text("text").strip()
        if text:
            pages.append(PageText(page_number=i + 1, text=text))
    doc.close()
    return pages
