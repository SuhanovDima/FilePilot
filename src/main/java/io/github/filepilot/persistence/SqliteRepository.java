package io.github.filepilot.persistence;

import io.github.filepilot.config.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class SqliteRepository implements OperationRepository, AutoCloseable {
    private static final Set<String> TABLES = Set.of("workspaces", "scan_runs", "file_inventory", "plans", "plan_actions", "operation_journal", "audit_events");
    private final JsonMapper json = JsonMapper.builder().build();
    private final Connection db;
    private final FileChannel lockChannel;
    private final FileLock lock;

    public SqliteRepository(PilotSettings settings) throws Exception {
        Files.createDirectories(settings.dataDir);
        for (String name : List.of("instance.lock","filepilot.db","filepilot.db-wal","filepilot.db-shm","filepilot.db-journal")) {
            Path path=settings.dataDir.resolve(name);
            PilotException.require(!Files.isSymbolicLink(path),"UNSAFE_DATA_DIR");
            if (Files.exists(path,LinkOption.NOFOLLOW_LINKS))
                PilotException.require(Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS),"UNSAFE_DATA_DIR");
        }
        lockChannel = FileChannel.open(settings.dataDir.resolve("instance.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        lock = lockChannel.tryLock();
        if (lock == null) { lockChannel.close(); throw new PilotException("INSTANCE_ALREADY_RUNNING"); }
        db = DriverManager.getConnection("jdbc:sqlite:" + settings.dataDir.resolve("filepilot.db"));
        try (Statement s = db.createStatement()) {
            s.execute("PRAGMA journal_mode=WAL");
            s.execute("PRAGMA synchronous=FULL");
            s.execute("PRAGMA busy_timeout=5000");
            int version;
            try (ResultSet r = s.executeQuery("PRAGMA user_version")) { version = r.getInt(1); }
            PilotException.require(version <= 1, "UNSUPPORTED_DB_VERSION");
            if (version == 0) {
                db.setAutoCommit(false);
                try {
                    for (String table : TABLES) s.execute("CREATE TABLE " + table + " (id TEXT PRIMARY KEY, payload TEXT NOT NULL)");
                    s.execute("PRAGMA user_version=1");
                    db.commit();
                } catch (Exception e) { db.rollback(); throw e; }
                finally { db.setAutoCommit(true); }
            }
        }
    }
    private void table(String t) { PilotException.require(TABLES.contains(t), "INVALID_TABLE"); }
    @Override public synchronized <T> void put(String table, UUID id, T value) {
        table(table);
        try (var s = db.prepareStatement("INSERT INTO " + table + "(id,payload) VALUES(?,?) ON CONFLICT(id) DO UPDATE SET payload=excluded.payload")) {
            s.setString(1, id.toString()); s.setString(2, json.writeValueAsString(value)); s.executeUpdate();
        } catch (Exception e) { throw new PilotException("PERSISTENCE_FAILURE"); }
    }
    @Override public synchronized <T> T get(String table, UUID id, Class<T> type) {
        table(table);
        try (var s = db.prepareStatement("SELECT payload FROM " + table + " WHERE id=?")) {
            s.setString(1, id.toString());
            try (var r = s.executeQuery()) {
                if (!r.next()) throw new PilotException("NOT_FOUND");
                return json.readValue(r.getString(1), type);
            }
        } catch (PilotException e) { throw e; }
        catch (Exception e) { throw new PilotException("PERSISTENCE_FAILURE"); }
    }
    @Override public synchronized <T> List<T> all(String table, Class<T> type) {
        table(table);
        try (var s = db.prepareStatement("SELECT payload FROM " + table + " ORDER BY rowid" ); var r = s.executeQuery()) {
            var result = new ArrayList<T>();
            while (r.next()) result.add(json.readValue(r.getString(1), type));
            return List.copyOf(result);
        } catch (Exception e) { throw new PilotException("PERSISTENCE_FAILURE"); }
    }
    @Override public synchronized void close() throws Exception { db.close(); lock.release(); lockChannel.close(); }
}
