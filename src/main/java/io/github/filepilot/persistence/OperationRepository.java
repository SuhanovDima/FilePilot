package io.github.filepilot.persistence;

import java.util.List;
import java.util.UUID;

/** Durable metadata only; all table names come from a closed internal allowlist. */
public interface OperationRepository {
    <T> void put(String table, UUID id, T value);
    <T> T get(String table, UUID id, Class<T> type);
    <T> List<T> all(String table, Class<T> type);
}
