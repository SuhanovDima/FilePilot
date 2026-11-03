package io.github.filepilot.scan;

import io.github.filepilot.config.*;
import io.github.filepilot.workspace.*;
import io.github.filepilot.classification.RuleClassifier;
import io.github.filepilot.persistence.OperationRepository;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import static java.nio.file.FileVisitResult.*;
import static java.nio.file.LinkOption.NOFOLLOW_LINKS;

@Service
public class ScanService {
    private final WorkspaceService workspaces;
    private final WorkspacePolicy policy;
    private final RuleClassifier rules;
    private final PilotSettings settings;
    private final OperationRepository repo;
    public ScanService(WorkspaceService workspaces, WorkspacePolicy policy, RuleClassifier rules, PilotSettings settings, OperationRepository repo) {
        this.workspaces=workspaces; this.policy=policy; this.rules=rules; this.settings=settings; this.repo=repo;
    }
    public static String fingerprint(BasicFileAttributes a) {
        return a.size() + ":" + a.lastModifiedTime() + ":" + a.creationTime() + ":" + a.fileKey();
    }
    public synchronized ScanRun scan(UUID workspaceId) throws IOException {
        var w = workspaces.get(workspaceId); Path root = policy.root(w);
        List<FileEntry> files = new ArrayList<>(); boolean[] partial = {false};
        int[] entries = {0};
        Files.walkFileTree(root, Set.of(), settings.maxDepth + 1, new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (++entries[0] > settings.maxFiles * 10 || Thread.currentThread().isInterrupted()) { partial[0] = true; return TERMINATE; }
                if (!dir.equals(root)) {
                    if (dir.getFileName().toString().startsWith(".") || Files.isHidden(dir)) return SKIP_SUBTREE;
                    try { policy.resolve(w, relative(root, dir)); } catch (PilotException e) { partial[0]=true; return SKIP_SUBTREE; }
                }
                return CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
                if (++entries[0] > settings.maxFiles * 10 || Thread.currentThread().isInterrupted()) { partial[0]=true; return TERMINATE; }
                if (attrs.isDirectory()) { partial[0]=true; return CONTINUE; }
                if (!attrs.isRegularFile() || attrs.isSymbolicLink() || attrs.isOther() || path.getFileName().toString().startsWith(".") || Files.isHidden(path)) return CONTINUE;
                if (files.size() >= settings.maxFiles) { partial[0]=true; return TERMINATE; }
                String rel = relative(root, path);
                try {
                    policy.resolve(w, rel);
                    var a = Files.readAttributes(path, BasicFileAttributes.class, NOFOLLOW_LINKS);
                    if (!a.isRegularFile()) { partial[0]=true; return CONTINUE; }
                    files.add(new FileEntry(UUID.randomUUID(), rel, rules.extension(path.getFileName().toString()), a.size(), a.lastModifiedTime().toString(), String.valueOf(a.fileKey()), fingerprint(a), rules.category(path.getFileName().toString()), "RULE"));
                } catch (PilotException | IOException e) { partial[0]=true; }
                return CONTINUE;
            }
            @Override public FileVisitResult visitFileFailed(Path path, IOException exc) { partial[0]=true; return CONTINUE; }
            @Override public FileVisitResult postVisitDirectory(Path path, IOException exc) { if (exc != null) partial[0]=true; return CONTINUE; }
        });
        files.sort(Comparator.comparing(FileEntry::relativePath));
        var scan = new ScanRun(UUID.randomUUID(), workspaceId, Instant.now(), partial[0] ? "PARTIAL_SCAN" : "COMPLETE", files, partial[0] ? "Limits, unsafe paths or unreadable entries omitted" : "Metadata only; symbolic links and hidden entries ignored");
        repo.put("scan_runs", scan.id(), scan);
        repo.audit("SCAN_COMPLETED",scan.id());
        return scan;
    }
    public ScanRun get(UUID id) { var s = repo.get("scan_runs", id, ScanRun.class); workspaces.get(s.workspaceId()); return s; }
    public static String relative(Path root, Path path) { return root.relativize(path).toString().replace('\\', '/'); }
}
