# Umsetzungsplan: Live-Zustellung (Bewertung 2)

Regeln: ein Schritt = ein Commit, Message auf Deutsch, KI-Commits mit Co-Authored-By.
Nach jedem Schritt wird "Geprueft und korrigiert" ausgefuellt. Der bestehende Text
bleibt stehen; Umplanungen kommen als eigener Eintrag mit Aenderungsgrund dazu.
Codestil fuer jeden Auftrag: CLAUDE.md (eine Anweisung pro Zeile, keine Streams,
keine Ternary-Ketten, kurze Methoden, deutsche Kommentare ueber jeder Methode und
an jedem Netzwerk- und Nebenlaeufigkeitsschritt).

## Schritt 0: Modul und Compose-Service auf chat-service umbenennen
Ziel: Verzeichnis chat-service/ statt backend/, Compose-Service chat-service.
Warum jetzt: Das Pruefskript faehrt --scale chat-service=2, docker compose pause auf
eine chat-service-Instanz und prueft den Quelltext von chat-service/. Mit dem
bisherigen Namen backend scheitern S3, S4, S6 und S8 vor dem ersten Inhalt.
Auftrag an die KI im Wortlaut:
"Benenne das Maven-Modulverzeichnis backend/ in chat-service/ um und den
Compose-Service backend in chat-service. Passe an: modules im Root-POM, die
Dockerfile-Pfade und den Build-Context in docker-compose.yml, das Dockerfile selbst
und alle Pfadangaben in tools/. Aendere keine Fachlogik und keine Queues. Liste mir
jede geaenderte Datei mit dem Grund der Aenderung."
Test: mvn clean test gruen; docker compose up -d --build; docker compose ps zeigt
chat-service ohne Port-Mapping; docker compose up -d --scale chat-service=2 startet
zwei Instanzen.
Geprueft und korrigiert: Resttreffer-Suche lieferte nur den Beschreibungstext "Chat-Backend" im Modul-POM, kein Pfad- oder Serviceverweis. Alten Container it3b-m321-backend-1 mit down --remove-orphans entfernt. Die lokale, git-ignorierte Override-Datei musste mit umbenannt werden, sonst merged Compose einen zweiten Service backend mit veroeffentlichten Ports. --scale 2 und zurueck auf 1 selbst gefahren.

## Schritt 1: Wire-Format feststellen und Fanout-Exchange anbinden
Ziel: Exchange chat.messages (fanout, durable) existiert, der chat-service
publiziert jedes MessageEvent zusaetzlich dorthin, chat.persist bleibt unveraendert.
Warum jetzt: Der Exchange ist die Basis fuer jede Instanz-Queue. Das tatsaechliche
Format auf der Leitung muss vorher feststehen, weil data: am Stream laut Auftrag
dasselbe Format tragen muss wie chat.persist.
Auftrag an die KI im Wortlaut:
"Zeige mir zuerst, welches Payload-Format der chat-service heute auf chat.persist
legt: lese eine Nachricht mit docker compose exec rabbitmq rabbitmqctl get
chat.persist und pruefe, ob der RabbitTemplate den Jackson2JsonMessageConverter
benutzt oder der Standard-Serializer greift. Setze den Converter explizit auf dem
RabbitTemplate, damit JSON mit content_type application/json entsteht. Deklariere
danach in RabbitConfig einen FanoutExchange-Bean chat.messages (durable) und sende
im MessageController nach dem bestehenden Versand auf chat.persist dasselbe
MessageEvent an diesen Exchange mit leerem Routing-Key. Aendere die
Persistenz-Strecke nicht."
Test: rabbitmqctl list_exchanges zeigt chat.messages als fanout; eine ueber POST
gesendete Nachricht liegt als JSON auf chat.persist; batch-writer schreibt sie
weiter in die Tabelle message.
Geprueft und korrigiert: Der Exchange erschien nach dem Start nicht in list_exchanges: RabbitAdmin deklariert erst bei der ersten Broker-Verbindung, ohne Listener ist das der erste POST. Der erste POST scheiterte zusaetzlich, weil die Anwendung noch nicht bereit war. Nach Warten auf "Started ChatApplication" HTTP 202. Gemessen: REST-Antwort zeigt createdAt als ISO-String, auf der Leitung liegt eine Epoch-Dezimalzahl.

