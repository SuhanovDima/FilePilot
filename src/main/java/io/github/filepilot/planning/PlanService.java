package io.github.filepilot.planning;

import io.github.filepilot.classification.*;
import io.github.filepilot.commands.CommandInterpreter;
import io.github.filepilot.config.PilotException;
import io.github.filepilot.persistence.OperationRepository;
import io.github.filepilot.scan.*;
import io.github.filepilot.workspace.*;
import java.io.IOException;
import java.nio.file.Files;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import static java.nio.file.LinkOption.NOFOLLOW_LINKS;

@Service
public class PlanService {
    private final ScanService scans;
    private final WorkspaceService workspaces;
    private final WorkspacePolicy policy;
    private final OperationRepository repo;
    private final CommandInterpreter commands;
    private final Clock clock;
    private ClassifierService classifiers;
    @org.springframework.beans.factory.annotation.Autowired
    public PlanService(ScanService scans, WorkspaceService workspaces, WorkspacePolicy policy, OperationRepository repo, CommandInterpreter commands, ClassifierService classifiers) {
        this(scans,workspaces,policy,repo,commands,Clock.systemUTC());
        this.classifiers=classifiers;
    }
    public PlanService(ScanService scans, WorkspaceService workspaces, WorkspacePolicy policy, OperationRepository repo, CommandInterpreter commands) {
        this(scans,workspaces,policy,repo,commands,Clock.systemUTC());
    }
    public PlanService(ScanService scans, WorkspaceService workspaces, WorkspacePolicy policy, OperationRepository repo, CommandInterpreter commands, Clock clock) {
        this.scans=scans; this.workspaces=workspaces; this.policy=policy; this.repo=repo; this.commands=commands; this.clock=clock;
    }
    public synchronized Plan create(UUID scanId, String instruction, boolean useAI) throws IOException {
        var intent = commands.parse(instruction);
        var scan = scans.get(scanId); var w = workspaces.get(scan.workspaceId()); policy.root(w);
        var enriched=classifiers==null ? new ClassifierService.Result(Map.of(),"RULES") : classifiers.enrich(scan.files(),instruction,useAI);
        List<PlanAction> actions = new ArrayList<>(); Set<String> destinations = new HashSet<>();
        for (FileEntry f : scan.files()) {
            var suggestion=enriched.suggestions().get(f.id());
            var category=suggestion==null ? f.category() : suggestion.category();
            String name = f.relativePath().substring(f.relativePath().lastIndexOf('/') + 1);
            String dest = category.folder() + "/" + name;
            var path = policy.resolve(w, dest); policy.resolve(w, f.relativePath());
            String key = dest.toLowerCase(Locale.ROOT);
            String status = intent == CommandInterpreter.Intent.DUPLICATES ? "SKIPPED" : f.relativePath().equals(dest) ? "SKIPPED_NO_CHANGE"
                : Files.exists(path,NOFOLLOW_LINKS) || !destinations.add(key) ? "CONFLICT" : "PROPOSED";
            actions.add(new PlanAction(UUID.randomUUID(),f.id(),f.relativePath(),dest,f.fingerprint(),category,suggestion==null ? "RULE" : "LLM",
                suggestion==null ? "Filename/extension rule; contents not read" : suggestion.reason(),suggestion==null ? 0.8 : suggestion.confidence(),status.equals("PROPOSED"),status));
        }
        var now = clock.instant();
        var plan = new Plan(UUID.randomUUID(),w.id(),scanId,now,now.plus(Duration.ofMinutes(15)),instruction,"DRAFT",actions,enriched.status());
        save(plan); return plan;
    }
    public Plan get(UUID id) { var p=repo.get("plans",id,Plan.class); workspaces.get(p.workspaceId()); return p; }
    public void save(Plan p) { repo.put("plans",p.id(),p); }
    public synchronized Plan select(UUID id, Set<UUID> selected) {
        var p=get(id); validateDraft(p);
        PilotException.require(selected != null && p.actions().stream().map(PlanAction::id).toList().containsAll(selected), "UNKNOWN_ACTION");
        PilotException.require(p.actions().stream().filter(a -> selected.contains(a.id())).allMatch(a -> a.status().equals("PROPOSED")), "CONFLICT");
        var result=p.actions(p.actions().stream().map(a -> a.select(selected.contains(a.id()))).toList()); save(result); return result;
    }
    public void validateDraft(Plan p) {
        PilotException.require(p.status().equals("DRAFT"), "PLAN_ALREADY_USED");
        if (!clock.instant().isBefore(p.expiresAt())) { save(p.status("EXPIRED")); throw new PilotException("EXPIRED_PLAN"); }
    }
}
