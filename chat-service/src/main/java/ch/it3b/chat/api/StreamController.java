package ch.it3b.chat.api;

import ch.it3b.chat.stream.LiveEreignis;
import ch.it3b.chat.stream.NachholDienst;
import ch.it3b.chat.stream.StreamVerbindung;
import ch.it3b.chat.stream.StreamVerwaltung;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Der Anzeigeweg nach aussen: GET /stream haelt eine Server-Sent-Events-Verbindung
 * offen. Der Raum ist Pflichtparameter; fehlt er oder ist er keine UUID, erzeugt
 * Spring selbst HTTP 400, bevor der Stream oeffnet. Mit Last-Event-ID wird
 * nachgeholt, ohne Last-Event-ID beruehrt der Weg die Datenbank nicht.
 */
@RestController
public class StreamController {

    private static final Logger LOG = LoggerFactory.getLogger(StreamController.class);

    private final StreamVerwaltung streamVerwaltung;
    private final NachholDienst nachholDienst;

    /**
     * Konstruktor fuer Dependency Injection.
     *
     * @param streamVerwaltung Haelt die Verbindungen pro Raum
     * @param nachholDienst Liest fehlende Nachrichten aus der Datenbank
     */
    public StreamController(StreamVerwaltung streamVerwaltung, NachholDienst nachholDienst) {
        this.streamVerwaltung = streamVerwaltung;
        this.nachholDienst = nachholDienst;
    }

    /**
     * Oeffnet einen Stream fuer einen Raum, mit Nachholen wenn eine Last-Event-ID
     * mitkommt. Die Datenbankabfrage laeuft hier synchron, bevor der Emitter
     * zurueckgegeben wird: nur so kann bei gestoppter Datenbank noch HTTP 503
     * geantwortet werden, danach ist der Status bereits hinaus.
     *
     * @param roomId Der Raum, den der Client hoeren will
     * @param lastEventId Die zuletzt gesehene Nachrichten-ID, darf fehlen
     * @return Der offene Emitter
     * @throws IOException Wenn der Client die Verbindung sofort wieder trennt
     */
    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter oeffneStream(@RequestParam UUID roomId,
                                   @RequestHeader(name = "Last-Event-ID", required = false) String lastEventId)
            throws IOException {

        UUID letzteId = leseLetzteId(lastEventId);
        if (letzteId == null) {
            return streamVerwaltung.registriereLive(roomId);
        }

        StreamVerbindung verbindung = streamVerwaltung.registriereZumNachholen(roomId);
        List<LiveEreignis> fehlende;
        try {
            fehlende = nachholDienst.leseFehlende(roomId, letzteId);
        } catch (DataAccessException fehler) {
            streamVerwaltung.entferne(verbindung);
            LOG.warn("Nachholen nicht moeglich, Datenbank nicht erreichbar");
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Nachholen nicht moeglich");
        }

        streamVerwaltung.spieleNach(verbindung, fehlende);
        return verbindung.getEmitter();
    }

    /**
     * Wandelt den Header in eine UUID um.
     * Fehlt der Header oder ist er keine UUID, wird er wie keine ID behandelt:
     * der Client kann nichts Besseres tun als live weiterzulesen.
     *
     * @param lastEventId Der Header-Wert, darf null sein
     * @return Die UUID oder null
     */
    private UUID leseLetzteId(String lastEventId) {
        if (lastEventId == null) {
            return null;
        }
        if (lastEventId.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(lastEventId.trim());
        } catch (IllegalArgumentException fehler) {
            LOG.info("Last-Event-ID {} ist keine UUID, es wird nicht nachgeholt", lastEventId);
            return null;
        }
    }
}