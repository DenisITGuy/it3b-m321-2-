# Spezifikation: Live-Zustellung (Bewertung 2)

Stand: 2026-11-12, vor der Umsetzung. Messwerte sind mit Datum belegt; Szenarien
ohne Messwert werden spaetestens in Schritt 9 mit tools/test-live-s1-s8.ps1
gemessen und in Abschnitt 5 nachgetragen.

## 1. Zweck und Abgrenzung

### 1.1 Zweck
Der chat-service stellt jede Nachricht zusaetzlich zur Persistenz live an
verbundene SSE-Clients zu. Weg: POST /messages -> Exchange chat.messages ->
Queue pro Instanz -> GET /stream. Der Anzeigeweg funktioniert ohne postgres und
ohne batch-writer (S5).

### 1.2 Abgrenzung
- Die Persistenz-Strecke (chat.persist, batch-writer, Tabelle message) bleibt
  unveraendert.
- Keine Authentifizierung: Keycloak ist nicht Teil dieses Auftrags.
- Kein Historien-Endpunkt: Nachholen liefert dieselben SSE-Ereignisse,
  ausgeloest durch den Header Last-Event-ID.

## 2. Vertrag

### 2.1 Zwei Wege, ein Versand
Der MessageController sendet jedes MessageEvent zweimal: unveraendert an den
default Exchange mit Routing-Key chat.persist (Bewertung 1) und zusaetzlich an
den Fanout-Exchange chat.messages (durable) mit leerem Routing-Key.
Fanout statt des Topic aus PLANUNG.md: Jede Instanz-Queue braucht eine
identische Kopie jeder Nachricht, und es gibt nur einen Ereignistyp - es gibt
nichts zu routen. Der Topic in PLANUNG.md gehoert zur alten Gesamtarchitektur
mit mehreren Routing-Keys (message.send, user.online, ...). Durable, damit der
Exchange einen Broker-Neustart ueberlebt.

### 2.2 Ereignis und Wire-Format (gemessen 2026-11-12)
Felder: id (UUID), roomId (UUID), sender (String), content (String),
createdAt (Instant). id und createdAt erzeugt der Server in
MessageController.sendMessage (UUID.randomUUID(), Instant.now()); der Client
liefert nur roomId, sender und content.

Gemessen auf chat.persist: batch-writer gestoppt, eine Nachricht per POST
erzeugt, Payload ueber die Management-API mit ackmode ack_requeue_true gelesen
(Nachricht bleibt erhalten), batch-writer wieder gestartet:

    payload: {"id":"991dd58f-4745-4cd2-9a72-0221956f24bf",
              "roomId":"11111111-1111-1111-1111-111111111111",
              "sender":"Tester","content":"format-probe",
              "createdAt":1794521459.617450158}
    properties: content_type=application/json, content_encoding=UTF-8,
                delivery_mode=2,
                headers.__TypeId__=ch.it3b.chat.messaging.MessageEvent
    exchange="", routing_key=chat.persist

Wichtig: Auf der Leitung ist createdAt eine Epoch-Dezimalzahl (Sekunden mit
Nanos); nur die REST-Antwort desselben Events zeigt einen ISO-String
(2026-11-12T22:10:59.617450158Z). Referenz fuer data: ist das Wire-Format,
nicht die REST-Antwort.

rabbitmqctl get existiert im Image rabbitmq:3.12-management nicht. Leseweg von
innen im Netz chat-net (Zugangsdaten gemaess .env):

    docker run --rm --network it3b-m321_chat-net curlimages/curl -s ^
      -u chat_user:rabbit_password -H "content-type: application/json" ^
      -X POST http://rabbitmq:15672/api/queues/%2F/chat.persist/get ^
      -d "{\"count\":1,\"ackmode\":\"ack_requeue_true\",\"encoding\":\"auto\"}"

