package io.github.filepilot;

import io.github.filepilot.config.PilotException;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

class WebFlowTest {
    @TempDir static Path temp;
    static ConfigurableApplicationContext app;
    static HttpClient client;
    static String base;
    static JsonMapper json=JsonMapper.builder().build();
    @BeforeAll static void start() {
        app=SpringApplication.run(FilePilotApplication.class,"--server.port=0","--filepilot.data-dir="+temp.resolve("db"));
        base="http://127.0.0.1:"+((ServletWebServerApplicationContext)app).getWebServer().getPort();
        client=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build();
    }
    @AfterAll static void stop() { if (app!=null) app.close(); if (client!=null) client.close(); }
    static HttpResponse<String> get(String path) throws Exception { return client.send(HttpRequest.newBuilder(URI.create(base+path)).GET().build(),HttpResponse.BodyHandlers.ofString()); }
    static HttpResponse<String> send(String method,String path,Object body,String token) throws Exception {
        var request=HttpRequest.newBuilder(URI.create(base+path)).header("Content-Type","application/json");
        if (token!=null) request.header("X-CSRF-TOKEN",token);
        return client.send(request.method(method,HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
    }
    @Test void realHttpCsrfOriginPreviewConfirmMoveAndUndo() throws Exception {
        var home=get("/"); assertThat(home.statusCode()).isEqualTo(200); assertThat(home.body()).contains("FilePilot","Разрешить каталог","LLM OFFLINE");
        assertThat(home.headers().firstValue("Content-Security-Policy")).isPresent();
        Path inbox=Files.createDirectory(temp.resolve("inbox")).toRealPath();
        Files.writeString(inbox.resolve("photo.jpg"),"photo"); Files.writeString(inbox.resolve("contract.pdf"),"contract"); Files.writeString(inbox.resolve("archive.zip"),"archive");
        assertThat(send("POST","/api/v1/workspaces",Map.of("rootPath",inbox.toString(),"confirmed",true),null).statusCode()).isEqualTo(403);
        String token=json.readTree(get("/api/v1/csrf").body()).get("token").asText();
        var invalid=send("POST","/api/v1/plans",Map.of("instruction","organize by type"),token);
        assertThat(invalid.statusCode()).isEqualTo(400); assertThat(invalid.body()).contains("INVALID_REQUEST").doesNotContain(inbox.toString());
        var root=send("POST","/api/v1/workspaces",Map.of("rootPath",inbox.toString(),"confirmed",true),token);
        assertThat(root.statusCode()).isEqualTo(200); String w=json.readTree(root.body()).get("id").asText();
        var scan=send("POST","/api/v1/workspaces/"+w+"/scans",Map.of(),token);
        assertThat(scan.statusCode()).isEqualTo(200); String scanId=json.readTree(scan.body()).get("id").asText();
        var created=send("POST","/api/v1/plans",Map.of("scanId",scanId,"instruction","разложи по типам","useAI",true),token);
        assertThat(created.statusCode()).isEqualTo(200); JsonNode p=json.readTree(created.body()); String plan=p.get("id").asText();
        assertThat(p.get("classificationStatus").asText()).startsWith("RULES_FALLBACK");
        var preview=get("/?workspaceId="+w+"&scanId="+scanId+"&planId="+plan);
        assertThat(preview.statusCode()).isEqualTo(200); assertThat(preview.body()).contains("План изменений","Подтвердить и выполнить","_csrf");
        assertThat(Files.exists(inbox.resolve("Images"))).isFalse();
        var selection=List.of(p.get("actions").get(0).get("id").asText(),p.get("actions").get(1).get("id").asText());
        assertThat(send("PATCH","/api/v1/plans/"+plan+"/actions",Map.of("actionIds",selection),token).statusCode()).isEqualTo(200);
        assertThat(send("POST","/api/v1/plans/"+plan+"/execute",Map.of("confirmed",false),token).statusCode()).isEqualTo(409);
        var executed=send("POST","/api/v1/plans/"+plan+"/execute",Map.of("confirmed",true),token);
        assertThat(json.readTree(executed.body()).get("status").asText()).isEqualTo("COMPLETED");
        assertThat(get("/?workspaceId="+w+"&planId="+plan).body()).contains("История и Undo","DONE");
        assertThat(send("POST","/api/v1/operations/"+plan+"/undo",Map.of("confirmed",true),token).statusCode()).isEqualTo(200);
        assertThat(Files.readString(inbox.resolve("photo.jpg"))).isEqualTo("photo");
        assertThat(Files.readString(inbox.resolve("contract.pdf"))).isEqualTo("contract");
        assertThat(Files.readString(inbox.resolve("archive.zip"))).isEqualTo("archive");
        var foreign=HttpRequest.newBuilder(URI.create(base+"/api/v1/workspaces")).header("Origin","http://evil.example").GET().build();
        assertThat(client.send(foreign,HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(403);
        try (var socket=new Socket("127.0.0.1",URI.create(base).getPort())) {
            socket.getOutputStream().write("GET /api/v1/health HTTP/1.1\r\nHost: evil.example\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
            assertThat(new java.io.BufferedReader(new java.io.InputStreamReader(socket.getInputStream(),StandardCharsets.US_ASCII)).readLine()).contains("403");
        }
        assertThat(app.getEnvironment().getProperty("server.address")).isEqualTo("127.0.0.1");
    }
}
