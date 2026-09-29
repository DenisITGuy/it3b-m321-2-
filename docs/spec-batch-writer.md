1. Zweck und Abgrenzung
1.1 Zweck
Der batch-writer ist ein eigenständiger Dienst im Chat-System. Er holt Nachrichten aus der RabbitMQ-Queue chat.persist und schreibt sie dauerhaft in die PostgreSQL-Datenbank. Dadurch wird verhindert, dass Nachrichten verloren gehen, wenn RabbitMQ neu startet oder die Queue voll läuft.
1.2 Abgrenzung (was der Dienst NICHT tut)
❌ Der batch-writer nimmt keine HTTP-Anfragen entgegen (kein Web-Server, kein Port).
❌ Der batch-writer liest keine Chat-Historie (kein GET-Endpunkt).
❌ Der batch-writer verwaltet keine Räume oder Mitgliedschaften.
❌ Der batch-writer validiert keine Keycloak-Tokens.
❌ Der batch-writer sendet keine Nachrichten an Clients (kein WebSocket).

2. Vertrag: Was auf der Queue ankommt
2.1 Queue-Name
chat.persist
2.2 Nachrichten-Format
Jede Nachricht in der Queue ist ein JSON-Objekt mit folgender Struktur:
---------------------------------------------------------------
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "roomId": "11111111-1111-1111-1111-111111111111",
  "sender": "testuser",
  "content": "Hallo Welt!",
  "createdAt": "2026-09-29T12:49:44.828265886Z"
}
---------------------------------------------------------------

Header:
content_type: application/json
Woher wissen wir das?
Der chat-service (POST /api/messages) erzeugt diese Nachrichten und sendet sie an die Queue chat.persist. Das Format entspricht der Java-Klasse MessageEvent im chat-service.
2.3 Dead Letter Queue (DLQ)
Falls eine Nachricht nicht verarbeitet werden kann (z.B. ungültiges JSON), wird sie an die Queue chat.dlq weitergeleitet.

3. Verhalten
3.1 Normalfall (S3)
Der batch-writer holt Nachrichten aus chat.persist.
Er speichert sie in der Tabelle message in PostgreSQL.
Nach erfolgreicher Speicherung wird die Nachricht aus der Queue entfernt (acknowledged).
Bei 1000 Nachrichten dauert die Verarbeitung maximal 60 Sekunden.
3.2 Batch-Verarbeitung (S4)
Der batch-writer verarbeitet Nachrichten in Batches (Gruppen).
Ein Batch enthält maximal 100 Nachrichten.
Für 1000 Nachrichten werden maximal 100 Transaktionen ausgeführt (1000 / 10 = 100 Transaktionen, wenn Batch-Größe 10 ist).
Wenn der batch-writer gestoppt wird, bleiben Nachrichten in der Queue. Beim Neustart werden sie verarbeitet.
3.3 Idempotenz (S5)
Wenn dieselbe Nachricht (gleiche id) zweimal in der Queue liegt, wird sie nur einmal in die Datenbank geschrieben.
Es entsteht kein Eintrag in chat.dlq.
Implementierung: Vor dem Insert wird geprüft, ob die id bereits in der Tabelle existiert (INSERT ... ON CONFLICT DO NOTHING oder SELECT vor INSERT).
3.4 Skalierbarkeit (S6)
Es können mehrere Instanzen des batch-writer parallel laufen (z.B. docker compose up --scale batch-writer=2).
Beide Instanzen hängen an derselben Queue chat.persist.
RabbitMQ verteilt die Nachrichten automatisch (Round-Robin).
Durch die Idempotenz-Prüfung (3.3) entstehen keine Duplikate, auch wenn zwei Instanzen dieselbe Nachricht gleichzeitig verarbeiten.
3.5 Datenbank-Ausfall (S7)
Wenn PostgreSQL nicht erreichbar ist, wartet der batch-writer und versucht es erneut (Retry mit Backoff).
Der `batch-writer** läuft ohne Neustart** weiter.
Nachrichten bleiben in der Queue, bis die Datenbank wieder verfügbar ist.
Nach spätestens 90 Sekunden (nach DB-Restart) sind alle Nachrichten verarbeitet.
3.6 Code-Qualität (S8)
Keine Streams im Code (laut CLAUDE.md).
Kommentar über jeder Klasse und Methode.
.env-Datei ist nicht im Repository (nur .env.example).

4. Datenmodell und Konfiguration
4.1 Tabelle: message

---------------------------------------------------------------
CREATE TABLE message (
    id UUID PRIMARY KEY,
    room_id UUID NOT NULL,
    sender VARCHAR(50) NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Index für schnelle Abfragen nach Raum
CREATE INDEX idx_message_room_id ON message(room_id);
---------------------------------------------------------------