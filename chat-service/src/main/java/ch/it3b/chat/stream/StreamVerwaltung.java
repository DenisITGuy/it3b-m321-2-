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
 * deshalb ist jede Methode synchronized: die HashMap ist nicht threadsicher.
 */
@Component
public class StreamVerwaltung {

    private static final Logger LOG = LoggerFactory.getLogger(StreamVerwaltung.class);

    /** Raum-ID auf die Liste der offenen Verbindungen in diesem Raum. */
    private final Map<UUID, List<StreamVerbindung>> verbindungenProRaum = new HashMap<>();

    /**
     * Registriert eine neue Verbindung fuer einen Raum und bestaetigt sie sofort.
     * Der Emitter bekommt Timeout 0, damit der Stream bei langer Messung (S6)
     * nicht von selbst schliesst. Direkt nach der Registrierung geht der
     * SSE-Kommentar :connected hinaus, damit der Client das Offensein sieht,
     * bevor die erste Nachricht kommt.
     *
     * @param raumId Der Raum, den der Client hoeren will
     * @return Der Emitter, den der Controller zurueckgibt
     * @throws IOException Wenn der Client schon wieder weg ist
     */
    public synchronized SseEmitter registriere(UUID raumId) throws IOException {
        SseEmitter emitter = new SseEmitter(0L);
        StreamVerbindung verbindung = new StreamVerbindung(emitter, raumId);

        List<StreamVerbindung> verbindungen = verbindungenProRaum.get(raumId);
        if (verbindungen == null) {
            verbindungen = new ArrayList<>();
            verbindungenProRaum.put(raumId, verbindungen);
        }
        verbindungen.add(verbindung);

        // Alle drei Callbacks laufen in Tomcat-Threads, nicht im Consumer-Thread.
        // Sie entfernen dieselbe Verbindung; entferne ist idempotent.
        emitter.onCompletion(() -> entferne(verbindung));
        emitter.onTimeout(() -> entferne(verbindung));
        emitter.onError(fehler -> entferne(verbindung));

        SseEmitter.SseEventBuilder kommentar = SseEmitter.event();
        kommentar.comment("connected");
        emitter.send(kommentar);

        LOG.info("Stream geoeffnet fuer Raum {}", raumId);
        return emitter;
    }

    /**
     * Entfernt eine Verbindung aus ihrer Raumliste.
     * Idempotent, weil Completion, Timeout und Error denselben Abbau ausloesen
     * koennen. Ohne das Entfernen wuerde die Liste wachsen und jeder weitere
     * Versand erneut fehlschlagen. Die Logzeile macht den Abbau sichtbar, weil
     * ein Client-Abbruch sonst unbemerkt bleibt.
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
     * Der JSON-Text wird unveraendert als Text geschrieben, nicht als Objekt:
     * mit APPLICATION_JSON wuerde Jackson den String noch einmal serialisieren
     * und in Anfuehrungszeichen setzen. Eine gescheiterte Verbindung wird
     * eingesammelt und danach entfernt, die Exception nicht weitergeworfen,
     * damit der Consumer keine Nachricht zurueck in die Queue legt.
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

        List<StreamVerbindung> verloreneVerbindungen = new ArrayList<>();
        for (StreamVerbindung verbindung : verbindungen) {
            try {
                SseEmitter.SseEventBuilder ereignis = SseEmitter.event();
                ereignis.id(nachrichtenId);
                ereignis.data(jsonText, MediaType.TEXT_PLAIN);
                verbindung.getEmitter().send(ereignis);
            } catch (IOException | IllegalStateException fehler) {
                LOG.info("Client im Raum {} ist weg, Verbindung wird entfernt", raumId);
                verloreneVerbindungen.add(verbindung);
            }
        }

        for (StreamVerbindung verbindung : verloreneVerbindungen) {
            entferne(verbindung);
        }
    }
}