## Schritt 2: Queue pro Instanz mit Binding und Lebensdauer
Ziel: Jede Instanz legt chat.live.<Kennung> an: nicht durable, exclusive,
auto-delete, gebunden an chat.messages.
Warum jetzt: Erst damit kommt eine Kopie in der Instanz an. Die Lebensdauer loest
S4, bevor irgendein Stream existiert, und ist isoliert messbar.
Auftrag an die KI im Wortlaut:
"Erzeuge in ChatApplication.main eine Instanz-Kennung mit UUID.randomUUID() und
setze sie als System-Property live.queue.name mit dem Wert chat.live.<Kennung>.
Deklariere in RabbitConfig eine Queue mit diesem Namen: durable=false,
exclusive=true, autoDelete=true, und ein Binding an chat.messages. Erklaere in
Kommentaren, warum exclusive und autoDelete die Queue entfernen, wenn die Instanz
beendet wird, und warum sie beim Pausieren der Instanz bestehen bleibt."
Test: zwei Instanzen starten; docker compose exec rabbitmq rabbitmqctl list_queues
name consumers zeigt zwei chat.live-Queues mit je einem Consumer; nach --scale
chat-service=1 ist binnen 30 s nur noch eine da, chat.persist und chat.dlq bleiben.
Geprueft und korrigiert: Zwei chat.live-Queues mit je einem Consumer, beide an chat.messages gebunden, Kopie in beiden Instanz-Logs mit derselben ID. Scale-down entfernte eine Queue in unter 10 s, chat.persist und chat.dlq blieben. Gegen den urspruenglichen Auftrag geaendert: Kennung als Default-Property statt System-Property, weil eine System-Property pro JVM gilt und der Test zwei Kontexte in einem JVM startet.

## Schritt 3: SSE-Endpunkt mit Raum-Verwaltung
Ziel: GET /stream?roomId=... liefert text/event-stream und haelt die Verbindung.
Warum jetzt: Der Endpunkt ist ein fester Punkt des Pruefskripts. Er wird ohne
Fachlogik gebaut, damit der Vertrag zuerst steht.
Auftrag an die KI im Wortlaut:
"Baue einen StreamController mit GET /stream?roomId=..., der einen SseEmitter ohne
Timeout zurueckgibt. Lege eine Klasse StreamVerwaltung an, die die Verbindungen pro
Raum in einer HashMap haelt und synchronized-Methoden fuer Hinzufuegen, Entfernen
und Senden anbietet. Registriere onCompletion, onTimeout und onError, damit eine
beendete Verbindung aus der Liste verschwindet. Kommentiere jeden Schritt, der mit
Threads zu tun hat, auf Deutsch. Keine Streams, keine Lambda-Ketten."
Test: von innen im Netz chat-net liefert curl -N -D - den Content-Type
text/event-stream und die Verbindung bleibt offen.
Geprueft und korrigiert: Content-Type text/event-stream, :connected sofort sichtbar, curl-Exit 28 nach 5 s als Beweis fuer offenes Streaming. Beide 400er erzeugt Spring selbst. Korrigiert: entferne() protokollierte den Abbau nicht, Logzeile ergaenzt. Der Abbau-Pfad ist hier nicht ausloesbar, weil ohne Sender kein Schreibversuch stattfindet.

