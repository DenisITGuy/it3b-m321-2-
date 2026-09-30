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
     * @param id ID der Nachricht (als String)
     * @param roomId ID des Raums (als String)
     * @param senderId Keycloak-Sub des Absenders
     * @param senderName Anzeigename des Absenders
     * @param content Text
     * @param sentAt Sendezeitpunkt
     */
    @Modifying
    @Query(value = "INSERT INTO message (id, room_id, sender_id, sender_name, content, sent_at) " +
                   "VALUES (cast(:id as uuid), cast(:roomId as uuid), :senderId, :senderName, :content, :sentAt) " +
                   "ON CONFLICT (id) DO NOTHING", nativeQuery = true)
    void saveIfNotExists(@Param("id") String id,
                         @Param("roomId") String roomId,
                         @Param("senderId") String senderId,
                         @Param("senderName") String senderName,
                         @Param("content") String content,
                         @Param("sentAt") Instant sentAt);
}