### 2.3 SSE-Stream
GET /stream?roomId=..., Content-Type: text/event-stream, SseEmitter mit
Timeout 0 (kein Timeout; S6 laeuft lange). Direkt nach erfolgreicher
Registrierung sendet der Server den SSE-Kommentar ":connected" - fuer
curl-Messungen der sofortige Beweis, dass der Stream offen ist, bevor die
erste Nachricht kommt; EventSource ignoriert Kommentarzeilen.

Pro Ereignis:
- id: die UUID der Nachricht (gleich dem Primaerschluessel der Tabelle message)
- data: einzeiliges JSON im Wire-Format aus 2.2 (kein Pretty-Print, keine
  Roh-Umbrueche)
- kein event:-Feld, also Default-Eventname message (Browser-onMessage greift)

Der Parameter ist @RequestParam UUID roomId. Fehlender Parameter und
Nicht-UUID erzeugen beide HTTP 400 durch Spring selbst
(MissingServletRequestParameterException bzw. TypeMismatchException), bevor
der Stream oeffnet - eigener Pruefcode entfaellt.

Warum data: dasselbe Format wie chat.persist haben muss: Die Formatgleichheit
entsteht nicht durch die Datenbank - der batch-writer parst das JSON und
schreibt Spalten (id, room_id, sender, content, created_at), in der Tabelle
liegt kein JSON. Sie entsteht dadurch, dass das Nachholen die DB-Zeilen mit
demselben Jackson2JsonMessageConverter serialisiert (ein Bean, dieselbe
Instanz). Der Client hat dadurch einen Parser fuer live und nachgeholt, und
id: ist auf beiden Wegen derselbe Schluessel.

### 2.4 Queue pro Instanz
Name chat.live.<UUID-Kennung>. Die Kennung erzeugt ChatApplication.main beim
Start (UUID.randomUUID()) und setzt sie als System-Property live.queue.name.
Flags: durable=false, exclusive=true, autoDelete=true; Binding an
chat.messages. Deklariert wird die Queue von der Instanz selbst (RabbitConfig),
nicht zentral. Genau ein Consumer pro Instanz (concurrency 1): S4 zaehlt
Consumer, und die Absender-Reihenfolge in S3 haelt nur ein einzelner Consumer.
Prefetch 10 (LIVE_QUEUE_PREFETCH, siehe 3.3 und 4).

## 3. Verhalten

### 3.1 Verteilung und Raumfilter (S3)
Der Fanout-Exchange legt jede Nachricht als Kopie in jede Instanz-Queue.
Gefiltert wird erst beim Ausspielen in der StreamVerwaltung: Der Listener
sendet nur an Verbindungen, deren roomId gleich der roomId der Nachricht ist.
Dort und nur dort ist bekannt, welche SSE-Verbindung zu welchem Raum gehoert;
ein Filter im Broker wuerde pro Raum eine Queue und einen Topic-Exchange
verlangen, obwohl jede Instanz ohnehin alle Nachrichten sehen muss, weil ihre
Clients in beliebigen Raeumen sein koennen. Versand in Eingangsreihenfolge.

### 3.2 Queue-Lebensdauer und Heartbeat (S4)
docker compose up -d --scale chat-service=1 stoppt den Container -> der Socket
wird geschlossen (FIN) -> der Broker loescht die exclusive Queue mit
autoDelete binnen Sekunden. docker compose pause haelt Prozess und
TCP-Verbindung alive -> die Queue bleibt. Dasselbe gilt nach einem Crash:
Queue weg, beim Wiederverbinden deklariert Spring sie automatisch neu
(RabbitAdmin deklariert Declarable-Beans bei Connection-Recovery erneut,
gleicher Name aus dem System-Property).

