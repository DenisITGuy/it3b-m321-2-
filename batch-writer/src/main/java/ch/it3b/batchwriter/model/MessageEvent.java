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

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getRoomId() { return roomId; }
    public void setRoomId(UUID roomId) { this.roomId = roomId; }

    public String getSender() { return sender; }
    public void setSender(String sender) { this.sender = sender; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}