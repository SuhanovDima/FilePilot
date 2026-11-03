package io.github.filepilot.planning;

import java.time.Instant;
import java.util.*;

public record Plan(UUID id, UUID workspaceId, UUID scanId, Instant createdAt, Instant expiresAt, String instruction,
                   String status, List<PlanAction> actions, String classificationStatus) {
    public Plan { actions = List.copyOf(actions); }
    public Plan status(String value) { return new Plan(id,workspaceId,scanId,createdAt,expiresAt,instruction,value,actions,classificationStatus); }
    public Plan actions(List<PlanAction> value) { return new Plan(id,workspaceId,scanId,createdAt,expiresAt,instruction,status,value,classificationStatus); }
}
