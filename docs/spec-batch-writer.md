# Spezifikation: batch-writer

## 1. Zweck und Abgrenzung

### 1.1 Zweck
Der batch-writer ist ein eigenständiger Spring-Boot-Dienst im Chat-System. Er holt
Nachrichten aus der RabbitMQ-Queue chat.persist und schreibt sie dauerhaft in die
PostgreSQL-Tabelle message. Dadurch gehen Nachrichten nicht verloren, wenn RabbitMQ
neu startet oder die Queue voll läuft.

### 1.2 Abgrenzung (was der Dienst bewusst NICHT tut)
- Kein HTTP-Endpunkt, kein veröffentlichter Port (S2).
- Liest keine Chat-Historie, verwaltet keine Räume/Mitgliedschaften.
- Validiert keine Keycloak-Tokens; Inhalts-Validierung bleibt beim chat-service.
- Sendet nichts an Clients (kein WebSocket).

## 2. Vertrag: Was auf der Queue ankommt

### 2.1 Queue
Name: chat.persist (durable). Zusätzlich existiert chat.dlq (durable) als Dead Letter Queue.

### 2.2 Nachrichten-Format
JSON-Objekt, Header content_type: application/json:

    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "roomId": "11111111-1111-1111-1111-111111111111",
      "sender": "testuser",
      "content": "Hallo Welt!",
      "createdAt": 1794430756.1654861
    }

Hinweis createdAt: Der Jackson2JsonMessageConverter des chat-service serialisiert
Instant standardmässig als Epoch-Sekunden (Dezimalzahl). Der Consumer deserialisiert
mit Jackson und akzeptiert zusätzlich ISO-8601-Strings.

### 2.3 Woher ich das weiss
- Payload-Klasse: MessageEvent im chat-service (backend/src/main/java/.../MessageEvent.java).
- Converter: Jackson2JsonMessageConverter in der RabbitConfig des chat-service.
- Verifiziert: Nachrichten aus der Queue via Integrationstest-Log und
  docker compose exec rabbitmq rabbitmqctl get chat.persist inspectiert.

## 3. Verhalten

### 3.1 Normalfall (S3)
Consumer liest chat.persist, schreibt Batches in message, ackt nach erfolgreichem
Commit. 1000 Nachrichten sind nach spätestens 60 s persistiert, Queue leer.

### 3.2 Batch-Verarbeitung (S4)
Batch-Grösse über BATCH_SIZE konfigurierbar (Default 100). Pro Batch genau eine
Transaktion (ein INSERT ... ON CONFLICT). 1000 Nachrichten → 10 Transaktionen
(gefordert: ≤ 100). Writer gestoppt: Nachrichten bleiben in der durable Queue;
nach Start werden sie verarbeitet, nichts geht verloren.

### 3.3 Idempotenz (S5)
INSERT ... ON CONFLICT (id) DO NOTHING: Dieselbe Nachricht zweimal ergibt genau
eine Zeile. Duplikate werden regulär geackt, daher bleibt chat.dlq leer.

### 3.4 Skalierung (S6)
Mehrere Instanzen (--scale batch-writer=2) sind Competing Consumers an chat.persist;
RabbitMQ verteilt per Round-Robin. Kein container_name in Compose. Die PK auf id
verhindert Duplikate auch bei gleichzeitiger Verarbeitung.

### 3.5 Datenbank-Ausfall (S7)
Schlägt der Batch fehl, wirft der Consumer RuntimeException. Spring Retry
(application.yml: enabled, initial-interval 1 s, multiplier 2.0, max-interval 10 s,
max-attempts 10) versucht erneut; danach wird die Nachricht requeued
(default-requeue-rejected: true). Nachrichten bleiben in der Queue, der Writer
läuft ohne Hand-Neustart weiter. Nach DB-Restart sind alle Nachrichten innerhalb
von 90 s persistiert.

### 3.6 Nicht verarbeitbare Nachrichten
Fataler Konvertierungsfehler (z. B. kaputtes JSON) → Reject ohne Requeue →
Dead-Lettering nach chat.dlq (siehe 4.3).

### 3.7 Code-Regeln (S8)
Keine Streams, Kommentar über jeder Klasse und Methode, .env nicht im Repo
(nur .env.example).

## 4. Datenmodell und Konfiguration

### 4.1 Tabelle message
Entsteht beim Start automatisch durch Hibernate (spring.jpa.hibernate.ddl-auto: update)
aus der Entity Message – kein manuelles DDL im Repo.

| Spalte     | Typ                          | Begründung |
|------------|------------------------------|------------|
| id         | uuid, PRIMARY KEY            | Idempotenz-Schlüssel für ON CONFLICT |
| room_id    | uuid, NOT NULL               | Fachlicher Kontext gemäss PLANUNG.md 3.7 |
| sender     | varchar(255), NOT NULL       | Absendername gemäss PLANUNG.md 3.7 |
| content    | text, NOT NULL               | Nachrichtentext, unbeschränkte Länge |
| created_at | timestamptz, NOT NULL        | Sendezeitpunkt (nicht Speicherzeit) |

Bewusst kein Zusatzindex: Der Dienst führt nur INSERTs und COUNTs aus, keine
Raum-Abfragen; der PK-Index genügt.

### 4.2 Umgebungsvariablen (alle in .env.example)
POSTGRES_USER, POSTGRES_PASSWORD, POSTGRES_DB, POSTGRES_HOST, POSTGRES_PORT,
RABBITMQ_HOST, RABBITMQ_PORT, RABBITMQ_USER, RABBITMQ_PASSWORD,
BATCH_SIZE (Default 100).

### 4.3 Netz und Queues
Dienst im Netz chat-net neben postgres und rabbitmq, ohne veröffentlichte Ports.
chat.persist trägt die Dead-Letter-Argumente x-dead-letter-exchange: "" und
x-dead-letter-routing-key: chat.dlq, damit Rejects in chat.dlq landen.

## 5. Abnahmekriterien (messbar)

| Nr | Kriterium | Messbefehl | Erwartung |
|----|-----------|------------|-----------|
| S1 | Tests grün | mvn clean test | BUILD SUCCESS |
| S2 | Keine Ports | docker compose ps | keine Spalte PORTS mit Mapping |
| S3 | 1000 Msg ≤ 60 s | docker compose exec postgres psql -U $POSTGRES_USER -d $POSTGRES_DB -tc "SELECT COUNT(*) FROM message;" | 1000 |
| S4 | ≤ 100 Tx | docker compose logs batch-writer \| grep -c "Batch mit" | ≤ 100, COUNT = 1000 |
| S5 | Duplikat 1× | SELECT COUNT(*) FROM message WHERE id = '<id>'; | 1; chat.dlq leer |
| S6 | Scale=2 | docker compose up --scale batch-writer=2, dann COUNT | 1000, keine Duplikate |
| S7 | Ausfall ≤ 90 s | COUNT nach DB-Restart pollend prüfen | 300 |
| S8 | Code-Regeln | git ls-files \| grep -c "^\.env$" ; Stream-Suche im Code | 0 Treffer |