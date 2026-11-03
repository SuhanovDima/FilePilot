# Architecture

One Java 21 / Spring Boot process, one SQLite database and a server-rendered local UI. No Docker, Node.js,
external DB or model process is required for the rules path. JSON API and HTML controllers invoke the same services.

WorkspacePolicy is the authorization boundary; IDs never grant access after permission revocation.
ScanService reads bounded metadata snapshots. RuleClassifier provides deterministic categories; ClassifierService
can merge strictly validated local AI suggestions. CommandInterpreter implements a closed, explicit grammar.
PlanService fixes paths to enum category folders and records immutable preconditions. Selection updates are serialized
with execution; once execution begins the plan cannot be edited. TTL expires before any mutations.

ExecutionService writes a PENDING journal entry before calling FileSystemPort. NioFileSystem rechecks paths,
identity, destination absence, permissions, file store and SHA-256 before ordinary no-replace Files.move.
Verification follows the move. Each result is independent: a failed action does not pretend to roll back others.
Undo is a new journaled action and uses the same checks. It never overwrites an occupied source.

SQLiteRepository commits each snapshot/journal row independently with FULL durability in WAL mode. A process-held
lock prevents sharing the same data-dir. After a crash the reconciler only inspects filesystem evidence; it never
replays a move. Unmoved PENDING becomes FAILED, verified moved PENDING becomes DONE, ambiguous cases remain blocked.

The AI adapter has no reference to the executor, workspace root or file API. Its custom SDK transport accepts only
the validated loopback chat endpoint, forbids proxies and redirects and bounds bytes/time. AI cannot authorize paths.
Model calls use one bounded worker; health and rules requests do not require inference.