## Schritt 4: Von der Instanz-Queue zum Stream
Ziel: Die Instanz liest ihre Queue und sendet id: und data: an die Clients des Raums.
Warum jetzt: Damit ist der Anzeigeweg durchgaengig und S3 erstmals messbar.
Auftrag an die KI im Wortlaut:
"Ergaenze einen Listener auf ${live.queue.name}, der die rohe AMQP-Nachricht
entgegennimmt. Parse sie zu MessageEvent, bilde den JSON-Text fuer data: unveraendert
aus dem Nachrichten-Body und setze die Nachrichten-ID als id:. Sende nur an die
Verbindungen, deren roomId gleich der roomId der Nachricht ist. Setze die
Consumer-Anzahl auf 1 und sende in der Reihenfolge des Eingangs. Begruende im
Kommentar, warum der Body unveraendert weitergereicht wird und warum ein einzelner
Consumer die Reihenfolge eines Absenders haelt."
Test: zwei Instanzen, je ein Client auf Raum A, ein dritter auf Raum B, je 200
Nachrichten fuer Raum A ueber jede Instanz: beide A-Clients erhalten 400, jede genau
einmal und je Absender in Reihenfolge, der B-Client keine.
Geprueft und korrigiert: Drei Clients gemessen: je 20 data und 20 verschiedene IDs an beiden Instanzen, 0 im fremden Raum. Abbau erst nach mehreren Schreibversuchen sichtbar; ein hart entfernter Container sendet kein FIN, dann bleibt die Verbindung bis zum TCP-Timeout. Der Tomcat-ERROR "Broken pipe" kommt aus dem Async-Error-Dispatch, nicht aus unserem Pfad: unser catch laeuft im Consumer-Thread und wirft nicht weiter.

## Schritt 5: Rueckstau begrenzen
Ziel: Die Live-Queue haelt nie mehr als 1000 Nachrichten.
Warum jetzt: Eine haengende Instanz darf den Broker nicht fuellen (S6). Die Grenze
wird gesetzt, bevor Last erzeugt wird.
Auftrag an die KI im Wortlaut:
"Setze an der Live-Queue die Argumente x-max-length=1000 und x-overflow=drop-head.
Mache die Grenze ueber die Umgebungsvariable LIVE_QUEUE_MAX_LENGTH konfigurierbar
mit Default 1000. Erklaere im Kommentar, welche Nachricht bei Ueberlauf verworfen
wird, warum die anderen Instanzen davon nicht betroffen sind und wie eine verworfene
Nachricht spaeter ueber das Nachholen aus der Datenbank zurueckkommt."
Test: eine von zwei Instanzen pausieren, 5000 Nachrichten senden, dabei die
Queue-Laenge pollen: nie mehr als 1000; der Client der anderen Instanz erhaelt alle
5000; nach dem Fortsetzen stellt die pausierte Instanz neue Nachrichten wieder zu.
Geprueft und korrigiert: Argumente am laufenden Stack geprueft: x-max-length 990, x-overflow drop-head. --force-recreate war noetig, weil eine bestehende Queue mit anderen Argumenten PRECONDITION_FAILED liefert. Unter Last gemessen: 990 ready + 10 unacknowledged = 1000, nie darueber. Damit ist die Reserve belegt, nicht nur begruendet.

## Schritt 6: Unabhaengigkeit von Datenbank und batch-writer
Ziel: Der Anzeigeweg funktioniert, wenn postgres und batch-writer gestoppt sind.
Warum jetzt: S5 misst genau das. Der Pfad muss frei von Datenbankzugriffen sein,
bevor das Nachholen in Schritt 7 wieder eine Datenbankabfrage einfuehrt.
Auftrag an die KI im Wortlaut:
"Pruefe den Pfad POST -> Exchange -> Instanz-Queue -> SSE auf Datenbankzugriffe und
entferne sie aus diesem Pfad. Der Stream und der Versand duerfen keine JPA-Abfrage
ausloesen und nicht auf den batch-writer warten. Aendere nichts an der
Persistenz-Strecke. Nenne mir jede Stelle, die du geprueft hast."
Test: postgres und batch-writer stoppen, 100 Nachrichten senden, alle 100 erscheinen
in hoechstens 5 s beim Client; nach dem Neustart beider Dienste stehen alle 100 in
der Tabelle message.
Geprueft und korrigiert: Mit gestopptem postgres und batch-writer 100 Nachrichten gesendet: 100 Ereignisse in 1.4 s beim Client, HTTP_OK=100. Nach Neustart beider Dienste DB-Delta +100. Der Pfad POST -> Exchange -> Queue -> SSE beruehrt die Datenbank nicht.

