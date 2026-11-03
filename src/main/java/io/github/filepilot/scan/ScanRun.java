package io.github.filepilot.scan;

import java.time.Instant;
import java.util.*;

public record ScanRun(UUID id, UUID workspaceId, Instant startedAt, String status, List<FileEntry> files, String warning) {
    public ScanRun { files = List.copyOf(files); }
}
