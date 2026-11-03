package io.github.filepilot.classification;

import com.openai.core.RequestOptions;
import com.openai.core.http.*;
import io.github.filepilot.config.PilotException;
import java.io.*;
import java.net.*;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import okhttp3.*;

/** Transport for Spring AI's SDK client. No redirects, proxies, streaming, or unbounded response bodies. */
final class LoopbackTransport implements com.openai.core.http.HttpClient {
    private final URI base;
    private final OkHttpClient client;
    LoopbackTransport(URI base, Duration timeout) {
        this.base=base;
        client=new OkHttpClient.Builder().proxy(Proxy.NO_PROXY).followRedirects(false).followSslRedirects(false)
            .retryOnConnectionFailure(false).connectTimeout(timeout).readTimeout(timeout).callTimeout(timeout).build();
    }
    @Override public HttpResponse execute(com.openai.core.http.HttpRequest req, RequestOptions options) {
        URI uri=URI.create(req.url());
        PilotException.require(uri.getScheme().equals(base.getScheme()) && uri.getRawAuthority().equals(base.getRawAuthority())
            && "/v1/chat/completions".equals(uri.getPath()) && uri.getQuery()==null && req.method()==HttpMethod.POST,"LOCAL_ENDPOINT_REQUIRED");
        try {
            var bytes=new ByteArrayOutputStream(); req.body().writeTo(bytes);
            PilotException.require(bytes.size()<=48*1024,"RESOURCE_LIMIT");
            var builder=new Request.Builder().url(uri.toString()).post(RequestBody.create(bytes.toByteArray(),MediaType.get("application/json")));
            for (String name : req.headers().names()) for (String value : req.headers().values(name)) builder.addHeader(name,value);
            try (Response response=client.newCall(builder.build()).execute()) {
                PilotException.require(response.code()<300 || response.code()>=400,"REDIRECT_DENIED");
                byte[] body=response.body()==null ? new byte[0] : response.body().byteStream().readNBytes(64*1024+1);
                PilotException.require(body.length<=64*1024,"RESOURCE_LIMIT");
                var headers=com.openai.core.http.Headers.builder().putAll(response.headers().toMultimap()).build(); int status=response.code();
                return new HttpResponse() {
                    public int statusCode() { return status; }
                    public com.openai.core.http.Headers headers() { return headers; }
                    public InputStream body() { return new ByteArrayInputStream(body); }
                    public void close() {}
                };
            }
        } catch (IOException e) { throw new PilotException("LLM_OFFLINE"); }
    }
    @Override public CompletableFuture<HttpResponse> executeAsync(com.openai.core.http.HttpRequest req, RequestOptions options) {
        return CompletableFuture.failedFuture(new PilotException("STREAMING_DISABLED"));
    }
    @Override public void close() { client.dispatcher().cancelAll(); client.connectionPool().evictAll(); client.dispatcher().executorService().shutdown(); }
}
