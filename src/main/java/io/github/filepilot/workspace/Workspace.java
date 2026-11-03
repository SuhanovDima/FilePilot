package io.github.filepilot.workspace;

import java.time.Instant;
import java.util.UUID;

public record Workspace(UUID id, String rootPath, Instant createdAt, boolean enabled, String identity) {}
