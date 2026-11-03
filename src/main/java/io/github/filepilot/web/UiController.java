package io.github.filepilot.web;

import io.github.filepilot.classification.ClassifierService;
import io.github.filepilot.config.PilotException;
import io.github.filepilot.execution.ExecutionService;
import io.github.filepilot.persistence.OperationRepository;
import io.github.filepilot.planning.*;
import io.github.filepilot.scan.*;
import io.github.filepilot.workspace.*;
import java.io.IOException;
import java.util.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class UiController {
    private final WorkspaceService workspaces;
    private final ScanService scans;
    private final PlanService plans;
    private final ExecutionService executions;
    private final DuplicateService duplicates;
    private final ClassifierService classifiers;
    private final OperationRepository repo;
    public UiController(WorkspaceService workspaces,ScanService scans,PlanService plans,ExecutionService executions,DuplicateService duplicates,ClassifierService classifiers,OperationRepository repo) {
        this.workspaces=workspaces; this.scans=scans; this.plans=plans; this.executions=executions; this.duplicates=duplicates; this.classifiers=classifiers; this.repo=repo;
    }
    @GetMapping("/") public String home(@RequestParam(required=false) UUID workspaceId,@RequestParam(required=false) UUID scanId,@RequestParam(required=false) UUID planId,
                                       @RequestParam(defaultValue="0") int offset,Model model) {
        model.addAttribute("workspaces",workspaces.list()); health(model);
        if (workspaceId!=null) {
            var w=workspaces.get(workspaceId); model.addAttribute("workspace",w);
            model.addAttribute("history",executions.history(w.id()).reversed().stream().limit(200).toList());
            model.addAttribute("batches",repo.all("plans",Plan.class).stream().filter(p -> p.workspaceId().equals(w.id()) && Set.of("COMPLETED","PARTIAL_FAILED").contains(p.status())).toList().reversed());
            if (scanId!=null) {
                var scan=scans.get(scanId); PilotException.require(scan.workspaceId().equals(w.id()),"UNAUTHORIZED_ROOT");
                PilotException.require(offset>=0,"INVALID_PAGE");
                model.addAttribute("scan",scan); model.addAttribute("files",scan.files().stream().skip(offset).limit(100).toList()); model.addAttribute("offset",offset);
                var d=duplicates.get(scanId); model.addAttribute("duplicates",d); var labels=new HashMap<UUID,String>();
                if (d!=null) { int n=0; for (var g : d.groups()) { n++; for (UUID id : g.fileIds()) labels.putIfAbsent(id,g.status()+" #"+n); } }
                model.addAttribute("duplicateLabels",labels);
            }
            if (planId!=null) { var p=plans.get(planId); PilotException.require(p.workspaceId().equals(w.id()),"UNAUTHORIZED_ROOT"); model.addAttribute("plan",p); }
        }
        return "index";
    }
    @GetMapping("/ui/health") public String health(Model model) { model.addAttribute("llmStatus",classifiers.status()); model.addAttribute("modelName",classifiers.modelName()); return "index :: modelStatus"; }
    @PostMapping("/ui/workspaces") public String add(@RequestParam String rootPath,@RequestParam(defaultValue="false") boolean confirmed) throws IOException { return location(workspaces.add(rootPath,confirmed).id(),null,null); }
    @PostMapping("/ui/workspaces/{id}/revoke") public String revoke(@PathVariable UUID id) { workspaces.revoke(id); return "redirect:/"; }
    @PostMapping("/ui/workspaces/{id}/scan") public String scan(@PathVariable UUID id) throws IOException { return location(id,scans.scan(id).id(),null); }
    @PostMapping("/ui/plans") public String plan(@RequestParam UUID scanId,@RequestParam String instruction,@RequestParam(defaultValue="false") boolean useAI) throws IOException {
        var p=plans.create(scanId,instruction,useAI); return location(p.workspaceId(),p.scanId(),p.id());
    }
    @PostMapping("/ui/plans/{id}/execute") public String execute(@PathVariable UUID id,@RequestParam(defaultValue="false") boolean confirmed,
                                                               @RequestParam(required=false) Set<UUID> actionIds) {
        PilotException.require(confirmed,"CONFIRMATION_REQUIRED");
        synchronized (plans) {
            var p=plans.get(id);
            if (p.status().equals("DRAFT")) plans.select(id,actionIds==null ? Set.of() : actionIds);
            p=executions.execute(id,true); return location(p.workspaceId(),p.scanId(),p.id());
        }
    }
    @PostMapping("/ui/operations/{id}/undo") public String undo(@PathVariable UUID id,@RequestParam(defaultValue="false") boolean confirmed) {
        var p=plans.get(id); executions.undo(id,confirmed); return location(p.workspaceId(),null,p.id());
    }
    @PostMapping("/ui/scans/{id}/duplicates") public String duplicates(@PathVariable UUID id) throws IOException {
        var s=scans.get(id); duplicates.compute(id); return location(s.workspaceId(),id,null);
    }
    @PostMapping("/ui/scans/{id}/duplicates/cancel") public String cancel(@PathVariable UUID id) { var s=scans.get(id); duplicates.cancel(id); return location(s.workspaceId(),id,null); }
    private String location(UUID workspace,UUID scan,UUID plan) { return "redirect:/?workspaceId="+workspace+(scan==null ? "" : "&scanId="+scan)+(plan==null ? "" : "&planId="+plan); }
    @ExceptionHandler({PilotException.class,IOException.class,IllegalArgumentException.class}) public String error(Exception e,RedirectAttributes redirect) {
        redirect.addFlashAttribute("error",e instanceof PilotException p ? p.code() : "INVALID_REQUEST"); return "redirect:/";
    }
}
