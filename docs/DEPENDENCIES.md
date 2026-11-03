# Dependency verification (2026-11-03)

Maven Central metadata was checked before creating the POM: Spring Boot 4.0.9 and Spring AI 2.0.2 are published stable releases.
SQLite JDBC 3.51.0.0 and JUnit 5.13.4 POMs were retrieved from Central.

Primary references:

- https://docs.spring.io/spring-ai/reference/getting-started.html
- https://docs.spring.io/spring-ai/reference/2.0/api/chat/openai-chat.html
- https://repo.maven.apache.org/maven2/org/springframework/ai/spring-ai-bom/maven-metadata.xml
- https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-starter-parent/maven-metadata.xml

Chat auto-configuration is disabled with `spring.ai.model.chat=none`. No cloud endpoint or real API key is configured.
The optional adapter is constructed programmatically after loopback URI validation; no embeddings, images or audio clients are enabled.

Boot 4 / Spring Test 7's Jupiter extension calls JUnit 6-only methods. To keep the requested JUnit 5.13.4,
tests use its engine without `SpringExtension`, starting the real Boot context programmatically.
Moderation auto-configuration must also be disabled (`spring.ai.model.moderation=none`) for key-free startup.
The final application additionally excludes all six OpenAI model auto-configurations in code: external Spring AI
environment properties cannot accidentally construct an alternate provider. The loopback adapter remains explicit.

Spring AI 2.0.2 uses the OpenAI Java SDK client. Its exact builder methods and HTTP interface were checked against
the published 2.0.2 sources JAR before implementing the adapter. The supplied transport disables redirects/proxies
and caps response bytes. HTMX 2.0.8 and its Zero-Clause BSD license were vendored from its official GitHub tag.
