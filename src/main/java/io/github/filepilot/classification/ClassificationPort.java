package io.github.filepilot.classification;

import io.github.filepilot.scan.FileEntry;
import java.util.List;

/** Suggestions only. Implementations have no filesystem mutation capability. */
public interface ClassificationPort {
    List<ClassificationSuggestion> classify(List<FileEntry> files, String instruction);
}
