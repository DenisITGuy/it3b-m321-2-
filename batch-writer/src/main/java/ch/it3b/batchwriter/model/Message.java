package ch.it3b.batchwriter.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

// @Entity = Markiert die Klasse als JPA-Entität (wird in Datenbank gespeichert)
// @Table(name = "message") = Die Datenbank-Tabelle heißt "message"
@Entity
@Table(name = "message")
public class Message {

    // @Id = Primärschlüssel (eindeutige ID)
    // @Column = Mapping zur Datenbank-Spalte "id"
    // nullable = false = Feld darf nicht null sein
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    // Mapping zur Spalte "room_id"
    @Column(name = "room_id", nullable = false)
    private UUID roomId;

    // Mapping zur Spalte "sender"
    @Column(name = "sender", nullable = false)
    private String sender;

    // Mapping zur Spalte "content"
    // columnDefinition = "TEXT" = Spaltentyp ist TEXT (für lange Texte)
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    // Mapping zur Spalte "created_at"
    // Instant = Zeitpunkt in UTC
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    // protected Constructor = Wird von JPA/Hibernate benötigt
    // JPA erstellt Objekte über Reflection, braucht einen leeren Konstruktor
    protected Message() {}

    // Normaler Konstruktor: Wird benutzt um neue Message-Objekte zu erstellen
    public Message(UUID id, UUID roomId, String sender, String content, Instant createdAt) {
        this.id = id;
        this.roomId = roomId;
        this.sender = sender;
        this.content = content;
        this.createdAt = createdAt;
    }

    // Getter-Methoden: Liefern die Werte zurück
    public UUID getId() { return id; }
    public UUID getRoomId() { return roomId; }
    public String getSender() { return sender; }
    public String getContent() { return content; }
    public Instant getCreatedAt() { return createdAt; }
}