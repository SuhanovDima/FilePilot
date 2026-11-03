package io.github.filepilot.history;

import io.github.filepilot.execution.FileSystemPort.Evidence;
import java.time.Instant;
import java.util.UUID;

public record JournalEntry(UUID id, UUID batchId, UUID planActionId, UUID workspaceId, String type,
                           String sourceRelative, String destinationRelative, Evidence evidence,
                           String status, String errorCode, UUID undoOf, Instant startedAt, Instant completedAt) {
    public JournalEntry status(String value, String error) {
        return new JournalEntry(id,batchId,planActionId,workspaceId,type,sourceRelative,destinationRelative,evidence,value,error,undoOf,startedAt,Instant.now());
    }
}
