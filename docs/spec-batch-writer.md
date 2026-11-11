# Spezifikation: batch-writer

## 1. Zweck und Abgrenzung

### 1.1 Zweck
Der batch-writer ist ein eigenstaendiger Spring-Boot-Dienst. Er konsumiert die
RabbitMQ-Queue chat.persist und schreibt die Nachrichten dauerhaft in die
PostgreSQL-Tabelle message. Damit gehen Nachrichten nicht verloren, wenn der
chat-service oder RabbitMQ neu startet.

### 1.2 Abgrenzung
- Kein HTTP-Endpunkt und kein veroeffentlichter Port (S2).
- Liest keine Chat-Historie, verwaltet keine Raeume oder Mitgliedschaften.
- Prueft keine Keycloak-Tokens; die Inhaltsvalidierung bleibt beim chat-service.
- Sendet nichts an Clients (kein WebSocket).

## 2. Vertrag

### 2.1 Queue
chat.persist, durable, ohne zustzliche Argumente. Zusaetzlich existiert chat.dlq
(durable) gemaess PLANUNG.md.

### 2.2 Nachricht
Ein JSON-Objekt pro Nachricht, Header content_type: application/json:

    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "roomId": "11111111-1111-1111-1111-111111111111",
      "sender": "testuser",
      "content": "Hallo Welt!",
      "createdAt": 1794430756.1654861
    }

createdAt ist eine Dezimalzahl in Epoch-Sekunden: Der Jackson2JsonMessageConverter
serialisiert Instant standardmaessig so. Der Consumer deserialisiert mit Jackson
und akzeptiert dadurch auch ISO-8601-Zeitstempel.

### 2.3 Herkunft des Vertrags (Beleg)
- Felder: backend/src/main/java/ch/it3b/chat/messaging/MessageEvent.java
- Deklaration: backend/src/main/java/ch/it3b/chat/config/RabbitConfig.java,
  new Queue(QUEUE_NAME, true) ohne Argumente
- Versand: backend/src/main/java/ch/it3b/chat/api/MessageController.java
- Verifiziert am laufenden System: Nachrichteninhalt im Integrationstest-Log und
  veroeffentlichte Nachricht ueber die Management-API von RabbitMQ.

## 3. Verhalten

### 3.1 Normalfall (S3)
Der Consumer liest chat.persist, schreibt die Nachrichten und ackt erst nach
erfolgreichem Commit. Gemessen: 1000 Nachrichten in 2.9 s, danach Queue leer.

### 3.2 Batch-Verarbeitung (S4)
Die Listener-Factory setzt consumerBatchEnabled und batchSize aus app.batch-size
(Default 100). Der Container sammelt einzelne Nachrichten zu einer Liste, der
Consumer schreibt sie in einer Transaktion. Begruendung: pro Batch ein Commit
statt pro Nachricht. Gemessen: 1000 Nachrichten nach Writer-Start in 10 Batches
(gefordert sind hoechstens 100 Transaktionen). Bei gestopptem Writer bleiben die
Nachrichten in der durable Queue und werden nach dem Start verarbeitet.

### 3.3 Idempotenz (S5)
Das Repository nutzt INSERT ... ON CONFLICT (id) DO NOTHING. Begruendung: Die
Queue garantiert keine Exactly-once-Zustellung; der Primaerschluessel auf id ist
der einzige Punkt, an dem Duplikate sicher verhindert werden. Eine doppelte
Nachricht wird regulaer verarbeitet und geackt, deshalb bleibt chat.dlq leer.

### 3.4 Skalierung (S6)
Mehrere Instanzen sind Competing Consumers an derselben Queue; RabbitMQ verteilt
per Round-Robin. In docker-compose.yml hat der Dienst kein container_name, sonst
liesse sich nicht skalieren. Duplikate verhindert der Primaerschluessel.
Gemessen mit --scale batch-writer=2: 1000 Nachrichten, 0 Duplikate, beide
Instanzen haben Batches geschrieben.

