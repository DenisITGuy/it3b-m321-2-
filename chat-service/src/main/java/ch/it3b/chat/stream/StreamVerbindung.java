package ch.it3b.chat.stream;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Eine offene Stream-Verbindung zu einem Client.
 * Haelt den Emitter, den Raum und den Zustand waehrend des Nachholens zusammen.
 * Alle Felder werden nur unter der Sperre der StreamVerwaltung angefasst.
 */
public class StreamVerbindung {

    private final SseEmitter emitter;
    private final UUID raumId;
    private final List<LiveEreignis> puffer;
    private final Set<String> gesendeteIds;
    private boolean holtNach;

    /**
     * Legt eine Verbindung fuer einen Raum an.
     *
     * @param emitter Der Emitter, der die Ereignisse an den Client schreibt
     * @param raumId Der Raum, den dieser Client sehen will
     * @param holtNach True, solange nachgeholt wird und live gepuffert werden muss
     */
    public StreamVerbindung(SseEmitter emitter, UUID raumId, boolean holtNach) {
        this.emitter = emitter;
        this.raumId = raumId;
        this.holtNach = holtNach;
        this.puffer = new ArrayList<>();
        this.gesendeteIds = new HashSet<>();
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

    /**
     * Sagt, ob diese Verbindung gerade nachholt.
     *
     * @return True, wenn live ankommende Ereignisse gepuffert werden muessen
     */
    public boolean istNachholen() {
        return holtNach;
    }

    /**
     * Schaltet die Verbindung auf live um.
     *
     * @param holtNach Neuer Zustand
     */
    public void setHoltNach(boolean holtNach) {
        this.holtNach = holtNach;
    }

    /**
     * Merkt ein Ereignis, das waehrend des Nachholens live ankam.
     *
     * @param ereignis Das Ereignis, das noch nicht gesendet werden darf
     */
    public void puffere(LiveEreignis ereignis) {
        puffer.add(ereignis);
    }

    /**
     * Gibt die gepufferten Ereignisse in Eingangsreihenfolge zurueck.
     *
     * @return Die Liste der zurueckgehaltenen Ereignisse
     */
    public List<LiveEreignis> getPuffer() {
        return puffer;
    }

    /**
     * Leert den Puffer nach dem Ausspielen.
     */
    public void leerePuffer() {
        puffer.clear();
    }

    /**
     * Merkt eine bereits gesendete Nachrichten-ID.
     *
     * @param nachrichtenId Die ID, die schon beim Client ist
     */
    public void merkeGesendet(String nachrichtenId) {
        gesendeteIds.add(nachrichtenId);
    }

    /**
     * Prueft, ob eine Nachrichten-ID schon gesendet wurde.
     *
     * @param nachrichtenId Die zu pruefende ID
     * @return True, wenn sie schon beim Client ist
     */
    public boolean wurdeGesendet(String nachrichtenId) {
        return gesendeteIds.contains(nachrichtenId);
    }

    /**
     * Vergisst die gesendeten IDs nach dem Umschalten auf live.
     * Live kommt jede Nachricht von einem einzelnen Consumer genau einmal, ein
     * Abgleich ist dann nicht mehr noetig. Ohne das Verwerfen wuerde die Menge bei
     * langen Verbindungen unbegrenzt wachsen.
     */
    public void vergesseGesendeteIds() {
        gesendeteIds.clear();
    }
}