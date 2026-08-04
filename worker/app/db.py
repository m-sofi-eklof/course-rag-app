"""
Thin psycopg3 helpers. The worker opens a fresh connection per operation —
acceptable for a 5-second poll cycle that processes one job at a time.
"""
import os
import psycopg


def _conninfo() -> str:
    return os.environ["DATABASE_URL"]


def execute(sql: str, params=None, *, fetch: bool = False, fetch_one: bool = False):
    """
    Run a single statement with autocommit (each call is its own transaction).
    Returns: one row if fetch_one=True, all rows if fetch=True, None otherwise.
    """
    with psycopg.connect(_conninfo(), autocommit=True) as conn:
        cur = conn.execute(sql, params)
        if fetch_one:
            return cur.fetchone()
        if fetch:
            return cur.fetchall()
        return None


def insert_many(sql: str, rows: list[tuple]):
    """
    Insert multiple rows in a single transaction using executemany.
    Rolls back the whole batch if any row fails.
    psycopg3: executemany lives on the Cursor, not the Connection.
    """
    with psycopg.connect(_conninfo()) as conn:
        with conn.transaction():
            with conn.cursor() as cur:
                cur.executemany(sql, rows)