### 3.5 Datenbank-Ausfall (S7)
Schlägt ein Batch fehl, wirft der Consumer eine RuntimeException. Die Transaktion
wird zurueckgerollt, Spring Retry versucht es erneut (initial-interval 1 s,
multiplier 2.0, max-interval 10 s, max-attempts 10). Danach wird die Nachricht
requeued (default-requeue-rejected: true), bleibt also in der Queue. Der Dienst
laeuft ohne Hand-Neustart weiter. Derselbe Mechanismus greift, solange das Schema
noch nicht angelegt ist. Gemessen: Postgres 15 s gestoppt, 300 Nachrichten, nach
dem Start in 14.2 s vollstaendig persistiert.

### 3.6 Nicht verarbeitbare Nachrichten
Ein fataler Konvertierungsfehler (z. B. ungueltiges JSON) fuehrt zu Reject ohne
Requeue; die Nachricht wird verworfen. chat.persist traegt bewusst keine
Dead-Letter-Argumente, weil der chat-service die Queue ohne Argumente deklariert
und RabbitMQ abweichende Argumente mit PRECONDITION_FAILED (406) ablehnt
(siehe 6.1). chat.dlq existiert und bleibt leer.

### 3.7 Tests (S1)
mvn clean test im Wurzelverzeichnis ist gruen. Die Integrationstests starten mit
Testcontainers eine echte RabbitMQ-Instanz und eine echte PostgreSQL-Instanz.

### 3.8 Code-Regeln (S8)
Keine Streams, Kommentar ueber jeder Klasse und Methode, .env nicht im Repo.

## 4. Datenmodell und Konfiguration

### 4.1 Tabelle message (aus dem laufenden System, \d message)

| Spalte     | Typ                         | Nullable | Begruendung |
|------------|-----------------------------|----------|-------------|
| id         | uuid                        | not null | Primaerschluessel und Idempotenz-Schluessel fuer ON CONFLICT |
| room_id    | uuid                        | not null | Zuordnung zum Chat-Raum gemaess PLANUNG.md 3.7 |
| sender     | character varying(255)      | not null | Absendername; Laenge ist der Hibernate-Standard fuer String |
| content    | text                        | not null | Nachrichtentext ohne sinnvolle Laengenbegrenzung |
| created_at | timestamp(6) with time zone | not null | Sendezeitpunkt, nicht Speicherzeit; Zeitzone fuer korrekte Ordnung |

Index: message_pkey (btree auf id). Bewusst kein weiterer Index: Der Dienst
fuehrt nur INSERTs und COUNTs aus, keine Abfragen pro Raum. Ein Index wuerde
Schreibleistung kosten und nichts nutzen.

### 4.2 Entstehung des Schemas
Das Schema entsteht beim Start durch Hibernate aus der Entity Message
(spring.jpa.hibernate.ddl-auto: update). Es liegt kein manuelles DDL im Repo,
damit Entity und Tabelle nicht auseinanderlaufen.

### 4.3 Umgebungsvariablen

| Variable | Quelle | Default in application.yml | Zweck |
|---|---|---|---|
| POSTGRES_USER | .env | chat_user | Datenbank-Benutzer |
| POSTGRES_PASSWORD | .env | super_secret_postgres_pw | Datenbank-Passwort |
| POSTGRES_DB | .env | chat_db | Datenbankname |
| POSTGRES_HOST | docker-compose.yml | postgres | Servicename im Compose-Netz |
| POSTGRES_PORT | docker-compose.yml | 5432 | Datenbank-Port |
| RABBITMQ_USER | .env | chat_user | Broker-Benutzer |
| RABBITMQ_PASSWORD | .env | rabbit_password | Broker-Passwort |
| RABBITMQ_HOST | docker-compose.yml | rabbitmq | Servicename im Compose-Netz |
| RABBITMQ_PORT | docker-compose.yml | 5672 | Broker-Port |
| BATCH_SIZE | .env | 100 | Nachrichten pro Batch und Transaktion |

