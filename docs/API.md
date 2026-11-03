# Local API

All endpoints are under `http://127.0.0.1:8080/api/v1`. No CORS. Keep the session cookie returned by GET `/csrf`;
send its token as `X-CSRF-TOKEN` on POST/PATCH/DELETE. Origin, if present, must exactly match the local origin.

- GET `/health`: appStatus, llmStatus (last attempt), model.
- GET `/csrf`: headerName, token; creates a local session as needed.
- GET `/workspaces`: enabled workspace IDs and local root paths (never sent to AI).
- POST `/workspaces`: `{"rootPath":"<existing absolute root>","confirmed":true}`.
- DELETE `/workspaces/{id}`: revoke permission; no filesystem deletion.
- POST `/workspaces/{id}/scans`: metadata scan; returns first 100 entries plus total fileCount.
- GET `/scans/{id}?offset=0&limit=100`: paginated metadata (limit 1–200).
- POST `/scans/{id}/duplicates`: bounded read-only hash computation; GET gets cached result; DELETE cancels active computation.
- POST `/plans`: `{"scanId":"UUID","instruction":"organize by type","useAI":false}`.
- GET `/plans/{id}`: full bounded preview, including conflicts/no-op, source, destination, reason, confidence and source.
- PATCH `/plans/{id}/actions`: `{"actionIds":["UUID"]}`; unknown IDs and conflicted actions are refused.
- POST `/plans/{id}/execute`: `{"confirmed":true}`. No paths accepted. Empty selection refuses; repeat does not move twice.
- GET `/operations?workspaceId=UUID&offset=0&limit=100`: paginated durable journal.
- POST `/operations/{batchId}/undo`: `{"confirmed":true}`; batchId is the executed plan ID.

Operational errors use RFC 9457 Problem Details with a machine-readable `code`. Invalid DTOs return 400,
unknown resources 404, unavailable permissions 403, precondition conflicts 409. Security-filter refusals (including
CSRF/Origin/Host) use HTTP 403. UI forms use the same services with CSRF-protected POSTs and escaped templates.

Never send file contents, secrets or real API keys to the API. Plans and inventory are metadata only. File operations
are synchronous and bounded; HTTP-disconnect is not a rollback. Inspect the journal if a browser request times out.
