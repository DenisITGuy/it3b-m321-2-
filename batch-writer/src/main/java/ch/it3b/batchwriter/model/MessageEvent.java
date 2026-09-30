package ch.it3b.batchwriter.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Repraesentiert das JSON-Format einer Nachricht aus der Queue 'chat.persist'.
 * Die Felder entsprechen exakt PLANUNG.md Abschnitt 3.7.
 */
public class MessageEvent {

    private UUID id;
    private UUID roomId;
    private String senderId;
    private String senderName;
    private String content;
    private Instant sentAt;

    /** Standard-Konstruktor fuer Jackson. */
    public MessageEvent() {
    }

    /**
     * All-Args Konstruktor (wichtig fuer die Integrationstests).
     *
     * @param id Eindeutige ID
     * @param roomId Raum-ID
     * @param senderId Keycloak-Sub des Absenders
     * @param senderName Anzeigename des Absenders
     * @param content Text
     * @param sentAt Sendezeitpunkt
     */
    public MessageEvent(UUID id, UUID roomId, String senderId, String senderName, String content, Instant sentAt) {
        this.id = id;
        this.roomId = roomId;
        this.senderId = senderId;
        this.senderName = senderName;
        this.content = content;
        this.sentAt = sentAt;
    }

    /** @return Die eindeutige ID der Nachricht */
    public UUID getId() { return id; }

    /** @param id Die eindeutige ID der Nachricht */
    public void setId(UUID id) { this.id = id; }

    /** @return Die ID des Chat-Raums */
    public UUID getRoomId() { return roomId; }

    /** @param roomId Die ID des Chat-Raums */
    public void setRoomId(UUID roomId) { this.roomId = roomId; }

    /** @return Die Keycloak-Sub des Absenders */
    public String getSenderId() { return senderId; }

    /** @param senderId Die Keycloak-Sub des Absenders */
    public void setSenderId(String senderId) { this.senderId = senderId; }

    /** @return Der Anzeigename des Absenders */
    public String getSenderName() { return senderName; }

    /** @param senderName Der Anzeigename des Absenders */
    public void setSenderName(String senderName) { this.senderName = senderName; }

    /** @return Der Nachrichtentext */
    public String getContent() { return content; }

    /** @param content Der Nachrichtentext */
    public void setContent(String content) { this.content = content; }

    /** @return Der Sendezeitpunkt */
    public Instant getSentAt() { return sentAt; }

    /** @param sentAt Der Sendezeitpunkt */
    public void setSentAt(Instant sentAt) { this.sentAt = sentAt; }
}