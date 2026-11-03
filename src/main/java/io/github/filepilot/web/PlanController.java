package io.github.filepilot.web;

import io.github.filepilot.execution.ExecutionService;
import io.github.filepilot.history.JournalEntry;
import io.github.filepilot.planning.*;
import java.io.IOException;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class PlanController {
    private final PlanService plans;
    private final ExecutionService executions;
    public PlanController(PlanService plans, ExecutionService executions) { this.plans=plans; this.executions=executions; }
    public record PlanRequest(UUID scanId, String instruction, boolean useAI) {}
    public record Selection(Set<UUID> actionIds) {}
    public record Confirmation(boolean confirmed) {}
    @PostMapping("/plans") public Plan create(@RequestBody PlanRequest req) throws IOException { return plans.create(req.scanId(),req.instruction(),req.useAI()); }
    @GetMapping("/plans/{id}") public Plan get(@PathVariable UUID id) { return plans.get(id); }
    @PatchMapping("/plans/{id}/actions") public Plan select(@PathVariable UUID id, @RequestBody Selection req) { return plans.select(id,req.actionIds()); }
    @PostMapping("/plans/{id}/execute") public Plan execute(@PathVariable UUID id, @RequestBody Confirmation req) { return executions.execute(id,req.confirmed()); }
    @GetMapping("/operations") public List<JournalEntry> history(@RequestParam UUID workspaceId, @RequestParam(defaultValue="0") int offset, @RequestParam(defaultValue="100") int limit) {
        io.github.filepilot.config.PilotException.require(offset>=0 && limit>0 && limit<=200,"INVALID_PAGE");
        return executions.history(workspaceId).stream().skip(offset).limit(limit).toList();
    }
    @PostMapping("/operations/{batchId}/undo") public List<JournalEntry> undo(@PathVariable UUID batchId,@RequestBody Confirmation req) { return executions.undo(batchId,req.confirmed()); }
}