## Schritt 7: Nachholen mit Last-Event-ID
Ziel: Eine Neuverbindung erhaelt genau die fehlenden Nachrichten in Reihenfolge,
keine doppelt, danach wieder live.
Warum jetzt: S7 baut auf Queue, Stream und Datenbank auf und ist der letzte
fachliche Baustein.
Auftrag an die KI im Wortlaut:
"Lies den Header Last-Event-ID. Bestimme aus der Tabelle message den created_at der
letzten ID und hole alle Nachrichten des Raums mit created_at danach, geordnet nach
created_at und id, per JdbcTemplate.queryForList und einer for-Schleife. Serialisiere
sie mit demselben Converter wie der Versand, damit das Format identisch zu
chat.persist bleibt. Puffere waehrend des Nachholens live ankommende Ereignisse pro
Verbindung und ueberspringe beim Ausspielen bereits gesendete IDs. Schuetze den
Uebergang von Nachholen zu live mit derselben Sperre wie das Senden. Kommentiere
die Nebenlaeufigkeit und den Umgang mit gleichem Zeitstempel."
Test: Client trennt nach einer Nachricht, 50 Nachrichten senden, nach 10 s mit
Last-Event-ID neu verbinden: genau die 50 fehlenden, in ihrer Reihenfolge, keine
doppelt, danach wieder live.
Geprueft und korrigiert: Client nach einer Nachricht sauber getrennt, 50 gesendet, 12 s gewartet, mit Last-Event-ID neu verbunden: genau 50, Reihenfolge 1..50 eingehalten, 50 verschiedene IDs, die erste ID nicht dabei. Danach eine weitere Nachricht: 51. Ereignis live auf derselben Verbindung.

## Schritt 8: Pflichttests
Ziel: Ein Test fuer die Verteilung an zwei Instanzen (S3) und einer fuer die
Neuverbindung (S7), beide mit echtem RabbitMQ.
Warum jetzt: D verlangt beide Tests und einen gruenen Lauf in einem Rutsch.
Auftrag an die KI im Wortlaut:
"Ergaenze in chat-service/pom.xml die Testcontainers-Abhaengigkeiten junit-jupiter,
rabbitmq und postgresql in der vom Root-POM verwalteten Version. Schreibe unter
chat-service/src/test/ einen Test, der zwei Anwendungskontexte mit unterschiedlicher
live.queue.name gegen einen echten RabbitMQ-Container startet und prueft, dass beide
jede Nachricht genau einmal erhalten und ein Client eines fremden Raums keine.
Schreibe einen zweiten Test, der nach einem Abbruch mit Last-Event-ID genau die
fehlenden Nachrichten liefert. Verwende keine Mocks fuer Broker oder Datenbank."
Test: mvn clean test im Wurzelverzeichnis, ein Lauf, alles gruen.
Geprueft und korrigiert: Erster Lauf scheiterte mit UnknownHostException: postgres - die Test-Werte lagen als Default-Properties unter application.yml. Umgestellt auf Kommandozeilen-Argumente pro Kontext. Zweiter Lauf: die Reihenfolgen-Assertion verlangte eine globale Ordnung ueber beide Instanzen und schlug fehl, obwohl keine Nachricht fehlte oder doppelt kam. RabbitMQ ordnet pro Verbindung, deshalb prueft der Test jetzt die Teilfolge pro Absender. Beide Tests gruen.

