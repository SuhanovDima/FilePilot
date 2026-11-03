package io.github.filepilot.classification;

import io.github.filepilot.config.PilotException;
import io.github.filepilot.scan.FileEntry;
import java.util.*;
import tools.jackson.databind.json.JsonMapper;

public final class SuggestionValidator {
    private final JsonMapper json=JsonMapper.builder().build();
    public List<ClassificationSuggestion> parse(String response, List<FileEntry> files) {
        PilotException.require(response!=null && response.length()<=32768,"INVALID_MODEL_OUTPUT");
        try { return validate(Arrays.asList(json.readValue(response,ClassificationSuggestion[].class)),files); }
        catch (RuntimeException e) { throw new PilotException("INVALID_MODEL_OUTPUT"); }
    }
    public List<ClassificationSuggestion> validate(List<ClassificationSuggestion> values, List<FileEntry> files) {
        PilotException.require(values!=null && !values.isEmpty() && values.size()<=files.size(),"INVALID_MODEL_OUTPUT");
        var known=new HashSet<>(files.stream().map(FileEntry::id).toList()); var seen=new HashSet<UUID>();
        for (var s : values) PilotException.require(s!=null && s.fileId()!=null && known.contains(s.fileId()) && seen.add(s.fileId())
            && s.category()!=null && s.reason()!=null && !s.reason().isBlank() && s.reason().length()<=300
            && s.reason().chars().noneMatch(c -> c<32) && Double.isFinite(s.confidence()) && s.confidence()>=0.6 && s.confidence()<=1.0,"INVALID_MODEL_OUTPUT");
        return List.copyOf(values);
    }
}
