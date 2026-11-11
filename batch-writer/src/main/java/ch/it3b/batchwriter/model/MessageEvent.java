package ch.it3b.batchwriter.model;

import java.time.Instant;
import java.util.UUID;

// MessageEvent = Datenobjekt für Nachrichten aus RabbitMQ
// Wird benutzt, um JSON aus der Queue in ein Java-Objekt umzuwandeln
public class MessageEvent {

    private UUID id;
    private UUID roomId;
    private String sender;
    private String content;
    private Instant createdAt;

    // Leerer Konstruktor: Wird von Jackson (JSON-Bibliothek) benötigt
    // Jackson erstellt zuerst ein leeres Objekt und setzt dann die Werte über Setter
    public MessageEvent() {}

    // Normaler Konstruktor: Kann benutzt werden, um manuell Objekte zu erstellen
    public MessageEvent(UUID id, UUID roomId, String sender, String content, Instant createdAt) {
        this.id = id;
        this.roomId = roomId;
        this.sender = sender;
        this.content = content;
        this.createdAt = createdAt;
    }

    // Getter und Setter:
    // Getter liefern die Werte zurück
    // Setter erlauben Jackson, die Werte aus JSON zu setzen
    
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