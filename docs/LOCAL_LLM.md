# Local classification

Rules work without any model process, API key or network. Model use must be enabled in configuration **and**
selected for each plan. Start Ollama/llama-server yourself; FilePilot never downloads weights or starts a runtime.

```sh
export FILEPILOT_LLM_ENABLED=true
export FILEPILOT_LLM_BASE_URL=http://127.0.0.1:11434/v1
export FILEPILOT_LLM_MODEL=qwen3.5:2b
./mvnw spring-boot:run
```

PowerShell uses `$env:FILEPILOT_LLM_ENABLED='true'` etc. For llama.cpp set its real port and loaded model name.
Only `http://127.0.0.1:PORT/v1`, `localhost` (pinned to 127.0.0.1), or `[::1]` are accepted. Redirects and proxies
are refused by a bounded transport supplied to Spring AI 2.0.2's OpenAI-compatible SDK client. The SDK needs a
credential field; a fixed non-secret `local-placeholder` is used only for the local endpoint. Do not set real keys.

At most 20 files per plan are enriched; the remainder uses rules. Request metadata is capped at 12k characters
and the HTTP request at 48 KiB; HTTP responses at 64 KiB and parsed content at 32k characters. Output tokens are
limited to 2048. Invalid JSON gets one retry; the overall deadline is 8 seconds (configurable 100–30000 ms with
`FILEPILOT_LLM_TIMEOUT_MS`). A single bounded worker prevents accumulating stalled requests. Failed/unknown IDs,
duplicate IDs, unknown categories, confidence outside 0.6–1, NaN and oversized reasons trigger rules fallback.
Confidence is a model score, not a calibrated probability. Filenames are untrusted data and may be sensitive.

The health status reflects the last classification attempt; before any successful request it is OFFLINE. There is
no background model polling. CPU inference latency and available RAM depend on hardware and model. The installed
runtime/model licenses and platform requirements must be checked separately; current Ollama support on Big Sur
is not claimed. Real 2B/4B inference is not part of automated tests: tests use FakeClassifier and local mock HTTP.
