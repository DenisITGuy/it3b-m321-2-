    # Umsetzungsplan: batch-writer

## Warum diese Reihenfolge?
Wir bauen zuerst das Fundament (Modul, Docker ohne Ports), dann den Happy-Path (Empfangen + Speichern), und erst danach die Robustheit (Batching, Idempotenz, Ausfall-Szenarien). So ist jeder Schritt einzeln testbar und entspricht einem sauberen Git-Commit.

## Schritt 1: Maven-Modul und Grundgerüst (S1)
- **Aufgabe:** Verzeichnis `batch-writer/` mit `pom.xml` (Dependencies: `spring-boot-starter-amqp`, `spring-boot-starter-data-jpa`, `postgresql`, `spring-boot-starter-test`) und leerer `BatchWriterApplication.java` erstellen.
- **Test:** `mvn clean test` läuft grün.
- **Commit:** `Schritt 1: Maven-Modul und Grundgerüst angelegt`

## Schritt 2: Docker-Integration ohne Ports (S2)
- **Aufgabe:** `Dockerfile` (Multi-Stage) und Dienst `batch-writer` in `docker-compose.yml` hinzufügen. Netzwerk: `chat-net`. **Keine** `ports:`-Konfiguration.
- **Test:** `docker compose up -d --build` startet erfolgreich. `docker compose ps` zeigt keine Ports für batch-writer.
- **Commit:** `Schritt 2: Docker-Integration ohne öffentliche Ports`

## Schritt 3: Datenbank-Entity und Schema (Vorbereitung S3)
- **Aufgabe:** Entity `Message.java` und `MessageRepository.java` erstellen. `application.yml` mit DB-Zugangsdaten konfigurieren.
- **Test:** Anwendung startet, Tabelle `message` ist via `psql` sichtbar.
- **Commit:** `Schritt 3: Datenbank-Entity und Schema definiert`

## Schritt 4: RabbitMQ Consumer Grundgerüst
- **Aufgabe:** `RabbitConfig.java` (JSON-Converter) und `MessageConsumer.java` mit `@RabbitListener(queues = "chat.persist")` erstellen.
- **Test:** Manuell eine Testnachricht in die Queue legen -> Log zeigt den empfangenen JSON-String.
- **Commit:** `Schritt 4: RabbitMQ Consumer empfängt Nachrichten`

## Schritt 5: Idempotentes Einzelspeichern (S5)
- **Aufgabe:** Nachricht parsen, in `Message`-Entity umwandeln und mit `ON CONFLICT DO NOTHING` (oder `existsById`-Prüfung) speichern.
- **Test:** Integrationstest: Dieselbe Nachricht zweimal senden -> DB enthält nur 1 Zeile.
- **Commit:** `Schritt 5: Idempotentes Speichern implementiert`

## Schritt 6: Batch-Verarbeitung (S4)
- **Aufgabe:** `BATCH_SIZE=10` konfigurieren. Listener sammelt Nachrichten und ruft `repository.saveAll()` auf. `@Transactional` sicherstellen.
- **Test:** 1000 Nachrichten senden -> DB enthält 1000 Zeilen. (Transaktionsanzahl wird durch Batch-Size garantiert).
- **Commit:** `Schritt 6: Batch-Verarbeitung für Performance implementiert`

## Schritt 7: Resilienz bei DB-Ausfall (S7)
- **Aufgabe:** Sicherstellen, dass bei `DataAccessException` die Nachricht nicht acknowledged wird (Standardverhalten von Spring AMQP bei Exceptions).
- **Test:** Integrationstest: Postgres-Container stoppen, Nachricht senden, Postgres starten -> Nachricht wird automatisch verarbeitet, ohne dass der batch-writer neu gestartet wurde.
- **Commit:** `Schritt 7: Resilienz und Retry-Logik bei DB-Ausfall`

## Schritt 8: Skalierbarkeit testen (S6)
- **Aufgabe:** Keine Code-Änderung nötig, da Idempotenz (Schritt 5) und Competing Consumers (RabbitMQ Standard) dies abdecken.
- **Test:** `docker compose up --scale batch-writer=2` + 1000 Nachrichten senden -> COUNT ergibt 1000, keine Duplikate.
- **Commit:** `Schritt 8: Skalierbarkeit mit 2 Instanzen verifiziert`

## Schritt 9: Code-Qualität und Aufräumen (S8)
- **Aufgabe:** Alle `Stream`-API-Aufrufe durch `for`-Schleifen ersetzen. Javadoc über jede Klasse und Methode hinzufügen. `.env` in `.gitignore` prüfen.
- **Test:** Manuelles Code-Review gegen CLAUDE.md.
- **Commit:** `Schritt 9: Code-Qualität, Kommentare und .gitignore finalisiert`