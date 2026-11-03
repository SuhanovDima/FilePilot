package io.github.filepilot.classification;

import java.util.UUID;
public record ClassificationSuggestion(UUID fileId, FileCategory category, String reason, double confidence) {}
