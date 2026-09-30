package ch.it3b.batchwriter.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Repraesentiert eine Chat-Nachricht in der Datenbank (Tabelle 'message').
 * Das Schema entspricht exakt PLANUNG.md Abschnitt 3.7.
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

    /** Keycloak-Sub des Absenders (sender_id laut PLANUNG.md 3.7). */
    @Column(name = "sender_id", nullable = false)
    private String senderId;

    /** Denormalisierter Anzeigename des Absenders (sender_name laut PLANUNG.md 3.7). */
    @Column(name = "sender_name", nullable = false)
    private String senderName;

    /** Der eigentliche Text der Nachricht. */
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    /** Zeitpunkt, an dem die Nachricht gesendet wurde (sent_at laut PLANUNG.md 3.7). */
    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    /** Standard-Konstruktor fuer JPA/Hibernate. */
    protected Message() {
    }

    /**
     * Konstruktor zum Erstellen einer neuen Nachricht.
     *
     * @param id Eindeutige ID
     * @param roomId ID des Raums
     * @param senderId Keycloak-Sub des Absenders
     * @param senderName Anzeigename des Absenders
     * @param content Nachrichtentext
     * @param sentAt Sendezeitpunkt
     */
    public Message(UUID id, UUID roomId, String senderId, String senderName, String content, Instant sentAt) {
        this.id = id;
        this.roomId = roomId;
        this.senderId = senderId;
        this.senderName = senderName;
        this.content = content;
        this.sentAt = sentAt;
    }

    /** @return Die eindeutige ID der Nachricht */
    public UUID getId() { return id; }

    /** @return Die ID des Chat-Raums */
    public UUID getRoomId() { return roomId; }

    /** @return Die Keycloak-Sub des Absenders */
    public String getSenderId() { return senderId; }

    /** @return Der Anzeigename des Absenders */
    public String getSenderName() { return senderName; }

    /** @return Der Nachrichtentext */
    public String getContent() { return content; }

    /** @return Der Sendezeitpunkt */
    public Instant getSentAt() { return sentAt; }
}