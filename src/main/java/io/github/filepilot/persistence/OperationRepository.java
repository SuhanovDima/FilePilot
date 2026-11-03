package io.github.filepilot.persistence;

import java.util.List;
import java.util.UUID;

/** Durable metadata only; all table names come from a closed internal allowlist. */
public interface OperationRepository {
    record AuditEvent(UUID id, java.time.Instant eventTime, String kind, String actor, String detailsRedacted) {}
    default void audit(String kind, UUID resource) {
        var event=new AuditEvent(UUID.randomUUID(),java.time.Instant.now(),kind,"LOCAL_UI",resource.toString());
        put("audit_events",event.id(),event);
    }
    <T> void put(String table, UUID id, T value);
    <T> T get(String table, UUID id, Class<T> type);
    <T> List<T> all(String table, Class<T> type);
}
