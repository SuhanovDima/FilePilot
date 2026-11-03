package io.github.filepilot.execution;

import io.github.filepilot.config.PilotException;
import io.github.filepilot.history.JournalEntry;
import io.github.filepilot.persistence.OperationRepository;
import io.github.filepilot.planning.*;
import io.github.filepilot.workspace.*;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
public class ExecutionService {
    private final PlanService plans;
    private final WorkspaceService workspaces;
    private final FileSystemPort fs;
    private final OperationRepository repo;
    public ExecutionService(PlanService plans, WorkspaceService workspaces, FileSystemPort fs, OperationRepository repo) {
        this.plans=plans; this.workspaces=workspaces; this.fs=fs; this.repo=repo;
    }
    public Plan execute(UUID planId, boolean confirmed) {
        PilotException.require(confirmed,"CONFIRMATION_REQUIRED");
        synchronized (plans) {
            reconcile();
            var p=plans.get(planId);
            if (!p.status().equals("DRAFT")) { PilotException.require(!p.status().equals("EXPIRED"),"EXPIRED_PLAN"); return p; }
            plans.validateDraft(p);
            PilotException.require(p.actions().stream().anyMatch(PlanAction::selected),"EMPTY_SELECTION");
            var selected=p.actions().stream().filter(PlanAction::selected).toList();
            PilotException.require(selected.size()<=200 && selected.stream().mapToLong(PlanAction::expectedSize).sum()<=256L*1024*1024,"BATCH_RESOURCE_LIMIT");
            plans.save(p.status("EXECUTING"));
            repo.audit("EXECUTION_CONFIRMED",planId);
            List<PlanAction> result=new ArrayList<>();
            boolean failed=false;
            for (PlanAction a : p.actions()) {
                if (!a.selected()) { result.add(a.status(a.status().equals("PROPOSED") ? "SKIPPED" : a.status())); continue; }
                PilotException.require(a.status().equals("PROPOSED"),"CONFLICT");
                var entry=perform(p.id(),a.id(),p.workspaceId(),"MOVE",a.sourceRelative(),a.destinationRelative(),a.expectedFingerprint(),null,null);
                boolean done=entry.status().equals("DONE"); failed |= !done;
                result.add(a.status(done ? "DONE" : entry.errorCode()));
            }
            p=p.actions(result).status(failed ? "PARTIAL_FAILED" : "COMPLETED"); plans.save(p); return p;
        }
    }
    private JournalEntry perform(UUID batch, UUID action, UUID workspaceId, String type, String source, String dest,
                                 String expected, FileSystemPort.Evidence known, UUID undoOf) {
        JournalEntry entry=null;
        try {
            var w=workspaces.get(workspaceId);
            var evidence=fs.inspect(w,source,expected);
            if (known != null) PilotException.require(evidence.equals(known),"UNDO_CONFLICT");
            PilotException.require(!fs.exists(w,dest),"CONFLICT");
            entry=new JournalEntry(UUID.randomUUID(),batch,action,workspaceId,type,source,dest,evidence,"PENDING",null,undoOf,Instant.now(),null);
            save(entry); // A committed PENDING row always precedes a move.
            fs.move(workspaces.get(workspaceId),source,dest,evidence);
            PilotException.require(fs.matches(workspaces.get(workspaceId),dest,evidence),"NEEDS_RECONCILIATION");
            entry=entry.status("DONE",null); save(entry); return entry;
        } catch (IOException | RuntimeException e) {
            String code=e instanceof PilotException pe ? pe.code() : "IO_FAILURE";
            if (entry == null) {
                entry=new JournalEntry(UUID.randomUUID(),batch,action,workspaceId,type,source,dest,known,"FAILED",code,undoOf,Instant.now(),Instant.now());
            } else {
                // Do not guess whether a syscall succeeded; leave the facts for read-only reconciliation.
                entry=entry.status("NEEDS_RECONCILIATION",code);
            }
            if (type.equals("UNDO") && entry.status().equals("FAILED")) entry=entry.status("UNDO_CONFLICT",code);
            save(entry); return entry;
        }
    }
    public List<JournalEntry> history(UUID workspaceId) {
        workspaces.get(workspaceId);
        return repo.all("operation_journal",JournalEntry.class).stream().filter(j -> j.workspaceId().equals(workspaceId)).toList();
    }
    public List<JournalEntry> undo(UUID batchId, boolean confirmed) {
        PilotException.require(confirmed,"CONFIRMATION_REQUIRED");
        synchronized (plans) {
            reconcile(); var p=plans.get(batchId);
            PilotException.require(Set.of("COMPLETED","PARTIAL_FAILED").contains(p.status()),"INVALID_BATCH");
            repo.audit("UNDO_CONFIRMED",batchId);
            var all=history(p.workspaceId());
            var moves=all.stream().filter(j -> j.batchId().equals(batchId) && j.type().equals("MOVE") && j.status().equals("DONE")).toList().reversed();
            for (var j : moves) {
                if (all.stream().anyMatch(u -> j.id().equals(u.undoOf()))) continue;
                perform(batchId,j.planActionId(),j.workspaceId(),"UNDO",j.destinationRelative(),j.sourceRelative(),j.evidence().fingerprint(),j.evidence(),j.id());
            }
            return history(p.workspaceId()).stream().filter(j -> j.batchId().equals(batchId)).toList();
        }
    }
    private void save(JournalEntry j) { repo.put("operation_journal",j.id(),j); }

    /** Inspect only. Never retry moves automatically after a crash. */
    @EventListener(ApplicationReadyEvent.class)
    public void reconcile() {
        synchronized (plans) {
            for (var j : repo.all("operation_journal",JournalEntry.class)) {
                if (!Set.of("PENDING","NEEDS_RECONCILIATION").contains(j.status())) continue;
                try {
                    var w=workspaces.get(j.workspaceId());
                    boolean src=fs.exists(w,j.sourceRelative()), dst=fs.exists(w,j.destinationRelative());
                    if (!src && dst && fs.matches(w,j.destinationRelative(),j.evidence())) save(j.status("DONE","RECOVERED_AFTER_RESTART"));
                    else if (src && !dst && fs.matches(w,j.sourceRelative(),j.evidence())) save(j.status("FAILED","MOVE_NOT_OBSERVED"));
                    else save(j.status("NEEDS_RECONCILIATION","AMBIGUOUS_FILESYSTEM_STATE"));
                } catch (IOException | RuntimeException e) { save(j.status("NEEDS_RECONCILIATION","UNSAFE_OR_UNAVAILABLE_PATH")); }
            }
            for (var p : repo.all("plans",Plan.class)) {
                if (!p.status().equals("EXECUTING")) continue;
                var entries=repo.all("operation_journal",JournalEntry.class);
                var actions=p.actions().stream().map(a -> entries.stream().filter(j -> j.batchId().equals(p.id()) && j.planActionId().equals(a.id()) && j.type().equals("MOVE")).findFirst()
                    .map(j -> a.status(j.status())).orElse(a.status(a.selected() ? "SKIPPED_AFTER_RESTART" : "SKIPPED"))).toList();
                plans.save(p.actions(actions).status("PARTIAL_FAILED"));
            }
        }
    }
}
