package io.github.filepilot.web;

import io.github.filepilot.scan.DuplicateService;
import java.io.IOException;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/scans/{id}/duplicates")
public class DuplicateController {
    private final DuplicateService duplicates;
    public DuplicateController(DuplicateService duplicates) { this.duplicates=duplicates; }
    @PostMapping public DuplicateService.Result compute(@PathVariable UUID id) throws IOException { return duplicates.compute(id); }
    @GetMapping public DuplicateService.Result get(@PathVariable UUID id) { return duplicates.get(id); }
    @DeleteMapping public void cancel(@PathVariable UUID id) { duplicates.cancel(id); }
}