Entscheidung Heartbeat: spring.rabbitmq.requested-heartbeat = 0 (aus).
Begruendung: Eine pausierte Instanz sendet keine Heartbeats; mit dem
Broker-Default von 60 s wuerde der Broker die Verbindung nach einer Minute
schliessen und die exclusive Queue loeschen - S6 erwartet sie nach dem
Fortsetzen aber noch. Der Prufer pausiert beliebig lange, deshalb ist nur
"aus" robust. S4 funktioniert weiter, weil der Container-Stop den Socket mit
FIN schliesst und nicht der Heartbeat. Kehrseite, dokumentiert: still
gestorbene Verbindungen (ohne FIN) erkennt der Broker nicht; im
abgeschlossenen Docker-Netz ist das akzeptabel.

### 3.3 Rueckstau (S6)
Queue-Argumente: x-max-length = LIVE_QUEUE_MAX_LENGTH - LIVE_QUEUE_PREFETCH
= 1000 - 10 = 990 (Default), x-overflow = drop-head. Bei Ueberlauf fliegt die
aelteste Nachricht aus der Queue der betroffenen Instanz; neue kommen weiter
rein. Andere Instanzen haben eigene Queues und sind nicht betroffen.

Begruendung der Reserve: Gemessen wird "nie mehr als 1000" ueber
rabbitmqctl list_queues name messages; die Spalte messages zaehlt ready plus
unacknowledged. Eine pausierte Instanz ackt nichts und haelt bis zu prefetch
(10) Nachrichten in flight. Nur mit x-max-length = 990 bleibt
ready + unacked <= 1000, egal ob RabbitMQ das Limit auf ready allein oder auf
die Gesamtzahl anwendet.

Messhinweis: Dieses Zusammenspiel ist vor Schritt 2 nicht messbar (keine
chat.live-Queue vorhanden). Der Test von Schritt 5 muss es bestaetigen:
waehrend eine Instanz pausiert, sekundlich pollen mit
rabbitmqctl list_queues name messages messages_ready messages_unacknowledged
consumers - Erwartung: messages <= 1000 und messages_unacknowledged <= 10.

Bewusste Konsequenz (Entscheidung, nicht Luecke): Ein Client, der verbunden
bleibt, dessen Instanz aber zurueckfaellt, verliert die gedroppten Nachrichten
endgueltig. Vollstaendigkeit garantiert nur der DB-Weg, und den holt sich nur,
wer mit Last-Event-ID neu verbindet. Live-Zustellung ist Best-Effort; die
Datenbank ist die Quelle der Wahrheit.

### 3.4 Unabhaengigkeit von der Datenbank (S5)
Der Weg POST -> Exchange -> Instanz-Queue -> SSE loest keine JPA-Abfrage aus
und wartet nicht auf den batch-writer. Randbedingung: Der chat-service muss
vollstaendig gestartet sein, bevor postgres gestoppt wird, denn sein Start
braucht die DB (JPA/HikariCP). Ein Neustart des chat-service bei gestoppter DB
ist nicht Teil der Szenarien und wird nicht gemessen.

### 3.5 Nachholen mit Last-Event-ID (S7)
Der Header Last-Event-ID enthaelt eine UUID ohne Ordnung. "Alles danach" ist
deshalb der lexikografische Paarvergleich
(created_at, id) > (last_created_at, last_id), in SQL:
WHERE created_at > :ts OR (created_at = :ts AND id > :id),
ORDER BY created_at, id. Ein striktes created_at > :ts allein wuerde
Nachrichten mit gleichem Zeitstempel und groesserer id schlucken. Abfrage per
JdbcTemplate.queryForList und for-Schleife, Serialisierung mit demselben
Jackson2JsonMessageConverter-Bean wie der Versand (2.3).

Unbekannte Last-Event-ID (nicht in der DB oder aus einem fremden Raum): wird
behandelt wie "keine ID" - kein Nachholen, direkt live, Vorfall geloggt.
Begruendung: Der Client kann nichts Besseres tun, und ein 400 wuerde eine
Verbindung abweisen, die sonst funktioniert; ein fremder Raum leakt nichts,
weil kein Nachholen stattfindet. Kehrseite, dokumentiert: Ist die ID nur noch
nicht persistiert (batch-writer haengt), wird das Nachholen still
uebersprungen. S7 wartet 10 s, deshalb greift es dort - siehe Annahme unten.

