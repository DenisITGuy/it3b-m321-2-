package ch.it3b.chat.stream;

import ch.it3b.chat.messaging.MessageEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Holt fehlende Nachrichten aus der Tabelle message.
 * Die Nachrichten-ID ist eine UUID und traegt keine Ordnung, deshalb wird der
 * Zeitpunkt der letzten Nachricht bestimmt und der Paarvergleich
 * (created_at, id) benutzt. Serialisiert wird mit demselben Converter wie der
 * Versand, damit nachgeholte Ereignisse dasselbe Format haben wie live.
 */
@Component
public class NachholDienst {

    private static final Logger LOG = LoggerFactory.getLogger(NachholDienst.class);

    private final JdbcTemplate jdbcTemplate;
    private final Jackson2JsonMessageConverter messageConverter;

    /**
     * Konstruktor fuer Dependency Injection.
     *
     * @param jdbcTemplate Fuehrt die Abfragen aus
     * @param messageConverter Derselbe Converter, der auch publiziert
     */
    public NachholDienst(JdbcTemplate jdbcTemplate,
                         Jackson2JsonMessageConverter messageConverter) {
        this.jdbcTemplate = jdbcTemplate;
        this.messageConverter = messageConverter;
    }

    /**
     * Liest alle Nachrichten des Raums nach der letzten gesehenen Nachricht.
     * Ein striktes created_at groesser wuerde Nachrichten mit gleichem Zeitstempel
     * und groesserer id verschlucken, deshalb der Paarvergleich. Ist die letzte ID
     * nicht in der Tabelle oder gehoert sie einem anderen Raum, wird wie ohne ID
     * behandelt: kein Nachholen, nur live. Kehrseite: eine noch nicht persistierte
     * ID ueberspringt das Nachholen still, S7 wartet deshalb zehn Sekunden.
     *
     * @param raumId Der Raum des Clients
     * @param letzteId Die zuletzt gesehene Nachrichten-ID
     * @return Die fehlenden Ereignisse in Reihenfolge, leer wenn nichts fehlt
     */
    public List<LiveEreignis> leseFehlende(UUID raumId, UUID letzteId) {
        List<Map<String, Object>> ankerZeilen = jdbcTemplate.queryForList(
                "SELECT created_at, id FROM message WHERE id = ? AND room_id = ?",
                letzteId, raumId);

        if (ankerZeilen.isEmpty()) {
            LOG.info("Last-Event-ID {} unbekannt, es wird nicht nachgeholt", letzteId);
            return new ArrayList<>();
        }

        Map<String, Object> ankerZeile = ankerZeilen.get(0);
        Timestamp ankerZeit = (Timestamp) ankerZeile.get("created_at");
        UUID ankerId = (UUID) ankerZeile.get("id");

        List<Map<String, Object>> zeilen = jdbcTemplate.queryForList(
                "SELECT id, room_id, sender, content, created_at FROM message "
                        + "WHERE room_id = ? AND (created_at > ? OR (created_at = ? AND id > ?)) "
                        + "ORDER BY created_at, id",
                raumId, ankerZeit, ankerZeit, ankerId);

        List<LiveEreignis> ereignisse = new ArrayList<>();
        for (Map<String, Object> zeile : zeilen) {
            MessageEvent event = baueEvent(zeile);
            String jsonText = serialisiere(event);
            LiveEreignis ereignis = new LiveEreignis(event.id().toString(), jsonText);
            ereignisse.add(ereignis);
        }
        return ereignisse;
    }

    /**
     * Baut aus einer Tabellenzeile ein MessageEvent.
     * Die Spalten entsprechen der Tabelle aus Bewertung 1.
     *
     * @param zeile Die Zeile aus message
     * @return Das Ereignis
     */
    private MessageEvent baueEvent(Map<String, Object> zeile) {
        UUID nachrichtenId = (UUID) zeile.get("id");
        UUID raumId = (UUID) zeile.get("room_id");
        String sender = (String) zeile.get("sender");
        String content = (String) zeile.get("content");
        Timestamp zeitstempel = (Timestamp) zeile.get("created_at");
        Instant createdAt = zeitstempel.toInstant();
        return new MessageEvent(nachrichtenId, raumId, sender, content, createdAt);
    }

    /**
     * Serialisiert ein Ereignis genau wie der Versand nach RabbitMQ.
     * Nur so ist createdAt auch hier eine Epoch-Dezimalzahl und der Client braucht
     * fuer live und nachgeholt denselben Parser.
     *
     * @param event Das Ereignis
     * @return Der JSON-Text
     */
    private String serialisiere(MessageEvent event) {
        MessageProperties eigenschaften = new MessageProperties();
        Message nachricht = messageConverter.toMessage(event, eigenschaften);
        return new String(nachricht.getBody(), StandardCharsets.UTF_8);
    }
}