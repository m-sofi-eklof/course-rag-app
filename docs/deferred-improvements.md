# Deferred improvements

Tracked gaps that are known and intentionally not blocking the current phase.
Revisit before anything here is exposed beyond local dev.

## No auth on `DELETE /courses/{id}` (and every other endpoint)

There is no Spring Security / auth layer in the backend at all yet — every
endpoint (`POST /courses`, uploads, chat, chunk lookup, delete) is reachable
by anyone who can reach the server. `DELETE /courses/{id}` is the one worth
calling out specifically because it's destructive and irreversible: it
cascades through `documents`, `ingestion_jobs`, and `chunks` via the V2
migration's `ON DELETE CASCADE` FKs, and best-effort deletes the course's R2
objects (see `CourseService.delete`). A stray or malicious request against
this endpoint permanently destroys course data with no confirmation step.

Not fixing now because: no auth exists anywhere yet, and adding it piecemeal
for one endpoint would be a false sense of security while everything else
stays open. Address as part of introducing real auth (session/JWT) across
the whole API, not as a one-off guard on this route.

## No `DELETE /documents/{id}` endpoint

There's currently no way to remove a single document short of going straight
to the DB and object storage by hand (which is how a stray test upload had
to be cleaned up during Phase 2 testing — deleting the `documents` row
directly, relying on the V2 cascade FKs for `chunks`/`ingestion_jobs`, and
separately removing the R2/MinIO object since that's not part of any DB
cascade). Legitimate feature to want — users will eventually need to remove
a bad upload without nuking the whole course — but it's a detour from
Phase 2's scope (multi-course support), not a blocker for it.

Should follow the same shape as `CourseService.delete`: best-effort R2
object delete for that document's storage key, then let cascades clean up
`chunks`/`ingestion_jobs` in the DB.
