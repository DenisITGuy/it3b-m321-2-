package ch.it3b.batchwriter.repository;

import ch.it3b.batchwriter.model.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

/**
 * Repository fuer den Zugriff auf die 'message' Tabelle.
 * Bietet Methoden zum sicheren Einfuegen von Nachrichten.
 */
@Repository
public interface MessageRepository extends JpaRepository<Message, UUID> {

    /**
     * Fuegt eine Nachricht ein, ignoriert sie aber, falls die ID schon existiert.
     * Dies ist entscheidend fuer Szenario S5 (Idempotenz) und S6 (Skalierung).
     * 
     * WICHTIG: Wir uebergeben die UUIDs als String, damit der PostgreSQL JDBC-Treiber
     * sie korrekt als Text an die DB sendet und der 'cast(... as uuid)' funktioniert.
     *
     * @param id ID der Nachricht (als String)
     * @param roomId ID des Raums (als String)
     * @param sender Absender
     * @param content Text
     * @param createdAt Zeitstempel
     */
    @Modifying
    @Query(value = "INSERT INTO message (id, room_id, sender, content, created_at) " +
                   "VALUES (cast(:id as uuid), cast(:roomId as uuid), :sender, :content, :createdAt) " +
                   "ON CONFLICT (id) DO NOTHING", nativeQuery = true)
    void saveIfNotExists(@Param("id") String id,
                         @Param("roomId") String roomId,
                         @Param("sender") String sender,
                         @Param("content") String content,
                         @Param("createdAt") Instant createdAt);
}