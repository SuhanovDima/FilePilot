# Persistence

SQLite JDBC, WAL, synchronous FULL, busy timeout 5 seconds; a process-held file lock prevents two instances using one data directory.
Migration 1 is transactional and tracked with `PRAGMA user_version`. Tables: workspaces, scan_runs, file_inventory,
plans, plan_actions, operation_journal, audit_events. The MVP stores versioned Java record snapshots in JSON payloads
under UUID primary keys; inventory is embedded in scan snapshots and actions in plans to make each snapshot update atomic.
The reserved inventory/action tables allow a future normalized migration. This is an intentional deviation from the
specification's suggested column layout. Journal entries are independent durable rows written before filesystem moves.

Application data belongs outside all permitted workspaces. No file contents, real API keys or full model responses are persisted.
Paths and filenames are sensitive metadata: protect the data directory with your OS account's access controls.
No automatic retention/deletion is implemented; database growth is a known MVP limitation.
