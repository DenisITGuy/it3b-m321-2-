package ch.it3b.chat.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Eine einzelne Chat-Nachricht, wie sie in der Tabelle "messages" liegt.
 * JPA erzeugt die Tabelle automatisch fuer uns (siehe ddl-auto in application.yml).
 */
@Entity
@Table(name = "messages")
public class ChatMessage {

    /** Eindeutige ID. Wird bewusst vom Service vergeben, nicht von der Datenbank. */
    @Id
    private UUID id;

    /** ID des Chat-Raums, in dem die Nachricht geschrieben wurde. */
    @Column(name = "room_id", nullable = false)
    private UUID roomId;

    /** Benutzername des Absenders. Spaeter aus dem JWT, im Moment noch fest gesetzt. */
    @Column(name = "sender", nullable = false, length = 50)
    private String sender;

    /** Der eigentliche Nachrichtentext. */
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    /** Zeitpunkt des Sendens. Setzt der Service, nicht die Datenbank. */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** Erzeugt eine neue Nachricht und vergibt dabei sofort ID und Zeitstempel. */
    public ChatMessage(UUID roomId, String sender, String content) {
        this.id = UUID.randomUUID();
        this.roomId = roomId;
        this.sender = sender;
        this.content = content;
        this.createdAt = Instant.now();
    }

    /** Pflicht fuer JPA: darueber baut es Objekte aus gelesenen Tabellenzeilen auf. */
    protected ChatMessage() {
    }

    public UUID getId() {
        return id;
    }

    public UUID getRoomId() {
        return roomId;
    }

    public String getSender() {
        return sender;
    }

    public String getContent() {
        return content;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}