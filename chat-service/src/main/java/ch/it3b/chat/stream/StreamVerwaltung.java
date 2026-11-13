package ch.it3b.chat.stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Verwaltet die offenen Stream-Verbindungen pro Raum.
 * Der RabbitMQ-Consumer und die HTTP-Threads greifen gleichzeitig darauf zu,
 * deshalb ist jede oeffentliche Methode synchronized: die HashMap ist nicht
 * threadsicher. Die Datenbankabfrage beim Nachholen laeuft bewusst ausserhalb
 * dieser Sperre, damit ein langsamer Nachholvorgang die anderen Raeume nicht
 * blockiert; waehrenddessen puffert die Verbindung live ankommende Ereignisse.
 */
@Component
public class StreamVerwaltung {

    private static final Logger LOG = LoggerFactory.getLogger(StreamVerwaltung.class);

    /** Raum-ID auf die Liste der offenen Verbindungen in diesem Raum. */
    private final Map<UUID, List<StreamVerbindung>> verbindungenProRaum = new HashMap<>();

    /**
     * Registriert eine Verbindung ohne Nachholen und bestaetigt sie sofort.
     * Dieser Weg beruehrt die Datenbank nicht, das ist die Garantie fuer S5.
     *
     * @param raumId Der Raum, den der Client hoeren will
     * @return Der Emitter, den der Controller zurueckgibt
     * @throws IOException Wenn der Client schon wieder weg ist
     */
    public synchronized SseEmitter registriereLive(UUID raumId) throws IOException {
        StreamVerbindung verbindung = neueVerbindung(raumId, false);
        sendeKommentar(verbindung);
        LOG.info("Stream geoeffnet fuer Raum {}", raumId);
        return verbindung.getEmitter();
    }

    /**
     * Registriert eine Verbindung im Zustand Nachholen.
     * Es wird noch nichts geschrieben, damit der Controller bei einer fehlenden
     * Datenbank noch HTTP 503 antworten kann, bevor der Stream beginnt.
     *
     * @param raumId Der Raum, den der Client hoeren will
     * @return Die Verbindung fuer den Nachholvorgang
     */
    public synchronized StreamVerbindung registriereZumNachholen(UUID raumId) {
        StreamVerbindung verbindung = neueVerbindung(raumId, true);
        LOG.info("Stream geoeffnet fuer Raum {}, Nachholen beginnt", raumId);
        return verbindung;
    }

    /**
     * Spielt die fehlenden Nachrichten aus und schaltet danach auf live.
     * Reihenfolge: Kommentar, Nachhol-Ereignisse, Puffer ohne schon gesendete IDs,
     * Zustand live. Alles unter derselben Sperre, damit kein live Ereignis
     * zwischen Nachholen und Umschalten verloren geht oder doppelt kommt.
     *
     * @param verbindung Die Verbindung, die nachholt
     * @param fehlende Die Ereignisse aus der Datenbank, in Reihenfolge
     * @throws IOException Wenn der Client waehrenddessen verschwindet
     */
    public synchronized void spieleNach(StreamVerbindung verbindung, List<LiveEreignis> fehlende) throws IOException {
        try {
            sendeKommentar(verbindung);

            for (LiveEreignis ereignis : fehlende) {
                sendeEreignis(verbindung, ereignis);
                verbindung.merkeGesendet(ereignis.getNachrichtenId());
            }

            for (LiveEreignis ereignis : verbindung.getPuffer()) {
                if (!verbindung.wurdeGesendet(ereignis.getNachrichtenId())) {
                    sendeEreignis(verbindung, ereignis);
                }
            }

            verbindung.leerePuffer();
            verbindung.setHoltNach(false);
            verbindung.vergesseGesendeteIds();
            LOG.info("Nachholen beendet fuer Raum {}, {} Ereignisse", verbindung.getRaumId(), fehlende.size());
        } catch (IOException | IllegalStateException fehler) {
            entferne(verbindung);
            throw new IOException("Client waehrend des Nachholens weg", fehler);
        }
    }

    /**
     * Entfernt eine Verbindung aus ihrer Raumliste.
     * Idempotent, weil Completion, Timeout und Error denselben Abbau ausloesen
     * koennen. Ohne das Entfernen wuerde die Liste wachsen und jeder weitere
     * Versand erneut fehlschlagen.
     *
     * @param verbindung Die Verbindung, die weg ist
     */
    public synchronized void entferne(StreamVerbindung verbindung) {
        UUID raumId = verbindung.getRaumId();
        List<StreamVerbindung> verbindungen = verbindungenProRaum.get(raumId);
        if (verbindungen == null) {
            return;
        }
        boolean warEntfernt = verbindungen.remove(verbindung);
        if (verbindungen.isEmpty()) {
            verbindungenProRaum.remove(raumId);
        }
        if (warEntfernt) {
            LOG.info("Stream geschlossen fuer Raum {}", raumId);
        }
    }

