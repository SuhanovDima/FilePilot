package io.github.filepilot;

import io.github.filepilot.config.*;
import io.github.filepilot.persistence.*;
import io.github.filepilot.workspace.*;
import io.github.filepilot.scan.*;
import io.github.filepilot.classification.*;
import java.nio.file.*;
import org.springframework.mock.env.MockEnvironment;

public class TestFixture implements AutoCloseable {
    public final PilotSettings settings;
    public final SqliteRepository repo;
    public final WorkspacePolicy policy;
    public final WorkspaceService workspaces;
    public final ScanService scans;
    public final io.github.filepilot.planning.PlanService plans;
    public final io.github.filepilot.execution.NioFileSystem fs;
    public final io.github.filepilot.execution.ExecutionService executions;
    public final Path inbox;
    public TestFixture(Path temp) throws Exception { this(temp, 2000, 4); }
    public TestFixture(Path temp, int maxFiles, int maxDepth) throws Exception {
        temp = temp.toRealPath();
        settings = new PilotSettings(new MockEnvironment().withProperty("filepilot.data-dir", temp.resolve("db").toString())
            .withProperty("filepilot.scan.max-files", ""+maxFiles).withProperty("filepilot.scan.max-depth", ""+maxDepth));
        repo = new SqliteRepository(settings); policy = new WorkspacePolicy(settings); workspaces = new WorkspaceService(policy, repo);
        scans = new ScanService(workspaces, policy, new RuleClassifier(), settings, repo);
        plans = new io.github.filepilot.planning.PlanService(scans,workspaces,policy,repo,new io.github.filepilot.commands.CommandInterpreter());
        fs = new io.github.filepilot.execution.NioFileSystem(policy);
        executions = new io.github.filepilot.execution.ExecutionService(plans,workspaces,fs,repo);
        inbox = Files.createDirectories(temp.resolve("inbox"));
    }
    public Workspace allow() throws Exception { return workspaces.add(inbox.toString(), true); }
    @Override public void close() throws Exception { repo.close(); }
}
