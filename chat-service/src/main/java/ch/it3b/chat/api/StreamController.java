package ch.it3b.chat.api;

import ch.it3b.chat.stream.StreamVerwaltung;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.UUID;

/**
 * Der Anzeigeweg nach aussen: GET /stream haelt eine Server-Sent-Events-Verbindung
 * offen. Der Raum kommt als Pflichtparameter; fehlt er oder ist er keine UUID,
 * erzeugt Spring selbst HTTP 400, bevor der Stream oeffnet.
 */
@RestController
public class StreamController {

    private final StreamVerwaltung streamVerwaltung;

    /**
     * Konstruktor fuer Dependency Injection.
     *
     * @param streamVerwaltung Haelt die Verbindungen pro Raum
     */
    public StreamController(StreamVerwaltung streamVerwaltung) {
        this.streamVerwaltung = streamVerwaltung;
    }

    /**
     * Oeffnet einen Stream fuer einen Raum.
     * Der Typ UUID im Parameter ist die ganze Pruefung: Spring wandelt um und
     * antwortet bei fehlendem oder ungueltigem Wert mit 400.
     *
     * @param roomId Der Raum, den der Client hoeren will
     * @return Der offene Emitter
     * @throws IOException Wenn der Client die Verbindung sofort wieder trennt
     */
    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter oeffneStream(@RequestParam UUID roomId) throws IOException {
        return streamVerwaltung.registriere(roomId);
    }
}