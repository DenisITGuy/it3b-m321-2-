package ch.it3b.chat.stream;

/**
 * Ein Ereignis fuer den Stream: die Nachrichten-ID fuer die id:-Zeile und der
 * unveraenderte JSON-Text fuer die data:-Zeile.
 * Wird beim Nachholen aus der Datenbank gebaut und beim Puffern waehrend des
 * Nachholens benutzt, damit beide Wege dieselbe Form haben.
 */
public class LiveEreignis {

    private final String nachrichtenId;
    private final String jsonText;

    /**
     * Legt ein Ereignis an.
     *
     * @param nachrichtenId Die ID der Nachricht
     * @param jsonText Der JSON-Text im Wire-Format von chat.persist
     */
    public LiveEreignis(String nachrichtenId, String jsonText) {
        this.nachrichtenId = nachrichtenId;
        this.jsonText = jsonText;
    }

    /**
     * Gibt die Nachrichten-ID zurueck.
     *
     * @return Die ID fuer die id:-Zeile
     */
    public String getNachrichtenId() {
        return nachrichtenId;
    }

    /**
     * Gibt den JSON-Text zurueck.
     *
     * @return Der Text fuer die data:-Zeile
     */
    public String getJsonText() {
        return jsonText;
    }
}