Zwei getrennte Faelle:
- Ohne Last-Event-ID: gar kein DB-Zugriff. Das ist die Garantie, die S5 braucht.
- Mit ID und postgres weg: HTTP 503 vor dem ersten Flush. Reihenfolge:
  Verbindung im Modus "Nachholen" registrieren (live puffert) -> DB synchron
  lesen -> bei Fehler Registrierung entfernen und 503 geben -> sonst
  Nachhol-Ereignisse senden, Puffer abarbeiten, auf live umschalten. Die
  DB-Abfrage laeuft im Controller, bevor der Emitter zurueckgegeben wird,
  deshalb kommt der 503 noch als HTTP-Status an.

Entscheidung HikariCP: Bei gestoppter DB blockiert HikariCP bis zum
connection-timeout (Default 30 s), bevor der 503 moeglich ist. Deshalb wird
spring.datasource.hikari.connection-timeout = 3000 ms gesetzt
(DB_CONNECTION_TIMEOUT_MS), damit der Nachhol-Pfad schnell scheitert. Kein
Szenario erzwingt das; es ist eine bewusste Entscheidung fuer beobachtbares
Verhalten. Der Start braucht die DB ohnehin, daher aendert das kurze Timeout
nichts am Startverhalten.

Puffer waehrend des Nachholens, in Baureihenfolge:
1. Die Verbindung wird vor der DB-Abfrage registriert, Status "Nachholen".
2. Ereignisse, die live eintreffen, werden solange pro Verbindung in einer
   Liste gesammelt, nicht gesendet.
3. Nach der Abfrage wird die Liste unter derselben Sperre wie das Nachholen
   abgearbeitet: Ereignis fuer Ereignis wird geprueft, ob seine id beim
   Nachholen schon gesendet wurde - wenn ja, ueberspringen; wenn nein, senden.
4. Danach wird die Verbindung auf "live" umgeschaltet.

Die Menge der gesendeten IDs wird nach dem Umschalten auf live verworfen:
Live kommt jede Nachricht ueber einen einzelnen Consumer genau einmal an, ein
Abgleich ist dann nicht mehr noetig. Ohne das Verwerfen wuerde die Menge bei
langen Verbindungen unbegrenzt wachsen.

Annahme: Nachholen kann nur liefern, was der batch-writer bereits persistiert
hat. S7 wartet deshalb 10 s zwischen Trennen und Neuverbinden.

### 3.6 Tests (S1)
mvn clean test im Wurzelverzeichnis ist in einem Lauf gruen, inklusive der
beiden Pflichttests mit Testcontainers (Verteilung auf zwei Instanzen,
Nachholen mit Last-Event-ID) gegen echten RabbitMQ- und PostgreSQL-Container,
ohne Mocks fuer Broker oder Datenbank.

### 3.7 Code-Regeln (S8)
Keine Streams, keine Ternary-Ketten, kurze Methoden, deutsche Kommentare ueber
jeder Klasse und jeder Methode sowie an jedem Netzwerk- und
Nebenlaeufigkeitsschritt (CLAUDE.md). .env liegt nicht im Repo (.gitignore).

## 4. Konfiguration (neue Variablen, Schritt 9 fuehrt .env.example nach)

| Variable | Default | Zweck |
|---|---|---|
| LIVE_QUEUE_MAX_LENGTH | 1000 | Gesamtlimit der Live-Queue (ready + unacked); gesetzt wird x-max-length = Wert - LIVE_QUEUE_PREFETCH |
| LIVE_QUEUE_PREFETCH | 10 | In-flight-Nachrichten pro Consumer; Reserve fuer das Gesamtlimit (3.3) |
| RABBITMQ_REQUESTED_HEARTBEAT | 0 | Heartbeats aus, damit beliebig langes Pausieren die exclusive Queue nicht loescht (3.2) |
| DB_CONNECTION_TIMEOUT_MS | 3000 | HikariCP-Timeout, damit Nachholen bei gestoppter DB schnell 503 gibt (3.5) |

