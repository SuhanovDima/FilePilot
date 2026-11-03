# Security model

FilePilot runs for one trusted OS user on 127.0.0.1. CSRF tokens, session cookies, same-origin/Host checks,
no CORS and a restrictive CSP protect browser requests. Do not expose this service with port forwarding.
Local malware with the same OS privileges, a compromised browser, malicious filesystem providers and hostile
concurrent filesystem mutation are outside an absolute guarantee. A desktop application is not an OS sandbox.

Only explicitly confirmed existing roots are allowed. The disk root, entire home, UNC paths and application-data
overlap are refused. Hidden entries, symlinks, junctions/reparse points are omitted; all existing path components
are checked again at execution. User-selected roots under system directories still require care; the UI does not
automatically select them. Root identity is checked with fileKey and creation time. macOS paths using `/var` or `/tmp`
symlink aliases must be entered using their actual `/private/...` path. No symlink alias is automatically authorized.

Planning reads metadata only and creates no destination directories. Categories map to fixed Java enum folders.
Portable relative-name validation denies traversal, absolute paths, alternate data streams, control characters,
Windows reserved names and ambiguous trailing dots/spaces. Case-folded destination reservations conservatively
reject collisions even on case-sensitive disks. Per-application execution/selection is serialized; another process
cannot open the same database (instance lock). Do not run separate data directories against the same workspace.

Immediately before each move: reauthorize paths, require an unchanged regular source, an absent destination,
read/write permission checks, matching file stores, metadata and bounded SHA-256. Only files <=64 MiB can be moved
in this MVP so that strict integrity checking is bounded. Each confirmation is limited to 200 actions / 256 MiB.
Files never receive REPLACE_EXISTING. Java's ATOMIC_MOVE
allows provider-specific replacement, so FilePilot intentionally uses ordinary no-replace Files.move within one
file store instead of the specification's initial ATOMIC_MOVE preference. No cross-volume copy/delete fallback.
There remains a TOCTOU window against hostile concurrent actors; Java NIO cannot guarantee this contract against
such an actor across all supported providers. Stop other organizers/editors before execution.

PENDING is durably committed before the move; DONE follows verification. Failures preserve evidence and partial
results. Restart reconciliation reads state without executing moves. Ambiguous states stay NEEDS_RECONCILIATION
and need manual inspection. Undo is separately confirmed and checks SHA-256, metadata and vacant original paths.
Conflicts never overwrite. Undo does not remove empty category directories. Duplicate requests never repeat a move.

LLM sees bounded relative names and metadata only over a validated numeric loopback endpoint; no contents,
absolute roots, executable tools, shell commands or credentials. Suggestions are validated against known IDs,
categories and confidence. Rules remain available when the model is absent or invalid. No automatic model downloads.
