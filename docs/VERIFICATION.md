# Verification — 2026-11-03

Environment actually used: macOS 14.5 Intel x86_64; Amazon Corretto JDK 21.0.4; Maven Wrapper 3.3.4 / Maven 3.9.9.
No real user directories were scanned or modified. All file operations used JUnit `@TempDir` or a fresh generated
FilePilotDemo temporary directory. No cloud models, real API keys, model downloads or git push.

## Stage results

- P0: 1 passing startup test, after correcting JUnit 5 / Spring Test 7 incompatibility and disabling moderation.
- P1: 6 passing tests (rules, allowlist, persistence/reopen, traversal/symlink, limits, revoke).
- P2: 15 passing tests (selective execution, idempotency, expiry, stale source, conflicts, Undo, crash reconciliation).
- P3: 21 passing tests including fake suggestions, malformed output, deadline, actual Spring AI + mock HTTP and redirect denial.
- P4: 23 passing tests including a real HTTP/CSRF/UI/API vertical scenario and duplicate verification.
- P5 final: **30 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS**. Executable JAR successfully repackaged.

## Commands actually executed

```sh
env JAVA_HOME=/Library/Java/JavaVirtualMachines/amazon-corretto-21.jdk/Contents/Home \
  MAVEN_USER_HOME=/private/tmp/filepilot-wrapper \
  ./mvnw -B -Dmaven.repo.local=/private/tmp/filepilot-m2 verify
sh -n scripts/demo-fixtures.sh scripts/start.sh
sh scripts/demo-fixtures.sh
git diff --check
```

Each stage also ran `mvn -B -Dmaven.repo.local=/private/tmp/filepilot-m2 test` with the same JDK 21; after
dependencies were available, stage retests used `-o`. Final wrapper verification took 1m54s on this filesystem.
Raw final build log: `/private/tmp/filepilot-wrapper-verify.log`; detailed reports: `target/surefire-reports/`.
The wrapper's first download and loopback-binding tests required sandbox permission; sandbox DNS/bind failures
were resolved with explicit execution approval. These were environment restrictions, not recorded as test PASS.

## Browser acceptance

Started the packaged JAR on 127.0.0.1:18086 with a new temporary SQLite data directory. Through the UI:
allowed only the generated Inbox; scanned 7 synthetic files; verified two duplicate fixtures; built a draft;
deselected notes.txt; separately confirmed execution; observed 6 DONE + 1 SKIPPED; separately confirmed Undo;
observed 6 UNDO/DONE. Compared all 7 original byte sequences after Undo: exact match. Empty category directories remain.
The final JAR was restarted on the same temporary database for review. Screenshots are under `docs/screenshots/`.
Reloading the browser after restart preserved the full journal. Local HTMX refreshed the status fragment successfully;
the browser reported no console errors or warnings during this acceptance scenario.
One UI snapshot timed out during the synchronous Undo request; a subsequent state read confirmed completion,
without resubmitting the operation. Filesystem checks are intentionally conservative and can be slow.

## Negative coverage and limits

Includes unknown resources/actions, missing confirmation, empty selection, TTL, source changes, late destination
conflict, symlink swap, root replacement, revoke, traversal/absolute/Windows-reserved paths, case-folded collision,
same-metadata changed Undo target, occupied/missing Undo source/target, partial success, PENDING before/after syscall,
large file/batch limits, hash cancellation, application-data path alias overlap, hostile instruction-like filenames,
invalid model JSON/IDs/categories/confidence, unavailable/slow model, redirected HTTP, CSRF, foreign Origin and Host.

Windows/Linux runtime and CI jobs were not executed in this session. PowerShell is unavailable here; PS1 scripts
were authored for Windows and await Windows CI/manual validation. A live Ollama/llama.cpp 2B/4B inference test was
not performed. Cross-volume behavior is denied by FileStore checks but was not exercised on a second physical volume.
No penetration-test, dependency vulnerability scan or guarantee against malicious concurrent local processes is claimed.
