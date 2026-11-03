package io.github.filepilot.web;

import io.github.filepilot.workspace.*;
import io.github.filepilot.scan.*;
import io.github.filepilot.config.PilotException;
import java.io.IOException;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.web.csrf.CsrfToken;

@RestController
@RequestMapping("/api/v1")
public class InventoryController {
    private final WorkspaceService workspaces;
    private final ScanService scans;
    public InventoryController(WorkspaceService workspaces, ScanService scans) { this.workspaces=workspaces; this.scans=scans; }
    public record RootRequest(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=4095) String rootPath, boolean confirmed) {}
    public record ScanPage(UUID id, UUID workspaceId, String status, int fileCount, int offset, List<FileEntry> files, String warning) {}
    @GetMapping("/csrf") public Map<String,String> csrf(CsrfToken token) { return Map.of("headerName", token.getHeaderName(), "token", token.getToken()); }
    @GetMapping("/workspaces") public List<Workspace> list() { return workspaces.list(); }
    @PostMapping("/workspaces") public Workspace add(@jakarta.validation.Valid @RequestBody RootRequest req) throws IOException { return workspaces.add(req.rootPath(), req.confirmed()); }
    @DeleteMapping("/workspaces/{id}") public void revoke(@PathVariable UUID id) { workspaces.revoke(id); }
    @PostMapping("/workspaces/{id}/scans") public ScanPage scan(@PathVariable UUID id) throws IOException { return page(scans.scan(id), 0, 100); }
    @GetMapping("/scans/{id}") public ScanPage scan(@PathVariable UUID id, @RequestParam(defaultValue="0") int offset, @RequestParam(defaultValue="100") int limit) { return page(scans.get(id), offset, limit); }
    private ScanPage page(ScanRun s, int offset, int limit) {
        PilotException.require(offset >= 0 && limit > 0 && limit <= 200, "INVALID_PAGE");
        return new ScanPage(s.id(), s.workspaceId(), s.status(), s.files().size(), offset, s.files().stream().skip(offset).limit(limit).toList(), s.warning());
    }
}