Host und Port sind Servicenamen im Compose-Netz und werden dort fest gesetzt;
Zugangsdaten und Batch-Groesse kommen aus .env. Platzhalter nutzen die
Spring-Syntax ${VAR:default}.

### 4.4 Netz
Der Dienst liegt im Netz chat-net neben postgres und rabbitmq und veroeffentlicht
keine Ports. Messungen erfolgen von innen mit docker compose exec.

## 5. Abnahmekriterien

Zugangsdaten in den Befehlen entsprechen .env (chat_user / chat_db).
Alle Szenarien laufen in Reihenfolge auf demselben Stack, gemessen wird als Delta.
Automatisiert: tools/test-s1-s8.ps1 (nutzt -f docker-compose.yml, sendet von innen).

| Nr | Kriterium | Befehl | Erwartung | Gemessen |
|----|-----------|--------|-----------|----------|
| S1 | Tests gruen | mvn clean test | BUILD SUCCESS | 2 Tests, 0 Fehler |
| S2 | Keine Ports | docker compose -f docker-compose.yml config \| findstr published | keine Treffer | 0 |
| S3 | 1000 in 60 s | tools/send.sh 1000 S3, danach SELECT COUNT(*) FROM message; | +1000, Queue 0 | +1000 in 2.9 s |
| S4 | hoechstens 100 Transaktionen | docker compose logs batch-writer \| findstr /C:"Batch mit" | +1000, <=100 Batches | 10 Batches |
| S5 | Duplikat genau einmal | SELECT COUNT(*) FROM message WHERE id='<id>'; rabbitmqctl list_queues name messages | 1 und chat.dlq 0 | 1 / 0 |
| S6 | zwei Instanzen, keine Duplikate | docker compose up -d --scale batch-writer=2, danach COUNT und GROUP BY id HAVING COUNT(*)>1 | +1000, 0 Duplikate | 0 Duplikate |
| S7 | Ausfall ueberlebt | postgres stoppen, 300 senden, nach 15 s starten, COUNT pollen | +300 in <=90 s | +300 in 14.2 s |
| S8 | Code-Regeln | Suche nach .stream()/.collect(); git ls-files \| findstr /R "^\.env$" | 0 Treffer | 0 / 0 |

## 6. Verworfene Loesungswege

### 6.1 Dead-Letter-Argumente an chat.persist
Verworfen. Der chat-service deklariert die Queue ohne Argumente. Mit
x-dead-letter-exchange am Consumer antwortet RabbitMQ mit PRECONDITION_FAILED
(406), schliesst den Channel des Producers und ein POST /messages scheitert mit
HTTP 500. Beobachtet: genau eine verlorene Nachricht pro Lauf von 1000.

### 6.2 setBatchListener(true)
Verworfen. Das erwartet ein JSON-Array als Payload. Der chat-service sendet
einzelne JSON-Objekte, die Deserialisierung schlug fehl. Richtig ist
consumerBatchEnabled: der Container bildet die Liste aus Einzelnachrichten.

### 6.3 Spalten sender_id / sender_name / sent_at
Verworfen. Der Vertrag des chat-service liefert sender und createdAt; die
Abbildung auf andere Spaltennamen erzeugte zwei abweichende Schemas.

### 6.4 ${BATCH_SIZE:-100}
Korrigiert zu ${BATCH_SIZE:100}. ":-" ist bash-Syntax; Spring liest alles nach dem
ersten Doppelpunkt als Default, also -100, und der Batch-Modus waere abgeschaltet.

### 6.5 Testcontainers 1.19.8
Angehoben auf 1.21.3 und API-Version 1.41 gesetzt. docker-java sendet sonst
API 1.32, neuere Docker-Engines verlangen mindestens 1.40 und antworten mit 400.