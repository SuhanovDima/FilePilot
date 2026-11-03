# Reproducible demo

Use JDK 21 and run `./mvnw verify` then `java -jar target/filepilot-0.1.0.jar`.
Windows: `.\mvnw.cmd verify`; the same JAR runs with `java -jar target\filepilot-0.1.0.jar`.

Generate synthetic files with `sh scripts/demo-fixtures.sh` or `powershell -File scripts\demo-fixtures.ps1`.
Each invocation makes a new temporary folder and prints its canonical Inbox path. Do not substitute Downloads,
Documents or other personal directories. Test PDF/JPG/ZIP files are intentionally plain synthetic text.

1. Open http://127.0.0.1:8080/ and explicitly allow the printed Inbox path.
2. Scan: seven files, no filesystem changes. Inspect relative paths and categories.
3. Request duplicate detection: copy-a.txt and nested/copy-b.txt form a VERIFIED SHA-256 group. Nothing is deleted.
4. Enter `разложи по типам`, `отдельно договоры` or `organize by type`; build a draft plan.
5. Check source/destination paths and deselect at least one file. The confirmation is a separate checkbox and button.
6. Execute. Selected files move to fixed category folders. Repeating execute does not move them again.
7. In history separately confirm Undo. Original bytes return where paths are free. Empty category folders remain.
8. For a conflict demo use only this synthetic dataset: after moving a file, create a different fixture at its original
   name with an editor, then Undo. The original name remains untouched and the journal reports UNDO_CONFLICT.
9. To try AI, follow LOCAL_LLM.md, restart with local runtime enabled, rescan and explicitly check the AI option.
   Stop the runtime and build another plan: rules fallback should remain available.

Do not manually edit the SQLite database or retry NEEDS_RECONCILIATION entries. First inspect both relative paths
under the approved root and compare against your backups. Automatic reconciliation never deletes or moves files.

Temporary fixtures and the test application data may be kept for inspection. No cleanup script removes them automatically.
