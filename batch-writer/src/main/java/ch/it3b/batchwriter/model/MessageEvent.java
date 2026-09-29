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