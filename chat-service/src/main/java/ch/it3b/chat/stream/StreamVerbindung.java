package ch.it3b.chat.stream;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

/**
 * Eine offene Stream-Verbindung zu einem Client.
 * Haelt den Emitter und den Raum zusammen, damit beim Aufraeumen bekannt ist,
 * aus welcher Raumliste die Verbindung entfernt werden muss.
 */
public class StreamVerbindung {

    private final SseEmitter emitter;
    private final UUID raumId;

    /**
     * Legt eine Verbindung fuer einen Raum an.
     *
     * @param emitter Der Emitter, der die Ereignisse an den Client schreibt
     * @param raumId Der Raum, den dieser Client sehen will
     */
    public StreamVerbindung(SseEmitter emitter, UUID raumId) {
        this.emitter = emitter;
        this.raumId = raumId;
    }

    /**
     * Gibt den Emitter dieser Verbindung zurueck.
     *
     * @return Der Emitter zum Schreiben
     */
    public SseEmitter getEmitter() {
        return emitter;
    }

    /**
     * Gibt den Raum dieser Verbindung zurueck.
     *
     * @return Die Raum-ID
     */
    public UUID getRaumId() {
        return raumId;
    }
}