import logging
import os
import time
from pathlib import Path

from dotenv import load_dotenv

# Load .env from the project root (two levels above this file: worker/app/ → worker/ → root)
_project_root = Path(__file__).resolve().parent.parent.parent
load_dotenv(dotenv_path=_project_root / ".env")

from .job_processor import process_next_job  # noqa: E402 — import after dotenv

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s %(name)s — %(message)s",
)
log = logging.getLogger(__name__)


def main() -> None:
    interval = int(os.getenv("WORKER_POLL_INTERVAL_SECONDS", "5"))
    log.info("Worker started. Poll interval: %ds", interval)

    while True:
        try:
            processed = process_next_job()
            if not processed:
                # Queue was empty — wait before polling again
                time.sleep(interval)
        except Exception as exc:
            log.error("Unexpected error in poll loop: %s", exc, exc_info=True)
            time.sleep(interval)


if __name__ == "__main__":
    main()
