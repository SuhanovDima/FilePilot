package io.github.filepilot;

import com.sun.net.httpserver.HttpServer;
import io.github.filepilot.classification.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

class LocalModelHttpTest {
    @Test void realSpringAiUsesLoopbackJsonAndRetriesInvalidOutputOnce() throws Exception {
        var file=ClassifierTest.file(); var count=new AtomicInteger(); var request=new AtomicReference<String>();
        var mapper=JsonMapper.builder().build();
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/v1/chat/completions",exchange -> {
            count.incrementAndGet(); request.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
            String content=count.get()==1 ? "invalid json" : mapper.writeValueAsString(List.of(new ClassificationSuggestion(file.id(),FileCategory.CONTRACTS,"Local mock suggestion",0.9)));
            byte[] response=mapper.writeValueAsBytes(Map.of("id","local-test","object","chat.completion","created",1,"model","fake",
                "choices",List.of(Map.of("index",0,"finish_reason","stop","message",Map.of("role","assistant","content",content)))));
            exchange.getResponseHeaders().set("Content-Type","application/json"); exchange.sendResponseHeaders(200,response.length);
            try (var out=exchange.getResponseBody()) { out.write(response); }
        });
        server.start();
        try (var adapter=new SpringAiLocalClassifier("http://127.0.0.1:"+server.getAddress().getPort()+"/v1","fake",Duration.ofSeconds(2))) {
            assertThat(adapter.classify(List.of(file),"organize by type")).extracting(ClassificationSuggestion::category).containsExactly(FileCategory.CONTRACTS);
            assertThat(count.get()).isEqualTo(2);
            assertThat(request.get()).contains("relativeName",file.id().toString()).doesNotContain("rootPath","tools\":");
        } finally { server.stop(0); }
    }
    @Test void redirectNeverReachesAnotherServer() throws Exception {
        var hits=new AtomicInteger(); var destination=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        destination.createContext("/",e -> { hits.incrementAndGet(); e.sendResponseHeaders(200,-1); e.close(); }); destination.start();
        var redirect=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        redirect.createContext("/v1/chat/completions",e -> {
            e.getResponseHeaders().set("Location","http://127.0.0.1:"+destination.getAddress().getPort()+"/"); e.sendResponseHeaders(307,-1); e.close();
        }); redirect.start();
        try (var adapter=new SpringAiLocalClassifier("http://127.0.0.1:"+redirect.getAddress().getPort()+"/v1","fake",Duration.ofMillis(500))) {
            assertThatThrownBy(() -> adapter.classify(List.of(ClassifierTest.file()),"organize by type")).isInstanceOf(RuntimeException.class);
            assertThat(hits.get()).isZero();
        } finally { redirect.stop(0); destination.stop(0); }
    }
}
