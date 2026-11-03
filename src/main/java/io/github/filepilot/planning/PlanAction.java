package io.github.filepilot.planning;

import io.github.filepilot.classification.FileCategory;
import java.util.UUID;

public record PlanAction(UUID id, UUID fileId, String sourceRelative, String destinationRelative, long expectedSize,
                         String expectedFingerprint, FileCategory category, String classifierSource,
                         String reason, double confidence, boolean selected, String status) {
    public PlanAction select(boolean value) { return new PlanAction(id,fileId,sourceRelative,destinationRelative,expectedSize,expectedFingerprint,category,classifierSource,reason,confidence,value,status); }
    public PlanAction status(String value) { return new PlanAction(id,fileId,sourceRelative,destinationRelative,expectedSize,expectedFingerprint,category,classifierSource,reason,confidence,selected,value); }
}
