# Umsetzungsplan: batch-writer

Kleine Schritte in Bau-Reihenfolge, jeder mit Test. Die Hashes verweisen auf die
tatsaechliche Historie (`git log`), damit Plan und Repo uebereinstimmen.

## Schritt 0: Vorgaben klaeren
Warum zuerst: PLANUNG.md 3.7 liefert Tabelle und Queues, CLAUDE.md den Codestil.
Test: Vertrag und Regeln gelesen, Architekturskizze vorhanden.
Commits: ae4b7b1, 2c458f4, 266c827, f3e0a8d, b2ab50a, d7d594c, 0793dda

## Schritt 1: Grundgeruest des Dienstes
Warum: Ohne Maven-Modul, Dockerfile und Compose-Service ist nichts messbar.
Inhalt: Modul im Eltern-POM, Dockerfile, Compose-Service ohne Ports und ohne
container_name, Queues, Entity, Repository, Consumer.
Test: `docker compose build batch-writer`, Dienst startet und verbindet sich.
Commit: 80ce6bc

## Schritt 2: Pflichttests aufsetzen
Warum: S1 verlangt gruene Tests, D verlangt je einen Test fuer S5 und S7.
Inhalt: Integrationstest mit Testcontainers (echte Queue, echte DB).
Test: `mvn clean test`.
Commit: 714b06a (Merge b6da583)

## Schritt 3: Pflicht-Variablen bereitstellen
Warum: Das Pruefskript liest POSTGRES_USER, POSTGRES_PASSWORD, POSTGRES_DB aus .env.
Test: `cp .env.example .env` und Stack startet ohne fehlende Variablen.
Commit: 06b5e2b

## Schritt 4: Ausfalltest und Code-Regeln schaerfen
Warum: S7 muss nicht nur "gruen" sein, sondern die Nachricht wirklich retten.
Inhalt: S7-Test mit gestoppter DB, S5-Assert auf genau eine Zeile, Javadoc (S8).
Test: `mvn clean test`, beide Tests laufen gegen echte Container.
Commit: 02573bb

## Schritt 5: Schema-Variante geprueft und verworfen
Warum: Spalten muessen zum Vertrag des chat-service passen, nicht nur zu PLANUNG.md.
Inhalt: Versuch mit sender_id/sender_name/sent_at, danach Rueckbau auf sender/created_at.
Test: Nachricht durch POST /messages senden und Zeile in message pruefen.
Commits: 90fcbfd (Variante), ba15800 (Korrektur)

## Schritt 6: Batch-Empfang und Testlauf reparieren
Warum: S4 verlangt wenige Transaktionen, S1 muss auf jeder Maschine gruen sein.
Inhalt: Testcontainers-Upgrade, Listener-Factory (consumerBatchEnabled statt
setBatchListener), Bean-Name der Factory, @RabbitListener ohne Fremdnamen.
Test: `mvn clean test`; 1000 Nachrichten ergeben 10 Batches.
Commit: 0a82cc7 (gebündelt, siehe Abweichungen)

## Schritt 7: Keine Ports veroeffentlichen
Warum: S2 verbietet veroeffentlichte Ports; Messung erfolgt von innen.
Test: `docker compose config` enthaelt kein "published".
Commits: 9d53960, 4a9a45e

## Schritt 8: Docker-API-Version fuer Tests festlegen
Warum: docker-java sendet API 1.32, neue Engines verlangen mindestens 1.40.
Test: `mvn clean test` ohne lokale Umgebungsvariablen.
Commit: 87565fa

## Schritt 9: Queue-Deklaration an den Producer angleichen
Warum: Abweichende Queue-Argumente fuehren zu PRECONDITION_FAILED (406),
schliessen den Producer-Channel und kosten Nachrichten (S3).
Test: 1000 Nachrichten von innen senden, HTTP_OK=1000, HTTP_FAIL=0.
Commit: 47a3df4

## Schritt 10: Konfiguration korrigieren
Warum: `${BATCH_SIZE:-100}` ist bash-Syntax; Spring wuerde -100 lesen und den
Batch-Modus abschalten. Kommentare ohne Zeichensalat.
Test: `mvn clean test` und `tools/test-s1-s8.ps1` (S4 bleibt bei 10 Batches).
Commit: folgt

## Schritt 11: README-Stand und Spezifikation nachfuehren
Warum: PDF 03 verlangt README-Tabelle «Stand» und eine eigenstaendige Spezifikation.
Test: Gegenlesen gegen Code und Messergebnisse.
Commit: folgt

## Schritt 12: Abgabe markieren
Warum: Bewertet wird der Stand mit Tag bewertung-1 im Fork.
Test: `git log --oneline -1` und Tag zeigen auf denselben Commit.
Commit: Tag bewertung-1

## Abweichungen vom Ideal
- 0a82cc7 buendelt mehrere Themen (Testcontainers, Factory, Listener). Kuenftig
  gilt: ein Thema pro Commit.
- 9d53960 und 4a9a45e behandeln dasselbe Thema; der erste Commit hatte die Ports
  nur teilweise entfernt, der zweite stellt den Endzustand her.
- Die lokale Datei docker-compose.override.yml veroeffentlicht Ports fuer die
  Entwicklung. Sie ist nicht im Repo; geprueft wird mit `-f docker-compose.yml`.