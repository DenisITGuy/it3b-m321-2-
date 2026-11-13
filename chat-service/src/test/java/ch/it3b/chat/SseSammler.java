package ch.it3b.chat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Liest einen Server-Sent-Events-Strom in einem eigenen Thread und sammelt die
 * id:- und data:-Zeilen. Wird im Test als echter HTTP-Client benutzt, damit der
 * Weg ueber den Socket geprueft wird und nicht nur ein Methodenaufruf.
 */
public class SseSammler implements Runnable {

    private final InputStream quelle;
    private final List<String> ids = new ArrayList<>();
    private final List<String> daten = new ArrayList<>();
    private boolean verbunden;
    private boolean beendet;

    /**
     * Legt einen Sammler fuer einen Strom an.
     *
     * @param quelle Der Antwortstrom des HTTP-Aufrufs
     */
    public SseSammler(InputStream quelle) {
        this.quelle = quelle;
    }

    /**
     * Liest Zeilen bis der Strom endet oder geschlossen wird.
     * Kommentarzeilen markieren, dass der Stream offen ist. Lesen blockiert,
     * deshalb laeuft dies in einem Daemon-Thread; beendet wird durch Schliessen
     * der Quelle, was hier eine IOException ausloest.
     */
    @Override
    public void run() {
        BufferedReader leser = new BufferedReader(new InputStreamReader(quelle, StandardCharsets.UTF_8));
        try {
            while (true) {
                String zeile = leser.readLine();
                if (zeile == null) {
                    break;
                }
                if (zeile.startsWith(":")) {
                    markiereVerbunden();
                }
                if (zeile.startsWith("id:")) {
                    speichereId(zeile.substring(3).trim());
                }
                if (zeile.startsWith("data:")) {
                    speichereDaten(zeile.substring(5).trim());
                }
            }
        } catch (IOException fehler) {
            // Strom wurde geschlossen, das ist das normale Ende des Sammlers
        }
        markiereBeendet();
    }

    /**
     * Merkt, dass der Stream den Kommentar gesendet hat.
     */
    private synchronized void markiereVerbunden() {
        verbunden = true;
    }

    /**
     * Merkt das Ende des Lesens.
     */
    private synchronized void markiereBeendet() {
        beendet = true;
    }

    /**
     * Speichert eine Nachrichten-ID.
     *
     * @param id Die ID aus der id:-Zeile
     */
    private synchronized void speichereId(String id) {
        ids.add(id);
    }

    /**
     * Speichert eine JSON-Zeile.
     *
     * @param json Der Text aus der data:-Zeile
     */
    private synchronized void speichereDaten(String json) {
        daten.add(json);
    }

    /**
     * Sagt, ob der Stream schon offen ist.
     *
     * @return True nach der ersten Kommentarzeile
     */
    public synchronized boolean istVerbunden() {
        return verbunden;
    }

    /**
     * Sagt, ob das Lesen beendet ist.
     *
     * @return True nach Stromende
     */
    public synchronized boolean istBeendet() {
        return beendet;
    }

    /**
     * Gibt die gesammelten IDs in Eingangsreihenfolge zurueck.
     *
     * @return Kopie der ID-Liste
     */
    public synchronized List<String> getIds() {
        return new ArrayList<>(ids);
    }

    /**
     * Gibt die gesammelten JSON-Zeilen zurueck.
     *
     * @return Kopie der Datenliste
     */
    public synchronized List<String> getDaten() {
        return new ArrayList<>(daten);
    }

    /**
     * Schliesst die Quelle und beendet damit den Lesethread.
     */
    public void schliesse() {
        try {
            quelle.close();
        } catch (IOException fehler) {
            // beim Schliessen eines toten Stroms unwichtig
        }
    }
}