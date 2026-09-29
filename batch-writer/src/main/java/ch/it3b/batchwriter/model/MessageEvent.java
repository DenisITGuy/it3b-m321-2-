package ch.it3b.batchwriter.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Repraesentiert das JSON-Format einer Nachricht aus der Queue 'chat.persist'.
 * Wird von Jackson automatisch aus dem JSON-String deserialisiert.
 */
public class MessageEvent {

    private UUID id;
    private UUID roomId;
    private String sender;
    private String content;
    private Instant createdAt;

    /** Standard-Konstruktor fuer Jackson. */
    public MessageEvent() {
    }

    /**
     * All-Args Konstruktor (wichtig fuer die Integrationstests).
     *
     * @param id Eindeutige ID
     * @param roomId Raum-ID
     * @param sender Absender
     * @param content Text
     * @param createdAt Zeitstempel
     */
    public MessageEvent(UUID id, UUID roomId, String sender, String content, Instant createdAt) {
        this.id = id;
        this.roomId = roomId;
        this.sender = sender;
        this.content = content;
        this.createdAt = createdAt;
    }

    /** @return Die eindeutige ID der Nachricht */
    public UUID getId() { return id; }

    /** @param id Die eindeutige ID der Nachricht */
    public void setId(UUID id) { this.id = id; }

    /** @return Die ID des Chat-Raums */
    public UUID getRoomId() { return roomId; }

    /** @param roomId Die ID des Chat-Raums */
    public void setRoomId(UUID roomId) { this.roomId = roomId; }

    /** @return Der Benutzername des Absenders */
    public String getSender() { return sender; }

    /** @param sender Der Benutzername des Absenders */
    public void setSender(String sender) { this.sender = sender; }

    /** @return Der Nachrichtentext */
    public String getContent() { return content; }

    /** @param content Der Nachrichtentext */
    public void setContent(String content) { this.content = content; }

    /** @return Der urspruengliche Sendezeitpunkt */
    public Instant getCreatedAt() { return createdAt; }

    /** @param createdAt Der urspruengliche Sendezeitpunkt */
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}