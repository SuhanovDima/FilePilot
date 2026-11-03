package io.github.filepilot.workspace;

import io.github.filepilot.config.PilotException;
import io.github.filepilot.persistence.OperationRepository;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class WorkspaceService {
    private final WorkspacePolicy policy;
    private final OperationRepository repo;
    public WorkspaceService(WorkspacePolicy policy, OperationRepository repo) { this.policy = policy; this.repo = repo; }
    public synchronized Workspace add(String input, boolean confirmed) throws IOException {
        PilotException.require(confirmed, "CONFIRMATION_REQUIRED");
        var root = policy.authorizeRoot(input);
        for (Workspace existing : list()) if (existing.rootPath().equals(root.toString())) return existing;
        var w = new Workspace(UUID.randomUUID(), root.toString(), Instant.now(), true, policy.identity(root));
        repo.put("workspaces", w.id(), w); return w;
    }
    public List<Workspace> list() { return repo.all("workspaces", Workspace.class).stream().filter(Workspace::enabled).toList(); }
    public Workspace get(UUID id) {
        var w = repo.get("workspaces", id, Workspace.class);
        PilotException.require(w.enabled(), "UNAUTHORIZED_ROOT"); return w;
    }
    public synchronized void revoke(UUID id) {
        var w = get(id); repo.put("workspaces", id, new Workspace(w.id(), w.rootPath(), w.createdAt(), false, w.identity()));
    }
}
