package ch.it3b.batchwriter.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Repraesentiert eine Chat-Nachricht in der Datenbank (Tabelle 'message').
 * Das Schema entspricht den Spalten aus PLANUNG.md 3.7.
 */
@Entity
@Table(name = "message")
public class Message {

    /** Eindeutige ID der Nachricht. Dient auch zur Duplikat-Erkennung (S5). */
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    /** ID des Chat-Raums, zu dem die Nachricht gehoert. */
    @Column(name = "room_id", nullable = false)
    private UUID roomId;

    /** Benutzername des Absenders. */
    @Column(name = "sender", nullable = false, length = 50)
    private String sender;

    /** Der eigentliche Text der Nachricht. */
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    /** Zeitpunkt, an dem die Nachricht urspruenglich gesendet wurde. */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** Standard-Konstruktor fuer JPA/Hibernate. */
    protected Message() {
    }

    /**
     * Konstruktor zum Erstellen einer neuen Nachricht.
     *
     * @param id Eindeutige ID
     * @param roomId ID des Raums
     * @param sender Absender
     * @param content Nachrichtentext
     * @param createdAt Zeitstempel
     */
    public Message(UUID id, UUID roomId, String sender, String content, Instant createdAt) {
        this.id = id;
        this.roomId = roomId;
        this.sender = sender;
        this.content = content;
        this.createdAt = createdAt;
    }

    /** @return Die eindeutige ID der Nachricht */
    public UUID getId() { return id; }

    /** @return Die ID des Chat-Raums */
    public UUID getRoomId() { return roomId; }

    /** @return Der Benutzername des Absenders */
    public String getSender() { return sender; }

    /** @return Der Nachrichtentext */
    public String getContent() { return content; }

    /** @return Der urspruengliche Sendezeitpunkt */
    public Instant getCreatedAt() { return createdAt; }
}