## Schritt 9: Nachfuehren, messen, Abgabe
Ziel: .env.example, README-Stand, Messskript, Tag bewertung-2.
Warum jetzt: Der Stack muss aus dem Fork heraus laufen, mit .env.example als
einziger Vorlage.
Auftrag an die KI im Wortlaut:
"Ergaenze alle neuen Variablen in .env.example, fuehre die README-Tabelle Stand nach
und schreibe tools/test-live-s1-s8.ps1. Das Skript misst die Szenarien S2 bis S8 in
der vorgegebenen Reihenfolge auf demselben Stack, ohne Aufraeumen dazwischen, ohne
Override-Datei und von innen im Netz chat-net, mit Delta-Messungen. Es verbindet
sich mit jeder chat-service-Instanz einzeln."
Test: frischer Klon auf dem Tag, .env aus .env.example, mvn clean test gruen, alle
acht Szenarien gemessen und protokolliert.
Geprueft und korrigiert: Frischer Klon brach zuerst ab: SseSammler lag in src/main und war nicht im Repo, lokal gruen weil Main-Klassen im Test-Classpath liegen (14 vs 13 Quelldateien). Nach src/test verschoben und committet; -DskipTests kompiliert Tests trotzdem. Danach Klon gruen, keine published-Treffer, beide Instanzen ohne Port-Mapping. Messausgaben tools/*.txt per .gitignore ausgeschlossen.

## Umplanung 2026-11-12 (vor Umsetzung)

Grund: Befunde der Spec-Durchsicht und Messungen am laufenden Stack. Der
Bestand oben bleibt unveraendert stehen; die folgenden Eintraege gehen den
Auftraegen vor. Einzelheiten und Begruendungen: docs/spec-live-zustellung.md.

- Schritt 1: rabbitmqctl get chat.persist existiert im Image
  rabbitmq:3.12-management nicht ("Command 'get' not found", gemessen
  2026-11-12). Ersatz: Payload ueber die Management-HTTP-API aus einem
  curlimages/curl-Container im chat-net mit ackmode ack_requeue_true lesen
  (Spec 2.2 und 7.1). Das Wire-Format ist gemessen: createdAt ist auf der
  Leitung eine Epoch-Dezimalzahl, die ISO-Anzeige der REST-Antwort ist nicht
  die Referenz fuer data: (Spec 2.2).
- Schritt 2: zusaetzlich spring.rabbitmq.requested-heartbeat = 0 setzen
  (Umgebungsvariable RABBITMQ_REQUESTED_HEARTBEAT, Default 0). Grund: Eine
  pausierte Instanz sendet keine Heartbeats; mit dem Default von 60 s wuerde
  der Broker die exclusive Queue waehrend einer beliebig langen Pause
  loeschen. S4 funktioniert weiter ueber den FIN beim Container-Stop
  (Spec 3.2, 7.5).
- Schritt 3: roomId als @RequestParam UUID deklarieren - die beiden 400er
  (fehlend, ungueltig) erzeugt Spring selbst, vor dem Oeffnen des Streams.
  Direkt nach der Registrierung den SSE-Kommentar :connected senden.
  SseEmitter mit Timeout 0 (Spec 2.3).
- Schritt 3/4: IOException beim emitter.send() behandeln: Verbindung aus der
  StreamVerwaltung entfernen, nicht weiterwerfen (sonst Requeue und
  Duplikate); onCompletion/onTimeout/onError raeumen idempotent auf
  (Spec 6.1).
- Schritt 5: Prefetch 10 (LIVE_QUEUE_PREFETCH) und x-max-length =
  LIVE_QUEUE_MAX_LENGTH - LIVE_QUEUE_PREFETCH = 990 statt glatt 1000. Grund:
  list_queues messages zaehlt ready plus unacknowledged; eine pausierte
  Instanz haelt bis zu prefetch Nachrichten in flight. Nur mit der Reserve
  bleibt ready + unacked <= 1000 (Spec 3.3, 7.3). Test ergaenzen: waehrend
  eine Instanz pausiert, sekundlich
  rabbitmqctl list_queues name messages messages_ready messages_unacknowledged
  consumers pollen - Erwartung messages <= 1000 und unacknowledged <= 10.
  Diese Messung bestaetigt die Reserve-Entscheidung, die vor Schritt 2 nicht
  messbar war.
- Schritt 7: Nachholen ueber lexikografischen Paarvergleich
  (created_at, id) > (last_created_at, last_id) statt striktem created_at >;
  unbekannte Last-Event-ID behandeln wie keine ID (live, geloggt); mit ID und
  gestoppter DB: 503 vor dem ersten Flush, Reihenfolge registrieren (live
  puffert) -> DB lesen -> bei Fehler entfernen und 503;
  spring.datasource.hikari.connection-timeout = 3000 ms
  (DB_CONNECTION_TIMEOUT_MS), damit das 503 prompt kommt statt nach dem
  30-s-HikariCP-Default; Menge der gesendeten IDs nach dem Umschalten auf
  live verwerfen (Spec 3.5).
- Schritt 9: Alle Messungen mit docker compose -f docker-compose.yml (ohne
  Override). Grund: Auf der Dev-Maschine liegt eine git-ignorierte
  docker-compose.override.yml, die backend:8080 und rabbitmq:15672 auf
  127.0.0.1 published und S2 rot faerben wuerde. Im Messskript die Instanzen
  einzeln ueber ihre Containernamen ansprechen
  (it3b-m321-chat-service-1 und -2), SSE-Clients von innen per
  docker run --rm --network it3b-m321_chat-net curlimages/curl:latest -N
  "http://it3b-m321-chat-service-1:8080/stream?roomId=<UUID>" (Spec 5).

  ## Umplanung 2026-11-13 (vor Umsetzung, zweite Durchsicht)

Grund: Konstruktionsfehler und Bestandsbefunde aus der zweiten Spec-Durchsicht.
Der Bestand oben bleibt stehen; diese Eintraege gehen den Auftraegen vor.

- Schritt 2 und 8: Die Instanz-Kennung wird nicht als System-Property gesetzt,
  sondern als Default-Property der Anwendung bzw. im Bean erzeugt, wenn
  live.queue.name leer ist. Grund: Eine System-Property gilt pro JVM. Der
  Pflichttest startet zwei Kontexte in einem JVM und wuerde sich die Kennung
  teilen; ausserdem steht eine System-Property in der Spring-Precedence ueber
  setDefaultProperties und liesse sich im Test nicht sauber ueberschreiben
  (Spec 2.4).
- Schritt 2: Fuer den Live-Listener missing-queues-fatal = false setzen. Grund:
  Nach einem Broker-Neustart ist die exclusive Queue zunaechst weg; mit dem
  Default true stoppt der Container nach drei Fehlversuchen, statt die
  Neu-Deklaration durch RabbitAdmin abzuwarten (Spec 6.2).
- Schritt 1: Die bestehende Ternary-Auswahl fuer sender im MessageController
  durch if/else ersetzen. Grund: S8 prueft den gesamten chat-service-Quelltext
  gegen CLAUDE.md; der Bestand enthaelt eine Ternary-Kette (Spec 3.7).

## Umplanung 2026-11-13 (zweite Durchsicht und Umsetzung)

Grund: Konstruktionsfehler aus der Spec-Durchsicht und Befunde aus den Laeufen. Der Bestand oben bleibt unveraendert stehen; diese Eintraege gehen den Auftraegen vor.

- Schritt 2 und 8: Instanz-Kennung als Default-Property der Anwendung statt als System-Property. Grund: Eine System-Property gilt pro JVM; der Pflichttest startet zwei Kontexte in einem JVM und wuerde sich die Kennung teilen.
- Schritt 2: missing-queues-fatal = false fuer den Live-Listener. Grund: Nach einem Broker-Neustart ist die exclusive Queue zunaechst weg; mit dem Default true stoppt der Container nach drei Fehlversuchen statt die Neu-Deklaration abzuwarten (Spec 6.2).
- Schritt 1: bestehende Ternary-Auswahl fuer sender durch if/else ersetzt. Grund: S8 prueft den gesamten chat-service-Quelltext gegen CLAUDE.md (Spec 3.7).
- Schritt 4 und 8: geprueft wird die Reihenfolge pro Absender, nicht eine globale Reihenfolge. Grund: RabbitMQ ordnet pro Verbindung, die zwei Instanzen publishen ueber zwei Verbindungen. Der erste Test verlangte global und scheiterte, obwohl keine Nachricht fehlte oder doppelt kam (Spec 3.1).
- Schritt 8: Test-Werte als Kommandozeilen-Argumente pro Kontext. Grund: Default-Properties stehen unter application.yml, der Kontext wollte zu postgres verbinden (UnknownHostException). System-Properties waeren hoch genug, gelten aber pro JVM.
- Schritt 8 und 9: SseSammler gehoert nach chat-service/src/test/. Grund: Abgabe 4 verlangt Tests dort; in src/main landet der Helfer im Produktions-Jar und war zudem nicht im Repo, der frische Klon brach ab.
- Schritt 5 und 9: Messbefehle korrigiert. Eine Instanz anhalten geht nur mit docker pause <container-name>; docker compose pause erwartet den Service und traefe beide. Der Sender braucht die laufende Instanz als Ziel und curl -m, sonst blockieren Requests an der eingefrorenen Instanz und man misst Blockaden statt Rueckstau. Bei geaenderten Queue-Argumenten --force-recreate, sonst PRECONDITION_FAILED. SSE-Ausgabe in eine Datei, nicht auf die Konsole.
