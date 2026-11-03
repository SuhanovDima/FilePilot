# FilePilot 0.1.0

Полностью локальный организатор файлов на Java 21. Сначала план, затем ваше подтверждение.
Spring Boot 4.0.9 · Spring AI 2.0.2 · SQLite · Thymeleaf · локальный HTMX 2.0.8 · JUnit 5.
Код — Apache-2.0. Облако, телеметрия, удаление и перезапись файлов отсутствуют.

## Быстрый запуск

Нужен JDK 21. Первый запуск Maven Wrapper загружает Maven и зависимости; после кеширования сборка может
работать с `-o`. Модель для сборки, тестов и режима правил не требуется. `JAVA_HOME` должен указывать на JDK 21.

macOS Intel / Linux:

```sh
./mvnw verify
./mvnw spring-boot:run
# либо после сборки:
java -jar target/filepilot-0.1.0.jar
```

Windows PowerShell:

```powershell
.\mvnw.cmd verify
.\mvnw.cmd spring-boot:run
# либо:
java -jar target\filepilot-0.1.0.jar
```

Откройте [http://127.0.0.1:8080/](http://127.0.0.1:8080/). Порт нельзя публиковать через proxy/tunnel.
Без модели показан `LLM OFFLINE`, классификация по правилам работает. При старте allowlist пуст;
ранее выданные разрешения сохраняются в локальной базе. Отозвать их можно в UI.

## Безопасное демо

```sh
sh scripts/demo-fixtures.sh
```

Windows: `powershell -File .\scripts\demo-fixtures.ps1`.
Скрипт создаёт **новый уникальный временный каталог**, печатает путь к Inbox и не принимает существующий каталог.
Внутри только искусственные текстовые fixtures, в том числе файлы с тестовыми расширениями JPG/PDF/ZIP.

1. Введите напечатанный путь в UI и явно разрешите доступ.
2. Нажмите «Сканировать». Посмотрите категории и метаданные.
3. При желании отдельно запустите SHA-256-проверку дубликатов.
4. Постройте план командой «разложи по типам» или «отдельно договоры».
5. Проверьте исходные/целевые пути, снимите лишние галочки и подтвердите перенос.
6. В истории отдельно подтвердите Undo. Неизменённые файлы вернутся; конфликт откажет без перезаписи.

Сканирование и preview не меняют файлы. Дубликаты никогда не удаляются. Подробности: [демо](docs/DEMO.md).

![Локальный интерфейс FilePilot](docs/screenshots/filepilot-preview.jpg)

## Что реализовано

- Явный allowlist, отзыв разрешений, проверка идентичности корня и каждого существующего компонента пути.
- Ограниченный scan: по умолчанию глубина 4 и 2000 файлов; скрытые записи, symlink и reparse points не обходятся.
- Восемь категорий, договоры по имени, SHA-256-дубликаты только по отдельному запросу.
- Сохранённые планы с TTL 15 минут, конфликтами и no-op, выбором действий и проверкой stale snapshot.
- Последовательное выполнение выбранных переносов без overwrite и cross-volume fallback; идемпотентность POST.
- SQLite WAL/FULL, PENDING до move, результаты/частичные ошибки, проверяемое восстановление после прерывания.
- Отдельно подтверждённый Undo с метаданными и SHA-256; изменённый файл, занятое исходное имя или отсутствие цели — конфликт.
- Опциональная локальная LLM через Spring AI: только предложения категорий, проверка ID/enum/confidence, timeout и fallback.
- Thymeleaf UI, локальный HTMX, JSON API с CSRF/session, Origin/Host checks и привязкой только к loopback.
- Maven Wrapper, переносимый JAR, скрипты для macOS/Windows, CI matrix Linux/Windows/macOS.

## Ограничения MVP

- Перенос до 64 МиБ на файл; до 200 действий и 256 МиБ за пакет. Хеширование дубликатов: до 200 файлов / 128 МиБ.
- Имена и метаданные читаются при scan; содержимое — только для явно запрошенных дубликатов и проверки целостности move/Undo.
- AI уточняет до 20 файлов за план. Только закрытый набор команд; произвольный чат не поддерживается.
- Статус LLM отражает последний запрос; фоновый опрос/скачивание моделей отсутствует.
- Обычный no-replace `Files.move` выбран вместо `ATOMIC_MOVE`, допускающего provider-specific overwrite.
  Абсолютная защита от злонамеренной конкурентной подмены путей на всех Java NIO-провайдерах не обещается.
- Undo оставляет пустые каталоги и не повторяет конфликтную отмену автоматически. Неоднозначный журнал требует ручной проверки.
- SQLite хранит snapshots как JSON под UUID; отдельная нормализация и retention — будущая миграция.
- Однопользовательский локальный процесс. Нельзя запускать разные data-dir против одного workspace.
- Фактическая проверка выполнена на macOS 14.5 Intel, JDK 21. Windows/Linux и живая 2B/4B LLM в этой сессии не проверены.
  Наличие CI-конфигурации не означает выполненный CI. Совместимость актуального Ollama с Big Sur не заявляется.
- MCP, извлечение текста, FTS5/RAG и multi-agent orchestration оставлены за пределами MVP.

## Конфигурация

Настройки передаются переменными окружения или `--filepilot.…` при запуске. `.env` автоматически не читается.

```text
FILEPILOT_DATA_DIR=<вне всех разрешённых каталогов>
FILEPILOT_SCAN_MAX_FILES=2000
FILEPILOT_SCAN_MAX_DEPTH=4
FILEPILOT_LLM_ENABLED=false
FILEPILOT_LLM_BASE_URL=http://127.0.0.1:11434/v1
FILEPILOT_LLM_MODEL=qwen3.5:2b
FILEPILOT_LLM_TIMEOUT_MS=8000
```

DB по умолчанию: `~/Library/Application Support/FilePilot` на macOS, `%LOCALAPPDATA%\FilePilot` на Windows,
`~/.local/share/filepilot` на Linux. Этот каталог и его родители не могут стать workspace.
API-ключи не нужны. Ollama/llama.cpp запускаются отдельно; [настройка локальной LLM](docs/LOCAL_LLM.md).

## Структура и документация

```text
src/main/java/io/github/filepilot/
  config/           loopback, security, limits, errors
  workspace/        allowlist and path policy
  scan/             inventory and duplicate detection
  classification/   rules, port, Spring AI, validation
  commands/         restricted grammar
  planning/         immutable plans and selection
  execution/        NIO moves and reconciliation
  history/          durable journal records
  persistence/      SQLite migration and repositories
  health/           application and last LLM status
  web/              UI and REST
src/main/resources/ templates/, static/ (including local HTMX)
src/test/java/      JUnit 5 fixtures, integration and negative tests
scripts/           safe demo generation and launch scripts
.github/workflows/ CI matrix and build artifacts
```

```mermaid
flowchart LR
    UI[Local browser / REST] --> Services[Scan → classify → plan]
    Services --> Preview[Immutable preview]
    Preview --> Consent[Explicit confirmation]
    Consent --> Validator[Java path and snapshot checks]
    Validator --> NIO[No-replace NIO move]
    NIO --> Journal[SQLite journal / reconciliation]
    Journal --> Undo[Confirmed safe Undo]
    Services --> AI[Optional loopback Spring AI]
    AI --> Suggestions[Validated categories only]
    Suggestions --> Preview
```

[Архитектура](docs/ARCHITECTURE.md) · [Модель безопасности](docs/SECURITY_MODEL.md) ·
[Persistence](docs/PERSISTENCE.md) · [API](docs/API.md) · [Проверки](docs/VERIFICATION.md) ·
[Roadmap](docs/ROADMAP.md) · [Contributing](CONTRIBUTING.md) · [Security policy](SECURITY.md).