## 5. Abnahmekriterien (S1-S8)

Regeln: Alle Szenarien laufen in der vorgegebenen Reihenfolge auf demselben
Stack, gemessen wird als Delta, ohne Aufraeumen dazwischen und ohne Override
(docker compose -f docker-compose.yml ...; auf der Dev-Maschine liegt eine
git-ignorierte docker-compose.override.yml mit veroeffentlichten Ports, die
S2 sonst rot faerbt). Zugangsdaten gemaess .env. Die Instanzen sind im Netz
chat-net einzeln ueber ihre Containernamen erreichbar
(it3b-m321-chat-service-1 und -2). Automatisiert: tools/test-live-s1-s8.ps1.

SSE-Client von innen:

    docker run --rm --network it3b-m321_chat-net curlimages/curl:latest -N "http://it3b-m321-chat-service-1:8080/stream?roomId=<UUID>"

Queue-Pruefung:

    docker compose exec rabbitmq rabbitmqctl list_queues name messages messages_ready messages_unacknowledged consumers

DB-Delta:

    docker compose exec postgres psql -U chat_user -d chat_db -c "SELECT count(*) FROM message"

| Nr | Kriterium | Befehl | Erwartung | Gemessen |
|----|-----------|--------|-----------|----------|
| S1 | Tests gruen in einem Lauf | mvn clean test | BUILD SUCCESS, 0 Fehler, beide Live-Tests dabei | BUILD SUCCESS, chat-service 2 Tests, batch-writer 2 Tests, 0 Fehler |
| S2 | Kein Dienst veroeffentlicht einen Port | docker compose -f docker-compose.yml config \| findstr published; vom Host curl http://localhost:8080/api/health; von innen curl http://it3b-m321-chat-service-1:8080/api/health | keine Treffer; Verbindung abgelehnt; HTTP 200 | 0 published, ps ohne Port-Mapping, auch im frischen Klon |
| S3 | Verteilung auf zwei Instanzen | zwei SSE-Clients auf Raum A (je einer pro Instanz), einer auf Raum B; je 200 POST /messages ueber jede Instanz | jeder A-Client genau 400 data:-Ereignisse, 400 paarweise verschiedene id:, je Absender in Sendereihenfolge; B-Client 0 | a1/a2 je 400 data, 400 verschiedene id, Reihenfolge eins/zwei True, b1 0 |
| S4 | Queue-Lebensdauer | list_queues (s. o.); docker compose -f docker-compose.yml up -d --scale chat-service=1; Pause-Variante: pause eine Instanz fuer 120 s | genau zwei chat.live.* mit consumers=1; nach Scale-down binnen 30 s nur noch eine; chat.persist und chat.dlq bleiben; nach 120 s Pause Queue samt Consumer noch da, nach unpause treffen neue Nachrichten ein | 2 chat.live.* mit consumers=1, nach Scale-down in 10 s nur eine, chat.persist und chat.dlq bleiben |
| S5 | Anzeige ohne DB | docker compose stop postgres batch-writer; 100 POST; SSE-Client zaehlen; docker compose start postgres batch-writer | 100 Ereignisse in <= 5 s (Delta Event-IDs); danach Delta count(message) = +100 binnen 30 s | 100 Ereignisse in 1.4 s, DB-Delta +100 |
| S6 | Rueckstau begrenzt | eine Instanz pausieren, 5000 POST, dabei sekundlich list_queues pollen | messages der pausierten chat.live.* nie > 1000, messages_unacknowledged <= 10; Client der anderen Instanz erhaelt 5000 (Delta); nach unpause kommen neu gesendete (+10) dort an | max 1000 (990 ready + 10 unacked), Instanz 2 = 5000, Instanz 1 1000 dann 1001 |
| S7 | Nachholen | Client empfaengt 1 Nachricht (id X) und trennt; 50 POST; 10 s warten; curl -N -H "Last-Event-ID: X" .../stream?roomId=... | genau 50 Ereignisse, X nicht dabei, keine id doppelt, Reihenfolge nach (created_at, id); danach 1 POST -> 51. Ereignis live auf derselben Verbindung | 50 nachgeholt, Reihenfolge 1..50 True, 50 verschiedene id, danach 51 |
| S8 | Code-Regeln | Suche in chat-service/src nach .stream( und Ternary-Ketten; git ls-files \| findstr /R "^\.env$"; mvn clean test | 0 Treffer; Kommentare ueber jeder Klasse und Methode; ein Lauf gruen | 0 Streams, .env 0 Treffer |

## 6. Fehlerfaelle

### 6.1 Client bricht mitten im Senden ab
emitter.send() wirft IOException, wenn der Client verschwunden ist.
Verhalten: Verbindung aus der StreamVerwaltung entfernen (loggen), Exception
nicht weiterwerfen, mit den uebrigen Verbindungen weitersenden. Weiterwerfen
wuerde beim Listener ein Requeue der Nachricht ausloesen und Duplikate bei
allen anderen Clients erzeugen. onCompletion, onTimeout und onError entfernen
dieselbe Verbindung idempotent. Ohne das Entfernen wuerde die Liste wachsen
und jeder spaetere Versand erneut werfen.

### 6.2 Broker neu gestartet
Nicht-durable, exclusive chat.live.*-Queues und ihre Bindings sind nach einem
Broker-Neustart weg. Spring AMQP verbindet sich automatisch wieder, und die
RabbitAdmin deklariert Queue und Binding beim Wiederverbinden neu (gleicher
Name, da das System-Property pro Instanz fix ist). Live-Ereignisse, die
waehrend des Broker-Ausfalls publiziert wurden, sind fuer live verloren
(Fanout ohne Queue speichert nichts); die Luecke schliesst der Client per
Last-Event-ID aus der Datenbank. chat.persist ist durable: Nachrichten, die
vor dem Neustart publiziert wurden, bleiben erhalten.

### 6.3 Ungueltige oder fehlende roomId
HTTP 400 durch Spring (@RequestParam UUID, siehe 2.3), bevor der Stream
oeffnet. Kein eigener Pruefcode.

### 6.4 Heartbeat-Timeout
Tritt per Entscheidung (3.2, requested-heartbeat = 0) nicht auf. Die
Kehrseite - eine ohne FIN still gestorbene Verbindung bleibt am Broker
haengen, bis der Client-Prozess endet - ist dort dokumentiert.

## 7. Verworfene Loesungswege

### 7.1 rabbitmqctl get chat.persist
Existiert im Image rabbitmq:3.12-management nicht ("Command 'get' not found",
gemessen 2026-11-12). Payload-Zugriff geht nur ueber die Management-HTTP-API
(2.2). Der Auftrag in Schritt 1 des Umsetzungsplans ist entsprechend per
Umplanungs-Eintrag korrigiert.

### 7.2 Topic-Exchange fuer die Live-Zustellung
PLANUNG.md sah chat.exchange als Topic vor. Fuer diesen Auftrag verworfen:
ein Ereignistyp, jede Instanz braucht jede Nachricht, kein Routing-Bedarf
(2.1).

### 7.3 x-max-length = 1000 ohne Reserve
Verworfen: Mit prefetch 10 koennten bis zu 1010 Nachrichten in der Spalte
messages sichtbar sein, obwohl das Limit greift - S6 fiele durch (3.3).

### 7.4 Unbekannte Last-Event-ID mit HTTP 400 beantworten
Verworfen: wuerde eine Verbindung abweisen, die sonst funktioniert; der Client
kann mit einem Fehler nichts anfangen (3.5).

### 7.5 requested-heartbeat = 300 s
Verworfen: Der Broker schloesse nach etwa zwei verpassten Intervallen
(~600 s), und die Pause-Laenge des Pruefers ist unbekannt (3.2).