    /**
     * Schreibt ein Ereignis an alle Verbindungen eines Raums.
     * Verbindungen im Zustand Nachholen bekommen es in ihren Puffer. Der JSON-Text
     * wird unveraendert als Text geschrieben, nicht als Objekt: mit
     * APPLICATION_JSON wuerde Jackson den String noch einmal serialisieren und in
     * Anfuehrungszeichen setzen. Eine gescheiterte Verbindung wird eingesammelt und
     * danach entfernt, die Exception nicht weitergeworfen, damit der Consumer
     * keine Nachricht zurueck in die Queue legt.
     *
     * @param raumId Der Raum der Nachricht
     * @param nachrichtenId Die Nachrichten-ID fuer die id:-Zeile
     * @param jsonText Der unveraenderte JSON-Text aus der Queue
     */
    public synchronized void sendeAnRaum(UUID raumId, String nachrichtenId, String jsonText) {
        List<StreamVerbindung> verbindungen = verbindungenProRaum.get(raumId);
        if (verbindungen == null) {
            return;
        }

        LiveEreignis ereignis = new LiveEreignis(nachrichtenId, jsonText);
        List<StreamVerbindung> verloreneVerbindungen = new ArrayList<>();

        for (StreamVerbindung verbindung : verbindungen) {
            if (verbindung.istNachholen()) {
                verbindung.puffere(ereignis);
                continue;
            }
            try {
                sendeEreignis(verbindung, ereignis);
            } catch (IOException | IllegalStateException fehler) {
                LOG.info("Client im Raum {} ist weg, Verbindung wird entfernt", raumId);
                verloreneVerbindungen.add(verbindung);
            }
        }

        for (StreamVerbindung verbindung : verloreneVerbindungen) {
            entferne(verbindung);
        }
    }

    /**
     * Legt die Verbindung an und haengt sie in die Raumliste.
     * Der Emitter bekommt Timeout 0, damit der Stream bei langer Messung nicht von
     * selbst schliesst. Die drei Callbacks laufen in Tomcat-Threads und entfernen
     * dieselbe Verbindung; entferne ist idempotent.
     *
     * @param raumId Der Raum der Verbindung
     * @param holtNach Startzustand der Verbindung
     * @return Die neue Verbindung
     */
    private StreamVerbindung neueVerbindung(UUID raumId, boolean holtNach) {
        SseEmitter emitter = new SseEmitter(0L);
        StreamVerbindung verbindung = new StreamVerbindung(emitter, raumId, holtNach);

        List<StreamVerbindung> verbindungen = verbindungenProRaum.get(raumId);
        if (verbindungen == null) {
            verbindungen = new ArrayList<>();
            verbindungenProRaum.put(raumId, verbindungen);
        }
        verbindungen.add(verbindung);

        emitter.onCompletion(() -> entferne(verbindung));
        emitter.onTimeout(() -> entferne(verbindung));
        emitter.onError(fehler -> entferne(verbindung));

        return verbindung;
    }

    /**
     * Schickt den SSE-Kommentar :connected.
     * Damit sieht der Client sofort, dass der Stream offen ist, auch wenn noch
     * keine Nachricht kommt. EventSource ignoriert Kommentarzeilen.
     *
     * @param verbindung Die Verbindung
     * @throws IOException Wenn der Client weg ist
     */
    private void sendeKommentar(StreamVerbindung verbindung) throws IOException {
        SseEmitter.SseEventBuilder kommentar = SseEmitter.event();
        kommentar.comment("connected");
        verbindung.getEmitter().send(kommentar);
    }

    /**
     * Schreibt ein Ereignis mit id:- und data:-Zeile.
     * Der Text wird als TEXT_PLAIN geschrieben, damit der JSON-Text genau so
     * hinausgeht, wie er auf chat.persist lag.
     *
     * @param verbindung Die Verbindung
     * @param ereignis Das Ereignis
     * @throws IOException Wenn der Client weg ist
     */
    private void sendeEreignis(StreamVerbindung verbindung, LiveEreignis ereignis) throws IOException {
        SseEmitter.SseEventBuilder builder = SseEmitter.event();
        builder.id(ereignis.getNachrichtenId());
        builder.data(ereignis.getJsonText(), MediaType.TEXT_PLAIN);
        verbindung.getEmitter().send(builder);
    }
}