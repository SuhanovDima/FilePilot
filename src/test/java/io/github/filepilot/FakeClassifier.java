package io.github.filepilot;

import io.github.filepilot.classification.*;
import io.github.filepilot.scan.FileEntry;
import java.util.*;

public class FakeClassifier implements ClassificationPort {
    @Override public List<ClassificationSuggestion> classify(List<FileEntry> files, String instruction) {
        return files.stream().map(f -> new ClassificationSuggestion(f.id(),FileCategory.CONTRACTS,"Fake local suggestion",0.9)).toList();
    }
}
