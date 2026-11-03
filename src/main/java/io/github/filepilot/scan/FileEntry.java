package io.github.filepilot.scan;

import io.github.filepilot.classification.FileCategory;
import java.util.UUID;

public record FileEntry(UUID id, String relativePath, String extension, long size, String modifiedAt,
                        String fileKey, String fingerprint, FileCategory category, String classifierSource) {}
